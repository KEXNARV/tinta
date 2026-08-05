package com.kevin.ink.importer

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.util.zip.ZipFile

/**
 * Lee los datos internos de un EPUB sin librerías externas.
 * Un EPUB es un ZIP: META-INF/container.xml apunta al archivo .opf,
 * y el .opf trae título, autor y la portada.
 */
object EpubMetadata {

    class Meta(val title: String?, val author: String?, val coverBytes: ByteArray?)

    fun read(file: File): Meta {
        return try {
            ZipFile(file).use { zip ->
                val opfPath = findOpfPath(zip) ?: return Meta(null, null, null)
                parseOpf(zip, opfPath)
            }
        } catch (e: Exception) {
            Meta(null, null, null)
        }
    }

    /** Lee META-INF/container.xml y devuelve la ruta del .opf. */
    private fun findOpfPath(zip: ZipFile): String? {
        val entry = zip.getEntry("META-INF/container.xml") ?: return null
        zip.getInputStream(entry).use { input ->
            val parser = Xml.newPullParser()
            parser.setInput(input, null)
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG &&
                    parser.name?.substringAfter(':') == "rootfile"
                ) {
                    return parser.getAttributeValue(null, "full-path")
                }
                event = parser.next()
            }
        }
        return null
    }

    /** Parsea el .opf: título, autor y referencia a la portada. */
    private fun parseOpf(zip: ZipFile, opfPath: String): Meta {
        val entry = zip.getEntry(opfPath) ?: return Meta(null, null, null)

        var title: String? = null
        var author: String? = null
        var coverId: String? = null
        var coverByProperty: String? = null
        val manifest = HashMap<String, String>()   // id -> href

        zip.getInputStream(entry).use { input ->
            val parser = Xml.newPullParser()
            parser.setInput(input, null)
            var current: String? = null
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        val local = parser.name?.substringAfter(':')
                        current = local
                        when (local) {
                            "meta" -> {
                                if (parser.getAttributeValue(null, "name") == "cover") {
                                    coverId = parser.getAttributeValue(null, "content")
                                }
                            }
                            "item" -> {
                                val id = parser.getAttributeValue(null, "id")
                                val href = parser.getAttributeValue(null, "href")
                                val props = parser.getAttributeValue(null, "properties")
                                if (id != null && href != null) manifest[id] = href
                                if (props?.contains("cover-image") == true && href != null) {
                                    coverByProperty = href
                                }
                            }
                        }
                    }
                    XmlPullParser.TEXT -> {
                        val text = parser.text
                        if (!text.isNullOrBlank()) {
                            when (current) {
                                "title" -> if (title == null) title = text.trim()
                                "creator" -> if (author == null) author = text.trim()
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> current = null
                }
                event = parser.next()
            }
        }

        val coverHref = manifest[coverId] ?: coverByProperty
        val coverBytes = coverHref?.let { href ->
            val coverPath = resolveRelative(opfPath, href)
            zip.getEntry(coverPath)?.let { ce -> zip.getInputStream(ce).use { it.readBytes() } }
        }

        return Meta(title, author, coverBytes)
    }

    /** Resuelve "OEBPS/content.opf" + "images/cover.jpg" -> "OEBPS/images/cover.jpg". */
    private fun resolveRelative(opfPath: String, href: String): String {
        val baseDir = opfPath.substringBeforeLast('/', missingDelimiterValue = "")
        val combined = if (baseDir.isEmpty()) href else "$baseDir/$href"
        val parts = ArrayDeque<String>()
        for (segment in combined.split('/')) {
            when (segment) {
                "", "." -> {}
                ".." -> if (parts.isNotEmpty()) parts.removeLast()
                else -> parts.addLast(segment)
            }
        }
        return parts.joinToString("/")
    }
}
