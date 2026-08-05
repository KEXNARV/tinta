package com.kevin.ink.ui.reader

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kevin.ink.data.Book
import com.kevin.ink.data.BookRepository
import com.kevin.ink.data.InkDatabase
import java.time.LocalDate
import com.kevin.ink.epub.Chapter
import com.kevin.ink.epub.ChapterRenderer
import com.kevin.ink.epub.EpubExtractor
import com.kevin.ink.epub.EpubReaderContent
import com.kevin.ink.epub.EpubReaderParser
import com.kevin.ink.epub.TocEntry
import com.kevin.ink.pdf.LiquidExtractor
import com.kevin.ink.pdf.PdfRendererHolder
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Estado de la pantalla de lectura. */
sealed interface ReaderUiState {
    data object Loading : ReaderUiState
    data class Error(val message: String) : ReaderUiState
    data class Pdf(val title: String, val pageCount: Int, val startPage: Int) : ReaderUiState
    data class Ready(
        val bookTitle: String,
        val chapters: List<Chapter>,
        val toc: List<TocEntry>,
        val index: Int
    ) : ReaderUiState
}

/** Lo que el WebView debe cargar ahora. */
data class ChapterRender(
    val html: String,
    val baseUrl: String,
    val index: Int,
    val scrollTo: Float,
    val fragment: String?
)

/** Estado del "modo líquido" (texto reflowable) de un PDF. */
sealed interface LiquidState {
    data object Off : LiquidState
    data class Building(val page: Int, val total: Int) : LiquidState
    data class Ready(val html: String) : LiquidState
    data class Error(val message: String) : LiquidState
}

class ReaderViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = InkDatabase.get(app).bookDao()
    private val repo = BookRepository(dao)
    private val prefs = ReadingPrefs(app)

    // Seguimiento de progreso y páginas leídas.
    private var chapterCount = 0
    private var lastScroll = 0f
    private var pdfPage = 0
    private var pdfPageCount = 0
    private var sessionStart = 0L     // inicio de la sesión de lectura (millis)
    private var pendingMs = 0L        // tiempo acumulado pendiente de convertir a minutos

    private val _uiState = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private val _render = MutableStateFlow<ChapterRender?>(null)
    val render: StateFlow<ChapterRender?> = _render.asStateFlow()

    private val _fontZoom = MutableStateFlow(prefs.fontZoom)
    val fontZoom: StateFlow<Int> = _fontZoom.asStateFlow()

    private val _theme = MutableStateFlow(prefs.theme)
    val theme: StateFlow<String> = _theme.asStateFlow()

    private val _pdfZoom = MutableStateFlow(prefs.pdfZoom)
    val pdfZoom: StateFlow<Int> = _pdfZoom.asStateFlow()

    private val _liquidMode = MutableStateFlow(false)
    val liquidMode: StateFlow<Boolean> = _liquidMode.asStateFlow()

    private val _liquidState = MutableStateFlow<LiquidState>(LiquidState.Off)
    val liquidState: StateFlow<LiquidState> = _liquidState.asStateFlow()

    private var bookId: String = ""
    private var content: EpubReaderContent? = null
    private var currentIndex = 0
    private var loaded = false
    private var pdf: PdfRendererHolder? = null
    private var pdfFile: File? = null
    private var liquidBody: String? = null

    fun load(bookId: String) {
        if (loaded) return
        loaded = true
        this.bookId = bookId
        viewModelScope.launch {
            val book = withContext(Dispatchers.IO) { dao.getById(bookId) }
            if (book == null) {
                _uiState.value = ReaderUiState.Error("No encontré el libro.")
                return@launch
            }
            if (book.format == Book.Format.PDF) {
                val holder = withContext(Dispatchers.IO) {
                    PdfRendererHolder.open(File(book.filePath))
                }
                if (holder == null || holder.pageCount == 0) {
                    _uiState.value = ReaderUiState.Error("No pude abrir este PDF.")
                    return@launch
                }
                pdf = holder
                pdfFile = File(book.filePath)
                val startPage = prefs.pageOf(bookId).coerceIn(0, holder.pageCount - 1)
                pdfPageCount = holder.pageCount
                pdfPage = startPage
                viewModelScope.launch { repo.setPageCount(bookId, holder.pageCount) }
                _uiState.value = ReaderUiState.Pdf(book.title, holder.pageCount, startPage)
                // Restaura el modo líquido si el libro se dejó en él.
                if (prefs.liquidOf(bookId)) setLiquidMode(true)
                return@launch
            }

            val parsed = withContext(Dispatchers.IO) {
                val dir = EpubExtractor.extract(
                    File(book.filePath),
                    File(getApplication<Application>().filesDir, "extracted/${book.id}")
                )
                EpubReaderParser.parse(dir)
            }
            if (parsed == null || parsed.chapters.isEmpty()) {
                _uiState.value = ReaderUiState.Error("No pude abrir este EPUB.")
                return@launch
            }

            content = parsed
            chapterCount = parsed.chapters.size
            // Estimación de páginas del EPUB: ~2500 bytes de HTML por "página".
            val totalBytes = parsed.chapters.sumOf { runCatching { it.file.length() }.getOrDefault(0L) }
            val estPages = (totalBytes / 2500L).toInt().coerceAtLeast(chapterCount).coerceAtLeast(1)
            viewModelScope.launch { repo.setPageCount(bookId, estPages) }
            val start = prefs.chapterOf(bookId).coerceIn(0, parsed.chapters.size - 1)
            currentIndex = start
            lastScroll = prefs.scrollOf(bookId)
            _uiState.value = ReaderUiState.Ready(book.title, parsed.chapters, parsed.toc, start)
            loadChapter(start, prefs.scrollOf(bookId), null)
        }
    }

    private fun loadChapter(index: Int, scrollTo: Float, fragment: String?) {
        val chapters = content?.chapters ?: return
        if (index !in chapters.indices) return
        currentIndex = index
        lastScroll = scrollTo
        prefs.saveChapter(bookId, index)
        persistProgress()
        (_uiState.value as? ReaderUiState.Ready)?.let {
            _uiState.value = it.copy(index = index)
        }
        viewModelScope.launch {
            val chapter = chapters[index]
            val raw = withContext(Dispatchers.IO) {
                runCatching { chapter.file.readText() }.getOrDefault("")
            }
            val html = ChapterRenderer.render(raw, _theme.value)
            _render.value = ChapterRender(
                html = html,
                baseUrl = ChapterRenderer.baseUrlFor(chapter.file),
                index = index,
                scrollTo = scrollTo,
                fragment = fragment
            )
        }
    }

    fun next() = loadChapter(currentIndex + 1, 0f, null)
    fun prev() = loadChapter(currentIndex - 1, 0f, null)

    fun goTo(entry: TocEntry) {
        val chapters = content?.chapters ?: return
        val idx = chapters.indexOfFirst { it.file.canonicalPath == entry.file.canonicalPath }
        if (idx >= 0) loadChapter(idx, 0f, entry.fragment)
    }

    fun setFontZoom(zoom: Int) {
        val clamped = zoom.coerceIn(70, 250)
        prefs.fontZoom = clamped
        _fontZoom.value = clamped
    }

    fun setTheme(theme: String) {
        prefs.theme = theme
        _theme.value = theme
    }

    /** Llamado desde el WebView (JS) al hacer scroll. */
    fun onScroll(fraction: Float) {
        lastScroll = fraction.coerceIn(0f, 1f)
        prefs.saveScroll(bookId, lastScroll)
    }

    // --- PDF ---
    suspend fun renderPdfPage(index: Int, width: Int): Bitmap? = pdf?.render(index, width)

    fun savePdfPage(index: Int) {
        pdfPage = index
        prefs.savePage(bookId, index)
        persistProgress()
    }

    fun setPdfZoom(zoom: Int) {
        val clamped = zoom.coerceIn(50, 300)
        prefs.pdfZoom = clamped
        _pdfZoom.value = clamped
    }

    // --- Modo líquido (texto reflowable) ---
    fun setLiquidMode(on: Boolean) {
        _liquidMode.value = on
        if (bookId.isNotEmpty()) prefs.saveLiquid(bookId, on)
        if (!on) return
        val body = liquidBody
        if (body != null) {
            _liquidState.value = LiquidState.Ready(ChapterRenderer.render(body, _theme.value))
        } else {
            buildLiquid()
        }
    }

    private fun buildLiquid() {
        val file = pdfFile ?: return
        val holder = pdf ?: return
        viewModelScope.launch {
            _liquidState.value = LiquidState.Building(0, holder.pageCount)
            try {
                val app = getApplication<Application>()
                val cacheFile = File(app.filesDir, "liquid/$bookId.v${LiquidExtractor.FORMAT_VERSION}.html")
                val body = withContext(Dispatchers.IO) {
                    if (cacheFile.exists()) {
                        cacheFile.readText()
                    } else {
                        val extracted = LiquidExtractor.extract(app, file, holder) { page, total ->
                            _liquidState.value = LiquidState.Building(page, total)
                        }
                        cacheFile.parentFile?.mkdirs()
                        cacheFile.writeText(extracted)
                        extracted
                    }
                }
                liquidBody = body
                _liquidState.value = LiquidState.Ready(ChapterRenderer.render(body, _theme.value))
            } catch (e: Exception) {
                _liquidState.value = LiquidState.Error("No pude convertir este PDF: ${e.message}")
            }
        }
    }

    // --- Seguimiento de tiempo (meta diaria) y progreso (páginas) ---

    /** El lector pasó a primer plano: arranca el cronómetro de la sesión. */
    fun startReadingSession() {
        sessionStart = System.currentTimeMillis()
    }

    /** El lector se va a segundo plano / se cierra: acumula minutos y guarda progreso. */
    fun endReadingSession() {
        if (sessionStart > 0L) {
            pendingMs += System.currentTimeMillis() - sessionStart
            sessionStart = 0L
        }
        val mins = (pendingMs / 60_000L).toInt()
        if (mins > 0) {
            pendingMs -= mins * 60_000L
            viewModelScope.launch { repo.addReadingMinutes(LocalDate.now().toEpochDay(), mins) }
        }
        persistProgress()
    }

    /** Calcula el progreso (0..1) y marca el libro como terminado si llegó al final. */
    private fun persistProgress() {
        val id = bookId
        if (id.isEmpty()) return
        val progress: Float
        val finished: Boolean
        when {
            pdfPageCount > 0 -> {
                progress = ((pdfPage + 1).toFloat() / pdfPageCount).coerceIn(0f, 1f)
                finished = pdfPage >= pdfPageCount - 1
            }
            chapterCount > 0 -> {
                progress = ((currentIndex + lastScroll.coerceIn(0f, 1f)) / chapterCount)
                    .coerceIn(0f, 1f)
                finished = currentIndex >= chapterCount - 1 && lastScroll >= 0.9f
            }
            else -> return
        }
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            repo.updateProgress(id, progress, now)
            if (finished) repo.markFinished(id, now)
        }
    }

    override fun onCleared() {
        super.onCleared()
        pdf?.close()
    }
}
