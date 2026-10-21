package io.github.mastin95.pliegue.ui.x

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import io.github.mastin95.pliegue.AppViewModel
import io.github.mastin95.pliegue.model.Source
import io.github.mastin95.pliegue.model.XMediaKind
import io.github.mastin95.pliegue.model.XView
import io.github.mastin95.pliegue.ui.theme.X
import io.github.mastin95.pliegue.ui.web.LocalWebPanes
import io.github.mastin95.pliegue.ui.web.WebPane
import androidx.compose.ui.unit.dp
import io.github.mastin95.pliegue.web.PaneOptions
import io.github.mastin95.pliegue.web.XActions
import io.github.mastin95.pliegue.web.XWeb

/**
 * Una ranura de X: la web (que se descarga los posts con tu sesión) y, encima, si la interfaz propia
 * está activada y la dirección es de las que la app sabe pintar, los mismos posts pintados por la app.
 * La web sigue debajo, cargando y pasando páginas; solo se ve si la app no sabe pintar la página.
 */
@Composable
fun NativeOrWeb(vm: AppViewModel, slot: String, url: String, options: PaneOptions, modifier: Modifier = Modifier, source: Source? = null, textZoom: Int = 100, selectedUrl: String? = null) {
    val view = if (vm.nativeUi && !options.external) XWeb.view(url, source) else null
    Box(modifier.fillMaxSize()) {
        WebPane(slot, url, Modifier.fillMaxSize(), options, textZoom)
        if (view != null) NativeLayer(vm, slot, view, selectedUrl)
    }
}

/** Lo que la app pinta encima de una web; se queda con todos los toques para que no lleguen a la web. */
@Composable
fun NativeLayer(vm: AppViewModel, slot: String, view: XView, selectedUrl: String? = null) {
    val web = LocalWebPanes.current
    val actor = remember(web, vm) { XActor(web, vm) }
    val tab = if (view == XView.Notifications) vm.notifTab(slot) else vm.homeTab(slot)
    val taps = rememberPostTaps(vm, slot, "$slot/" + feedName(view, tab))
    val selectedId = selectedUrl?.let(XWeb::statusId)
    Box(
        Modifier
            .fillMaxSize()
            .background(X.Bg)
            // Un nodo que escucha toques: así los toques sobre la capa (hasta en los huecos) no bajan a la web.
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } },
    ) {
        when (view) {
            is XView.Thread -> XThreadScreen(vm, slot, view.statusId, taps)
            else -> XFeedScreen(vm, slot, view, taps, selectedId, onFollow = actor::follow)
        }
    }
}

/**
 * Qué hace la app al tocar cada parte de un post pintado por ella: el post o la cuenta se abren en
 * la otra mitad; las fotos, en el visor propio a tamaño grande; los vídeos y GIF, en la pantalla de
 * vídeos en vertical junto con los demás de `videosFrom` ("ranura/consulta", el feed donde está el
 * post); y las acciones (me gusta, repost, guardar) se hacen pulsando el botón de X en la web escondida.
 */
@Composable
fun rememberPostTaps(vm: AppViewModel, slot: String, videosFrom: String): PostTaps {
    val web = LocalWebPanes.current
    val clipboard = LocalClipboardManager.current
    val actor = remember(web, vm) { XActor(web, vm) }
    return remember(slot, vm, videosFrom) {
        PostTaps(
            onOpen = { vm.onWebPost(slot, it.url) },
            onUser = { vm.onWebPost(slot, "https://x.com/$it") },
            onLink = { url ->
                when {
                    XWeb.isViewer(url) || XWeb.isExternal(url) -> if (!vm.onWebLink(slot, url)) web.openInBrowser(url)
                    XWeb.isX(url) -> vm.onWebPost(slot, url)
                    else -> web.openInBrowser(url)
                }
            },
            onMedia = { t, m ->
                if (m.kind != XMediaKind.Photo && m.video != null) {
                    // Donde no hay otra mitad (post ampliado, la mesa), el vídeo va al navegador.
                    if (!vm.onWebLink(slot, XWeb.videosUrl(t.id, videosFrom))) web.openInBrowser(m.video)
                } else {
                    // Un vídeo sin dirección de vídeo enseña su imagen.
                    val target = XWeb.largeImage(m.url)
                    if (!vm.onWebLink(slot, target)) web.openInBrowser(target)
                }
            },
            onReply = { vm.replyFromNative(slot, it.id) },
            onRepost = actor::repost,
            onLike = actor::like,
            onBookmark = actor::bookmark,
            onShare = {
                clipboard.setText(AnnotatedString(it.url))
                vm.showToast("Enlace copiado.")
            },
        )
    }
}

/**
 * La web escondida donde se hacen las acciones (me gusta, repost, guardar, seguir): existe y mide como
 * la pantalla de un móvil para que X pinte su página entera, pero no se ve ni se puede tocar.
 */
@Composable
fun ActionsPane() {
    val web = LocalWebPanes.current
    Box(Modifier.size(0.dp).clipToBounds()) {
        Box(Modifier.requiredSize(412.dp, 915.dp)) {
            WebPane(XActions.SLOT, web.actions.url, Modifier.fillMaxSize(), PaneOptions(interceptPosts = false, hideChrome = false, keepInside = true))
        }
    }
}
