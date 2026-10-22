package io.github.mastin95.pliegue.ui.x

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/** Reproducción automática de los vídeos en los feeds, con las tres opciones de X. */
enum class Autoplay(val key: String, val label: String) {
    Always("siempre", "Con datos móviles o wifi"),
    Wifi("wifi", "Solo con wifi"),
    Never("nunca", "Nunca");

    companion object {
        fun of(key: String?): Autoplay = entries.firstOrNull { it.key == key } ?: Wifi
    }
}

/**
 * Qué vídeo de un feed se mueve solo: uno a la vez, el de más arriba de los que se ven al menos a
 * medias. Cada vídeo o GIF de la lista avisa de cuánto se ve cada vez que la lista se mueve.
 */
@Stable
class FeedAutoplay {
    private val seen = mutableMapOf<Any, Pair<Float, Float>>()

    /** El vídeo que se reproduce ahora (la marca con la que se apuntó), o ninguno. */
    var current by mutableStateOf<Any?>(null)
        private set

    fun report(who: Any, visible: Float, top: Float) {
        seen[who] = visible to top
        pick()
    }

    fun gone(who: Any) {
        seen.remove(who)
        pick()
    }

    private fun pick() {
        current = seen.entries.filter { it.value.first >= 0.5f }.minByOrNull { it.value.second }?.key
    }
}

/** El reparto de un feed cuando sus vídeos se mueven solos; nulo donde no (o si está apagado). */
val LocalFeedAutoplay = compositionLocalOf<FeedAutoplay?> { null }

/** Si ahora toca que los vídeos se muevan solos: según Ajustes y, con "solo con wifi", si la red no es de datos. */
@Composable
fun rememberAutoplayAllowed(setting: Autoplay): Boolean {
    val context = LocalContext.current
    var unmetered by remember { mutableStateOf(true) }
    if (setting == Autoplay.Wifi) {
        DisposableEffect(context) {
            val cm = context.getSystemService(ConnectivityManager::class.java)
            unmetered = cm?.isActiveNetworkMetered == false
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    unmetered = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                }

                override fun onLost(network: Network) {
                    unmetered = cm?.isActiveNetworkMetered == false
                }
            }
            runCatching { cm?.registerDefaultNetworkCallback(callback) }
            onDispose { runCatching { cm?.unregisterNetworkCallback(callback) } }
        }
    }
    return when (setting) {
        Autoplay.Always -> true
        Autoplay.Never -> false
        Autoplay.Wifi -> unmetered
    }
}
