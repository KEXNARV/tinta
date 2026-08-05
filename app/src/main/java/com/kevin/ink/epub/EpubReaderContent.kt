package com.kevin.ink.epub

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.net.URLDecoder

/** Un capítulo del libro (un archivo de la "columna vertebral" / spine). */
data class Chapter(val title: String?, val file: File)

/** Una entrada de la tabla de contenidos. */
data class TocEntry(val title: String, val file: File, val fragment: String?, val depth: Int)

class EpubReaderContent(
    val rootDir: File,
    val chapters: List<Chapter>,
    val toc: List<TocEntry>
)

/** Lee la estructura de lectura (capítulos en orden + tabla de contenidos) de un EPUB descomprimido. */
object EpubReaderParser {

    fun parse(rootDir: File): EpubReaderContent? {
        return try {
            val opfFile = findOpf(rootDir) ?: return null
            val opfDir = opfFile.parentFile ?: return null

            val manifest = HashMap<String, ManifestItem>()   // id -> item
            val spineIds = ArrayList<String>()
            var ncxId: String? = null
            var navHref: String? = null

            opfFile.inputStream().use { input ->
                val p = Xml.newPullParser()
                p.setInput(input, null)
                var event = p.eventType
                while (event != XmlPullParser.END_DOCUMENT) {
                    if (event == XmlPullParser.START_TAG) {
                        when (p.name?.substringAfter(':')) {
                            "item" -> {
                                val id = p.getAttributeValue(null, "id")
                                val href = p.getAttributeValue(null, "href")
                                val props = p.getAttributeValue(null, "properties").orEmpty()
                                if (id != null && href != null) {
                                    manifest[id] = ManifestItem(href, props)
                                    if (props.contains("nav")) navHref = href
                                }
                            }
                            "spine" -> {
                                p.getAttributeValue(null, "toc")?.let { ncxId = it }
                            }
                            "itemref" -> {
                                p.getAttributeValue(null, "idref")?.let { spineIds.add(it) }
                            }
                        }
                    }
                    event = p.next()
                }
            }

            val chapters = spineIds.mapNotNull { id ->
                manifest[id]?.let { item ->
                    val (file, _) = resolveHref(opfDir, item.href)
                    if (file.exists()) Chapter(null, file) else null
                }
            }
            if (chapters.isEmpty()) return null

            // Tabla de contenidos: EPUB3 (nav) primero, luego EPUB2 (ncx), luego fallback.
            val toc = when {
                navHref != null -> parseNav(resolveHref(opfDir, navHref!!).first)
                ncxId != null && manifest[ncxId] != null ->
                    parseNcx(resolveHref(opfDir, manifest[ncxId]!!.href).first)
                else -> emptyList()
            }.ifEmpty {
                chapters.mapIndexed { i, ch -> TocEntry("Sección ${i + 1}", ch.file, null, 0) }
            }

            EpubReaderContent(rootDir, chapters, toc)
        } catch (e: Exception) {
            null
        }
    }

    private data class ManifestItem(val href: String, val properties: String)

    private fun findOpf(rootDir: File): File? {
        val container = File(rootDir, "META-INF/container.xml")
        if (!container.exists()) return null
        container.inputStream().use { input ->
            val p = Xml.newPullParser()
            p.setInput(input, null)
            var event = p.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && p.name?.substringAfter(':') == "rootfile") {
                    val full = p.getAttributeValue(null, "full-path") ?: return null
                    return resolveHref(rootDir, full).first
                }
                event = p.next()
            }
        }
        return null
    }

    /** Tabla de contenidos de un EPUB3 (documento nav con <nav epub:type="toc">). */
    private fun parseNav(navFile: File): List<TocEntry> {
        if (!navFile.exists()) return emptyList()
        val baseDir = navFile.parentFile ?: return emptyList()
        val entries = ArrayList<TocEntry>()
        navFile.inputStream().use { input ->
            val p = Xml.newPullParser()
            p.setInput(input, null)
            var inToc = false
            var tocDone = false
            var olDepth = 0
            var capturing = false
            var href: String? = null
            val titleBuf = StringBuilder()
            var event = p.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                val local = p.name?.substringAfter(':')
                when (event) {
                    XmlPullParser.START_TAG -> when (local) {
                        "nav" -> if (!tocDone &&
                            p.getAttributeValue(null, "type")?.contains("toc") == true
                        ) inToc = true
                        "ol" -> if (inToc) olDepth++
                        "a" -> if (inToc) {
                            href = p.getAttributeValue(null, "href")
                            titleBuf.setLength(0)
                            capturing = true
                        }
                    }
                    XmlPullParser.TEXT -> if (capturing) titleBuf.append(p.text)
                    XmlPullParser.END_TAG -> when (local) {
                        "nav" -> if (inToc) { inToc = false; tocDone = true }
                        "ol" -> if (inToc) olDepth--
                        "a" -> if (inToc && href != null) {
                            val (file, frag) = resolveHref(baseDir, href!!)
                            val title = titleBuf.toString().trim()
                            if (title.isNotEmpty()) {
                                entries.add(TocEntry(title, file, frag, (olDepth - 1).coerceAtLeast(0)))
                            }
                            capturing = false
                            href = null
                        }
                    }
                }
                event = p.next()
            }
        }
        return entries
    }

    /** Tabla de contenidos de un EPUB2 (archivo .ncx con navPoint anidados). */
    private fun parseNcx(ncxFile: File): List<TocEntry> {
        if (!ncxFile.exists()) return emptyList()
        val baseDir = ncxFile.parentFile ?: return emptyList()
        val entries = ArrayList<TocEntry>()
        ncxFile.inputStream().use { input ->
            val p = Xml.newPullParser()
            p.setInput(input, null)
            var depth = 0
            var capturing = false
            var pendingTitle = ""
            val titleBuf = StringBuilder()
            var event = p.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                val local = p.name?.substringAfter(':')
                when (event) {
                    XmlPullParser.START_TAG -> when (local) {
                        "navPoint" -> depth++
                        "text" -> { titleBuf.setLength(0); capturing = true }
                        "content" -> {
                            val src = p.getAttributeValue(null, "src")
                            if (src != null && pendingTitle.isNotEmpty()) {
                                val (file, frag) = resolveHref(baseDir, src)
                                entries.add(TocEntry(pendingTitle, file, frag, (depth - 1).coerceAtLeast(0)))
                            }
                        }
                    }
                    XmlPullParser.TEXT -> if (capturing) titleBuf.append(p.text)
                    XmlPullParser.END_TAG -> when (local) {
                        "text" -> { capturing = false; pendingTitle = titleBuf.toString().trim() }
                        "navPoint" -> depth--
                    }
                }
                event = p.next()
            }
        }
        return entries
    }

    /** Resuelve un href (posible con #fragmento y % codificado) contra un directorio base. */
    private fun resolveHref(baseDir: File, href: String): Pair<File, String?> {
        val hash = href.indexOf('#')
        val rawPath = if (hash >= 0) href.substring(0, hash) else href
        val fragment = if (hash >= 0) href.substring(hash + 1) else null
        val decoded = runCatching { URLDecoder.decode(rawPath, "UTF-8") }.getOrDefault(rawPath)
        val file = runCatching { File(baseDir, decoded).canonicalFile }.getOrDefault(File(baseDir, decoded))
        return file to fragment
    }
}
