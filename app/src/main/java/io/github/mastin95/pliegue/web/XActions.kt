package io.github.mastin95.pliegue.web

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Lo que la app sabe hacer en X a través de su web escondida. `js` es el nombre que entiende el guion. */
enum class XActionKind(val js: String, val label: String, val onProfile: Boolean = false) {
    Like("like", "el me gusta"),
    Unlike("unlike", "quitar el me gusta"),
    Repost("repost", "el repost"),
    Unrepost("unrepost", "deshacer el repost"),
    Bookmark("bookmark", "guardar el post"),
    Unbookmark("unbookmark", "quitar el post de Guardados"),
    Follow("follow", "seguir", onProfile = true),
    Unfollow("unfollow", "dejar de seguir", onProfile = true),
    Reply("reply", "la respuesta a"),
}

/**
 * Las acciones (me gusta, repost, guardar, seguir, responder) se hacen como las haría uno en la web: una
 * WebView escondida ("acciones", la cuelga ActionsPane) abre el post, el perfil o el redactor de X (con
 * el texto de la respuesta ya puesto) con la sesión de X y un guion toca el botón de X ([XWeb.actScript]). Van de una en una y cada una avisa al acabar con si X la
 * aceptó; la app ya cambió la tarjeta al tocar y la deshace si no. No se inventa ninguna petición a X.
 */
class XActions(private val web: WebPanes) {
    class Pending(val kind: XActionKind, val target: String, val text: String, val done: (ok: Boolean, why: String) -> Unit) {
        val url: String get() = when {
            kind == XActionKind.Reply -> XWeb.replyUrl(target, text)
            kind.onProfile -> "https://x.com/$target"
            else -> "https://x.com/i/status/$target"
        }
        val label: String get() = kind.label + " " + (if (kind.onProfile) "@$target" else target)
    }

    private val queue = ArrayDeque<Pending>()
    private var current: Pending? = null
    private var token = 0
    private val main = Handler(Looper.getMainLooper())

    /** La dirección que debe tener la web de acciones (la pinta ActionsPane). */
    var url by mutableStateOf("about:blank")
        private set

    /** Cuántas quedan por hacer, contando la que está en marcha. */
    var pending by mutableStateOf(0)
        private set

    fun run(kind: XActionKind, target: String, text: String = "", done: (ok: Boolean, why: String) -> Unit) {
        queue.addLast(Pending(kind, target, text, done))
        pending = queue.size + (if (current != null) 1 else 0)
        next()
    }

    private fun next() {
        if (current != null) return
        val a = queue.removeFirstOrNull() ?: return
        if (!web.has(SLOT)) {
            pending = queue.size
            a.done(false, "la web de acciones no está")
            next()
            return
        }
        current = a
        val t = ++token
        pending = queue.size + 1
        url = a.url
        web.load(SLOT, a.url)
        // El texto de una respuesta no va al registro (que se copia para mandarlo).
        web.note("[$SLOT] ${a.label}: abro ${XWeb.withoutText(a.url).removePrefix("https://")}")
        main.postDelayed({ finish(t, false, "la web no respondió") }, 40_000)
    }

    /** La web de acciones terminó de cargar una página: se lanza el guion de la acción en marcha. */
    fun pageReady() {
        val a = current ?: return
        web.evaluate(SLOT, XWeb.actScript(a.kind.js, a.target, token))
    }

    /** El guion avisa por el puente (ya en el hilo principal). */
    fun acted(token: Int, ok: Boolean, why: String) = finish(token, ok, why)

    private fun finish(t: Int, ok: Boolean, why: String) {
        if (t != token) return
        val a = current ?: return
        current = null
        pending = queue.size
        web.note("[$SLOT] ${a.label}: " + if (ok) "hecho" + (if (why.isNotEmpty()) " ($why)" else "") else "falló: $why")
        a.done(ok, why)
        next()
    }

    /** Se cierran las webs (salir de la cuenta): lo pendiente se da por fallido. */
    fun reset() {
        token++
        val all = listOfNotNull(current) + queue
        current = null
        queue.clear()
        pending = 0
        all.forEach { it.done(false, "se cerró la sesión") }
    }

    companion object {
        const val SLOT = "acciones"
    }
}
