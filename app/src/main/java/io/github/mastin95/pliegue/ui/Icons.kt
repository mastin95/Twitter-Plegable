package io.github.mastin95.pliegue.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

// Iconos propios en el estilo de X (rejilla de 24). Se pintan en blanco y se tiñen con `tint`.
object XIcons {
    val Reply = stroke("reply", "M4 11.5a7.5 7.5 0 0 1 7.5-7.5h1a7.5 7.5 0 0 1 0 15H11l-5 3v-4.2A7.4 7.4 0 0 1 4 11.5z")
    val Repost = stroke("repost", "M4 9l3-3 3 3M7 6v9a3 3 0 0 0 3 3h7M20 15l-3 3-3-3M17 18V9a3 3 0 0 0-3-3H7")
    val Like = stroke("like", HEART)
    val LikeFilled = fill("like_filled", HEART)
    val Views = stroke("views", "M5 20V12M10 20V5M15 20v-9M20 20v-5")
    val Bookmark = stroke("bookmark", BOOKMARK)
    val BookmarkFilled = fill("bookmark_filled", BOOKMARK)
    val Share = stroke("share", "M12 15V3M7 8l5-5 5 5M4 14v6h16v-6")
    val Back = stroke("back", "M20 12H5M11 5l-7 7 7 7")
    val Home = stroke("home", "M3.5 10.5L12 3.5l8.5 7V20.5h-6v-6h-5v6h-6z")
    val HomeFilled = fill("home_filled", "M12 3l9 7.5V21h-6.5v-6.5h-5V21H3V10.5z")
    val Search = stroke("search", "M10.5 4a6.5 6.5 0 1 1 0 13 6.5 6.5 0 0 1 0-13zM15.5 15.5L21 21")
    val People = stroke("people", "M9 11a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7zM2.5 20a6.5 6.5 0 0 1 13 0M16 4.5a3.5 3.5 0 0 1 0 6.5M18 14a5.5 5.5 0 0 1 3.5 6")
    val Bell = stroke("bell", "M6 16V11a6 6 0 0 1 12 0v5l2 2H4zM10 20a2 2 0 0 0 4 0")
    val Mail = stroke("mail", "M3.5 5.5h17v13h-17zM3.5 7l8.5 6 8.5-6")
    val User = stroke("user", "M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM4 21a8 8 0 0 1 16 0")
    val More = fill("more", "M5 10.5a1.5 1.5 0 1 1 0 3 1.5 1.5 0 0 1 0-3zm7 0a1.5 1.5 0 1 1 0 3 1.5 1.5 0 0 1 0-3zm7 0a1.5 1.5 0 1 1 0 3 1.5 1.5 0 0 1 0-3z")
    val Compose = stroke("compose", "M14.5 4.5l5 5L9 20H4v-5zM12 7l5 5M20 2v5M17.5 4.5h5")
    val Close = stroke("close", "M6 6l12 12M18 6L6 18")
    val Image = stroke("image", "M4 5h16v14H4zM4 16l5-5 4 4 2-2 5 5M15 9.5a1 1 0 1 0 0-.01")
    val Gif = stroke("gif", "M3.5 5.5h17v13h-17zM10 10H8v4h2v-1.5M12.5 10v4M15.5 14v-4h2M15.5 12h1.5")
    val Poll = stroke("poll", "M4 6h9M4 12h14M4 18h6")
    val Smile = stroke("smile", "M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18zM8.5 14.5a4.5 4.5 0 0 0 7 0M9 9.5v.5M15 9.5v.5")
    val Calendar = stroke("calendar", "M4 6h16v14H4zM4 10h16M8 3v4M16 3v4")
    val Pin = stroke("pin", "M12 21s-6.5-5.8-6.5-11a6.5 6.5 0 0 1 13 0c0 5.2-6.5 11-6.5 11zM12 12.5a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5z")
    val Globe = stroke("globe", "M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18zM3 12h18M12 3c2.5 2.6 3.7 5.6 3.7 9s-1.2 6.4-3.7 9c-2.5-2.6-3.7-5.6-3.7-9S9.5 5.6 12 3z")
    val Swap = stroke("swap", "M7 7h13M16 3l4 4-4 4M17 17H4M8 13l-4 4 4 4")
    val Plus = stroke("plus", "M12 5v14M5 12h14", width = 2.4f)
    val Sparkle = stroke("sparkle", "M12 3l2.2 6.8L21 12l-6.8 2.2L12 21l-2.2-6.8L3 12l6.8-2.2z")
    val ListIcon = stroke("list", "M5 4h14v16H5zM8.5 9h7M8.5 12.5h7M8.5 16h4")
    val Trend = stroke("trend", "M3 17l6-6 4 4 8-8M15 7h6v6")
    val Stack = stroke("stack", "M4 9h16v11H4zM7 6h10M9 3h6")
    val Verified = fill("verified", "M12 1.6l2.5 1.9 3.1-.3.9 3 2.6 1.7-1 3 1 3-2.6 1.7-.9 3-3.1-.3L12 22.4l-2.5-1.9-3.1.3-.9-3-2.6-1.7 1-3-1-3 2.6-1.7.9-3 3.1.3z")
    val Check = stroke("check", "M7.8 12.3l2.8 2.8 5.6-6", width = 2.2f)
}

private const val HEART = "M12 20s-7-4.4-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.6-7 10-7 10z"
private const val BOOKMARK = "M6 4h12v17l-6-4-6 4z"

private fun builder(name: String) = ImageVector.Builder(
    name = name,
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
)

private fun stroke(name: String, d: String, width: Float = 1.8f): ImageVector = builder(name).apply {
    addPath(
        pathData = addPathNodes(d),
        stroke = SolidColor(Color.White),
        strokeLineWidth = width,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    )
}.build()

private fun fill(name: String, d: String): ImageVector = builder(name).apply {
    addPath(pathData = addPathNodes(d), fill = SolidColor(Color.White))
}.build()
