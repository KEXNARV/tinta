package com.kevin.ink.ui.reader

import android.graphics.Bitmap
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kevin.ink.epub.TocEntry

private fun themeColor(theme: String): Int = when (theme) {
    "day" -> 0xFFFFFFFF.toInt()
    "sepia" -> 0xFFF4ECD8.toInt()
    else -> 0xFF121212.toInt()
}

private class InkJsBridge(val onScroll: (Float) -> Unit) {
    @JavascriptInterface
    fun saveScroll(fraction: Double) = onScroll(fraction.toFloat())
}

@Composable
fun ReaderScreen(
    bookId: String,
    onBack: () -> Unit,
    viewModel: ReaderViewModel = viewModel()
) {
    LaunchedEffect(bookId) { viewModel.load(bookId) }

    // Cronometrar el tiempo de lectura (meta diaria) y guardar progreso al salir.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.startReadingSession()
                Lifecycle.Event.ON_PAUSE -> viewModel.endReadingSession()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.endReadingSession()
        }
    }

    when (val state = viewModel.uiState.collectAsStateWithLifecycle().value) {
        ReaderUiState.Loading -> CenterMessage("Abriendo el libro…", loading = true)
        is ReaderUiState.Error -> CenterMessage(state.message, onBack = onBack)
        is ReaderUiState.Pdf -> PdfReaderContent(state, viewModel, onBack)
        is ReaderUiState.Ready -> ReaderContent(state, viewModel, onBack)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderContent(
    state: ReaderUiState.Ready,
    viewModel: ReaderViewModel,
    onBack: () -> Unit
) {
    val render by viewModel.render.collectAsStateWithLifecycle()
    val fontZoom by viewModel.fontZoom.collectAsStateWithLifecycle()
    val theme by viewModel.theme.collectAsStateWithLifecycle()
    val currentRender = rememberUpdatedState(render)

    var webView by remember { mutableStateOf<WebView?>(null) }
    var showToc by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    // Cargar el capítulo cuando cambia (o cuando el WebView ya existe).
    LaunchedEffect(render, webView) {
        val wv = webView ?: return@LaunchedEffect
        val r = render ?: return@LaunchedEffect
        wv.setBackgroundColor(themeColor(theme))
        wv.loadDataWithBaseURL(r.baseUrl, r.html, "text/html", "utf-8", null)
    }
    LaunchedEffect(fontZoom, webView) { webView?.settings?.textZoom = fontZoom }
    LaunchedEffect(theme, webView) {
        webView?.let {
            it.setBackgroundColor(themeColor(theme))
            it.evaluateJavascript(
                "var h=document.documentElement;" +
                    "h.classList.remove('ink-day','ink-sepia','ink-night');" +
                    "h.classList.add('ink-$theme');",
                null
            )
        }
    }
    DisposableEffect(Unit) { onDispose { webView?.destroy() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(state.bookTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    IconButton(onClick = { showToc = true }) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Contenido")
                    }
                    TextButton(onClick = { showSettings = true }) {
                        Text("Aa", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                }
            )
        },
        bottomBar = {
            BottomAppBar {
                IconButton(onClick = { viewModel.prev() }, enabled = state.index > 0) {
                    Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Capítulo anterior")
                }
                Text(
                    "Capítulo ${state.index + 1} de ${state.chapters.size}",
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                IconButton(
                    onClick = { viewModel.next() },
                    enabled = state.index < state.chapters.size - 1
                ) {
                    Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Capítulo siguiente")
                }
            }
        }
    ) { inner ->
        AndroidView(
            modifier = Modifier.padding(inner).fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.allowFileAccess = true
                    settings.textZoom = fontZoom
                    overScrollMode = WebView.OVER_SCROLL_NEVER
                    setBackgroundColor(themeColor(theme))
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String?) {
                            val r = currentRender.value ?: return
                            when {
                                r.fragment != null -> view.evaluateJavascript(
                                    "var e=document.getElementById('${r.fragment}')||" +
                                        "(document.getElementsByName('${r.fragment}')[0]);" +
                                        "if(e){e.scrollIntoView();}",
                                    null
                                )
                                r.scrollTo > 0f -> view.postDelayed({
                                    view.evaluateJavascript(
                                        "var h=document.documentElement;" +
                                            "window.scrollTo(0,(h.scrollHeight-h.clientHeight)*${r.scrollTo});",
                                        null
                                    )
                                }, 160)
                            }
                        }
                    }
                    addJavascriptInterface(InkJsBridge { viewModel.onScroll(it) }, "InkBridge")
                    webView = this
                }
            }
        )
    }

    if (showToc) {
        ModalBottomSheet(
            onDismissRequest = { showToc = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            TocSheet(
                toc = state.toc,
                currentChapterPath = state.chapters[state.index].file.canonicalPath,
                onPick = { viewModel.goTo(it); showToc = false }
            )
        }
    }

    if (showSettings) {
        ModalBottomSheet(onDismissRequest = { showSettings = false }) {
            SettingsSheet(
                fontZoom = fontZoom,
                theme = theme,
                onFontZoom = viewModel::setFontZoom,
                onTheme = viewModel::setTheme
            )
        }
    }
}

@Composable
private fun TocSheet(
    toc: List<TocEntry>,
    currentChapterPath: String,
    onPick: (TocEntry) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        Text(
            "Contenido",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 20.dp, top = 4.dp, bottom = 12.dp)
        )
        LazyColumn(Modifier.fillMaxWidth()) {
            items(toc) { entry ->
                val isCurrent = runCatching { entry.file.canonicalPath == currentChapterPath }
                    .getOrDefault(false)
                Text(
                    text = entry.title,
                    color = if (isCurrent) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(entry) }
                        .padding(
                            start = (20 + entry.depth * 16).dp,
                            end = 20.dp,
                            top = 12.dp,
                            bottom = 12.dp
                        )
                )
            }
        }
    }
}

@Composable
private fun SettingsSheet(
    fontZoom: Int,
    theme: String,
    onFontZoom: (Int) -> Unit,
    onTheme: (String) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(20.dp)) {
        Text("Tamaño de letra", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { onFontZoom(fontZoom - 10) }) { Text("A−", fontSize = 16.sp) }
            Text(
                "$fontZoom%",
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium
            )
            OutlinedButton(onClick = { onFontZoom(fontZoom + 10) }) { Text("A+", fontSize = 20.sp) }
        }

        Spacer(Modifier.height(20.dp))
        Text("Tema", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeOption("Día", "day", theme, onTheme, Modifier.weight(1f))
            ThemeOption("Sepia", "sepia", theme, onTheme, Modifier.weight(1f))
            ThemeOption("Noche", "night", theme, onTheme, Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun ThemeOption(
    label: String,
    value: String,
    current: String,
    onTheme: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (current == value) {
        Button(onClick = { onTheme(value) }, modifier = modifier) { Text(label) }
    } else {
        OutlinedButton(onClick = { onTheme(value) }, modifier = modifier) { Text(label) }
    }
}

private fun pdfColorFilter(theme: String): ColorFilter? = when (theme) {
    "night" -> ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f
            )
        )
    )
    "sepia" -> ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                0.393f, 0.769f, 0.189f, 0f, 0f,
                0.349f, 0.686f, 0.168f, 0f, 0f,
                0.272f, 0.534f, 0.131f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
        )
    )
    else -> null
}

private fun pdfBackground(theme: String): Color = when (theme) {
    "night" -> Color(0xFF0E0E0E)
    "sepia" -> Color(0xFFE9DEC6)
    else -> Color(0xFFD6D6D6)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PdfReaderContent(
    state: ReaderUiState.Pdf,
    viewModel: ReaderViewModel,
    onBack: () -> Unit
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = state.startPage)
    val context = LocalContext.current
    val metrics = remember { context.resources.displayMetrics }
    val theme by viewModel.theme.collectAsStateWithLifecycle()
    val zoom by viewModel.pdfZoom.collectAsStateWithLifecycle()
    val liquidMode by viewModel.liquidMode.collectAsStateWithLifecycle()
    val liquidState by viewModel.liquidState.collectAsStateWithLifecycle()
    val currentPage by remember { derivedStateOf { listState.firstVisibleItemIndex } }
    val scope = rememberCoroutineScope()
    var showSettings by remember { mutableStateOf(false) }

    LaunchedEffect(currentPage) { viewModel.savePdfPage(currentPage) }

    val colorFilter = remember(theme) { pdfColorFilter(theme) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    TextButton(onClick = { showSettings = true }) {
                        Text("Aa", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                }
            )
        },
        bottomBar = {
            BottomAppBar {
                Text(
                    if (liquidMode) "Modo líquido 📖"
                    else "Página ${currentPage + 1} de ${state.pageCount}",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }
    ) { inner ->
        Box(
            modifier = Modifier
                .padding(inner)
                .fillMaxSize()
                .background(pdfBackground(theme))
        ) {
            if (liquidMode) {
                LiquidContent(state = liquidState, theme = theme, zoom = zoom)
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            // Pellizco con 2 dedos = zoom; con 1 dedo no consumimos, así el scroll sigue.
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                var raw = viewModel.pdfZoom.value.toFloat()
                                do {
                                    val event = awaitPointerEvent()
                                    if (event.changes.count { it.pressed } >= 2) {
                                        val zoomChange = event.calculateZoom()
                                        if (zoomChange != 1f) {
                                            raw = (raw * zoomChange).coerceIn(50f, 300f)
                                            val snapped = (raw / 10f).roundToInt() * 10
                                            if (snapped != viewModel.pdfZoom.value) {
                                                viewModel.setPdfZoom(snapped)
                                            }
                                            event.changes.forEach { it.consume() }
                                        }
                                    }
                                } while (event.changes.any { it.pressed })
                            }
                        }
                ) {
                    items(state.pageCount) { index ->
                        PdfPageItem(
                            index = index,
                            screenWidthPx = metrics.widthPixels,
                            density = metrics.density,
                            zoomPercent = zoom,
                            colorFilter = colorFilter,
                            viewModel = viewModel
                        )
                    }
                }
            }
        }
    }

    if (showSettings) {
        ModalBottomSheet(onDismissRequest = { showSettings = false }) {
            PdfSettingsSheet(
                zoom = zoom,
                theme = theme,
                pageCount = state.pageCount,
                liquidMode = liquidMode,
                onZoom = viewModel::setPdfZoom,
                onTheme = viewModel::setTheme,
                onLiquidMode = viewModel::setLiquidMode,
                onJump = { page ->
                    scope.launch { listState.scrollToItem(page.coerceIn(0, state.pageCount - 1)) }
                }
            )
        }
    }
}

@Composable
private fun LiquidContent(state: LiquidState, theme: String, zoom: Int) {
    when (state) {
        is LiquidState.Ready -> LiquidWebView(html = state.html, theme = theme, zoom = zoom)
        is LiquidState.Building -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text("Convirtiendo a texto…")
                if (state.total > 0) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Página ${state.page} de ${state.total}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
        is LiquidState.Error -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(state.message, textAlign = TextAlign.Center)
        }
        LiquidState.Off -> Unit
    }
}

@Composable
private fun LiquidWebView(html: String, theme: String, zoom: Int) {
    var webView by remember { mutableStateOf<WebView?>(null) }

    LaunchedEffect(html, webView) {
        webView?.let {
            it.setBackgroundColor(themeColor(theme))
            it.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
        }
    }
    LaunchedEffect(zoom, webView) { webView?.settings?.textZoom = zoom }
    LaunchedEffect(theme, webView) {
        webView?.let {
            it.setBackgroundColor(themeColor(theme))
            it.evaluateJavascript(
                "var h=document.documentElement;" +
                    "h.classList.remove('ink-day','ink-sepia','ink-night');" +
                    "h.classList.add('ink-$theme');",
                null
            )
        }
    }
    DisposableEffect(Unit) { onDispose { webView?.destroy() } }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.textZoom = zoom
                overScrollMode = WebView.OVER_SCROLL_NEVER
                setBackgroundColor(themeColor(theme))
                webView = this
            }
        }
    )
}

@Composable
private fun PdfPageItem(
    index: Int,
    screenWidthPx: Int,
    density: Float,
    zoomPercent: Int,
    colorFilter: ColorFilter?,
    viewModel: ReaderViewModel
) {
    val renderWidthPx = (screenWidthPx * (zoomPercent / 100f)).toInt().coerceAtLeast(1)
    val displayWidthDp = (renderWidthPx / density)
    val bitmap by produceState<Bitmap?>(initialValue = null, index, renderWidthPx) {
        value = viewModel.renderPdfPage(index, renderWidthPx)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .horizontalScroll(rememberScrollState()),
        contentAlignment = Alignment.TopCenter
    ) {
        val b = bitmap
        if (b != null) {
            Image(
                bitmap = b.asImageBitmap(),
                contentDescription = "Página ${index + 1}",
                colorFilter = colorFilter,
                modifier = Modifier
                    .width(displayWidthDp.dp)
                    .aspectRatio(b.width.toFloat() / b.height.toFloat())
            )
        } else {
            Box(
                modifier = Modifier
                    .width(displayWidthDp.dp)
                    .aspectRatio(1f / 1.414f)
                    .background(Color(0xFF1A1A1A)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
private fun PdfSettingsSheet(
    zoom: Int,
    theme: String,
    pageCount: Int,
    liquidMode: Boolean,
    onZoom: (Int) -> Unit,
    onTheme: (String) -> Unit,
    onLiquidMode: (Boolean) -> Unit,
    onJump: (Int) -> Unit
) {
    var pageText by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Modo líquido", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Texto que se reajusta, como un ebook",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = liquidMode, onCheckedChange = onLiquidMode)
        }

        Spacer(Modifier.height(20.dp))
        Text(
            if (liquidMode) "Tamaño de letra" else "Zoom",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { onZoom(zoom - 25) }) { Text("−", fontSize = 20.sp) }
            Text(
                "$zoom%",
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium
            )
            OutlinedButton(onClick = { onZoom(zoom + 25) }) { Text("+", fontSize = 20.sp) }
        }

        Spacer(Modifier.height(20.dp))
        Text("Tema", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeOption("Día", "day", theme, onTheme, Modifier.weight(1f))
            ThemeOption("Sepia", "sepia", theme, onTheme, Modifier.weight(1f))
            ThemeOption("Noche", "night", theme, onTheme, Modifier.weight(1f))
        }

        if (!liquidMode) {
            Spacer(Modifier.height(20.dp))
            Text("Ir a página", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = pageText,
                    onValueChange = { pageText = it.filter { c -> c.isDigit() }.take(5) },
                    singleLine = true,
                    placeholder = { Text("1 – $pageCount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = { pageText.toIntOrNull()?.let { onJump(it - 1) } }) { Text("Ir") }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun CenterMessage(
    message: String,
    loading: Boolean = false,
    onBack: (() -> Unit)? = null
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (loading) {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
            }
            Text(message, textAlign = TextAlign.Center)
            if (onBack != null) {
                Spacer(Modifier.height(16.dp))
                Button(onClick = onBack) { Text("Volver") }
            }
        }
    }
}
