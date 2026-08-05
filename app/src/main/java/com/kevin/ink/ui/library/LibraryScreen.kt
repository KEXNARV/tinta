package com.kevin.ink.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kevin.ink.R
import com.kevin.ink.data.Book
import com.kevin.ink.ui.components.BookCover
import com.kevin.ink.ui.components.CapsLabel
import com.kevin.ink.ui.components.InkBadge
import com.kevin.ink.ui.components.InkBottomNav
import com.kevin.ink.ui.components.ProgressBar
import com.kevin.ink.ui.components.SolidIconButton
import com.kevin.ink.ui.theme.InkTheme
import com.kevin.ink.ui.theme.JetBrainsMono
import com.kevin.ink.ui.theme.Manrope
import com.kevin.ink.ui.theme.SpaceGrotesk
import kotlin.math.roundToInt

private val H_PAD = 22.dp

private val MIME_TYPES = arrayOf(
    "application/epub+zip",
    "application/pdf",
    "application/octet-stream",
)

private enum class Segment(val label: String) { LEYENDO("Leyendo"), POR_LEER("Por leer"), TERMINADOS("Terminados") }

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onOpenBook: (Book) -> Unit,
    onSelectTab: (String) -> Unit,
) {
    val c = InkTheme.colors
    val books by viewModel.books.collectAsStateWithLifecycle()
    val importing by viewModel.importing.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    var segment by remember { mutableIntStateOf(0) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var toDelete by remember { mutableStateOf<Book?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.importFrom(it) }
    }

    if (message != null) {
        androidx.compose.runtime.LaunchedEffect(message) {
            snackbar.showSnackbar(message!!)
            viewModel.consumeMessage()
        }
    }

    val inProgress = books.count { it.progress > 0f && it.finishedAt == null }
    val matches = { b: Book ->
        query.isBlank() || b.title.contains(query, true) || (b.author?.contains(query, true) == true)
    }
    val shown = remember(books, segment, query) {
        when (Segment.entries[segment]) {
            Segment.LEYENDO -> books.filter { it.progress > 0f && it.finishedAt == null }
                .sortedByDescending { it.lastReadAt ?: 0L }
            Segment.POR_LEER -> books.filter { it.progress <= 0f && it.finishedAt == null }
            Segment.TERMINADOS -> books.filter { it.finishedAt != null }
                .sortedByDescending { it.finishedAt ?: 0L }
        }.filter(matches)
    }

    Box(Modifier.fillMaxSize().background(c.bgApp)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            // Header
            Row(
                Modifier.fillMaxWidth().padding(start = H_PAD, end = H_PAD, top = 10.dp, bottom = 14.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Biblioteca", fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 25.sp, color = c.textPrimary)
                    Text(
                        "${books.size} ${if (books.size == 1) "libro" else "libros"} · $inProgress en curso",
                        fontFamily = Manrope, fontSize = 12.5.sp, color = c.textTertiary,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SolidIconButton(R.drawable.ic_search, "Buscar") { searching = !searching; if (!searching) query = "" }
                    SolidIconButton(R.drawable.ic_plus, "Agregar") { picker.launch(MIME_TYPES) }
                }
            }

            if (searching) {
                SearchField(query, { query = it }, Modifier.padding(horizontal = H_PAD, vertical = 4.dp))
            }

            // Segmented control
            SegmentedControl(
                options = Segment.entries.map { it.label },
                selected = segment,
                onSelect = { segment = it },
                modifier = Modifier.padding(horizontal = H_PAD, vertical = 10.dp),
            )

            if (importing) LinearProgressIndicator(Modifier.fillMaxWidth(), color = c.accent, trackColor = c.chartTrack)

            if (shown.isEmpty()) {
                EmptyHint(Segment.entries[segment])
            } else {
                LazyColumn(Modifier.weight(1f).padding(horizontal = H_PAD)) {
                    items(shown, key = { it.id }) { book ->
                        when (Segment.entries[segment]) {
                            Segment.POR_LEER -> ToReadRow(book, onOpen = { onOpenBook(book) }, onLong = { toDelete = book })
                            else -> ReadingRow(
                                book,
                                finished = book.finishedAt != null,
                                onOpen = { onOpenBook(book) },
                                onLong = { toDelete = book },
                            )
                        }
                        RowDivider()
                    }
                }
            }

            InkBottomNav(current = "library", onSelect = onSelectTab)
        }

        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp))
    }

    toDelete?.let { book ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            containerColor = c.surfaceCard,
            title = { Text("¿Eliminar libro?", fontFamily = SpaceGrotesk, color = c.textPrimary) },
            text = { Text("Se quitará \"${book.title}\" de tu estante.", fontFamily = Manrope, color = c.textSecondary) },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(book); toDelete = null }) {
                    Text("Eliminar", color = c.accent, fontFamily = Manrope, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { toDelete = null }) { Text("Cancelar", color = c.textTertiary, fontFamily = Manrope) }
            },
        )
    }
}

// ---------------------------------------------------------------- rows

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReadingRow(book: Book, finished: Boolean, onOpen: () -> Unit, onLong: () -> Unit) {
    val c = InkTheme.colors
    val total = book.pageCount
    val cur = if (total > 0) (book.progress * total).roundToInt().coerceIn(0, total) else 0
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onOpen, onLongClick = onLong)
            .padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        BookCover(book, Modifier.width(48.dp), showText = false)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Column {
                Text(book.title, fontFamily = SpaceGrotesk, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, lineHeight = 18.sp)
                book.author?.let { Text(it, fontFamily = Manrope, fontSize = 12.sp, color = c.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            if (finished) {
                InkBadge("Leído", accent = true)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    ProgressBar(book.progress, Modifier.weight(1f), height = 5.dp)
                    Text(
                        if (total > 0) "$cur/$total" else "${(book.progress * 100).roundToInt()}%",
                        fontFamily = JetBrainsMono, fontSize = 11.sp, color = c.textSecondary,
                    )
                }
            }
        }
        Icon(painterResource(R.drawable.ic_chevron_right), null, tint = c.textTertiary, modifier = Modifier.size(18.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ToReadRow(book: Book, onOpen: () -> Unit, onLong: () -> Unit) {
    val c = InkTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onOpen, onLongClick = onLong)
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        BookCover(book, Modifier.width(38.dp), showText = false)
        Column(Modifier.weight(1f)) {
            Text(book.title, fontFamily = SpaceGrotesk, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, lineHeight = 17.sp)
            book.author?.let { Text(it, fontFamily = Manrope, fontSize = 11.5.sp, color = c.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        Box(
            Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).clickable { onOpen() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_plus), "Empezar", tint = c.accent, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun RowDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(InkTheme.colors.border))
}

// ---------------------------------------------------------------- segmented control

@Composable
private fun SegmentedControl(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = InkTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(c.bgSunken)
            .border(1.dp, c.border, RoundedCornerShape(10.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEachIndexed { i, label ->
            val active = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (active) c.surfaceRaised else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    fontFamily = Manrope,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                    fontSize = 13.sp,
                    color = if (active) c.textPrimary else c.textTertiary,
                )
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = InkTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(c.bgSunken)
            .border(1.dp, c.border, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Icon(painterResource(R.drawable.ic_search), null, tint = c.textTertiary, modifier = Modifier.size(18.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) Text("Buscar título o autor", fontFamily = Manrope, fontSize = 14.sp, color = c.textTertiary)
            BasicTextField(
                value = query, onValueChange = onQuery, singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(fontFamily = Manrope, fontSize = 14.sp, color = c.textPrimary),
                cursorBrush = SolidColor(c.accent), modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ColumnScope.EmptyHint(segment: Segment) {
    val c = InkTheme.colors
    Box(Modifier.fillMaxWidth().weight(1f).padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                when (segment) {
                    Segment.LEYENDO -> "No tienes libros en curso"
                    Segment.POR_LEER -> "Nada pendiente por leer"
                    Segment.TERMINADOS -> "Aún no has terminado ninguno"
                },
                fontFamily = SpaceGrotesk, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = c.textSecondary,
            )
            CapsLabel("Toca + para agregar libros", Modifier.padding(top = 8.dp))
        }
    }
}
