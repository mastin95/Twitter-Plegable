package io.github.mastin95.pliegue.ui.x

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.mastin95.pliegue.AppViewModel
import io.github.mastin95.pliegue.model.XMediaKind
import io.github.mastin95.pliegue.model.XThread
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
                // menos de la mitad de la pantalla, para que se lean las respuestas).
                val ratio = repliesFor?.video?.takeIf { it.width > 0 && it.height > 0 }?.let { it.height.toFloat() / it.width } ?: (9f / 16f)
                val videoHeight by animateDpAsState(
                    if (repliesFor != null) minOf(width * ratio, full * 0.42f) else full,
                    animationSpec = tween(280),
                    label = "alto del vídeo",
                )
                Column(Modifier.fillMaxSize()) {
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
                    if (repliesFor != null) VideoReplies(vm, slot, repliesFor, Modifier.fillMaxWidth().weight(1f))
                }
            }
        }
    }
}

private val REPLIES_PANE = PaneOptions(interceptPosts = false, hideChrome = false, keepInside = true)

/**
 * Las respuestas de un vídeo, debajo de él: las saca una web escondida que abre el post con tu
 * sesión (como la conversación de un post), con más al bajar. Abajo, la caja para responder.
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
    Column(
        modifier
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .background(X.Bg)
            .border(1.dp, X.Border, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
    ) {
        // La web que carga la conversación: existe y mide como un móvil para que X la pinte entera, pero no se ve.
        Box(Modifier.size(0.dp).clipToBounds()) {
            Box(Modifier.requiredSize(412.dp, 915.dp)) {
                WebPane(repliesSlot, t.url, Modifier.fillMaxSize(), REPLIES_PANE)
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Respuestas", style = XType.name, modifier = Modifier.weight(1f))
            if (t.replies > 0) Text(count(t.replies), style = XType.small, modifier = Modifier.padding(end = 4.dp))
            IconButton(onClick = { vm.closeVideoReplies(slot) }) {
                Icon(XIcons.Close, contentDescription = "Cerrar respuestas", tint = X.Text, modifier = Modifier.size(22.dp))
            }
        }
        HorizontalDivider(color = X.Border)
        Box(Modifier.fillMaxWidth().weight(1f)) {
            when {
                thread == null -> Column(
                    Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(color = X.Blue, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
                    if (slow) {
                        Text("X no ha mandado las respuestas todavía", style = XType.name)
                        PillButton("Recargar", onClick = { web.reload(repliesSlot) }, background = X.Blue, content = Color.White, height = 32.dp)
                    }
                }
                thread.replies.isEmpty() && thread.cursorBottom == null ->
                    Text("Aún no hay respuestas. Sé el primero.", style = XType.secondary, modifier = Modifier.align(Alignment.Center).padding(24.dp))
                else -> RepliesList(repliesSlot, thread, state, taps, now)
            }
        }
        HorizontalDivider(color = X.Border)
        XReplyBox(t, web.store.byId(web.userId)?.avatar ?: "") { taps.onReply(t) }
    }
}

@Composable
private fun RepliesList(repliesSlot: String, thread: XThread, state: LazyListState, taps: PostTaps, now: Long) {
    val web = LocalWebPanes.current
    val nearEnd by remember(state, thread.replies.size) {
        derivedStateOf {
            val info = state.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 3
        }
    }
    LaunchedEffect(nearEnd, thread.cursorBottom) {
        if (nearEnd && thread.cursorBottom != null) web.loadMore(repliesSlot)
    }
    LazyColumn(state = state, modifier = Modifier.fillMaxSize()) {
        items(thread.replies, key = { it.id }) { e -> XEntryCards(e.tweets, now, taps) }
        item("fin") {
            Box(Modifier.fillMaxWidth().height(72.dp), contentAlignment = Alignment.TopCenter) {
                if (thread.cursorBottom != null) CircularProgressIndicator(color = X.Blue, strokeWidth = 2.dp, modifier = Modifier.padding(top = 20.dp).size(24.dp))
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
