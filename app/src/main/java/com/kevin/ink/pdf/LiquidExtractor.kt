package com.kevin.ink.pdf

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * "Modo líquido": convierte un PDF en texto reflowable (HTML).
 * - Saca el texto real de cada página con PDFBox (PDFs digitales).
 * - Si una página casi no tiene texto (escaneada), la dibuja y le pasa OCR.
 * - Limpia encabezados/pies repetidos, números de página y une palabras cortadas con guion.
 */
object LiquidExtractor {

    /** Subir este número invalida la caché cuando cambia la lógica de extracción. */
    const val FORMAT_VERSION = 3

    private const val MIN_CHARS_BEFORE_OCR = 16
    private const val OCR_RENDER_WIDTH = 1100

    /** Devuelve el cuerpo HTML reflowable. onProgress(páginaActual, total). */
    suspend fun extract(
        context: Context,
        file: File,
        holder: PdfRendererHolder,
        onProgress: (Int, Int) -> Unit
    ): String = withContext(Dispatchers.IO) {
        PDFBoxResourceLoader.init(context.applicationContext)

        // 1) Texto crudo por página (con OCR de respaldo).
        val pageTexts = ArrayList<String>()
        PDDocument.load(file).use { doc ->
            val stripper = PDFTextStripper()
            val total = doc.numberOfPages
            for (i in 0 until total) {
                stripper.startPage = i + 1
                stripper.endPage = i + 1
                var text = runCatching { stripper.getText(doc) }.getOrDefault("").trim()
                if (text.length < MIN_CHARS_BEFORE_OCR) {
                    val bmp = holder.render(i, OCR_RENDER_WIDTH)
                    if (bmp != null) {
                        text = runCatching { PdfOcr.recognize(bmp) }.getOrDefault("").trim()
                    }
                }
                pageTexts.add(text)
                onProgress(i + 1, total)
            }
        }

        // 2) Quitar encabezados/pies repetidos y números de página.
        val cleaned = removeBoilerplate(pageTexts)

        // 3) Reflowear a párrafos y títulos.
        val body = StringBuilder("<html><head></head><body class=\"ink-liquid\">")
        for (page in cleaned) appendReflowed(body, page)
        body.append("</body></html>")
        body.toString()
    }

    /** Detecta líneas que se repiten arriba/abajo en muchas páginas (encabezados/pies) y las elimina. */
    private fun removeBoilerplate(pages: List<String>): List<String> {
        val total = pages.size

        val boilerplate: Set<String> = if (total < 4) {
            emptySet()
        } else {
            val freq = HashMap<String, Int>()
            for (page in pages) {
                val lines = page.lines().map { it.trim() }.filter { it.isNotEmpty() }
                // Solo las 2 primeras y 2 últimas líneas son candidatas a encabezado/pie.
                val candidates = (lines.take(2) + lines.takeLast(2))
                    .map { normalizeForFreq(it) }
                    .filter { it.isNotEmpty() }
                    .toSet()
                for (c in candidates) freq[c] = (freq[c] ?: 0) + 1
            }
            val threshold = maxOf(3, (total * 0.3).toInt()) // aparece en ≥30% de páginas
            freq.filterValues { it >= threshold }.keys
        }

        return pages.map { page ->
            page.lines().filter { line ->
                val t = line.trim()
                t.isEmpty() || (normalizeForFreq(t) !in boilerplate && !isPageNumberLine(t))
            }.joinToString("\n")
        }
    }

    /** Normaliza una línea para comparar (sin números ni mayúsculas) — así "...com.ar 2" y "...com.ar 3" empatan. */
    private fun normalizeForFreq(line: String): String =
        line.lowercase()
            .replace(Regex("\\d+"), "")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun isPageNumberLine(line: String): Boolean {
        val t = line.trim()
        return t.matches(Regex("\\d{1,4}")) ||
            t.matches(Regex("(?i)(página|page|pág\\.?)\\s*\\d+"))
    }

    private enum class Kind { SKIP, H2, H3, P }

    /** Une líneas en párrafos (guiones de corte unidos), clasifica títulos y descarta basura. */
    private fun appendReflowed(out: StringBuilder, pageText: String) {
        if (pageText.isBlank()) return
        val blocks = pageText.split(Regex("\\n\\s*\\n"))
        for (block in blocks) {
            // Unir palabra cortada por guion al final de línea: "exce-\nlente" -> "excelente"
            var paragraph = block.replace(Regex("(\\p{L})-\\s*\\n\\s*(\\p{L})"), "$1$2")
            paragraph = paragraph.replace(Regex("\\s*\\n\\s*"), " ")
                .replace(Regex("\\s{2,}"), " ")
                .trim()
            if (paragraph.isEmpty()) continue
            when (classify(paragraph)) {
                Kind.SKIP -> {}
                Kind.H2 -> out.append("<h2>").append(escapeHtml(paragraph)).append("</h2>")
                Kind.H3 -> out.append("<h3>").append(escapeHtml(paragraph)).append("</h3>")
                Kind.P -> out.append("<p>").append(escapeHtml(paragraph)).append("</p>")
            }
        }
    }

    /** Decide si un párrafo es título (h2/h3), cuerpo (p) o basura (skip). */
    private fun classify(text: String): Kind {
        val letters = text.count { it.isLetter() }
        if (letters < 2) return Kind.SKIP // símbolos sueltos (⊗), números, viñetas
        if (text.length <= 80) {
            if (PART_REGEX.containsMatchIn(text)) return Kind.H2
            val upper = text.count { it.isLetter() && it.isUpperCase() }
            if (upper.toFloat() / letters > 0.7f) return Kind.H3 // línea casi toda en MAYÚSCULAS
            if (CHAPTER_REGEX.containsMatchIn(text)) return Kind.H3
        }
        return Kind.P
    }

    private val PART_REGEX = Regex(
        "(?i)^(primera|segunda|tercera|cuarta|quinta|sexta|s[eé]ptima|octava|novena|d[eé]cima)\\s+parte\\b"
    )
    private val CHAPTER_REGEX = Regex("(?i)^(regla|cap[ií]tulo)\\s+\\d+")

    private fun escapeHtml(text: String): String =
        text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
}
