package io.github.mastin95.pliegue.ui.x

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import io.github.mastin95.pliegue.model.XCard
import io.github.mastin95.pliegue.model.XLink
import io.github.mastin95.pliegue.model.XMedia
import io.github.mastin95.pliegue.model.XMediaKind
import io.github.mastin95.pliegue.model.XTime
import io.github.mastin95.pliegue.model.XTweet
import io.github.mastin95.pliegue.ui.clock
import io.github.mastin95.pliegue.ui.XIcons
import io.github.mastin95.pliegue.ui.components.VerifiedBadge
import io.github.mastin95.pliegue.ui.components.bottomBorder
import io.github.mastin95.pliegue.ui.count
import io.github.mastin95.pliegue.ui.theme.X
import io.github.mastin95.pliegue.ui.theme.XType

// Piezas de la interfaz propia: un post de X pintado por la app, con el aspecto de X.

/** Qué se tocó en un post; quien pinta el feed decide qué hacer (abrirlo en la otra mitad, etc.). */
class PostTaps(
    val onOpen: (XTweet) -> Unit,
    val onUser: (String) -> Unit,
    val onLink: (String) -> Unit,
    val onMedia: (XTweet, XMedia) -> Unit,
    val onReply: (XTweet) -> Unit,
    val onRepost: (XTweet) -> Unit,
    val onLike: (XTweet) -> Unit,
    val onBookmark: (XTweet) -> Unit,
    val onShare: (XTweet) -> Unit,
)

/** Foto de perfil redonda; mientras llega, un círculo gris. */
@Composable
fun XAvatar(url: String, size: Dp = 40.dp, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(X.Card)
            .then(if (onClick != null) Modifier.clickable(onClickLabel = "Ver perfil", role = Role.Button, onClick = onClick) else Modifier),
    ) {
        if (url.isNotEmpty()) AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

private val mention = Regex("@[A-Za-z0-9_]{1,15}")
private val hashtag = Regex("#[\\p{L}\\p{N}_]+")

/**
 * El texto de un post con sus enlaces, @usuarios y #etiquetas en azul y tocables.
 * Los enlaces se enseñan como los enseña X ("elpais.com/…") y abren la dirección de verdad.
 */
fun richText(text: String, links: List<XLink>, color: Color = X.Text, onLink: (String) -> Unit): AnnotatedString {
    data class Span(val start: Int, val end: Int, val url: String)
    val spans = ArrayList<Span>()
    for (l in links) {
        var from = 0
        while (true) {
            val at = text.indexOf(l.display, from)
            if (at < 0) break
            spans += Span(at, at + l.display.length, l.url)
            from = at + l.display.length
        }
    }
    fun free(a: Int, b: Int) = spans.none { a < it.end && b > it.start }
    for (m in mention.findAll(text)) if (free(m.range.first, m.range.last + 1)) spans += Span(m.range.first, m.range.last + 1, "https://x.com/" + m.value.drop(1))
    for (m in hashtag.findAll(text)) if (free(m.range.first, m.range.last + 1)) spans += Span(m.range.first, m.range.last + 1, "https://x.com/hashtag/" + m.value.drop(1))
    spans.sortBy { it.start }
    val blue = TextLinkStyles(SpanStyle(color = X.Blue))
    return buildAnnotatedString {
        var at = 0
        for (s in spans) {
            if (s.start < at) continue
            withStyle(SpanStyle(color = color)) { append(text, at, s.start) }
            withLink(LinkAnnotation.Clickable(s.url, blue) { onLink(s.url) }) { append(text, s.start, s.end) }
            at = s.end
        }
        withStyle(SpanStyle(color = color)) { append(text, at, text.length) }
    }
}

@Composable
fun XText(text: String, links: List<XLink>, style: TextStyle, onLink: (String) -> Unit, modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE) {
    if (text.isEmpty()) return
    Text(richText(text, links, style.color, onLink), style = style, modifier = modifier, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

/** La imagen de X en el tamaño justo: pequeña para el feed, mediana para una sola grande. */
private fun sized(url: String, name: String): String = if (url.contains("pbs.twimg.com/media/") && !url.contains("name=")) "$url?format=jpg&name=$name" else url

@Composable
private fun MediaBox(m: XMedia, modifier: Modifier, name: String, onOpen: () -> Unit) {
    // En un feed con los vídeos moviéndose solos, este dice cuánto se ve y se mueve si le toca.
    val auto = LocalFeedAutoplay.current?.takeIf { m.kind != XMediaKind.Photo && m.video != null }
    val me = remember { Any() }
    if (auto != null) DisposableEffect(auto) { onDispose { auto.gone(me) } }
    val playing = auto != null && auto.current === me
    val progress = remember { VideoProgress() }
    Box(
        modifier
            .background(X.Card)
            .then(
                if (auto == null) Modifier
                else Modifier.onGloballyPositioned { c ->
                    val h = c.size.height
                    if (h > 0) c.boundsInWindow().let { b -> auto.report(me, b.height / h, b.top) }
                },
            )
            .clickable(onClickLabel = if (m.kind == XMediaKind.Photo) "Ver la foto" else "Ver el vídeo", onClick = onOpen),
    ) {
        AsyncImage(model = sized(m.url, name), contentDescription = m.alt.ifEmpty { if (m.kind == XMediaKind.Photo) "Imagen" else "Vídeo" }, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        if (playing && m.video != null) {
            // Sin sonido y en bucle, como en X; tocarlo abre los vídeos en vertical, con sonido.
            VideoPlayer(m.video, Modifier.fillMaxSize(), loop = true, controls = false, muted = true, progress = progress, preview = true)
            val left = (progress.duration - progress.position).takeIf { progress.duration > 0 }
            val label = if (m.kind == XMediaKind.Gif) "GIF" else left?.let(::clock) ?: duration(m.durationMs)
            if (label.isNotEmpty()) TimeLabel(label, Modifier.align(Alignment.BottomStart))
            if (m.kind != XMediaKind.Gif) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.7f))
                        .semantics { contentDescription = "Vídeo sin sonido" },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(XIcons.SoundOff, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        } else if (m.kind != XMediaKind.Photo) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(X.Blue),
                contentAlignment = Alignment.Center,
            ) {
                Icon(XIcons.Play, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
            }
            val label = if (m.kind == XMediaKind.Gif) "GIF" else duration(m.durationMs)
            if (label.isNotEmpty()) TimeLabel(label, Modifier.align(Alignment.BottomStart))
        }
    }
}

/** La etiqueta de abajo a la izquierda de un vídeo: lo que dura (o lo que le queda) o "GIF". */
@Composable
private fun TimeLabel(label: String, modifier: Modifier) {
    Text(
        label,
        style = XType.small.copy(color = Color.White, fontWeight = FontWeight.Bold),
        modifier = modifier
            .padding(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color.Black.copy(alpha = 0.7f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

private fun duration(ms: Long): String {
    if (ms <= 0) return ""
    val s = ms / 1000
    return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}

/** Las fotos y vídeos de un post, en la rejilla de X: una grande, dos lado a lado, tres o cuatro. */
@Composable
fun XMediaGrid(media: List<XMedia>, modifier: Modifier = Modifier, onOpen: (XMedia) -> Unit) {
    if (media.isEmpty()) return
    val shape = RoundedCornerShape(16.dp)
    val gap = 2.dp
    Box(
        modifier
            .clip(shape)
            .border(1.dp, X.Border, shape),
    ) {
        when (media.size) {
            1 -> MediaBox(media[0], Modifier.fillMaxWidth().aspectRatio(media[0].ratio.coerceIn(0.6f, 2.2f)), "medium") { onOpen(media[0]) }
            2 -> Row(Modifier.fillMaxWidth().aspectRatio(16f / 9f), horizontalArrangement = Arrangement.spacedBy(gap)) {
                media.forEach { m -> MediaBox(m, Modifier.weight(1f).fillMaxHeight(), "small") { onOpen(m) } }
            }
            3 -> Row(Modifier.fillMaxWidth().aspectRatio(16f / 9f), horizontalArrangement = Arrangement.spacedBy(gap)) {
                MediaBox(media[0], Modifier.weight(1f).fillMaxHeight(), "small") { onOpen(media[0]) }
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(gap)) {
                    MediaBox(media[1], Modifier.weight(1f).fillMaxWidth(), "small") { onOpen(media[1]) }
                    MediaBox(media[2], Modifier.weight(1f).fillMaxWidth(), "small") { onOpen(media[2]) }
                }
            }
            else -> Column(Modifier.fillMaxWidth().aspectRatio(16f / 9f), verticalArrangement = Arrangement.spacedBy(gap)) {
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    MediaBox(media[0], Modifier.weight(1f).fillMaxHeight(), "small") { onOpen(media[0]) }
                    MediaBox(media[1], Modifier.weight(1f).fillMaxHeight(), "small") { onOpen(media[1]) }
                }
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    MediaBox(media[2], Modifier.weight(1f).fillMaxHeight(), "small") { onOpen(media[2]) }
                    MediaBox(media[3], Modifier.weight(1f).fillMaxHeight(), "small") { onOpen(media[3]) }
                }
            }
        }
    }
}

/** La tarjeta de un enlace: imagen grande con el título debajo, o miniatura a la izquierda. */
@Composable
fun XLinkCard(card: XCard, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    val box = modifier
        .fillMaxWidth()
        .clip(shape)
        .border(1.dp, X.Border, shape)
        .clickable(onClickLabel = "Abrir ${card.domain}", onClick = { onOpen(card.url) })
    if (card.large && card.image != null) {
        Column(box) {
            AsyncImage(model = card.image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().aspectRatio(1.91f).background(X.Card))
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(card.domain, style = XType.small, maxLines = 1)
                Text(card.title, style = XType.body, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    } else {
        Row(box.height(100.dp), verticalAlignment = Alignment.CenterVertically) {
            if (card.image != null) {
                AsyncImage(model = card.image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(100.dp).background(X.Card))
            } else {
                Box(Modifier.size(100.dp).background(X.Card), contentAlignment = Alignment.Center) {
                    Icon(XIcons.Globe, contentDescription = null, tint = X.Secondary, modifier = Modifier.size(28.dp))
                }
            }
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(card.domain, style = XType.small, maxLines = 1)
                Text(card.title, style = XType.body, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (card.description.isNotEmpty()) Text(card.description, style = XType.small, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Un post citado dentro de otro: la caja con borde de X. */
@Composable
fun XQuoted(t: XTweet, now: Long, taps: PostTaps, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, X.Border, shape)
            .clickable(onClickLabel = "Abrir el post citado") { taps.onOpen(t) }
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            XAvatar(t.author.avatar, 20.dp)
            Text(t.author.name, style = XType.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            if (t.author.verified) VerifiedBadge(16.dp)
            Text("@${t.author.handle} · ${XTime.ago(t.createdAt, now)}", style = XType.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        }
        XText(t.text, t.links, XType.body, taps.onLink, maxLines = 8)
        if (t.media.isNotEmpty()) XMediaGrid(t.media, Modifier.padding(top = 6.dp)) { taps.onMedia(t, it) }
    }
}

@Composable
private fun ActionButton(icon: ImageVector, label: String, n: Int, active: Boolean, activeColor: Color, iconSize: Dp, onClick: () -> Unit) {
    val color = if (active) activeColor else X.Secondary
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(iconSize))
        if (n > 0) Text(count(n), style = XType.small, color = color)
    }
}

/** Fila de acciones de X: responder, repostear, me gusta, visualizaciones, guardar y compartir. */
@Composable
fun XActions(t: XTweet, taps: PostTaps, big: Boolean = false) {
    val iconSize = if (big) 22.dp else 18.75.dp
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        ActionButton(XIcons.Reply, "Responder", t.replies, false, X.Blue, iconSize) { taps.onReply(t) }
        ActionButton(XIcons.Repost, if (t.reposted) "Deshacer repost" else "Repostear", t.reposts + t.quotes, t.reposted, X.Green, iconSize) { taps.onRepost(t) }
        ActionButton(if (t.liked) XIcons.LikeFilled else XIcons.Like, if (t.liked) "Ya no me gusta" else "Me gusta", t.likes, t.liked, X.Pink, iconSize) { taps.onLike(t) }
        if (big) {
            ActionButton(if (t.bookmarked) XIcons.BookmarkFilled else XIcons.Bookmark, "Guardar", t.bookmarks, t.bookmarked, X.Blue, iconSize) { taps.onBookmark(t) }
            ActionButton(XIcons.Share, "Compartir post", 0, false, X.Blue, iconSize) { taps.onShare(t) }
        } else {
            ActionButton(XIcons.Views, "Visualizaciones", t.views, false, X.Blue, iconSize) { taps.onOpen(t) }
            Row {
                ActionButton(if (t.bookmarked) XIcons.BookmarkFilled else XIcons.Bookmark, "Guardar", 0, t.bookmarked, X.Blue, iconSize) { taps.onBookmark(t) }
                ActionButton(XIcons.Share, "Compartir post", 0, false, X.Blue, iconSize) { taps.onShare(t) }
            }
        }
    }
}

/**
 * Un post en un feed, como la tarjeta de X. `selected` lo marca cuando está abierto en la otra mitad.
 * `lineAbove` y `lineBelow` dibujan la línea que une los posts de una conversación.
 */
@Composable
fun XPostCard(
    t: XTweet,
    now: Long,
    taps: PostTaps,
    selected: Boolean = false,
    lineAbove: Boolean = false,
    lineBelow: Boolean = false,
    bordered: Boolean = true,
) {
    val reposter = t.repostedBy
    val headline = when {
        t.pinned -> "Fijado" to XIcons.Pin
        reposter != null -> "${reposter.name} reposteó" to XIcons.Repost
        else -> null
    }
    val avatarTop = 12.dp + (if (headline != null) 24.dp else 0.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .background(if (selected) X.Card else X.Bg)
            .clickable(onClickLabel = "Abrir post") { taps.onOpen(t) }
            .drawBehind {
                val w = 3.dp.toPx()
                if (selected) drawRect(X.Text, topLeft = Offset(size.width - w, 0f), size = Size(w, size.height))
                // La línea de una conversación, por el centro del avatar.
                val x = 36.dp.toPx()
                val lw = 2.dp.toPx()
                if (lineAbove) drawLine(X.Border, Offset(x, 0f), Offset(x, avatarTop.toPx()), lw)
                if (lineBelow) drawLine(X.Border, Offset(x, avatarTop.toPx() + 40.dp.toPx()), Offset(x, size.height), lw)
            }
            .then(if (bordered && !lineBelow) Modifier.bottomBorder() else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        if (headline != null) {
            Row(
                Modifier.padding(start = 28.dp, bottom = 4.dp).height(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(headline.second, contentDescription = null, tint = X.Secondary, modifier = Modifier.size(16.dp))
                Text(headline.first, style = XType.small.copy(fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            XAvatar(t.author.avatar, onClick = { taps.onUser(t.author.handle) })
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(t.author.name, style = XType.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false).clickable { taps.onUser(t.author.handle) })
                        if (t.author.verified) VerifiedBadge()
                        Text("@${t.author.handle} · ${XTime.ago(t.createdAt, now)}", style = XType.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    }
                    Icon(XIcons.More, contentDescription = "Más opciones", tint = X.Secondary, modifier = Modifier.size(18.75.dp))
                }
                if (t.replyTo != null && !lineAbove) {
                    Text(
                        buildAnnotatedString {
                            append("Respondiendo a ")
                            withStyle(SpanStyle(color = X.Blue)) { append("@${t.replyTo}") }
                        },
                        style = XType.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                XText(t.text, t.links, XType.body, taps.onLink)
                if (t.media.isNotEmpty()) XMediaGrid(t.media, Modifier.fillMaxWidth().padding(top = 10.dp)) { taps.onMedia(t, it) }
                t.quoted?.let { XQuoted(it, now, taps, Modifier.padding(top = 10.dp)) }
                t.card?.let { XLinkCard(it, taps.onLink, Modifier.padding(top = 10.dp)) }
                Spacer(Modifier.height(2.dp))
                XActions(t, taps)
            }
        }
    }
}

/** Una fila de feed: un post suelto o una conversación de varios posts unidos por la línea. */
@Composable
fun XEntryCards(tweets: List<XTweet>, now: Long, taps: PostTaps, selectedId: String? = null) {
    tweets.forEachIndexed { i, t ->
        XPostCard(t, now, taps, selected = t.id == selectedId, lineAbove = i > 0, lineBelow = i < tweets.lastIndex)
    }
}

/** El post abierto, en grande: autor, texto a 17 px, fecha completa y recuentos, como la página de un post en X. */
@Composable
fun XFocalPost(t: XTweet, now: Long, taps: PostTaps, onFollow: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            XAvatar(t.author.avatar, onClick = { taps.onUser(t.author.handle) })
            Column(Modifier.weight(1f).clickable { taps.onUser(t.author.handle) }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(t.author.name, style = XType.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (t.author.verified) VerifiedBadge()
                }
                Text("@${t.author.handle}", style = XType.secondary, maxLines = 1)
            }
            Icon(XIcons.More, contentDescription = "Más opciones", tint = X.Secondary, modifier = Modifier.size(20.dp))
        }
        if (t.replyTo != null) {
            Text(
                buildAnnotatedString {
                    append("Respondiendo a ")
                    withStyle(SpanStyle(color = X.Blue)) { append("@${t.replyTo}") }
                },
                style = XType.secondary,
            )
        }
        XText(t.text, t.links, XType.detail, taps.onLink)
        if (t.media.isNotEmpty()) XMediaGrid(t.media, Modifier.fillMaxWidth()) { taps.onMedia(t, it) }
        t.quoted?.let { XQuoted(it, now, taps) }
        t.card?.let { XLinkCard(it, taps.onLink) }
        Text(
            buildAnnotatedString {
                append(XTime.full(t.createdAt))
                if (t.views > 0) {
                    append(" · ")
                    withStyle(SpanStyle(color = X.Text, fontWeight = FontWeight.Bold)) { append(count(t.views)) }
                    append(" Visualizaciones")
                }
            },
            style = XType.secondary,
        )
        val counts = listOfNotNull(
            t.reposts.takeIf { it > 0 }?.let { count(it) to if (it == 1) "repost" else "reposts" },
            t.quotes.takeIf { it > 0 }?.let { count(it) to if (it == 1) "cita" else "citas" },
            t.likes.takeIf { it > 0 }?.let { count(it) to "me gusta" },
            t.bookmarks.takeIf { it > 0 }?.let { count(it) to if (it == 1) "guardado" else "guardados" },
        )
        if (counts.isNotEmpty()) {
            Text(
                buildAnnotatedString {
                    counts.forEachIndexed { i, (n, label) ->
                        if (i > 0) append("   ")
                        withStyle(SpanStyle(color = X.Text, fontWeight = FontWeight.Bold)) { append(n) }
                        append(" $label")
                    }
                },
                style = XType.secondary,
                modifier = Modifier.fillMaxWidth().drawBehind {
                    val w = 1.dp.toPx()
                    drawLine(X.Border, Offset(0f, w / 2), Offset(size.width, w / 2), w)
                }.padding(top = 12.dp),
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .drawBehind {
                    val w = 1.dp.toPx()
                    drawLine(X.Border, Offset(0f, w / 2), Offset(size.width, w / 2), w)
                    drawLine(X.Border, Offset(0f, size.height - w / 2), Offset(size.width, size.height - w / 2), w)
                }
                .padding(vertical = 4.dp),
        ) {
            XActions(t, taps, big = true)
        }
    }
}

/** "Postea tu respuesta": al tocarlo se abre el redactor de X con la respuesta preparada. */
@Composable
fun XReplyBox(t: XTweet, avatar: String, onReply: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .bottomBorder()
            .clickable(onClickLabel = "Responder", onClick = onReply)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        XAvatar(avatar)
        Text("Postea tu respuesta", style = XType.detail.copy(color = X.Secondary), modifier = Modifier.weight(1f))
        Box(
            Modifier
                .height(36.dp)
                .clip(RoundedCornerShape(50))
                .background(X.Blue.copy(alpha = 0.5f))
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("Responder", style = XType.button.copy(fontSize = 15.sp), color = Color.White)
        }
    }
}
