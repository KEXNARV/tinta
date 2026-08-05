package com.kevin.ink.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kevin.ink.R
import com.kevin.ink.data.Book
import com.kevin.ink.ui.components.BookCover
import com.kevin.ink.ui.components.CapsLabel
import com.kevin.ink.ui.components.InkBadge
import com.kevin.ink.ui.components.InkBottomNav
import com.kevin.ink.ui.components.InkCard
import com.kevin.ink.ui.components.PrimaryButton
import com.kevin.ink.ui.components.ProgressBar
import com.kevin.ink.ui.components.ProgressRing
import com.kevin.ink.ui.components.SolidIconButton
import com.kevin.ink.ui.theme.InkTheme
import com.kevin.ink.ui.theme.JetBrainsMono
import com.kevin.ink.ui.theme.Manrope
import com.kevin.ink.ui.theme.SpaceGrotesk
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val H_PAD = 22.dp

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenBook: (String) -> Unit,
    onOpenLibrary: () -> Unit,
    onSelectTab: (String) -> Unit,
    darkMode: Boolean,
    onSetDark: (Boolean) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val c = InkTheme.colors
    var query by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }

    val visibleBooks = remember(state.library, query) {
        if (query.isBlank()) state.library
        else state.library.filter {
            it.title.contains(query, true) || (it.author?.contains(query, true) == true)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.bgApp)
            .statusBarsPadding()
    ) {
        Header(onSettings = { showSettings = true })

        if (!state.loading && state.library.isEmpty()) {
            EmptyState(onAdd = onOpenLibrary)
        } else {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = H_PAD)
                    .padding(bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Spacer(Modifier.height(2.dp))
                SearchBar(query = query, onQuery = { query = it })

                state.continueBook?.let { ContinueHero(it, onOpen = { onOpenBook(it.id) }) }

                StatsRow(state)

                DailyGoalCard(today = state.todayMinutes, goal = state.dailyGoalMinutes)

                SectionHeader(title = "Tu biblioteca", action = "Ver todo", onAction = onOpenLibrary)
                LibraryGrid(books = visibleBooks.take(8), onOpen = { onOpenBook(it.id) })

                Spacer(Modifier.height(2.dp))
            }
        }

        InkBottomNav(current = "home", onSelect = onSelectTab)
    }

    if (showSettings) {
        SettingsSheet(darkMode = darkMode, onSetDark = onSetDark, onDismiss = { showSettings = false })
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(darkMode: Boolean, onSetDark: (Boolean) -> Unit, onDismiss: () -> Unit) {
    val c = InkTheme.colors
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.surfaceCard) {
        Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 36.dp, top = 4.dp)) {
            Text("Ajustes", fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = c.textPrimary)
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Modo oscuro", fontFamily = SpaceGrotesk, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = c.textPrimary)
                    Text("Cambia entre tema oscuro y claro", fontFamily = Manrope, fontSize = 12.5.sp, color = c.textTertiary, modifier = Modifier.padding(top = 2.dp))
                }
                androidx.compose.material3.Switch(
                    checked = darkMode,
                    onCheckedChange = onSetDark,
                    colors = androidx.compose.material3.SwitchDefaults.colors(
                        checkedThumbColor = c.onAccent,
                        checkedTrackColor = c.accent,
                        uncheckedThumbColor = c.textTertiary,
                        uncheckedTrackColor = c.surfaceRaised,
                        uncheckedBorderColor = c.border,
                    ),
                )
            }
        }
    }
}

// ---------------------------------------------------------------- Header

@Composable
private fun Header(onSettings: () -> Unit) {
    val c = InkTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = H_PAD, end = H_PAD, top = 10.dp, bottom = 14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                greeting(),
                fontFamily = SpaceGrotesk,
                fontWeight = FontWeight.Bold,
                fontSize = 25.sp,
                color = c.textPrimary,
            )
            Text(
                today(),
                fontFamily = JetBrainsMono,
                fontSize = 12.5.sp,
                color = c.textTertiary,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        SolidIconButton(R.drawable.ic_settings, "Ajustes", onSettings)
    }
}

// ---------------------------------------------------------------- Search

@Composable
private fun SearchBar(query: String, onQuery: (String) -> Unit) {
    val c = InkTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(c.bgSunken)
            .border(1.dp, c.border, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Icon(
            painterResource(R.drawable.ic_search),
            contentDescription = null,
            tint = c.textTertiary,
            modifier = Modifier.size(18.dp),
        )
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                Text("Buscar en tu biblioteca", fontFamily = Manrope, fontSize = 14.sp, color = c.textTertiary)
            }
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(fontFamily = Manrope, fontSize = 14.sp, color = c.textPrimary),
                cursorBrush = SolidColor(c.accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ---------------------------------------------------------------- Continue hero

@Composable
private fun ContinueHero(book: Book, onOpen: () -> Unit) {
    val c = InkTheme.colors
    val pct = (book.progress * 100).roundToInt().coerceIn(0, 100)
    val started = book.progress > 0f
    val total = book.pageCount
    val currentPage = if (total > 0) (book.progress * total).roundToInt().coerceIn(0, total) else 0
    InkCard {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            BookCover(book, Modifier.width(96.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                CapsLabel(if (started) "Sigues en" else "Empieza con")
                Text(
                    book.title,
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    lineHeight = 20.sp,
                    color = c.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                book.author?.let {
                    Text(it, fontFamily = Manrope, fontSize = 12.5.sp, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                ProgressBar(book.progress, Modifier.padding(top = 4.dp))
                Text(
                    when {
                        !started -> "Sin empezar"
                        total > 0 -> "p. $currentPage / $total"
                        else -> "$pct% leído"
                    },
                    fontFamily = JetBrainsMono,
                    fontSize = 11.5.sp,
                    color = c.textTertiary,
                )
                PrimaryButton(
                    text = if (started) "Seguir leyendo" else "Empezar a leer",
                    iconRes = R.drawable.ic_book_open,
                    onClick = onOpen,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

// ---------------------------------------------------------------- Stats

@Composable
private fun StatsRow(state: HomeUiState) {
    val monthH = state.monthMinutes / 60
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatCard("Racha", state.streakDays.toString(), if (state.streakDays == 1) "día" else "días", Modifier.weight(1f))
        if (monthH >= 1) {
            StatCard("Este mes", monthH.toString(), "h", Modifier.weight(1f))
        } else {
            StatCard("Este mes", state.monthMinutes.toString(), "min", Modifier.weight(1f))
        }
        StatCard("Este año", state.booksThisYear.toString(), "libros", Modifier.weight(1f))
    }
}

@Composable
private fun StatCard(label: String, value: String, unit: String, modifier: Modifier = Modifier) {
    val c = InkTheme.colors
    InkCard(modifier = modifier, padding = 18.dp) {
        Column {
            CapsLabel(label)
            Spacer(Modifier.height(30.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, fontFamily = JetBrainsMono, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, color = c.textPrimary)
                Text(" $unit", fontFamily = JetBrainsMono, fontSize = 11.sp, color = c.textTertiary, modifier = Modifier.padding(bottom = 3.dp))
            }
        }
    }
}

// ---------------------------------------------------------------- Daily goal

@Composable
private fun DailyGoalCard(today: Int, goal: Int) {
    val c = InkTheme.colors
    val safeGoal = goal.coerceAtLeast(1)
    val frac = (today.toFloat() / safeGoal).coerceIn(0f, 1f)
    val pct = (frac * 100).roundToInt()
    val remaining = (safeGoal - today).coerceAtLeast(0)
    InkCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ProgressRing(progress = frac, size = 56.dp, stroke = 7.dp) {
                Text("$pct%", fontFamily = JetBrainsMono, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = c.textPrimary)
            }
            Column(Modifier.weight(1f)) {
                Text("Meta de hoy", fontFamily = SpaceGrotesk, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = c.textPrimary)
                Text("$today / $goal min leídos", fontFamily = JetBrainsMono, fontSize = 12.5.sp, color = c.textSecondary, modifier = Modifier.padding(top = 3.dp))
            }
            InkBadge(if (remaining > 0) "$remaining min" else "¡Listo!", accent = true)
        }
    }
}

// ---------------------------------------------------------------- Section + grid

@Composable
private fun SectionHeader(title: String, action: String, onAction: () -> Unit) {
    val c = InkTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, fontFamily = SpaceGrotesk, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = c.textPrimary)
        Text(action, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = c.accent, modifier = Modifier.clickable { onAction() })
    }
}

@Composable
private fun LibraryGrid(books: List<Book>, onOpen: (Book) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        books.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { book ->
                    Box(Modifier.weight(1f)) {
                        BookCover(book, Modifier.clickable { onOpen(book) })
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

// ---------------------------------------------------------------- Empty state

@Composable
private fun EmptyState(onAdd: () -> Unit) {
    val c = InkTheme.colors
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("📚", fontSize = 56.sp)
        Spacer(Modifier.height(16.dp))
        Text("Tu biblioteca está vacía", fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = c.textPrimary)
        Spacer(Modifier.height(8.dp))
        Text("Agrega tu primer EPUB o PDF para empezar a leer.", fontFamily = Manrope, fontSize = 14.sp, color = c.textTertiary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        PrimaryButton("Agregar libros", R.drawable.ic_plus, onAdd)
    }
}

// ---------------------------------------------------------------- helpers

private fun greeting(): String = when (LocalTime.now().hour) {
    in 0..11 -> "Buenos días"
    in 12..19 -> "Buenas tardes"
    else -> "Buenas noches"
}

private fun today(): String =
    LocalDate.now()
        .format(DateTimeFormatter.ofPattern("EEE · d MMM", Locale("es")))
        .replace(".", "")
        .lowercase(Locale("es"))
