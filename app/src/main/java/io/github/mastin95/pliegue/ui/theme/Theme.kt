package io.github.mastin95.pliegue.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Colores del modo oscuro de X. */
object X {
    val Bg = Color(0xFF000000)
    val Card = Color(0xFF16181C)
    val Border = Color(0xFF2F3336)
    val Text = Color(0xFFE7E9EA)
    val Secondary = Color(0xFF71767B)
    val Blue = Color(0xFF1D9BF0)
    val Pink = Color(0xFFF91880)
    val Green = Color(0xFF00BA7C)
    val FollowBg = Color(0xFFEFF3F4)
    val Ink = Color(0xFF0F1419)
    val OutlineButton = Color(0xFF536471)
    val Hinge = Color(0xFF3A3D42)
    val Hover = Color(0xFF080808)
    /** Fondo azul muy oscuro de los posts de creadores nuevos y de la zona de soltar. */
    val NewBg = Color(0xFF031018)
    val NewBgHover = Color(0xFF07243A)
}

object XType {
    val body = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, color = X.Text)
    val name = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold, color = X.Text)
    val secondary = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, color = X.Secondary)
    val small = TextStyle(fontSize = 13.sp, lineHeight = 16.sp, color = X.Secondary)
    val title = TextStyle(fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold, color = X.Text)
    val detail = TextStyle(fontSize = 17.sp, lineHeight = 24.sp, color = X.Text)
    val button = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold)
}

@Composable
fun PliegueTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = X.Blue,
            background = X.Bg,
            surface = X.Bg,
            onBackground = X.Text,
            onSurface = X.Text,
            outline = X.Border,
        ),
    ) {
        CompositionLocalProvider(LocalContentColor provides X.Text, content = content)
    }
}
