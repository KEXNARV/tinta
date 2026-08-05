package com.kevin.ink.ui.rhythm

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kevin.ink.R
import com.kevin.ink.ui.components.BookCover
import com.kevin.ink.ui.components.CapsLabel
import com.kevin.ink.ui.components.InkBadge
import com.kevin.ink.ui.components.InkBottomNav
import com.kevin.ink.ui.components.InkCard
import com.kevin.ink.ui.components.ProgressBar
import com.kevin.ink.ui.components.ProgressRing
import com.kevin.ink.ui.components.SolidIconButton
import com.kevin.ink.ui.theme.InkTheme
import com.kevin.ink.ui.theme.JetBrainsMono
import com.kevin.ink.ui.theme.Manrope
import com.kevin.ink.ui.theme.SpaceGrotesk
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val H_PAD = 22.dp

@Composable
fun RitmoScreen(
    viewModel: RitmoViewModel,
    onOpenBook: (String) -> Unit,
    onSelectTab: (String) -> Unit,
    onComingSoon: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val c = InkTheme.colors

    Column(Modifier.fillMaxSize().background(c.bgApp).statusBarsPadding()) {
        // Header
        Row(
            Modifier.fillMaxWidth().padding(start = H_PAD, end = H_PAD, top = 10.dp, bottom = 14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Hoy", fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 25.sp, color = c.textPrimary)
                Text(today(), fontFamily = JetBrainsMono, fontSize = 12.5.sp, color = c.textTertiary, modifier = Modifier.padding(top = 3.dp))
            }
            SolidIconButton(R.drawable.ic_search, "Buscar") { onComingSoon("Buscar") }
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = H_PAD).padding(bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            GoalCard(state)
            ConstanciaCard(state.activity, state.activityWeeks)
            MinutesCard(state.week, state.weekTotal)
            state.continueBook?.let { book ->
                MiniContinue(
                    title = book.title,
                    progress = book.progress,
                    onOpen = { onOpenBook(book.id) },
                )
            }
            Spacer(Modifier.height(2.dp))
        }

        InkBottomNav(current = "rhythm", onSelect = onSelectTab)
    }
}

// ---------------------------------------------------------------- Goal card

@Composable
private fun GoalCard(state: RitmoUiState) {
    val c = InkTheme.colors
    val goal = state.goalMinutes.coerceAtLeast(1)
    val frac = (state.todayMinutes.toFloat() / goal).coerceIn(0f, 1f)
    val remaining = (goal - state.todayMinutes).coerceAtLeast(0)
    InkCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            ProgressRing(progress = frac, size = 112.dp, stroke = 11.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${state.todayMinutes}", fontFamily = JetBrainsMono, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, color = c.textPrimary, lineHeight = 26.sp)
                    Text("/ $goal min", fontFamily = JetBrainsMono, fontSize = 11.sp, color = c.textTertiary)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                CapsLabel("Meta diaria")
                Text(
                    when {
                        state.todayMinutes == 0 -> "Aún no lees hoy — tu meta son $goal min."
                        remaining == 0 -> "¡Meta de hoy cumplida!"
                        else -> "Vas bien — $remaining min para cerrar el día."
                    },
                    fontFamily = SpaceGrotesk, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 21.sp, color = c.textPrimary,
                )
                InkBadge("racha ${state.streak} días", accent = true)
            }
        }
    }
}

// ---------------------------------------------------------------- Constancia (heatmap)

@Composable
private fun ConstanciaCard(activity: List<Int>, weeks: Int) {
    val c = InkTheme.colors
    InkCard {
        Column {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Tu constancia", fontFamily = SpaceGrotesk, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = c.textPrimary)
                Text("6 meses", fontFamily = JetBrainsMono, fontSize = 11.5.sp, color = c.textTertiary)
            }
            Spacer(Modifier.height(14.dp))
            StreakGrid(activity, weeks)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { Legend() }
        }
    }
}

/** Color de una celda según los minutos leídos ese día (estilo GitHub). */
private fun cellAlpha(minutes: Int): Float = when {
    minutes <= 0 -> 0f
    minutes < 10 -> 0.30f
    minutes < 20 -> 0.52f
    minutes < 30 -> 0.74f
    else -> 1f
}

private const val CELL = 13
private const val CELL_GAP = 4

@Composable
private fun emptyCellColor(): Color {
    val c = InkTheme.colors
    return if (c.isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.07f)
}

@Composable
private fun StreakGrid(activity: List<Int>, weeks: Int) {
    val c = InkTheme.colors
    val empty = emptyCellColor()
    val scroll = rememberScrollState()
    // Mostrar lo más reciente: desplazar al final.
    LaunchedEffect(scroll.maxValue) { scroll.scrollTo(scroll.maxValue) }
    Row(Modifier.horizontalScroll(scroll), horizontalArrangement = Arrangement.spacedBy(CELL_GAP.dp)) {
        for (w in 0 until weeks) {
            Column(verticalArrangement = Arrangement.spacedBy(CELL_GAP.dp)) {
                for (d in 0 until 7) {
                    val v = activity.getOrElse(w * 7 + d) { -1 }
                    val color = when {
                        v < 0 -> Color.Transparent
                        v == 0 -> empty
                        else -> c.accent.copy(alpha = cellAlpha(v))
                    }
                    Box(Modifier.size(CELL.dp).clip(RoundedCornerShape(3.dp)).background(color))
                }
            }
        }
    }
}

@Composable
private fun Legend() {
    val c = InkTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Menos", fontFamily = JetBrainsMono, fontSize = 10.sp, color = c.textTertiary)
        val levels = listOf(emptyCellColor(), c.accent.copy(alpha = 0.30f), c.accent.copy(alpha = 0.52f), c.accent.copy(alpha = 0.74f), c.accent)
        levels.forEach { Box(Modifier.size(11.dp).clip(RoundedCornerShape(3.dp)).background(it)) }
        Text("Más", fontFamily = JetBrainsMono, fontSize = 10.sp, color = c.textTertiary)
    }
}

// ---------------------------------------------------------------- Minutos por día (barras)

@Composable
private fun MinutesCard(week: List<WeekDay>, total: Int) {
    val c = InkTheme.colors
    InkCard {
        Column {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Minutos por día", fontFamily = SpaceGrotesk, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = c.textPrimary)
                InkBadge("$total min")
            }
            Spacer(Modifier.height(16.dp))
            BarChart(week)
        }
    }
}

@Composable
private fun BarChart(week: List<WeekDay>) {
    val c = InkTheme.colors
    val maxBar = (week.maxOfOrNull { it.minutes } ?: 0).coerceAtLeast(1)
    Row(
        Modifier.fillMaxWidth().height(120.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        week.forEach { day ->
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                if (day.minutes > 0) {
                    Text("${day.minutes}", fontFamily = JetBrainsMono, fontSize = 9.sp, color = c.textTertiary)
                    Spacer(Modifier.height(3.dp))
                }
                val frac = day.minutes.toFloat() / maxBar
                val barH = (frac * 86f).roundToInt().coerceAtLeast(if (day.minutes > 0) 4 else 2)
                Box(
                    Modifier
                        .fillMaxWidth(0.55f)
                        .height(barH.dp)
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .background(if (day.isToday) c.accent else c.accent.copy(alpha = 0.32f))
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    day.label,
                    fontFamily = Manrope,
                    fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 11.sp,
                    color = if (day.isToday) c.textPrimary else c.textTertiary,
                )
            }
        }
    }
}

// ---------------------------------------------------------------- Mini continue

@Composable
private fun MiniContinue(title: String, progress: Float, onOpen: () -> Unit) {
    val c = InkTheme.colors
    InkCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(title, fontFamily = SpaceGrotesk, fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                ProgressBar(progress, height = 5.dp)
            }
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(c.accent).clickable { onOpen() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_play), "Seguir leyendo", tint = c.onAccent, modifier = Modifier.size(18.dp))
            }
        }
    }
}

// ---------------------------------------------------------------- helper

private fun today(): String =
    LocalDate.now()
        .format(DateTimeFormatter.ofPattern("EEE · d MMM", Locale("es")))
        .replace(".", "")
        .lowercase(Locale("es"))
