package io.github.mastin95.pliegue.model

import java.util.Calendar
import java.util.Locale

// Lo que la app pinta por su cuenta con los datos que la web de X se descarga (interfaz propia).
// Solo datos: nada de aquí toca la WebView ni Compose.

/** Una cuenta de X tal como viene en las respuestas de su web. */
data class XUser(
    val id: String,
    val name: String,
    val handle: String,
    /** Foto de perfil (pbs.twimg.com), ya en tamaño mediano. */
    val avatar: String = "",
    val verified: Boolean = false,
    val bio: String = "",
    val followers: Int = 0,
    val following: Int = 0,
    val banner: String = "",
    val location: String = "",
    val website: String = "",
    val posts: Int = 0,
) {
    val url: String get() = "https://x.com/$handle"
}

enum class XMediaKind { Photo, Video, Gif }

/** Una foto, un vídeo o un GIF de un post. `url` es siempre la imagen (la previa, en los vídeos). */
data class XMedia(
    val kind: XMediaKind,
    val url: String,
    val width: Int = 0,
    val height: Int = 0,
    /** El mp4 de más calidad de un vídeo o GIF. */
    val video: String? = null,
    val alt: String = "",
    val durationMs: Long = 0,
) {
    val ratio: Float get() = if (width > 0 && height > 0) width.toFloat() / height else 16f / 9f
}

/** La tarjeta de un enlace (título, dominio, imagen) que X pinta bajo el texto. */
data class XCard(val url: String, val title: String, val domain: String, val image: String? = null, val large: Boolean = false, val description: String = "")

/** Un enlace del texto: cómo se enseña ("elpais.com/…") y a dónde va de verdad. */
data class XLink(val display: String, val url: String)

/** Un post de X con todo lo que la app pinta de él. */
data class XTweet(
    val id: String,
    val author: XUser,
    val text: String,
    val createdAt: Long,
    val replies: Int = 0,
    val reposts: Int = 0,
    val quotes: Int = 0,
    val likes: Int = 0,
    val views: Int = 0,
    val bookmarks: Int = 0,
    val liked: Boolean = false,
    val reposted: Boolean = false,
    val bookmarked: Boolean = false,
    val media: List<XMedia> = emptyList(),
    val links: List<XLink> = emptyList(),
    val quoted: XTweet? = null,
    /** Quien lo reposteó, si llega como repost de otra persona. */
    val repostedBy: XUser? = null,
    val replyTo: String? = null,
    val card: XCard? = null,
    val conversationId: String = id,
    val lang: String = "",
    val pinned: Boolean = false,
    /** Texto largo (artículo o post ampliado) ya completo. */
    val long: Boolean = false,
) {
    val url: String get() = "https://x.com/${author.handle}/status/$id"
}

/**
 * Una fila de un feed: un post suelto o una conversación de dos o tres posts enlazados
 * (X los agrupa en Inicio y en los perfiles).
 */
data class XEntry(val id: String, val tweets: List<XTweet>) {
    constructor(tweet: XTweet) : this("tweet-" + tweet.id, listOf(tweet))
}

/** Una página de un feed tal como la manda X, con sus cursores para seguir. */
data class XTimeline(
    val entries: List<XEntry> = emptyList(),
    val cursorBottom: String? = null,
    val cursorTop: String? = null,
    val pinned: XTweet? = null,
) {
    val tweets: List<XTweet> get() = entries.flatMap { it.tweets }
}

/** Un feed acumulado en la app: lo que X ha mandado hasta ahora para una ranura. */
data class XFeed(
    val entries: List<XEntry> = emptyList(),
    val cursorBottom: String? = null,
    val cursorTop: String? = null,
    val pinned: XTweet? = null,
    val updatedAt: Long = 0,
    /** Ya no hay más páginas (la última llegó vacía). */
    val exhausted: Boolean = false,
    val pages: Int = 0,
) {
    val tweets: List<XTweet> get() = entries.flatMap { it.tweets }

    /**
     * Mezcla una página nueva. Sin cursor en la petición es una carga limpia (sustituye); con el
     * cursor de abajo se añade al final; con el de arriba, delante. Los posts repetidos no se duplican.
     */
    fun merge(page: XTimeline, requestCursor: String?, now: Long): XFeed {
        val top = requestCursor != null && requestCursor == cursorTop
        if (requestCursor == null || pages == 0) {
            return XFeed(page.entries, page.cursorBottom ?: cursorBottom, page.cursorTop ?: cursorTop, page.pinned ?: pinned, now, page.entries.isEmpty() && pages > 0, pages + 1)
        }
        val known = entries.flatMap { e -> e.tweets.map { it.id } }.toHashSet()
        val fresh = page.entries.map { e -> e.copy(tweets = e.tweets.filter { it.id !in known }) }.filter { it.tweets.isNotEmpty() }
        return copy(
            entries = if (top) fresh + entries else entries + fresh,
            cursorBottom = if (top) cursorBottom else page.cursorBottom ?: cursorBottom,
            cursorTop = if (top) page.cursorTop ?: cursorTop else cursorTop,
            pinned = page.pinned ?: pinned,
            updatedAt = now,
            exhausted = !top && fresh.isEmpty(),
            pages = pages + 1,
        )
    }
}

/** La conversación de un post: lo que hay encima (a lo que responde), el post y las respuestas agrupadas. */
data class XThread(
    val focal: XTweet,
    val ancestors: List<XTweet> = emptyList(),
    val replies: List<XEntry> = emptyList(),
    val cursorBottom: String? = null,
    val updatedAt: Long = 0,
) {
    fun merge(more: XThread, requestCursor: String?, now: Long): XThread {
        if (requestCursor == null) return more.copy(updatedAt = now)
        val known = (ancestors + focal).map { it.id }.toHashSet() + replies.flatMap { e -> e.tweets.map { it.id } }
        val fresh = more.replies.map { e -> e.copy(tweets = e.tweets.filter { it.id !in known }) }.filter { it.tweets.isNotEmpty() }
        return copy(replies = replies + fresh, cursorBottom = more.cursorBottom, updatedAt = now)
    }
}

/** Qué datos de X pinta la app en una dirección: el inicio, una búsqueda, un perfil, un post… */
sealed class XView {
    /** Inicio: pestaña 0 "Para ti" (HomeTimeline) o 1 "Siguiendo" (HomeLatestTimeline). */
    object Home : XView()
    data class Search(val query: String) : XView()
    object Bookmarks : XView()
    data class Profile(val handle: String?, val userId: String?) : XView()
    data class Thread(val statusId: String) : XView()
}

object XTime {
    private val months = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sept", "oct", "nov", "dic")

    /** "ahora", "35 min", "2 h", "18 oct" o "18 oct 2024", como en el feed de X. */
    fun ago(createdAt: Long, now: Long): String {
        if (createdAt <= 0) return ""
        val s = (now - createdAt) / 1000
        if (s < 60) return "ahora"
        val min = s / 60
        if (min < 60) return "$min min"
        val h = min / 60
        if (h < 24) return "$h h"
        val c = Calendar.getInstance(Locale.ROOT).apply { timeInMillis = createdAt }
        val n = Calendar.getInstance(Locale.ROOT).apply { timeInMillis = now }
        val day = "${c.get(Calendar.DAY_OF_MONTH)} ${months[c.get(Calendar.MONTH)]}"
        return if (c.get(Calendar.YEAR) == n.get(Calendar.YEAR)) day else "$day ${c.get(Calendar.YEAR)}"
    }

    /** "4:12 p. m. · 18 oct. 2026", la fecha completa bajo un post abierto. */
    fun full(createdAt: Long): String {
        if (createdAt <= 0) return ""
        val c = Calendar.getInstance().apply { timeInMillis = createdAt }
        val h = c.get(Calendar.HOUR_OF_DAY)
        val m = c.get(Calendar.MINUTE).toString().padStart(2, '0')
        val hour12 = if (h % 12 == 0) 12 else h % 12
        val ampm = if (h < 12) "a. m." else "p. m."
        return "$hour12:$m $ampm · ${c.get(Calendar.DAY_OF_MONTH)} ${months[c.get(Calendar.MONTH)]}. ${c.get(Calendar.YEAR)}"
    }
}
