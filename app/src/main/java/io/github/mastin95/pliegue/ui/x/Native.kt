package io.github.mastin95.pliegue.ui.x

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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
import io.github.mastin95.pliegue.web.PaneOptions
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
    val clipboard = LocalClipboardManager.current
    val taps = remember(slot, vm) {
        PostTaps(
            onOpen = { vm.onWebPost(slot, it.url) },
            onUser = { vm.onWebPost(slot, "https://x.com/$it") },
            onLink = { url ->
                when {
                    XWeb.isMedia(url) || XWeb.isVideo(url) || XWeb.isExternal(url) -> if (!vm.onWebLink(slot, url)) web.openInBrowser(url)
                    XWeb.isX(url) -> vm.onWebPost(slot, url)
                    else -> web.openInBrowser(url)
                }
            },
            onMedia = { _, m ->
                val target = if (m.kind == XMediaKind.Photo) XWeb.largeImage(m.url) else m.video ?: m.url
                if (!vm.onWebLink(slot, target)) web.openInBrowser(target)
            },
            onReply = { vm.replyFromNative(slot, it.id) },
            onRepost = { vm.showToast("Repostear desde aquí llega en la siguiente versión.") },
            onLike = { vm.showToast("Me gusta desde aquí llega en la siguiente versión.") },
            onBookmark = { vm.showToast("Guardar desde aquí llega en la siguiente versión.") },
            onShare = {
                clipboard.setText(AnnotatedString(it.url))
                vm.showToast("Enlace copiado.")
            },
        )
    }
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
            else -> XFeedScreen(vm, slot, view, taps, selectedId)
        }
    }
}
