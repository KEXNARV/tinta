package com.kevin.ink.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kevin.ink.R
import com.kevin.ink.data.Book
import com.kevin.ink.ui.components.BookCover
import com.kevin.ink.ui.components.CapsLabel
import com.kevin.ink.ui.components.PrimaryButton
import com.kevin.ink.ui.components.ProgressBar
import com.kevin.ink.ui.theme.InkTheme
import com.kevin.ink.ui.theme.JetBrainsMono
import com.kevin.ink.ui.theme.Manrope
import com.kevin.ink.ui.theme.SpaceGrotesk
import kotlin.math.roundToInt

private val H_PAD = 22.dp

@Composable
fun DetailScreen(
    bookId: String,
    onBack: () -> Unit,
    onOpenReader: (String) -> Unit,
    onOpenBook: (String) -> Unit,
    onOpenLibrary: () -> Unit,
    viewModel: DetailViewModel = viewModel(),
) {
    LaunchedEffect(bookId) { viewModel.load(bookId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val c = InkTheme.colors
    val book = state.book

    Column(Modifier.fillMaxSize().background(c.bgApp).statusBarsPadding()) {
        // Top bar (back)
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            GhostIconButton(R.drawable.ic_arrow_left, "Volver", onBack)
            Spacer(Modifier.width(4.dp))
            Text("ink", fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = c.textPrimary, letterSpacing = (-0.02).sp)
        }

        if (book == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = c.accent) }
            return@Column
        }

        val pct = (book.progress * 100).roundToInt().coerceIn(0, 100)
        val total = book.pageCount
        val cur = if (total > 0) (book.progress * total).roundToInt().coerceIn(0, total) else 0
        val started = book.progress > 0f

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = H_PAD).padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(4.dp))
            CapsLabel(if (started) "Sigues en" else "Empieza con")
            Spacer(Modifier.height(18.dp))

            BookCover(
                book,
                Modifier.width(172.dp).shadow(20.dp, RoundedCornerShape(8.dp), clip = false),
            )

            Spacer(Modifier.height(18.dp))
            Text(book.title, fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = c.textPrimary, textAlign = TextAlign.Center, lineHeight = 24.sp)
            book.author?.let {
                Spacer(Modifier.height(3.dp))
                Text(it, fontFamily = Manrope, fontSize = 13.5.sp, color = c.textSecondary, textAlign = TextAlign.Center)
            }

            Spacer(Modifier.height(18.dp))
            Row(Modifier.widthIn(max = 280.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ProgressBar(book.progress, Modifier.weight(1f))
                Text(
                    if (total > 0) "$cur/$total" else "$pct%",
                    fontFamily = JetBrainsMono, fontSize = 12.sp, color = c.textTertiary,
                )
            }

            Spacer(Modifier.height(18.dp))
            PrimaryButton(
                text = if (started) "Seguir leyendo" else "Empezar a leer",
                iconRes = R.drawable.ic_book_open,
                onClick = { onOpenReader(book.id) },
            )

            if (state.upNext.isNotEmpty()) {
                Spacer(Modifier.height(28.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("A continuación", fontFamily = SpaceGrotesk, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = c.textPrimary)
                    Text("Ver todo", fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = c.accent, modifier = Modifier.clickable { onOpenLibrary() })
                }
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                    state.upNext.forEach { b ->
                        BookCover(b, Modifier.width(98.dp).clickable { onOpenBook(b.id) })
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            StatsFooter(state)
        }
    }
}

@Composable
private fun StatsFooter(state: DetailUiState) {
    val c = InkTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatCol("${state.streak}", "${if (state.streak == 1) "día" else "días"} de racha", Modifier.weight(1f))
        VDivider()
        StatCol("${state.booksThisYear}", "libros / ${state.yearlyGoal}", Modifier.weight(1f))
        VDivider()
        StatCol("${state.monthMinutes / 60}h", "este mes", Modifier.weight(1f))
    }
}

@Composable
private fun StatCol(value: String, label: String, modifier: Modifier = Modifier) {
    val c = InkTheme.colors
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontFamily = JetBrainsMono, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, color = c.textPrimary)
        Spacer(Modifier.height(2.dp))
        Text(label, fontFamily = Manrope, fontSize = 11.sp, color = c.textTertiary, textAlign = TextAlign.Center)
    }
}

@Composable
private fun VDivider() {
    Box(Modifier.width(1.dp).height(34.dp).background(InkTheme.colors.border))
}

@Composable
private fun GhostIconButton(iconRes: Int, contentDescription: String, onClick: () -> Unit) {
    val c = InkTheme.colors
    Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).clickable { onClick() }, contentAlignment = Alignment.Center) {
        Icon(painterResource(iconRes), contentDescription, tint = c.textSecondary, modifier = Modifier.size(22.dp))
    }
}
