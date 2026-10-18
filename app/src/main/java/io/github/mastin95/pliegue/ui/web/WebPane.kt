package io.github.mastin95.pliegue.ui.web

import android.content.ClipDescription
import androidx.activity.compose.BackHandler
import android.view.ViewGroup
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.mastin95.pliegue.AppViewModel
import io.github.mastin95.pliegue.ui.XIcons
import io.github.mastin95.pliegue.ui.components.bottomBorder
import io.github.mastin95.pliegue.ui.theme.X
import io.github.mastin95.pliegue.ui.theme.XType
import io.github.mastin95.pliegue.web.PaneOptions
import io.github.mastin95.pliegue.web.WebPanes
import io.github.mastin95.pliegue.web.XWeb

/** Las WebViews de la actividad, una por ranura. Las crea MainActivity. */
val LocalWebPanes = staticCompositionLocalOf<WebPanes> { error("Sin WebPanes") }

/**
 * Una web de X en una ranura. La WebView vive en [WebPanes]: aquí solo se cuelga del árbol y se
 * le pide la dirección; si `url` cambia se carga la nueva, pero navegar dentro no la recarga.
 */
@Composable
fun WebPane(
    slot: String,
    url: String,
    modifier: Modifier = Modifier,
    options: PaneOptions = PaneOptions(),
    textZoom: Int = 100,
) {
    val web = LocalWebPanes.current
    Box(modifier.fillMaxSize().background(X.Bg)) {
        key(slot) {
            AndroidView(
                factory = { web.get(slot, url, options).also { v -> (v.parent as? ViewGroup)?.removeView(v) } },
                modifier = Modifier.fillMaxSize(),
                onRelease = { v -> (v.parent as? ViewGroup)?.removeView(v) },
            )
        }
        LaunchedEffect(slot, url) { web.ensure(slot, url) }
        LaunchedEffect(slot, options) { web.configure(slot, options) }
        LaunchedEffect(slot, textZoom) { web.textZoom(slot, textZoom) }
        val p = web.progress[slot] ?: 100
        if (p < 100) {
            Box(
                Modifier
                    .fillMaxWidth(p / 100f)
                    .height(3.dp)
                    .background(X.Blue)
                    .align(Alignment.TopStart),
            )
        }
    }
}

/**
 * La ventana aparte que abrió una web (el inicio de sesión con Google elige la cuenta en una),
 * encima de todo y con su propio cierre. Se quita sola cuando la web la cierra.
 */
@Composable
fun PopupSheet() {
    val web = LocalWebPanes.current
    val popup = web.popup ?: return
    BackHandler { if (popup.canGoBack()) popup.goBack() else web.closePopup() }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f))) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.94f)
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .background(X.Bg),
        ) {
            Row(
                Modifier.fillMaxWidth().height(48.dp).bottomBorder().padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(XWeb.host(web.popupUrl) ?: "Ventana", style = XType.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (web.popupProgress < 100) Text("cargando ${web.popupProgress} %", style = XType.small, maxLines = 1)
                }
                IconButton(onClick = { web.closePopup() }) {
                    Icon(XIcons.Close, contentDescription = "Cerrar la ventana", tint = X.Text, modifier = Modifier.size(20.dp))
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                key(popup) {
                    AndroidView(
                        factory = { popup.also { v -> (v.parent as? ViewGroup)?.removeView(v) } },
                        modifier = Modifier.fillMaxSize(),
                        onRelease = { v -> (v.parent as? ViewGroup)?.removeView(v) },
                    )
                }
                if (web.popupProgress < 100) {
                    Box(Modifier.fillMaxWidth(web.popupProgress / 100f).height(3.dp).background(X.Blue).align(Alignment.TopStart))
                }
            }
        }
    }
}

/**
 * Zona para soltar un usuario arrastrado desde otra web y añadirlo a Mi feed. Aparece sola en
 * cuanto empieza un arrastre (la web de X también arrastra enlaces de perfil por su cuenta).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun UserDropTarget(vm: AppViewModel, fromLabel: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var active by remember { mutableStateOf(false) }
    var hovering by remember { mutableStateOf(false) }
    val target = remember(fromLabel) {
        object : DragAndDropTarget {
            override fun onStarted(event: DragAndDropEvent) {
                active = true
            }

            override fun onEntered(event: DragAndDropEvent) {
                hovering = true
            }

            override fun onExited(event: DragAndDropEvent) {
                hovering = false
            }

            override fun onDrop(event: DragAndDropEvent): Boolean {
                hovering = false
                active = false
                val text = event.toAndroidDragEvent().clipData?.let { c -> if (c.itemCount > 0) c.getItemAt(0).coerceToText(null)?.toString() else null }
                vm.dropText(text, fromLabel)
                return true
            }

            override fun onEnded(event: DragAndDropEvent) {
                active = false
                hovering = false
                vm.dragHandle = null
            }
        }
    }
    Box(
        modifier.dragAndDropTarget(
            shouldStartDragAndDrop = { e -> e.mimeTypes().any { it == ClipDescription.MIMETYPE_TEXT_PLAIN || it == ClipDescription.MIMETYPE_TEXT_URILIST } },
            target = target,
        ),
    ) {
        Column(Modifier.fillMaxSize()) {
            if (active) {
                val handle = vm.dragHandle
                DropBanner(if (handle != null) "Suelta para añadir a @$handle a Mi feed" else "Suelta aquí para añadir a Mi feed", hovering)
            }
            Box(Modifier.weight(1f).fillMaxWidth()) { content() }
        }
    }
}

@Composable
private fun DropBanner(text: String, hovering: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .height(64.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (hovering) X.NewBgHover else X.NewBg)
            .drawBehind {
                drawRoundRect(
                    color = X.Blue,
                    cornerRadius = CornerRadius(16.dp.toPx()),
                    style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 12f))),
                )
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(XIcons.Plus, contentDescription = null, tint = X.Blue, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = XType.body.copy(color = X.Blue, fontWeight = FontWeight.Bold), maxLines = 1)
    }
}

/** Mi feed sin creadores todavía: cómo llenarlo. */
@Composable
fun EmptyMyFeed(modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Mi feed está vacío", style = XType.title.copy(fontSize = XType.title.fontSize * 1.4f))
        Spacer(Modifier.height(8.dp))
        Text("Mantén pulsado el avatar o el nombre de alguien en el otro lado y arrástralo hasta aquí. Mi feed enseñará lo último de esas personas.", style = XType.secondary)
    }
}

/** Ayuda para que un `Modifier` de relleno completo quede legible en las pantallas. */
fun Modifier.fillPane(): Modifier = fillMaxHeight().fillMaxWidth()
