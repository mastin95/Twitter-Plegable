package io.github.mastin95.pliegue.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mastin95.pliegue.model.MediaKind
import io.github.mastin95.pliegue.ui.theme.X

// Imágenes de ejemplo dibujadas en código, para no depender de fotos externas.

private fun alt(kind: MediaKind) = when (kind) {
    MediaKind.Amanecer -> "Amanecer sobre el mar con el sol saliendo"
    MediaKind.Boceto -> "Muestra de una tipografía en varios tamaños"
    MediaKind.Plegable -> "Móvil plegable abierto con dos paneles"
    MediaKind.Paleta -> "Cinco colores con su contraste sobre fondo blanco"
    MediaKind.Ciudad -> "Tejados de una ciudad con una torre al amanecer"
    MediaKind.Comida -> "Cazuela de arroz al horno vista desde arriba"
}

private val swatches = listOf(
    0xFF0F1419 to "19,3",
    0xFF1D9BF0 to "3,0",
    0xFF00704A to "6,1",
    0xFFB3261E to "6,5",
    0xFF8A6D00 to "4,6",
)

/** Imagen de un post. Quien la usa decide el ancho; aquí se fija la proporción 16:9. */
@Composable
fun MediaArt(kind: MediaKind, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .aspectRatio(16f / 9f)
            .clip(shape)
            .border(1.dp, X.Border, shape)
            .semantics { contentDescription = alt(kind) },
        contentAlignment = Alignment.Center,
    ) {
        when (kind) {
            MediaKind.Amanecer -> Sunrise()
            MediaKind.Boceto -> TypeSpecimen()
            MediaKind.Plegable -> FoldDevice()
            MediaKind.Paleta -> Palette()
            MediaKind.Ciudad -> City()
            MediaKind.Comida -> Food()
        }
    }
}

@Composable
private fun Sunrise() = Canvas(Modifier.fillMaxSize()) {
    val horizon = size.height * 0.68f
    val sun = Offset(size.width * 0.62f, horizon)
    drawRect(
        Brush.verticalGradient(listOf(Color(0xFF1B1F4A), Color(0xFF6B3B7A), Color(0xFFE8735A), Color(0xFFF6C26B)), endY = horizon),
        size = Size(size.width, horizon),
    )
    drawCircle(Brush.radialGradient(listOf(Color(0x99FFE6A6), Color(0x00FFE6A6)), center = sun, radius = size.height * 0.35f), radius = size.height * 0.35f, center = sun)
    drawCircle(Color(0xFFFFD98A), radius = size.height * 0.1f, center = sun)
    drawRect(
        Brush.verticalGradient(listOf(Color(0xFF3A3150), Color(0xFF0E1420)), startY = horizon, endY = size.height),
        topLeft = Offset(0f, horizon),
        size = Size(size.width, size.height - horizon),
    )
    for (i in 0 until 5) {
        val w = size.width * (0.12f - i * 0.018f)
        drawRect(Color(0x66FFD98A), topLeft = Offset(sun.x - w / 2, horizon + 6f + i * size.height * 0.055f), size = Size(w, 3f))
    }
}

@Composable
private fun TypeSpecimen() = Column(
    Modifier
        .fillMaxSize()
        .background(X.Card)
        .padding(16.dp),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally,
) {
    Text("Aa", color = X.Text, fontSize = 48.sp, fontWeight = FontWeight.ExtraBold)
    Text("Pliegue Sans", color = X.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    Text("Regular · Media · Negrita · 11 px", color = X.Secondary, fontSize = 13.sp)
}

@Composable
private fun FoldDevice() = Canvas(Modifier.fillMaxSize()) {
    drawRect(Brush.linearGradient(listOf(Color(0xFF0B2A3F), Color(0xFF1D9BF0)), start = Offset.Zero, end = Offset(size.width, size.height)))
    val h = size.height * 0.72f
    val w = h * 0.62f
    val top = (size.height - h) / 2
    val cx = size.width / 2
    listOf(cx - w - 2f, cx + 2f).forEach { left ->
        drawRoundRect(Color.Black, topLeft = Offset(left, top), size = Size(w, h), cornerRadius = CornerRadius(14f))
        drawRoundRect(Color(0xFFE7E9EA), topLeft = Offset(left, top), size = Size(w, h), cornerRadius = CornerRadius(14f), style = Stroke(width = 3f))
        for (row in 0 until 4) {
            val y = top + h * 0.12f + row * h * 0.2f
            drawCircle(Color(0xFF536471), radius = w * 0.07f, center = Offset(left + w * 0.16f, y + w * 0.05f))
            drawRoundRect(Color(0xFF2F3336), topLeft = Offset(left + w * 0.3f, y), size = Size(w * 0.55f, h * 0.035f), cornerRadius = CornerRadius(4f))
            drawRoundRect(Color(0xFF2F3336), topLeft = Offset(left + w * 0.3f, y + h * 0.06f), size = Size(w * 0.4f, h * 0.035f), cornerRadius = CornerRadius(4f))
        }
    }
}

@Composable
private fun Palette() = Row(Modifier.fillMaxSize()) {
    swatches.forEach { (color, ratio) ->
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(Color(color)),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Text(
                "$ratio:1",
                color = X.Ink,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(bottom = 10.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun City() = Canvas(Modifier.fillMaxSize()) {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF2B3A67), Color(0xFFB86B77), Color(0xFFF2A65A))))
    val base = size.height
    val skyline = Path().apply {
        moveTo(0f, base * 0.78f)
        val steps = listOf(0.08f to 0.7f, 0.16f to 0.74f, 0.24f to 0.66f, 0.3f to 0.72f, 0.38f to 0.68f, 0.52f to 0.75f, 0.6f to 0.69f, 0.7f to 0.73f, 0.8f to 0.65f, 0.9f to 0.71f, 1f to 0.68f)
        steps.forEach { (x, y) ->
            lineTo(size.width * x, base * y)
        }
        lineTo(size.width, base)
        lineTo(0f, base)
        close()
    }
    drawPath(skyline, Color(0xFF1A1424))
    // Torre del Miguelete, con su remate.
    val tx = size.width * 0.44f
    val tw = size.width * 0.05f
    drawRect(Color(0xFF1A1424), topLeft = Offset(tx, base * 0.3f), size = Size(tw, base * 0.5f))
    drawRect(Color(0xFF1A1424), topLeft = Offset(tx + tw * 0.2f, base * 0.22f), size = Size(tw * 0.6f, base * 0.1f))
    drawRect(Color(0x55F2A65A), topLeft = Offset(tx + tw * 0.35f, base * 0.38f), size = Size(tw * 0.3f, base * 0.06f))
}

@Composable
private fun Food() = Canvas(Modifier.fillMaxSize()) {
    drawRect(Color(0xFF3B2A1E))
    val c = Offset(size.width / 2, size.height / 2)
    val r = size.height * 0.42f
    drawCircle(Color(0xFF2A2523), radius = r * 1.08f, center = c)
    drawCircle(Brush.radialGradient(listOf(Color(0xFFE59A3C), Color(0xFFB5541C)), center = c, radius = r), radius = r, center = c)
    val slices = listOf(-0.45f to -0.3f, 0.35f to -0.4f, 0.05f to 0.45f, -0.5f to 0.35f, 0.55f to 0.25f)
    slices.forEach { (dx, dy) -> drawCircle(Color(0xFFF0D59A), radius = r * 0.17f, center = Offset(c.x + dx * r, c.y + dy * r)) }
    for (i in 0 until 18) {
        val a = i * 0.9f
        val d = r * (0.2f + (i % 5) * 0.14f)
        drawCircle(Color(0xFFE8C77A), radius = r * 0.05f, center = Offset(c.x + d * kotlin.math.cos(a), c.y + d * kotlin.math.sin(a)))
    }
    drawCircle(Color(0xFF8E2A1C), radius = r * 0.12f, center = Offset(c.x + r * 0.1f, c.y - r * 0.05f))
}
