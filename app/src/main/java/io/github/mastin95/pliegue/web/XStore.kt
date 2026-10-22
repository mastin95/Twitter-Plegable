package io.github.mastin95.pliegue.web

import androidx.compose.runtime.mutableStateMapOf
import io.github.mastin95.pliegue.model.XEntry
import io.github.mastin95.pliegue.model.XFeed
import io.github.mastin95.pliegue.model.XThread
import io.github.mastin95.pliegue.model.XTimeline
import io.github.mastin95.pliegue.model.XTweet
import io.github.mastin95.pliegue.model.XUser
import io.github.mastin95.pliegue.model.videoPosts
import io.github.mastin95.pliegue.model.withTweet

/**
 * Lo que la app sabe de X por haber escuchado a sus webs: un feed por ranura y consulta
 * (pane0/HomeTimeline…), la conversación abierta en cada ranura y las cuentas vistas.
 * Todo son estados de Compose: la interfaz propia se repinta sola cuando llega algo.
 */
class XStore {
    val feeds = mutableStateMapOf<String, XFeed>()
    val threads = mutableStateMapOf<String, XThread>()

    /** Cuentas por @usuario en minúsculas. */
    val users = mutableStateMapOf<String, XUser>()

    /** Cuándo se pidió la página siguiente a cada ranura; se borra al llegar algo. */
    val asked = mutableStateMapOf<String, Long>()

    /** Cuándo llegó lo último de cada ranura. */
    val lastSeen = mutableStateMapOf<String, Long>()

    fun feed(slot: String, name: String): XFeed? = feeds["$slot/$name"]

    fun thread(slot: String): XThread? = threads[slot]

    fun user(handle: String?): XUser? = handle?.let { users[it.lowercase()] }

    /**
     * Los posts con vídeo o GIF de un feed o conversación ya cargado, para la pantalla de vídeos en
     * vertical: `source` es "ranura/consulta" (pane0/HomeTimeline, pane0/NotificationsTimeline/All,
     * pane1/TweetDetail…). Según llegan páginas nuevas, la lista crece.
     */
    fun videos(source: String): List<XTweet> {
        val slot = source.substringBefore('/')
        val name = source.substringAfter('/')
        val tweets = if (name == XParse.THREAD) {
            threads[slot]?.let { it.ancestors + it.focal + it.replies.flatMap { e -> e.tweets } } ?: emptyList()
        } else {
            (feeds[source] ?: feeds[source.substringBeforeLast('/')])?.let { listOfNotNull(it.pinned) + it.tweets } ?: emptyList()
        }
        return videoPosts(tweets)
    }

    /**
     * La lista de la pantalla de vídeos, aparte del feed del que salió (clave "videos/ranura/consulta"):
     * sigue entera aunque la web de esa ranura cargue otra cosa (el post, el redactor), crece con lo
     * que va llegando y, al estar en `feeds`, los me gusta y guardados la actualizan como a los demás.
     */
    fun keepVideos(source: String, fresh: List<XTweet>, reset: Boolean = false) {
        val key = "videos/$source"
        val old = if (reset) emptyList() else feeds[key]?.tweets ?: emptyList()
        val known = old.map { it.id }.toHashSet()
        val merged = old.map { o -> fresh.firstOrNull { it.id == o.id } ?: o } + fresh.filter { it.id !in known }
        if (reset || merged != old) feeds[key] = XFeed(merged.map { XEntry(it) }, updatedAt = System.currentTimeMillis(), pages = 1)
    }

    fun videosKept(source: String): List<XTweet> = feeds["videos/$source"]?.tweets ?: emptyList()

    private val handles = HashMap<String, String>()

    /** Una cuenta por su id numérico (el tuyo sale de la cookie twid). */
    fun byId(id: String?): XUser? = id?.let { handles[it] }?.let { users[it] }

    /** Cambia un post en todos los feeds y conversaciones donde esté (me gusta, repost, guardado). */
    fun updateTweet(id: String, f: (XTweet) -> XTweet) {
        for (k in feeds.keys.toList()) {
            val old = feeds[k] ?: continue
            val new = old.withTweet(id, f)
            if (new !== old) feeds[k] = new
        }
        for (k in threads.keys.toList()) {
            val old = threads[k] ?: continue
            val new = old.withTweet(id, f)
            if (new !== old) threads[k] = new
        }
    }

    /** Cambia una cuenta (seguir, dejar de seguir). */
    fun updateUser(handle: String, f: (XUser) -> XUser) {
        val k = handle.lowercase()
        users[k]?.let { users[k] = f(it) }
    }

    /** La ranura carga otra dirección: lo que tenía ya no vale. */
    fun clear(slot: String) {
        feeds.keys.filter { it.startsWith("$slot/") }.forEach { feeds.remove(it) }
        threads.remove(slot)
        asked.remove(slot)
        lastSeen.remove(slot)
    }

    sealed class Parsed {
        data class Feed(val timeline: XTimeline) : Parsed()
        data class Thread(val page: XParse.ThreadPage) : Parsed()
        data class User(val user: XUser) : Parsed()
    }

    companion object {
        /** Lee una respuesta (en cualquier hilo). Nulo si no es de las que entendemos. */
        fun parse(req: XParse.Request, body: String): Parsed? = when {
            req.name in XParse.FEEDS -> XParse.timeline(body)?.let { Parsed.Feed(it) }
            req.name == XParse.THREAD -> XParse.thread(body, req.focalTweetId)?.let { Parsed.Thread(it) }
            req.name in XParse.USERS -> XParse.user(body)?.let { Parsed.User(it) }
            else -> null
        }
    }

    /** Guarda lo leído (en el hilo principal) y devuelve una línea para el registro. */
    fun put(slot: String, req: XParse.Request, parsed: Parsed, now: Long): String {
        lastSeen[slot] = now
        asked.remove(slot)
        return when (parsed) {
            is Parsed.Feed -> {
                val key = "$slot/${req.name}" + (req.timelineType?.let { "/$it" } ?: "")
                val f = (feeds[key] ?: XFeed()).merge(parsed.timeline, req.cursor, now)
                feeds[key] = f
                parsed.timeline.tweets.forEach { remember(it.author) }
                val what = if (parsed.timeline.entries.any { it.note != null }) "${parsed.timeline.entries.size} avisos" else "${parsed.timeline.tweets.size} posts"
                "${key.substringAfter('/')}: $what (${if (req.cursor == null) "carga" else "página ${f.pages}"}), ${f.entries.size} en total"
            }
            is Parsed.Thread -> {
                val p = parsed.page
                val current = threads[slot]
                val next = when {
                    p.focal != null -> {
                        val fresh = XThread(p.focal, p.ancestors, p.replies, p.cursorBottom, now)
                        if (current != null && current.focal.id == fresh.focal.id && req.cursor != null) current.merge(fresh, req.cursor, now) else fresh
                    }
                    current != null && req.cursor != null -> current.merge(XThread(current.focal, replies = p.replies, cursorBottom = p.cursorBottom), req.cursor, now)
                    else -> null
                }
                if (next != null) {
                    threads[slot] = next
                    (next.ancestors + next.focal).forEach { remember(it.author) }
                }
                "TweetDetail: " + (if (p.focal != null) "post ${p.focal.id} con ${p.ancestors.size} encima y " else "") + "${p.replies.sumOf { it.tweets.size }} respuestas" + (if (next == null) " (sin post abierto, se ignora)" else "")
            }
            is Parsed.User -> {
                remember(parsed.user)
                "cuenta @${parsed.user.handle}"
            }
        }
    }

    private fun remember(u: XUser) {
        val k = u.handle.lowercase()
        if (u.id.isNotEmpty()) handles[u.id] = k
        val old = users[k]
        // Un post trae la cuenta sin biografía ni cifras: no pisa lo que ya se sabía por el perfil.
        users[k] = if (old != null && u.bio.isEmpty() && u.followers == 0 && old.followers > 0) old.copy(name = u.name, avatar = u.avatar.ifEmpty { old.avatar }, verified = u.verified) else u
    }
}
