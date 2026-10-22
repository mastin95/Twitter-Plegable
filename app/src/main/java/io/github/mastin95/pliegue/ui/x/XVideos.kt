package io.github.mastin95.pliegue.ui.x

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mastin95.pliegue.AppViewModel
import io.github.mastin95.pliegue.model.XMediaKind
import io.github.mastin95.pliegue.model.XTime
import io.github.mastin95.pliegue.model.XTweet
import io.github.mastin95.pliegue.ui.XIcons
import io.github.mastin95.pliegue.ui.components.PillButton
import io.github.mastin95.pliegue.ui.components.VerifiedBadge
import io.github.mastin95.pliegue.ui.count
import io.github.mastin95.pliegue.ui.theme.X
import io.github.mastin95.pliegue.ui.theme.XType
import io.github.mastin95.pliegue.ui.web.LocalWebPanes
import io.github.mastin95.pliegue.ui.web.WebPane
import io.github.mastin95.pliegue.web.PaneOptions
import io.github.mastin95.pliegue.web.XParse
import io.github.mastin95.pliegue.web.XWeb
import kotlinx.coroutines.delay

/**
 * Vídeos en vertical, como los de X al tocar un vídeo: el tocado a pantalla completa y, deslizando
 * hacia arriba, el siguiente con vídeo del mismo feed (Inicio, un perfil, una búsqueda, una
 * conversación…). Cada uno va en bucle; un toque lo para y lo sigue. Al acercarse al final se le
 * pide a la web escondida del feed la página siguiente, así que la lista no se acaba mientras X mande.
 *
 * El globo abre las respuestas en la misma pantalla (Martin, 2026-10-22): el vídeo sube arriba y
 * debajo salen sus respuestas; atrás o la equis las cierran y el vídeo vuelve a ocupar todo.
 */
@Composable
fun XVideosScreen(vm: AppViewModel, slot: String, url: String, modifier: Modifier = Modifier) {
    val web = LocalWebPanes.current
    val source = XWeb.videosSource(url)
    // Lo que hay ahora en el feed de origen va a la lista propia de esta pantalla, que no se vacía
    // aunque la web de esa ranura cargue otra cosa (el post en la otra mitad, el redactor).
    val fresh = web.store.videos(source)
    LaunchedEffect(fresh) { web.store.keepVideos(source, fresh) }
    val videos = web.store.videosKept(source).ifEmpty { fresh }
    val taps = rememberPostTaps(vm, slot, source)
    val withReplies = XWeb.showsReplies(url)
    // Otra lista de vídeos en la misma mitad (los de una respuesta) empieza de cero, en su vídeo.
    key(source) {
        BoxWithConstraints(modifier.fillMaxSize().background(Color.Black)) {
            val full = maxHeight
            val width = maxWidth
            if (videos.isEmpty()) {
                CircularProgressIndicator(color = X.Blue, strokeWidth = 3.dp, modifier = Modifier.size(32.dp).align(Alignment.Center))
            } else {
                val start = remember { videos.indexOfFirst { it.id == XWeb.statusId(url) }.coerceAtLeast(0) }
                val pager = rememberPagerState(initialPage = start) { videos.size }
                LaunchedEffect(pager.settledPage, videos.size) {
                    // Más vídeos: se le pide a la web del feed de origen, si sigue en ese feed (no si ahora enseña un post o el redactor).
                    if (pager.settledPage >= videos.size - 2 && fresh.isNotEmpty()) web.loadMore(source.substringBefore('/'))
                    // La dirección abierta sigue al vídeo que se ve: al volver de un post o del redactor se sigue por aquí.
                    videos.getOrNull(pager.settledPage)?.let { vm.moveWithin(slot, XWeb.videosUrl(it.id, source, withReplies)) }
                }
                val repliesFor = if (withReplies) videos.firstOrNull { it.id == XWeb.statusId(url) } else null
                // Con las respuestas abiertas el vídeo sube y se queda arriba, a su proporción (sin pasar de algo
                // menos de la mitad de la pantalla), y la hoja de respuestas sale de abajo y se monta un poco
                // sobre él con sus esquinas redondeadas, como una sola pantalla.
                val ratio = repliesFor?.video?.takeIf { it.width > 0 && it.height > 0 }?.let { it.height.toFloat() / it.width } ?: (9f / 16f)
                val videoHeight by animateDpAsState(
                    if (repliesFor != null) minOf(width * ratio, full * 0.42f) + SHEET_RADIUS else full,
                    animationSpec = tween(280),
                    label = "alto del vídeo",
                )
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxWidth().height(videoHeight)) {
                        VerticalPager(pager, Modifier.fillMaxSize(), key = { videos[it].id }, userScrollEnabled = repliesFor == null) { page ->
                            VideoPage(
                                videos[page],
                                taps,
                                play = pager.settledPage == page,
                                overlay = repliesFor == null,
                                onReplies = { vm.openVideoReplies(slot, XWeb.videosUrl(it.id, source, replies = true)) },
                            )
                        }
                    }
                    if (repliesFor != null) {
                        VideoReplies(
                            vm,
                            slot,
                            repliesFor,
                            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height((full - videoHeight + SHEET_RADIUS).coerceAtLeast(0.dp)),
                        )
                    }
                }
            }
        }
    }
}

private val SHEET_RADIUS = 20.dp
private val SHEET_BG = X.Card
private val REPLIES_PANE = PaneOptions(interceptPosts = false, hideChrome = false, keepInside = true)

/**
 * La hoja de respuestas de un vídeo, como la de comentarios de X o de los vídeos cortos: un asa
 * (arrastrar hacia abajo la cierra), el número de respuestas, el texto del vídeo y debajo las
 * respuestas en filas compactas, con "Responder" y el me gusta a la derecha. Abajo, el campo para
 * responder. Las respuestas las saca una web escondida que abre el post con tu sesión, con más al bajar.
 */
@Composable
private fun VideoReplies(vm: AppViewModel, slot: String, t: XTweet, modifier: Modifier = Modifier) {
    val web = LocalWebPanes.current
    val repliesSlot = XWeb.repliesSlot(slot)
    val thread = web.store.thread(repliesSlot)?.takeIf { it.focal.id == t.id }
    // Lo tocado aquí se abre encima en esta misma mitad; un vídeo de una respuesta sigue con los vídeos de esta conversación.
    val taps = rememberPostTaps(vm, slot, "$repliesSlot/${XParse.THREAD}")
    val state = vm.feedState("$repliesSlot/${t.id}")
    val now = System.currentTimeMillis()
    var slow by remember(t.id) { mutableStateOf(false) }
    LaunchedEffect(t.id, thread == null) {
        slow = false
        if (thread == null) {
            delay(12_000)
            slow = true
        }
    }
    val nearEnd by remember(state, thread?.replies?.size) {
        derivedStateOf {
            val info = state.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 3
        }
    }
    LaunchedEffect(nearEnd, thread?.cursorBottom) {
        if (nearEnd && thread?.cursorBottom != null) web.loadMore(repliesSlot)
    }
    val shape = RoundedCornerShape(topStart = SHEET_RADIUS, topEnd = SHEET_RADIUS)
    Column(modifier.clip(shape).background(SHEET_BG)) {
        // La web que carga la conversación: existe y mide como un móvil para que X la pinte entera, pero no se ve.
        Box(Modifier.size(0.dp).clipToBounds()) {
            Box(Modifier.requiredSize(412.dp, 915.dp)) {
                WebPane(repliesSlot, t.url, Modifier.fillMaxSize(), REPLIES_PANE)
            }
        }
        SheetHeader(t.replies, onClose = { vm.closeVideoReplies(slot) })
        LazyColumn(state = state, modifier = Modifier.fillMaxWidth().weight(1f)) {
            item("texto") { CaptionRow(t, now, taps) }
            when {
                thread == null -> item("cargando") {
                    Column(
                        Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(color = X.Blue, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                        if (slow) {
                            Text("X no ha mandado las respuestas todavía", style = XType.secondary)
                            PillButton("Recargar", onClick = { web.reload(repliesSlot) }, background = X.Blue, content = Color.White, height = 32.dp)
                        }
                    }
                }
                thread.replies.isEmpty() && thread.cursorBottom == null -> item("nada") {
                    Text("Aún no hay respuestas. Sé el primero.", style = XType.secondary, modifier = Modifier.fillMaxWidth().padding(24.dp))
                }
                else -> {
                    // Cada respuesta con las que la siguen en su hilo, un poco metidas.
                    items(thread.replies, key = { it.id }) { e ->
                        Column { e.tweets.forEachIndexed { i, r -> ReplyRow(r, now, taps, nested = i > 0, last = i == e.tweets.lastIndex) } }
                    }
                    item("fin") {
                        Box(Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.Center) {
                            if (thread.cursorBottom != null) CircularProgressIndicator(color = X.Blue, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
        ReplyField(web.store.byId(web.userId)?.avatar ?: "") { taps.onReply(t) }
    }
}

/** El asa y el título de la hoja; arrastrar hacia abajo (o la equis) la cierra. */
@Composable
private fun SheetHeader(replies: Int, onClose: () -> Unit) {
    var dragged by remember { mutableFloatStateOf(0f) }
    Column(
        Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { dragged = 0f },
                    onDragEnd = { if (dragged > 48.dp.toPx()) onClose() },
                ) { change, dy ->
                    change.consume()
                    dragged += dy
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.padding(top = 8.dp).size(width = 36.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(X.OutlineButton))
        Box(Modifier.fillMaxWidth().height(44.dp)) {
            Text(
                when {
                    replies == 1 -> "1 respuesta"
                    replies > 1 -> "${count(replies)} respuestas"
                    else -> "Respuestas"
                },
                style = XType.name,
                modifier = Modifier.align(Alignment.Center),
            )
            IconButton(onClick = onClose, modifier = Modifier.align(Alignment.CenterEnd)) {
                Icon(XIcons.Close, contentDescription = "Cerrar respuestas", tint = X.Text, modifier = Modifier.size(20.dp))
            }
        }
        HorizontalDivider(color = X.Border, thickness = 0.5.dp)
    }
}

/** Arriba de las respuestas, de quién es el vídeo y su texto (el vídeo, encogido, ya no lo lleva encima). */
@Composable
private fun CaptionRow(t: XTweet, now: Long, taps: PostTaps) {
    Column {
        Row(
            Modifier.fillMaxWidth().clickable(onClickLabel = "Ver el post") { taps.onOpen(t) }.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            XAvatar(t.author.avatar, 32.dp, onClick = { taps.onUser(t.author.handle) })
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                ReplyAuthor(t, now)
                if (t.text.isNotEmpty()) XText(t.text, t.links, XType.body, onLink = taps.onLink, maxLines = 4)
            }
        }
        HorizontalDivider(color = X.Border, thickness = 0.5.dp)
    }
}

@Composable
private fun ReplyAuthor(t: XTweet, now: Long) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(t.author.name, style = XType.small.copy(color = X.Text, fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        if (t.author.verified) VerifiedBadge(14.dp)
        val ago = XTime.ago(t.createdAt, now)
        Text("@${t.author.handle}" + if (ago.isNotEmpty()) " · $ago" else "", style = XType.small, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
    }
}

/**
 * Una respuesta en una fila compacta: foto pequeña, nombre y hace cuánto, el texto, sus fotos en
 * pequeño y debajo "Responder" (y "Ver N respuestas" si tiene y es la `last` de su hilo); el me gusta,
 * a la derecha. Tocarla abre su conversación encima. `nested`: sigue a la de arriba en su hilo, un poco metida.
 */
@Composable
private fun ReplyRow(t: XTweet, now: Long, taps: PostTaps, nested: Boolean, last: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Abrir respuesta") { taps.onOpen(t) }
            .padding(start = if (nested) 58.dp else 16.dp, end = 6.dp, top = 10.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        XAvatar(t.author.avatar, if (nested) 24.dp else 32.dp, onClick = { taps.onUser(t.author.handle) })
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            ReplyAuthor(t, now)
            if (t.text.isNotEmpty()) XText(t.text, t.links, XType.body, onLink = taps.onLink)
            if (t.media.isNotEmpty()) {
                XMediaGrid(t.media, Modifier.padding(top = 6.dp).widthIn(max = 220.dp).heightIn(max = 200.dp)) { taps.onMedia(t, it) }
            }
            t.quoted?.let { XQuoted(it, now, taps, Modifier.padding(top = 6.dp)) }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SmallLink("Responder") { taps.onReply(t) }
                // "Ver N respuestas" solo en la última de su hilo: las anteriores ya tienen debajo la que las sigue.
                if (last && t.replies > 0) SmallLink(if (t.replies == 1) "Ver 1 respuesta" else "Ver ${count(t.replies)} respuestas") { taps.onOpen(t) }
            }
        }
        val label = if (t.liked) "Ya no me gusta" else "Me gusta"
        Column(
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClickLabel = label, role = Role.Button) { taps.onLike(t) }
                .padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val color = if (t.liked) X.Pink else X.Secondary
            Icon(if (t.liked) XIcons.LikeFilled else XIcons.Like, contentDescription = label, tint = color, modifier = Modifier.size(18.dp))
            if (t.likes > 0) Text(count(t.likes), style = XType.small.copy(fontSize = 12.sp), color = color)
        }
    }
}

@Composable
private fun SmallLink(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = XType.small.copy(fontWeight = FontWeight.Bold),
        modifier = Modifier.clip(RoundedCornerShape(4.dp)).clickable(onClickLabel = text, role = Role.Button, onClick = onClick).padding(vertical = 6.dp),
    )
}

/** Abajo de la hoja, el campo para responder al vídeo (abre el redactor de X en esta mitad). */
@Composable
private fun ReplyField(avatar: String, onReply: () -> Unit) {
    val pill = RoundedCornerShape(20.dp)
    Column {
        HorizontalDivider(color = X.Border, thickness = 0.5.dp)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (avatar.isNotEmpty()) XAvatar(avatar, 32.dp)
            Box(
                Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(pill)
                    .background(X.Bg)
                    .border(1.dp, X.Border, pill)
                    .clickable(onClickLabel = "Responder", role = Role.Button, onClick = onReply)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text("Postea tu respuesta", style = XType.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * Un vídeo a toda pantalla con quién lo publicó, su texto y las acciones de X en una columna a la
 * derecha. Sin `overlay` (con las respuestas abiertas debajo), solo el vídeo.
 */
@Composable
private fun VideoPage(t: XTweet, taps: PostTaps, play: Boolean, overlay: Boolean, onReplies: (XTweet) -> Unit) {
    val m = t.video ?: return
    val src = m.video ?: return
    var paused by remember(t.id) { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(t.id) { detectTapGestures(onTap = { paused = !paused }) },
    ) {
        VideoPlayer(src + (if (m.kind == XMediaKind.Gif) XWeb.GIF else XWeb.VIDEO), Modifier.fillMaxSize(), play = play && !paused, loop = true, controls = false)
        if (paused) {
            Icon(XIcons.Play, contentDescription = "Reproducir", tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(72.dp).align(Alignment.Center))
        }
        if (overlay) {
            // Un degradado abajo para leer sobre el vídeo.
            Box(Modifier.fillMaxWidth().height(260.dp).align(Alignment.BottomCenter).background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)))))
            Row(Modifier.fillMaxWidth().align(Alignment.BottomStart).padding(start = 12.dp, end = 4.dp, bottom = 12.dp), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f).padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClickLabel = "Ver perfil") { taps.onUser(t.author.handle) },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        XAvatar(t.author.avatar, 36.dp)
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(t.author.name, style = XType.name.copy(color = Color.White), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                if (t.author.verified) VerifiedBadge(16.dp)
                            }
                            Text("@${t.author.handle}", style = XType.small.copy(color = Color.White.copy(alpha = 0.8f)), maxLines = 1)
                        }
                    }
                    if (t.text.isNotEmpty()) {
                        XText(t.text, t.links, XType.body.copy(color = Color.White), onLink = taps.onLink, maxLines = 3, modifier = Modifier.clickable(onClickLabel = "Ver el post") { taps.onOpen(t) })
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    // El globo abre las respuestas aquí mismo, debajo del vídeo; desde ellas se responde.
                    SideAction(XIcons.Reply, "Ver respuestas", t.replies, false, X.Blue) { onReplies(t) }
                    SideAction(XIcons.Repost, if (t.reposted) "Deshacer repost" else "Repostear", t.reposts + t.quotes, t.reposted, X.Green) { taps.onRepost(t) }
                    SideAction(if (t.liked) XIcons.LikeFilled else XIcons.Like, if (t.liked) "Ya no me gusta" else "Me gusta", t.likes, t.liked, X.Pink) { taps.onLike(t) }
                    SideAction(if (t.bookmarked) XIcons.BookmarkFilled else XIcons.Bookmark, "Guardar", t.bookmarks, t.bookmarked, X.Blue) { taps.onBookmark(t) }
                    SideAction(XIcons.Share, "Compartir post", 0, false, X.Blue) { taps.onShare(t) }
                }
            }
        }
    }
}

@Composable
private fun SideAction(icon: ImageVector, label: String, n: Int, active: Boolean, activeColor: Color, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(icon, contentDescription = label, tint = if (active) activeColor else Color.White, modifier = Modifier.size(26.dp))
        if (n > 0) Text(count(n), style = XType.small.copy(color = Color.White))
    }
}
