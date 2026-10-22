package io.github.mastin95.pliegue.ui.x

import android.content.Context
import android.view.LayoutInflater
import android.net.Uri
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import io.github.mastin95.pliegue.AppViewModel
import io.github.mastin95.pliegue.R
import io.github.mastin95.pliegue.ui.components.PillButton
import io.github.mastin95.pliegue.ui.theme.X
import io.github.mastin95.pliegue.ui.theme.XType
import io.github.mastin95.pliegue.ui.web.LocalWebPanes
import io.github.mastin95.pliegue.ui.web.WebPane
import io.github.mastin95.pliegue.web.PaneOptions
import io.github.mastin95.pliegue.web.XWeb
import java.io.File
import kotlinx.coroutines.delay

/**
 * Lo que se abre en una mitad al tocar algo que no es una página de X: una foto de X en el visor
 * propio (con zoom), los vídeos en vertical (el tocado y los demás del feed, deslizando), un vídeo
 * o GIF suelto en el reproductor propio, y cualquier otra web tal cual.
 */
@Composable
fun MediaOrWeb(vm: AppViewModel, slot: String, url: String, modifier: Modifier = Modifier) {
    when {
        XWeb.isMedia(url) -> PhotoViewer(url, modifier)
        XWeb.isVideos(url) -> XVideosScreen(vm, slot, url, modifier)
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

/**
 * Por dónde va un vídeo y cuánto dura (en milisegundos), para la barra propia de la pantalla de
 * vídeos; `seekTo` lo lleva a otro punto. Lo rellena el [VideoPlayer] al que se le pasa.
 */
class VideoProgress {
    var position by mutableLongStateOf(0L)
        private set
    var duration by mutableLongStateOf(0L)
        private set
    internal var player: Player? = null

    internal fun read(p: Player) {
        position = p.currentPosition.coerceAtLeast(0L)
        duration = p.duration.takeIf { it > 0 } ?: 0L
    }

    fun seekTo(ms: Long) {
        val p = player ?: return
        p.seekTo(ms)
        read(p)
    }
}

/**
 * Vídeos y GIF de X con reproductor propio (ExoPlayer): los GIF van en bucle, sin sonido ni controles.
 * `play` lo arranca y lo para desde fuera (la pantalla de vídeos para el que no está a la vista);
 * `loop` y `controls` cambian lo que toca por defecto según sea vídeo o GIF. `muted` quita el sonido
 * (y entonces no para la música de otra app), `speed` lo acelera, `progress` recibe por dónde va y
 * `startAt` es el milisegundo por el que empieza. `preview` es el de una tarjeta del feed que se mueve
 * solo: sin fondo negro (se sigue viendo la miniatura hasta el primer fotograma), recortado como ella
 * y sin aviso si falla (se queda la miniatura).
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(
    url: String,
    modifier: Modifier = Modifier,
    play: Boolean = true,
    loop: Boolean = XWeb.isGif(url),
    controls: Boolean = !XWeb.isGif(url),
    muted: Boolean = false,
    speed: Float = 1f,
    progress: VideoProgress? = null,
    startAt: Long = 0L,
    preview: Boolean = false,
) {
    val context = LocalContext.current
    val web = LocalWebPanes.current
    val gif = XWeb.isGif(url)
    val src = remember(url, web.simulatedNet) { (if (web.simulatedNet) sampleVideo(context) else null) ?: XWeb.mediaUrl(url) }
    var error by remember(src) { mutableStateOf<String?>(null) }
    val player = remember(src) {
        ExoPlayer.Builder(context).build().apply {
            // Con sonido se pide el foco de audio (para la música); un GIF o un vídeo sin sonido ni suena ni lo pide.
            setAudioAttributes(AudioAttributes.DEFAULT, !gif && !muted)
            if (gif || muted) volume = 0f
            if (loop) repeatMode = Player.REPEAT_MODE_ONE
            setMediaItem(MediaItem.fromUri(src), startAt)
            prepare()
            playWhenReady = play
        }
    }
    LaunchedEffect(player, play) { player.playWhenReady = play }
    LaunchedEffect(player, muted) {
        val silent = gif || muted
        player.volume = if (silent) 0f else 1f
        player.setAudioAttributes(AudioAttributes.DEFAULT, !silent)
    }
    LaunchedEffect(player, speed) { player.setPlaybackSpeed(speed) }
    if (progress != null) {
        DisposableEffect(player, progress) {
            progress.player = player
            onDispose { if (progress.player === player) progress.player = null }
        }
        LaunchedEffect(player, progress) {
            while (true) {
                progress.read(player)
                delay(200)
            }
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
    // Al irse la app detrás (o apagarse la pantalla) el vídeo se para; al volver sigue si tocaba seguir.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val wanted by rememberUpdatedState(play)
    DisposableEffect(lifecycle, player) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> player.pause()
                Lifecycle.Event.ON_START -> player.playWhenReady = wanted
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    Box(modifier.background(if (preview) Color.Transparent else Color.Black), contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { ctx ->
                (LayoutInflater.from(ctx).inflate(R.layout.reproductor, null) as PlayerView).apply {
                    useController = controls
                    controllerShowTimeoutMs = 2500
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    resizeMode = if (preview) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
                    val back = if (preview) android.graphics.Color.TRANSPARENT else android.graphics.Color.BLACK
                    setBackgroundColor(back)
                    setShutterBackgroundColor(back)
                    if (preview) setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                }
            },
            modifier = Modifier.fillMaxSize(),
            onRelease = { it.player = null },
            update = {
                it.useController = controls
                it.player = player
            },
        )
        if (!preview) error?.let { MediaProblem(if (gif) "No se pudo reproducir el GIF ($it)." else "No se pudo reproducir el vídeo ($it).", src) }
    }
}

/**
 * En las pruebas del Android virtual no hay vídeos de X: si las pruebas dejaron un vídeo de muestra
 * (muestra.mp4) en la carpeta de la app, la externa (Android/data/…/files) o la privada, con la red
 * simulada se reproduce ese. Nulo si no está.
 */
private fun sampleVideo(context: Context): String? =
    listOfNotNull(context.filesDir, context.getExternalFilesDir(null)).map { File(it, "muestra.mp4") }.firstOrNull { it.canRead() }?.let { Uri.fromFile(it).toString() }

@Composable
private fun MediaProblem(text: String, url: String) {
    val web = LocalWebPanes.current
    Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(text, style = XType.body, textAlign = TextAlign.Center)
        PillButton("Abrir en el navegador", onClick = { web.openInBrowser(url) }, background = X.Bg, content = X.Text, border = X.OutlineButton, height = 32.dp)
    }
}
