package io.github.mastin95.pliegue.ui.x

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.mastin95.pliegue.AppViewModel
import io.github.mastin95.pliegue.model.XEntry
import io.github.mastin95.pliegue.model.XFeed
import io.github.mastin95.pliegue.model.XTweet
import io.github.mastin95.pliegue.model.XUser
import io.github.mastin95.pliegue.model.XView
import io.github.mastin95.pliegue.ui.XIcons
import io.github.mastin95.pliegue.ui.components.PillButton
import io.github.mastin95.pliegue.ui.components.VerifiedBadge
import io.github.mastin95.pliegue.ui.components.bottomBorder
import io.github.mastin95.pliegue.ui.count
import io.github.mastin95.pliegue.ui.theme.X
import io.github.mastin95.pliegue.ui.theme.XType
import io.github.mastin95.pliegue.ui.web.LocalWebPanes
import io.github.mastin95.pliegue.web.XParse
import kotlinx.coroutines.delay

/** Qué consulta de X llena cada vista. */
/** Las pestañas de Notificaciones de X y el timeline_type con el que pide cada una. */
val NOTIF_TABS = listOf("Todas" to "All", "Verificadas" to "Verified", "Menciones" to "Mentions")

fun feedName(view: XView, tab: Int): String = when (view) {
    XView.Home -> if (tab == 1) "HomeLatestTimeline" else "HomeTimeline"
    is XView.Search -> "SearchTimeline"
    XView.Bookmarks -> "Bookmarks"
    XView.Notifications -> "NotificationsTimeline/" + NOTIF_TABS.getOrElse(tab) { NOTIF_TABS[0] }.second
    is XView.Profile -> "UserTweets"
    is XView.Thread -> XParse.THREAD
}

/**
 * Un feed de X pintado por la app con lo que la web escondida se descarga. Al llegar al final se
 * pide más; tirando hacia abajo se recarga la web. Si X no manda nada, se ofrece volver a la web.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun XFeedScreen(vm: AppViewModel, slot: String, view: XView, taps: PostTaps, selectedId: String? = null, header: @Composable () -> Unit = {}, onFollow: (XUser) -> Unit = {}) {
    val web = LocalWebPanes.current
    val store = web.store
    val tabbed = view == XView.Home || view == XView.Notifications
    val tab = if (view == XView.Notifications) vm.notifTab(slot) else vm.homeTab(slot)
    val name = feedName(view, tab)
    // Si X pidiera las notificaciones sin decir la pestaña, valen igual.
    val feed = store.feed(slot, name) ?: if (view == XView.Notifications) store.feed(slot, "NotificationsTimeline") else null
    val state = vm.feedState("$slot/$name")
    val now = System.currentTimeMillis()
    var refreshing by remember { mutableStateOf(false) }
    LaunchedEffect(feed?.updatedAt) { refreshing = false }
    LaunchedEffect(refreshing) {
        if (refreshing) {
            delay(15_000)
            refreshing = false
        }
    }
    // Inicio en "Siguiendo" o Notificaciones en "Menciones": la web tiene que estar en esa pestaña para cargar ese feed.
    if (tabbed) {
        LaunchedEffect(slot, tab, web.urls[slot]) {
            delay(1200)
            web.homeTab(slot, tab)
        }
    }
    Column(Modifier.fillMaxSize().background(X.Bg)) {
        header()
        if (view == XView.Home) {
            XTabs(listOf("Para ti", "Siguiendo"), tab) { i ->
                vm.setHomeTab(slot, i)
                web.homeTab(slot, i)
            }
        }
        if (view == XView.Notifications) {
            XTabs(NOTIF_TABS.map { it.first }, tab) { i ->
                vm.setNotifTab(slot, i)
                web.homeTab(slot, i)
            }
        }
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = {
                refreshing = true
                web.reload(slot)
            },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            when {
                feed == null || feed.pages == 0 -> Waiting(vm, slot)
                feed.entries.isEmpty() && feed.pinned == null -> Empty(view)
                else -> XFeedList(
                    feed,
                    state,
                    onEnd = { web.loadMore(slot) },
                    header = {
                        if (view is XView.Profile) item("perfil") { ProfileHeader(profileOf(view, feed, store.user(view.handle)), onFollow) }
                        feed.pinned?.let { p -> item("pinned") { XPostCard(p, now, taps, selected = p.id == selectedId) } }
                    },
                ) { entry ->
                    val note = entry.note
                    if (note != null) NotificationRow(note, entry.tweets.firstOrNull(), taps) else XEntryCards(entry.tweets, now, taps, selectedId)
                }
            }
        }
    }
}

/** La cuenta de un perfil: la que X contó al abrirlo o, si no, la de sus posts. */
private fun profileOf(view: XView.Profile, feed: XFeed, known: XUser?): XUser? {
    known?.let { return it }
    val handle = view.handle?.lowercase()
    val authors = (listOfNotNull(feed.pinned) + feed.tweets).map { it.repostedBy ?: it.author }
    return authors.firstOrNull { handle == null || it.handle.lowercase() == handle } ?: authors.firstOrNull()
}

/** Lista de posts con carga de la página siguiente al acercarse al final. */
@Composable
fun XFeedList(
    feed: XFeed,
    state: LazyListState,
    onEnd: () -> Unit,
    header: LazyListScope.() -> Unit = {},
    item: @Composable (XEntry) -> Unit,
) {
    val nearEnd by remember(state, feed.entries.size) {
        derivedStateOf {
            val info = state.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 4
        }
    }
    LaunchedEffect(nearEnd, feed.pages, feed.exhausted) {
        if (nearEnd && !feed.exhausted) onEnd()
    }
    LazyColumn(state = state, modifier = Modifier.fillMaxSize()) {
        header()
        items(feed.entries, key = { it.id }) { item(it) }
        item("fin") {
            Box(Modifier.fillMaxWidth().height(if (feed.exhausted) 120.dp else 96.dp), contentAlignment = Alignment.TopCenter) {
                if (!feed.exhausted) CircularProgressIndicator(color = X.Blue, strokeWidth = 2.dp, modifier = Modifier.padding(top = 24.dp).size(24.dp))
                else Text("No hay más", style = XType.small, modifier = Modifier.padding(top = 24.dp))
            }
        }
    }
}

/** Pestañas como las de X (Inicio: Para ti y Siguiendo; Notificaciones: Todas, Verificadas, Menciones), a ancho completo. */
@Composable
fun XTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().height(48.dp).bottomBorder().padding(end = LocalTabsEndInset.current)) {
        labels.forEachIndexed { i, label ->
            val active = i == selected
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(onClickLabel = label, role = Role.Tab) { onSelect(i) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(label, style = XType.body.copy(fontWeight = if (active) FontWeight.Bold else FontWeight.Medium), color = if (active) X.Text else X.Secondary)
                }
                Box(Modifier.width(56.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(if (active) X.Blue else Color.Transparent))
            }
        }
    }
}

/** Hueco al final de la fila de pestañas: en plegado, el botón de Pliegue flota arriba a la derecha y pisaba la tercera. */
val LocalTabsEndInset = compositionLocalOf { 0.dp }

/** Cabecera de un perfil: portada, foto, nombre, biografía y cifras, como en X. */
@Composable
fun ProfileHeader(u: XUser?, onFollow: (XUser) -> Unit) {
    u ?: return
    Column(Modifier.fillMaxWidth().bottomBorder()) {
        Box(Modifier.fillMaxWidth().aspectRatio(3f).background(X.Card)) {
            if (u.banner.isNotEmpty()) AsyncImage(model = u.banner, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.offset(y = (-36).dp).size(76.dp).clip(CircleShape).background(X.Bg), contentAlignment = Alignment.Center) {
                XAvatar(u.avatar.replace("_bigger.", "_200x200."), 68.dp)
            }
            Spacer(Modifier.weight(1f))
            if (u.followed) {
                PillButton("Siguiendo", onClick = { onFollow(u) }, height = 32.dp, modifier = Modifier.padding(top = 12.dp), background = Color.Transparent, content = X.Text, border = X.Border)
            } else {
                PillButton("Seguir", onClick = { onFollow(u) }, height = 32.dp, modifier = Modifier.padding(top = 12.dp))
            }
        }
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp).offset(y = (-24).dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(u.name, style = XType.title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (u.verified) VerifiedBadge(20.dp)
            }
            Text("@${u.handle}", style = XType.secondary)
            if (u.bio.isNotEmpty()) XText(u.bio, emptyList(), XType.body, onLink = {})
            if (u.location.isNotEmpty() || u.website.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (u.location.isNotEmpty()) {
                        Icon(XIcons.Pin, contentDescription = null, tint = X.Secondary, modifier = Modifier.size(16.dp))
                        Text(u.location, style = XType.secondary, maxLines = 1)
                    }
                    if (u.website.isNotEmpty()) {
                        Icon(XIcons.Globe, contentDescription = null, tint = X.Secondary, modifier = Modifier.size(16.dp))
                        Text(u.website.removePrefix("https://").removePrefix("http://").removeSuffix("/"), style = XType.secondary.copy(color = X.Blue), maxLines = 1)
                    }
                }
            }
            if (u.followers > 0 || u.following > 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(buildAnnotatedString {
                        withStyle(SpanStyle(color = X.Text, fontWeight = FontWeight.Bold)) { append(count(u.following)) }
                        append(" Siguiendo")
                    }, style = XType.secondary)
                    Text(buildAnnotatedString {
                        withStyle(SpanStyle(color = X.Text, fontWeight = FontWeight.Bold)) { append(count(u.followers)) }
                        append(" Seguidores")
                    }, style = XType.secondary)
                }
            }
        }
    }
}

/**
 * Aún no ha llegado nada de X para esta ranura: una rueda y, si tarda, qué hacer. La web escondida
 * sigue cargando debajo (su progreso se ve en la línea azul).
 */
@Composable
fun Waiting(vm: AppViewModel, slot: String) {
    val web = LocalWebPanes.current
    var slow by remember(slot) { mutableStateOf(false) }
    LaunchedEffect(slot, web.urls[slot]) {
        slow = false
        delay(12_000)
        slow = true
    }
    val p = web.progress[slot] ?: 100
    Box(Modifier.fillMaxSize()) {
        if (p < 100) Box(Modifier.fillMaxWidth(p / 100f).height(3.dp).background(X.Blue).align(Alignment.TopStart))
        Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CircularProgressIndicator(color = X.Blue, strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
            if (slow) {
                Text("X no ha mandado posts todavía", style = XType.name)
                Text(
                    "Puede que X esté pidiendo comprobar que eres humano, o que haya cambiado su formato. Mira la web tal cual o recarga.",
                    style = XType.small,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PillButton("Recargar", onClick = { web.reload(slot) }, background = X.Blue, content = Color.White, height = 32.dp)
                    PillButton("Ver la web de X", onClick = { vm.updateNativeUi(false) }, background = X.Bg, content = X.Text, border = X.OutlineButton, height = 32.dp)
                }
            }
        }
    }
}

@Composable
private fun Empty(view: XView) {
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center) {
        Text(
            when (view) {
                XView.Bookmarks -> "No tienes nada guardado"
                XView.Notifications -> "Nada por aquí todavía"
                is XView.Search -> "Sin resultados"
                else -> "Nada por aquí"
            },
            style = XType.title,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            when (view) {
                XView.Bookmarks -> "Guarda un post desde su menú y aparecerá aquí."
                XView.Notifications -> "Cuando alguien reaccione a tus posts o te siga, saldrá aquí."
                is XView.Search -> "X no ha encontrado posts para esta búsqueda."
                else -> "X no ha mandado posts para esta lista."
            },
            style = XType.secondary,
        )
    }
}

/** Para que un feed sepa qué post está abierto al lado y marcarlo. */
fun XTweet.matches(url: String?): Boolean = url != null && url.contains("/status/$id")
