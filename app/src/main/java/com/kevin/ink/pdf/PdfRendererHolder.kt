package com.kevin.ink.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Dibuja páginas de un PDF como imágenes.
 * PdfRenderer NO es seguro entre hilos y solo deja abrir una página a la vez,
 * así que serializamos con un mutex. Guardamos las últimas páginas en caché.
 */
class PdfRendererHolder private constructor(
    private val pfd: ParcelFileDescriptor,
    private val renderer: PdfRenderer
) {
    private val mutex = Mutex()
    private val cache = LruCache<Int, Bitmap>(8)

    val pageCount: Int get() = renderer.pageCount

    suspend fun render(index: Int, targetWidth: Int): Bitmap? {
        if (index < 0 || index >= renderer.pageCount || targetWidth <= 0) return null
        // La clave incluye el ancho, porque al cambiar el zoom re-dibujamos a otra resolución.
        val key = index * 100_000 + targetWidth
        cache.get(key)?.let { return it }
        return mutex.withLock {
            cache.get(key) ?: withContext(Dispatchers.IO) {
                runCatching {
                    renderer.openPage(index).use { page ->
                        val scale = targetWidth.toFloat() / page.width
                        val height = (page.height * scale).toInt().coerceAtLeast(1)
                        val bitmap = Bitmap.createBitmap(targetWidth, height, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmap
                    }
                }.getOrNull()?.also { cache.put(key, it) }
            }
        }
    }

    fun close() {
        runCatching { renderer.close() }
        runCatching { pfd.close() }
        cache.evictAll()
    }

    companion object {
        fun open(file: File): PdfRendererHolder? = runCatching {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            PdfRendererHolder(pfd, PdfRenderer(pfd))
        }.getOrNull()
    }
}
