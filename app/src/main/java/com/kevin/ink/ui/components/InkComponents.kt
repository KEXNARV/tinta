package com.kevin.ink.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.kevin.ink.R
import com.kevin.ink.data.Book
import com.kevin.ink.ui.theme.InkTheme
import com.kevin.ink.ui.theme.Manrope
import com.kevin.ink.ui.theme.SpaceGrotesk
import java.io.File

// ---------------------------------------------------------------- Tarjeta

@Composable
fun InkCard(modifier: Modifier = Modifier, padding: Dp = 16.dp, content: @Composable () -> Unit) {
    val c = InkTheme.colors
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(c.surfaceCard)
            .border(1.dp, c.border, RoundedCornerShape(14.dp))
            .padding(padding),
    ) { content() }
}

@Composable
fun CapsLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier,
        fontFamily = Manrope,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.5.sp,
        letterSpacing = 0.09.em,
        color = InkTheme.colors.textTertiary,
    )
}

// ---------------------------------------------------------------- Progreso

@Composable
fun ProgressBar(progress: Float, modifier: Modifier = Modifier, height: Dp = 6.dp) {
    val c = InkTheme.colors
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(99.dp))
            .background(c.chartTrack),
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(height)
                .clip(RoundedCornerShape(99.dp))
                .background(c.accent),
        )
    }
}

@Composable
fun InkBadge(text: String, accent: Boolean = false) {
    val c = InkTheme.colors
    Box(
        Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(if (accent) c.accentSubtle else c.surfaceRaised)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text,
            fontFamily = com.kevin.ink.ui.theme.JetBrainsMono,
            fontWeight = FontWeight.Medium,
            fontSize = 11.5.sp,
            color = if (accent) c.accent else c.textSecondary,
        )
    }
}

// ---------------------------------------------------------------- Botones

@Composable
fun PrimaryButton(text: String, iconRes: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = InkTheme.colors
    Row(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(c.accent)
            .clickable { onClick() }
            .height(40.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(painterResource(iconRes), null, tint = c.onAccent, modifier = Modifier.size(16.dp))
        Text(text, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = c.onAccent)
    }
}

@Composable
fun ProgressRing(progress: Float, size: Dp, stroke: Dp, content: @Composable () -> Unit) {
    val c = InkTheme.colors
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val sw = stroke.toPx()
            val inset = sw / 2f
            val arcSize = Size(this.size.width - sw, this.size.height - sw)
            val topLeft = Offset(inset, inset)
            drawArc(c.chartTrack, -90f, 360f, false, topLeft, arcSize, style = Stroke(sw, cap = StrokeCap.Round))
            drawArc(c.accent, -90f, 360f * progress.coerceIn(0f, 1f), false, topLeft, arcSize, style = Stroke(sw, cap = StrokeCap.Round))
        }
        content()
    }
}

@Composable
fun SolidIconButton(iconRes: Int, contentDescription: String, onClick: () -> Unit) {
    val c = InkTheme.colors
    Box(
        Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(c.surfaceRaised)
            .border(1.dp, c.border, RoundedCornerShape(12.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(iconRes), contentDescription, tint = c.textSecondary, modifier = Modifier.size(19.dp))
    }
}

// ---------------------------------------------------------------- Portada

val coverPalette = listOf(
    Color(0xFF1F6F4A), Color(0xFF24405F), Color(0xFF6E2E2E), Color(0xFF5B2A4E),
    Color(0xFF2E2E38), Color(0xFF1E5757), Color(0xFFA9772E), Color(0xFF3A4A2E),
    Color(0xFF7A2E3A),
)

fun coverColor(title: String): Color {
    val size = coverPalette.size
    return coverPalette[((title.hashCode() % size) + size) % size]
}

/**
 * Portada de un libro (relación 2/3). Usa la imagen real si existe; si no, una
 * portada de color. [showText] muestra título/autor (rejilla); en miniaturas
 * pequeñas conviene `false` (solo color + inicial).
 */
@Composable
fun BookCover(book: Book, modifier: Modifier = Modifier, showText: Boolean = true) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(2f / 3f)
            .clip(shape),
    ) {
        when {
            book.coverPath != null -> AsyncImage(
                model = File(book.coverPath),
                contentDescription = book.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            showText -> PlaceholderCoverFull(book)
            else -> PlaceholderCoverMini(book)
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to Color.Black.copy(alpha = 0.22f),
                        0.06f to Color.Transparent,
                    )
                )
        )
    }
}

@Composable
private fun PlaceholderCoverFull(book: Book) {
    Column(
        Modifier
            .fillMaxSize()
            .background(coverColor(book.title))
            .padding(horizontal = 9.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            book.title,
            fontFamily = SpaceGrotesk,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            lineHeight = 13.sp,
            color = Color(0xFFF3ECDC),
            maxLines = 4,
        )
        book.author?.let {
            Text(it, fontFamily = Manrope, fontSize = 8.5.sp, color = Color(0xFFF3ECDC).copy(alpha = 0.72f), maxLines = 1)
        }
    }
}

@Composable
private fun PlaceholderCoverMini(book: Book) {
    Box(
        Modifier.fillMaxSize().background(coverColor(book.title)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            book.title.trim().take(1).uppercase().ifBlank { "?" },
            fontFamily = SpaceGrotesk,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = Color(0xFFF3ECDC),
        )
    }
}

// ---------------------------------------------------------------- Barra inferior

data class InkTab(val key: String, val label: String, val iconRes: Int)

val inkTabs = listOf(
    InkTab("home", "Inicio", R.drawable.ic_book_open),
    InkTab("library", "Biblioteca", R.drawable.ic_library),
    InkTab("rhythm", "Tu ritmo", R.drawable.ic_chart_no_axes_column),
    InkTab("profile", "Perfil", R.drawable.ic_user),
)

@Composable
fun InkBottomNav(current: String, onSelect: (String) -> Unit) {
    val c = InkTheme.colors
    Column(Modifier.fillMaxWidth().background(c.surfaceCard)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(top = 8.dp, bottom = 8.dp),
        ) {
            inkTabs.forEach { tab ->
                NavItem(tab, active = tab.key == current, onClick = { onSelect(tab.key) })
            }
        }
    }
}

@Composable
private fun RowScope.NavItem(tab: InkTab, active: Boolean, onClick: () -> Unit) {
    val c = InkTheme.colors
    val color = if (active) c.accent else c.textTertiary
    Column(
        Modifier
            .weight(1f)
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(painterResource(tab.iconRes), tab.label, tint = color, modifier = Modifier.size(21.dp))
        Text(tab.label, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = color)
    }
}
