package io.github.mastin95.pliegue.ui.x

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.mastin95.pliegue.AppViewModel
import io.github.mastin95.pliegue.model.XMediaKind
import io.github.mastin95.pliegue.model.XTweet
import io.github.mastin95.pliegue.ui.XIcons
import io.github.mastin95.pliegue.ui.components.VerifiedBadge
import io.github.mastin95.pliegue.ui.count
import io.github.mastin95.pliegue.ui.theme.X
import io.github.mastin95.pliegue.ui.theme.XType
import io.github.mastin95.pliegue.ui.web.LocalWebPanes
import io.github.mastin95.pliegue.web.XWeb

/**
 * Vídeos en vertical, como los de X al tocar un vídeo: el tocado a pantalla completa y, deslizando
 * hacia arriba, el siguiente con vídeo del mismo feed (Inicio, un perfil, una búsqueda, una
 * conversación…). Cada uno va en bucle; un toque lo para y lo sigue. Al acercarse al final se le
 * pide a la web escondida del feed la página siguiente, así que la lista no se acaba mientras X mande.
 */
@Composable
fun XVideosScreen(vm: AppViewModel, slot: String, url: String, modifier: Modifier = Modifier) {
    val web = LocalWebPanes.current
    val source = XWeb.videosSource(url)
    val videos = web.store.videos(source)
    val taps = rememberPostTaps(vm, slot, source)
    Box(modifier.fillMaxSize().background(Color.Black)) {
        if (videos.isEmpty()) {
            CircularProgressIndicator(color = X.Blue, strokeWidth = 3.dp, modifier = Modifier.size(32.dp).align(Alignment.Center))
        } else {
            val start = remember(url) { videos.indexOfFirst { it.id == XWeb.statusId(url) }.coerceAtLeast(0) }
            val pager = rememberPagerState(initialPage = start) { videos.size }
            LaunchedEffect(pager.settledPage, videos.size) {
                if (pager.settledPage >= videos.size - 2) web.loadMore(source.substringBefore('/'))
            }
            VerticalPager(pager, Modifier.fillMaxSize(), key = { videos[it].id }) { page ->
                VideoPage(videos[page], taps, play = pager.settledPage == page)
            }
        }
    }
}

/** Un vídeo a toda pantalla con quién lo publicó, su texto y las acciones de X en una columna a la derecha. */
@Composable
private fun VideoPage(t: XTweet, taps: PostTaps, play: Boolean) {
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
                SideAction(XIcons.Reply, "Responder", t.replies, false, X.Blue) { taps.onReply(t) }
                SideAction(XIcons.Repost, if (t.reposted) "Deshacer repost" else "Repostear", t.reposts + t.quotes, t.reposted, X.Green) { taps.onRepost(t) }
                SideAction(if (t.liked) XIcons.LikeFilled else XIcons.Like, if (t.liked) "Ya no me gusta" else "Me gusta", t.likes, t.liked, X.Pink) { taps.onLike(t) }
                SideAction(if (t.bookmarked) XIcons.BookmarkFilled else XIcons.Bookmark, "Guardar", t.bookmarks, t.bookmarked, X.Blue) { taps.onBookmark(t) }
                SideAction(XIcons.Share, "Compartir post", 0, false, X.Blue) { taps.onShare(t) }
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
