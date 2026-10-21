package io.github.mastin95.pliegue.ui.x

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import io.github.mastin95.pliegue.ui.components.PillButton
import io.github.mastin95.pliegue.ui.theme.X
import io.github.mastin95.pliegue.ui.theme.XType
import io.github.mastin95.pliegue.ui.web.LocalWebPanes
import io.github.mastin95.pliegue.ui.web.WebPane
import io.github.mastin95.pliegue.web.PaneOptions
import io.github.mastin95.pliegue.web.XWeb

/**
 * Lo que se abre en una mitad al tocar algo que no es una página de X: una foto de X en el visor
 * propio (con zoom), un vídeo o GIF en el reproductor propio, y cualquier otra web tal cual.
 */
@Composable
fun MediaOrWeb(slot: String, url: String, modifier: Modifier = Modifier) {
    when {
        XWeb.isMedia(url) -> PhotoViewer(url, modifier)
        XWeb.isVideo(url) -> VideoPlayer(url, modifier)
        else -> WebPane(slot, url, modifier, PaneOptions.EXTERNAL)
    }
}

/**
 * Una foto sobre negro, como en X: pellizcar para ampliar, doble toque para acercar y alejar,
 * arrastrar para moverse por ella.
 */
@Composable
fun PhotoViewer(url: String, modifier: Modifier = Modifier) {
    var scale by remember(url) { mutableFloatStateOf(1f) }
    var offset by remember(url) { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var loaded by remember(url) { mutableStateOf(false) }
    var failed by remember(url) { mutableStateOf(false) }
    fun clamp(o: Offset, s: Float): Offset {
        val maxX = size.width * (s - 1f) / 2f
        val maxY = size.height * (s - 1f) / 2f
        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
    }
    Box(
        modifier
            .background(Color.Black)
            .onSizeChanged { size = it }
            .pointerInput(url) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val s = (scale * zoom).coerceIn(1f, 5f)
                    scale = s
                    offset = if (s > 1f) clamp(offset + pan, s) else Offset.Zero
                }
            }
            .pointerInput(url) {
                detectTapGestures(onDoubleTap = { tap ->
                    if (scale > 1f) {
                        scale = 1f
                        offset = Offset.Zero
                    } else {
                        scale = 2.5f
                        offset = clamp(Offset((size.width / 2f - tap.x) * 1.5f, (size.height / 2f - tap.y) * 1.5f), 2.5f)
                    }
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = url,
            contentDescription = "Foto",
            contentScale = ContentScale.Fit,
            onSuccess = { loaded = true },
            onError = { failed = true },
            modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
        )
        if (!loaded && !failed) CircularProgressIndicator(color = X.Blue, strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
        if (failed) MediaProblem("No se pudo cargar la foto.", url)
    }
}

/** Vídeos y GIF de X con reproductor propio (ExoPlayer): los GIF van en bucle, sin sonido ni controles. */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(url: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val gif = XWeb.isGif(url)
    val src = XWeb.mediaUrl(url)
    var error by remember(src) { mutableStateOf<String?>(null) }
    val player = remember(src) {
        ExoPlayer.Builder(context).build().apply {
            // Con sonido se pide el foco de audio (para la música); un GIF ni suena ni lo pide.
            setAudioAttributes(AudioAttributes.DEFAULT, !gif)
            if (gif) {
                volume = 0f
                repeatMode = Player.REPEAT_MODE_ONE
            }
            setMediaItem(MediaItem.fromUri(src))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(e: PlaybackException) {
                error = e.errorCodeName
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    // Al irse la app detrás (o apagarse la pantalla) el vídeo se para; se le da al play para seguir.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, player) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) player.pause() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = !gif
                    controllerShowTimeoutMs = 2500
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            modifier = Modifier.fillMaxSize(),
            onRelease = { it.player = null },
            update = { it.player = player },
        )
        error?.let { MediaProblem(if (gif) "No se pudo reproducir el GIF ($it)." else "No se pudo reproducir el vídeo ($it).", src) }
    }
}

@Composable
private fun MediaProblem(text: String, url: String) {
    val web = LocalWebPanes.current
    Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(text, style = XType.body, textAlign = TextAlign.Center)
        PillButton("Abrir en el navegador", onClick = { web.openInBrowser(url) }, background = X.Bg, content = X.Text, border = X.OutlineButton, height = 32.dp)
    }
}
