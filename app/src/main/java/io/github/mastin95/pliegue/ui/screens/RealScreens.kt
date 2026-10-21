package io.github.mastin95.pliegue.ui.screens

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mastin95.pliegue.AppViewModel
import io.github.mastin95.pliegue.Posture
import io.github.mastin95.pliegue.ScreenInfo
import io.github.mastin95.pliegue.model.OpenMode
import io.github.mastin95.pliegue.model.Source
import io.github.mastin95.pliegue.ui.XIcons
import io.github.mastin95.pliegue.ui.components.EmptyDetail
import io.github.mastin95.pliegue.ui.components.Fab
import io.github.mastin95.pliegue.ui.components.Logo
import io.github.mastin95.pliegue.ui.components.OpenToolbar
import io.github.mastin95.pliegue.ui.components.PillButton
import io.github.mastin95.pliegue.ui.components.bottomBorder
import io.github.mastin95.pliegue.ui.components.endBorder
import io.github.mastin95.pliegue.ui.components.startBorder
import io.github.mastin95.pliegue.ui.components.NavIcon
import io.github.mastin95.pliegue.ui.components.topBorder
import io.github.mastin95.pliegue.ui.x.LocalTabsEndInset
import io.github.mastin95.pliegue.ui.x.MediaOrWeb
import io.github.mastin95.pliegue.ui.x.NativeOrWeb
import io.github.mastin95.pliegue.ui.theme.X
import io.github.mastin95.pliegue.ui.theme.XType
import io.github.mastin95.pliegue.ui.web.EmptyMyFeed
import io.github.mastin95.pliegue.ui.web.LocalWebPanes
import io.github.mastin95.pliegue.ui.web.UserDropTarget
import io.github.mastin95.pliegue.web.PaneOptions
import io.github.mastin95.pliegue.web.XWeb

/** X real con tu sesión: la misma carcasa por posturas, pero cada mitad es una web de X. */
@Composable
fun RealApp(screen: ScreenInfo, vm: AppViewModel) {
    val web = LocalWebPanes.current
    BackHandler(enabled = web.inFullscreen) { web.exitFullscreen() }
    when (screen.posture) {
        Posture.Plegado -> RealFolded(vm)
        Posture.Abierto -> RealOpen(vm, screen)
        Posture.Sobremesa -> RealTabletop(vm, screen)
    }
}

/**
 * Plegado: una sola ranura a pantalla entera. Con la interfaz propia, la app pinta los posts y pone
 * su barra inferior (Inicio, Explorar, Notificaciones, Mensajes, Perfil) y el botón de redactar;
 * sin ella, la web de X entera con sus propias barras.
 */
@Composable
fun RealFolded(vm: AppViewModel) {
    val web = LocalWebPanes.current
    val context = LocalContext.current
    val focused = vm.webFocused()
    val panel = focused ?: 0
    val slot = "pane$panel"
    val url = vm.paneUrl(panel, web.userId)
    val source = vm.webPanels[panel].source
    val native = vm.nativeUi
    val external = url != null && (XWeb.isExternal(url) || XWeb.isMedia(url) || XWeb.isVideo(url))
    // Con la interfaz propia, atrás no deshace la web escondida (la app no se enteraría): cierra lo abierto.
    // Una foto o un vídeo en el visor propio tampoco tienen web detrás que deshacer.
    val media = url != null && (XWeb.isMedia(url) || XWeb.isVideo(url))
    val ownPage = url != null && (media || (native && !external && XWeb.view(url, source) != null))
    BackHandler {
        when {
            !ownPage && web.canGoBack(slot) -> web.goBack(slot)
            focused != null -> vm.closeWebPost(focused)
            else -> (context as? Activity)?.moveTaskToBack(true)
        }
    }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (focused != null) WebPostHeader(vm, focused, compact = true)
            when {
                url == null -> EmptyMyFeed(Modifier.weight(1f))
                external -> MediaOrWeb(slot, url, Modifier.weight(1f))
                // Con el botón de Pliegue flotando arriba a la derecha, las pestañas le dejan hueco.
                else -> CompositionLocalProvider(LocalTabsEndInset provides if (focused == null) 52.dp else 0.dp) {
                    NativeOrWeb(vm, slot, url, PaneOptions(interceptPosts = native, hideChrome = native), Modifier.weight(1f), source)
                }
            }
            if (native && focused == null) FoldedNav(vm)
        }
        if (focused == null) {
            FoldedMenu(vm, Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 8.dp))
            if (native) {
                Fab(
                    onClick = { vm.composeWeb(1) },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 53.dp + 16.dp),
                )
            }
        }
    }
}

/** Barra inferior de X, nuestra: cambia la fuente de la ranura plegada; tocar la activa vuelve arriba. */
@Composable
private fun FoldedNav(vm: AppViewModel) {
    val web = LocalWebPanes.current
    val current = vm.webPanels[0].source
    Row(
        Modifier
            .fillMaxWidth()
            .height(53.dp)
            .background(X.Bg)
            .topBorder(),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(Source.Inicio, Source.Explorar, Source.Notificaciones, Source.Mensajes, Source.Perfil).forEach { s ->
            val active = s == current
            val icon = when (s) {
                Source.Inicio -> if (active) XIcons.HomeFilled else XIcons.Home
                Source.Explorar -> XIcons.Search
                Source.Notificaciones -> XIcons.Bell
                Source.Mensajes -> XIcons.Mail
                else -> XIcons.User
            }
            val count = when (s) {
                Source.Notificaciones -> web.unread["notifications"]
                Source.Mensajes -> web.unread["messages"]
                else -> null
            } ?: 0
            Box {
                NavIcon(icon, s.label, { if (active) web.scrollTop("pane0") else vm.selectSource(0, s) })
                if (count > 0) UnreadBadge(count, Modifier.align(Alignment.TopEnd).offset(x = (-6).dp, y = 6.dp))
            }
        }
    }
}

/** Botón pequeño sobre la cabecera de la web: cambiar de fuente o abrir los ajustes. */
@Composable
private fun FoldedMenu(vm: AppViewModel, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(X.Bg.copy(alpha = 0.6f))
                .clickable(onClickLabel = "Menú de Pliegue", role = Role.Button) { open = true },
            contentAlignment = Alignment.Center,
        ) {
            Logo(20.dp)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.background(X.Bg)) {
            listOf(Source.Inicio, Source.MiFeed, Source.Guardados, Source.Perfil).forEach { s ->
                DropdownMenuItem(
                    text = { Text(s.label, style = XType.body) },
                    onClick = {
                        vm.selectSource(0, s)
                        open = false
                    },
                )
            }
            DropdownMenuItem(
                text = { Text("Ajustes", style = XType.body) },
                onClick = {
                    vm.settingsOpen = true
                    open = false
                },
            )
        }
    }
}

/**
 * Abierto: dos webs de X separadas por la bisagra. Un post tocado a un lado se abre al otro.
 * Columnas, la mesa y el post ampliado reparten la pantalla de otra forma.
 */
@Composable
fun RealOpen(vm: AppViewModel, screen: ScreenInfo) {
    val web = LocalWebPanes.current
    val context = LocalContext.current
    val density = LocalDensity.current
    var originX by remember { mutableFloatStateOf(0f) }
    BackHandler {
        val focused = vm.webFocused()
        when {
            vm.expanded != null -> vm.collapse()
            vm.mode == OpenMode.Columnas && vm.webColumnPost != null -> vm.closeWebColumnPost()
            // Con la interfaz propia no se deshace la web escondida: la app no se enteraría del cambio.
            !vm.nativeUi && web.canGoBack(web.lastActive) -> web.goBack(web.lastActive)
            focused != null -> vm.closeWebPost(focused)
            else -> (context as? Activity)?.moveTaskToBack(true)
        }
    }
    // El libro empieza por tus guardados: lo que dejaste para leer con calma.
    LaunchedEffect(vm.mode) {
        if (vm.mode == OpenMode.Libro && vm.webPanels[0].source == Source.Inicio && vm.webPanels[0].openUrl == null) vm.selectSource(0, Source.Guardados)
    }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { originX = it.positionInWindow().x },
    ) {
        // Las dos mitades llegan hasta la bisagra: solo se deja hueco si la bisagra corta de verdad la
        // pantalla (dos pantallas separadas). Antes había 24 dp de margen a cada lado: una franja negra
        // en medio que sobraba.
        val origin = with(density) { originX.toDp() }
        val hingeStart = screen.firstPane?.let { it - origin } ?: (maxWidth / 2)
        val leftWidth = hingeStart.coerceAtLeast(200.dp)
        val gap = screen.hinge
        val compact = maxWidth < 760.dp
        val expanded = vm.expanded
        Column(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                when {
                    expanded != null -> ExpandedScreen(vm, expanded, leftWidth, gap)
                    vm.mode == OpenMode.Columnas -> WebColumnsBoard(vm, leftWidth, gap)
                    vm.mode == OpenMode.Mesa -> MesaBoard(vm, leftWidth, gap)
                    else -> Row(Modifier.fillMaxSize()) {
                        WebHalf(vm, 0, Modifier.width(leftWidth).fillMaxHeight().endBorder())
                        Spacer(Modifier.width(gap))
                        WebHalf(vm, 1, Modifier.weight(1f).fillMaxHeight().startBorder())
                    }
                }
            }
            // La barra de modos tiene su sitio fijo: así no tapa el final de las webs.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .topBorder(),
                contentAlignment = Alignment.Center,
            ) {
                OpenToolbar(vm, compact = compact)
            }
        }
        if (expanded == null) {
            Fab(
                onClick = { vm.composeWeb(0) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 80.dp),
            )
        }
    }
}

/**
 * Una mitad con X real: sus pestañas de fuente (o la cabecera del post abierto encima) y la web.
 * Recibe usuarios arrastrados desde la otra mitad para Mi feed.
 */
@Composable
fun WebHalf(vm: AppViewModel, index: Int, modifier: Modifier) {
    val web = LocalWebPanes.current
    val panel = vm.webPanels[index]
    val open = panel.openUrl
    val url = vm.paneUrl(index, web.userId)
    val other = vm.webPanels[1 - index]
    val external = open != null && (XWeb.isExternal(open) || XWeb.isMedia(open) || XWeb.isVideo(open))
    val reading = vm.mode == OpenMode.Libro && open != null && !external
    UserDropTarget(vm, fromLabel = other.source.label, modifier) {
        Column(Modifier.fillMaxSize()) {
            if (open != null) WebPostHeader(vm, index) else SourceTabs(vm, index)
            when {
                index == 1 && (vm.mode == OpenMode.Uno || vm.mode == OpenMode.Libro) && open == null -> EmptyDetail(libro = vm.mode == OpenMode.Libro)
                panel.source == Source.Fijados && open == null -> PinnedList(vm, onOpen = { vm.openWebPost(index, it) }, modifier = Modifier.weight(1f))
                url == null -> EmptyMyFeed(Modifier.weight(1f))
                external -> MediaOrWeb("pane$index", url, Modifier.weight(1f))
                // El post abierto al otro lado se marca en este feed.
                else -> NativeOrWeb(vm, "pane$index", url, PaneOptions(interceptPosts = true, hideChrome = true), Modifier.weight(1f), if (open == null) panel.source else null, textZoom = if (reading) 118 else 100, selectedUrl = other.openUrl)
            }
        }
    }
}

/** Pestañas de fuente de una mitad, al estilo de las de inicio de X, y un botón de recargar. */
@Composable
fun SourceTabs(vm: AppViewModel, index: Int) {
    val web = LocalWebPanes.current
    val selected = vm.webPanels[index].source
    val slot = "pane$index"
    Row(
        Modifier
            .fillMaxWidth()
            .height(53.dp)
            .background(X.Bg)
            .bottomBorder()
            .pointerInput(slot) { detectTapGestures(onDoubleTap = { web.scrollTop(slot) }) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
        ) {
            Source.entries.forEach { s ->
                val active = s == selected
                val count = when (s) {
                    Source.Notificaciones -> web.unread["notifications"]
                    Source.Mensajes -> web.unread["messages"]
                    else -> null
                } ?: 0
                Column(
                    Modifier
                        .fillMaxHeight()
                        // Tocar otra vez la pestaña activa vuelve arriba, como en X.
                        .clickable(onClickLabel = s.label, role = Role.Tab) { if (active) web.scrollTop(slot) else vm.selectSource(index, s) }
                        .padding(horizontal = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(
                            s.label,
                            style = XType.body.copy(fontWeight = if (active) FontWeight.Bold else FontWeight.Medium),
                            color = if (active) X.Text else X.Secondary,
                            maxLines = 1,
                        )
                        if (count > 0 && !active) UnreadBadge(count, Modifier.align(Alignment.TopEnd).offset(x = 12.dp, y = 4.dp))
                    }
                    Box(
                        Modifier
                            .width(if (active) 48.dp else 0.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(X.Blue),
                    )
                }
            }
        }
        IconButton(onClick = { web.reload(slot) }) {
            Icon(XIcons.Reload, contentDescription = "Recargar", tint = X.Secondary, modifier = Modifier.size(18.dp))
        }
    }
}

/** Globo azul con los no leídos, como el de X en su barra. */
@Composable
private fun UnreadBadge(count: Int, modifier: Modifier = Modifier) {
    Box(
        modifier
            .defaultMinSize(minWidth = 17.dp)
            .height(17.dp)
            .clip(CircleShape)
            .background(X.Blue)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(if (count > 99) "99+" else count.toString(), style = XType.small.copy(color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold), maxLines = 1)
    }
}

/**
 * Cabecera "← Post" sobre una web con un post abierto desde la otra mitad: volver a la fuente,
 * ampliar el post a las dos mitades con tus notas, o citarlo en el redactor del otro lado.
 */
@Composable
fun WebPostHeader(vm: AppViewModel, index: Int, compact: Boolean = false) {
    val web = LocalWebPanes.current
    val panel = vm.webPanels[index]
    val url = panel.openUrl ?: return
    val composing = panel.fromLabel == "Redactar"
    val external = XWeb.isExternal(url) || XWeb.isMedia(url) || XWeb.isVideo(url)
    // Una web ajena se llama por su dominio, el de la página de ahora (los enlaces de X pasan por t.co).
    val current = web.urls["pane$index"]?.takeIf { XWeb.isExternal(it) } ?: url
    val title = when {
        composing -> "Redactar"
        XWeb.isMedia(url) -> "Foto"
        XWeb.isGif(url) -> "GIF"
        XWeb.isVideo(url) -> "Vídeo"
        external -> XWeb.siteLabel(current)
        XWeb.isStatus(url) -> "Post"
        else -> XWeb.handleOf(url)?.let { "@$it" } ?: "X"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(53.dp)
            .background(X.Bg)
            .bottomBorder()
            .padding(start = 4.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { vm.closeWebPost(index) }) {
            Icon(XIcons.Back, contentDescription = "Atrás", tint = X.Text, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(title, style = XType.title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (external) {
            IconButton(onClick = { web.openInBrowser(XWeb.mediaUrl(current)) }) {
                Icon(XIcons.Globe, contentDescription = "Abrir en el navegador", tint = X.Text, modifier = Modifier.size(20.dp))
            }
        }
        if (!composing && !compact) {
            IconButton(onClick = { vm.composeWeb(1 - index, quoting = current) }) {
                Icon(XIcons.Compose, contentDescription = "Citar en un post", tint = X.Text, modifier = Modifier.size(20.dp))
            }
            if (!external) {
                IconButton(onClick = { vm.expand(url) }) {
                    Icon(XIcons.Expand, contentDescription = "Ampliar", tint = X.Text, modifier = Modifier.size(20.dp))
                }
            }
        }
        if (!compact) {
            PillButton(
                panel.source.label,
                onClick = { vm.closeWebPost(index) },
                background = X.Bg,
                content = X.Text,
                border = X.OutlineButton,
                height = 32.dp,
                icon = XIcons.Stack,
            )
        }
    }
}

/** Sobremesa con X real: arriba lo que lees, abajo lo que tocas; un post de arriba se abre abajo. */
@Composable
fun RealTabletop(vm: AppViewModel, screen: ScreenInfo) {
    val web = LocalWebPanes.current
    val context = LocalContext.current
    val density = LocalDensity.current
    var originY by remember { mutableFloatStateOf(0f) }
    BackHandler {
        val focused = vm.webFocused()
        when {
            web.canGoBack(web.lastActive) -> web.goBack(web.lastActive)
            focused != null -> vm.closeWebPost(focused)
            else -> (context as? Activity)?.moveTaskToBack(true)
        }
    }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { originY = it.positionInWindow().y },
    ) {
        val origin = with(density) { originY.toDp() }
        val split = screen.firstPane?.let { it - origin } ?: (maxHeight / 2)
        val topHeight = split.coerceIn(80.dp, (maxHeight - 80.dp).coerceAtLeast(80.dp))
        Column(Modifier.fillMaxSize()) {
            WebHalf(vm, 0, Modifier.height(topHeight).fillMaxWidth())
            Spacer(Modifier.height(screen.hinge))
            WebHalf(vm, 1, Modifier.weight(1f).fillMaxWidth().topBorder())
        }
        Fab(
            onClick = { vm.composeWeb(1) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 16.dp),
        )
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = 6.dp, end = 8.dp)
                .size(36.dp)
                .clip(CircleShape)
                .background(X.Bg.copy(alpha = 0.6f))
                .clickable(onClickLabel = "Ajustes", role = Role.Button) { vm.settingsOpen = true },
            contentAlignment = Alignment.Center,
        ) {
            Icon(XIcons.More, contentDescription = "Ajustes", tint = Color.White, modifier = Modifier.size(18.dp))
        }
    }
}
