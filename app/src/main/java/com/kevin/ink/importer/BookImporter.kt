package com.kevin.ink.importer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.kevin.ink.data.Book
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

private const val TAG = "BookImporter"

/**
 * Mete un libro a la biblioteca: copia el archivo dentro de la app,
 * detecta el formato y le extrae título/autor/portada.
 */
class BookImporter(private val context: Context) {

    suspend fun import(uri: Uri): Book? = withContext(Dispatchers.IO) {
        val displayName = queryDisplayName(uri)
        val mime = context.contentResolver.getType(uri)
        val format = detectFormat(displayName, mime)
        Log.i(TAG, "import uri=$uri name=$displayName mime=$mime -> format=$format")
        if (format == null) return@withContext null

        val id = UUID.randomUUID().toString()
        val ext = if (format == Book.Format.EPUB) "epub" else "pdf"

        // 1) Copiar el archivo a un lugar estable dentro de la app.
        val booksDir = File(context.filesDir, "books").apply { mkdirs() }
        val dest = File(booksDir, "$id.$ext")
        val copied = try {
            val input = context.contentResolver.openInputStream(uri)
            if (input == null) {
                Log.w(TAG, "openInputStream devolvió null para $uri")
                false
            } else {
                input.use { src -> dest.outputStream().use { out -> src.copyTo(out) } }
                true
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error copiando $uri", e)
            false
        }
        if (!copied || dest.length() == 0L) {
            Log.w(TAG, "copia fallida o vacía: copied=$copied size=${dest.length()}")
            dest.delete()
            return@withContext null
        }

        // 2) Extraer datos según el formato.
        val fallbackTitle = (displayName ?: "Libro")
            .substringBeforeLast('.')
            .ifBlank { "Libro" }

        var title = fallbackTitle
        var author: String? = null
        var coverPath: String? = null

        val coversDir = File(context.filesDir, "covers").apply { mkdirs() }

        when (format) {
            Book.Format.EPUB -> {
                val meta = EpubMetadata.read(dest)
                meta.title?.let { if (it.isNotBlank()) title = it }
                author = meta.author
                meta.coverBytes?.let { bytes ->
                    val coverFile = File(coversDir, id)   // bytes crudos; Coil reconoce el formato
                    runCatching { coverFile.writeBytes(bytes); coverPath = coverFile.absolutePath }
                }
            }
            Book.Format.PDF -> {
                PdfCover.render(dest)?.let { bitmap ->
                    val coverFile = File(coversDir, "$id.png")
                    runCatching {
                        FileOutputStream(coverFile).use { out ->
                            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 90, out)
                        }
                        coverPath = coverFile.absolutePath
                    }
                    bitmap.recycle()
                }
            }
        }

        Book(
            id = id,
            title = title,
            author = author,
            format = format,
            filePath = dest.absolutePath,
            coverPath = coverPath,
            addedAt = System.currentTimeMillis()
        )
    }

    private fun detectFormat(name: String?, mime: String?): Book.Format? {
        val lowerName = name?.lowercase().orEmpty()
        val lowerMime = mime?.lowercase().orEmpty()
        return when {
            lowerMime.contains("epub") || lowerName.endsWith(".epub") -> Book.Format.EPUB
            lowerMime.contains("pdf") || lowerName.endsWith(".pdf") -> Book.Format.PDF
            else -> null
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) cursor.getString(idx) else null
                    } else null
                }
        }.getOrNull() ?: uri.lastPathSegment
    }
}
