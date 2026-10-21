package io.github.mastin95.pliegue.web

import io.github.mastin95.pliegue.model.XCard
import io.github.mastin95.pliegue.model.XEntry
import io.github.mastin95.pliegue.model.XLink
import io.github.mastin95.pliegue.model.XMedia
import io.github.mastin95.pliegue.model.XMediaKind
import io.github.mastin95.pliegue.model.XTimeline
import io.github.mastin95.pliegue.model.XTweet
import io.github.mastin95.pliegue.model.XUser
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Lee las respuestas que la web de X se descarga para pintar sus listas (su API interna GraphQL:
 * HomeTimeline, SearchTimeline, UserTweets, TweetDetail…) y las convierte en posts de la app.
 * Es tolerante: lo que no entiende lo salta, y si X cambia el formato devuelve listas vacías
 * en vez de fallar (la app vuelve entonces a enseñar la web).
 */
object XParse {

    /** Qué pidió la web: el nombre de la consulta y sus variables útiles. */
    data class Request(
        val name: String,
        val cursor: String? = null,
        val focalTweetId: String? = null,
        val userId: String? = null,
        val query: String? = null,
        val screenName: String? = null,
    )

    /** Consultas de las que la app saca posts. */
    val FEEDS = setOf("HomeTimeline", "HomeLatestTimeline", "SearchTimeline", "UserTweets", "UserTweetsAndReplies", "UserMedia", "Likes", "Bookmarks", "ListLatestTweetsTimeline", "ListRankedTweetsTimeline")
    const val THREAD = "TweetDetail"
    val USERS = setOf("UserByScreenName", "UserByRestId")

    /** Una página de una conversación: el post principal (si viene), lo de encima y las respuestas. */
    data class ThreadPage(val focal: XTweet? = null, val ancestors: List<XTweet> = emptyList(), val replies: List<XEntry> = emptyList(), val cursorBottom: String? = null)

    /**
     * Qué consulta es y sus variables. Las consultas GET las llevan en la dirección (`?variables=…`); las
     * POST (Inicio, por ejemplo) en el cuerpo JSON de la petición (`{"variables":{…},"features":{…}}`).
     */
    fun request(url: String, requestBody: String? = null): Request {
        val name = Regex("/i/api/graphql/[^/]+/([A-Za-z0-9_]+)").find(url)?.groupValues?.get(1) ?: ""
        val vars = Regex("[?&]variables=([^&#]*)").find(url)?.groupValues?.get(1)
            ?.let { runCatching { JSONObject(URLDecoder.decode(it, "UTF-8")) }.getOrNull() }
            ?: requestBody?.takeIf { it.isNotBlank() }?.let { runCatching { JSONObject(it).optJSONObject("variables") }.getOrNull() }
        fun s(k: String) = vars?.optString(k)?.takeIf { it.isNotEmpty() }
        return Request(name, s("cursor"), s("focalTweetId"), s("userId"), s("rawQuery"), s("screen_name"))
    }

    // ---- Feeds ----

    fun timeline(body: String): XTimeline? {
        val ins = instructions(body) ?: return null
        val out = Collector()
        for (i in 0 until ins.length()) {
            val inst = ins.optJSONObject(i) ?: continue
            when (inst.optString("type")) {
                "TimelineAddEntries" -> inst.optJSONArray("entries")?.let { arr -> for (j in 0 until arr.length()) out.entry(arr.optJSONObject(j)) }
                "TimelineReplaceEntry" -> out.entry(inst.optJSONObject("entry"))
                "TimelinePinEntry" -> inst.optJSONObject("entry")?.let { e -> out.pinned = firstTweet(e)?.copy(pinned = true) }
                "TimelineAddToModule" -> out.module("", inst.optJSONArray("moduleItems"))
            }
        }
        return XTimeline(out.entries, out.bottom, out.top, out.pinned)
    }

    /** La conversación de un post (TweetDetail). `focalId` es el post abierto, por si no viene marcado. */
    fun thread(body: String, focalId: String?): ThreadPage? {
        val ins = instructions(body) ?: return null
        val out = Collector()
        for (i in 0 until ins.length()) {
            val inst = ins.optJSONObject(i) ?: continue
            when (inst.optString("type")) {
                "TimelineAddEntries" -> inst.optJSONArray("entries")?.let { arr -> for (j in 0 until arr.length()) out.entry(arr.optJSONObject(j)) }
                "TimelineAddToModule" -> out.module("", inst.optJSONArray("moduleItems"))
            }
        }
        val entries = out.entries
        val at = entries.indexOfFirst { e -> e.tweets.size == 1 && (e.tweets[0].id == focalId || e.id == "tweet-$focalId") }
        if (at < 0) return ThreadPage(replies = entries, cursorBottom = out.bottom)
        val ancestors = entries.take(at).filter { it.tweets.size == 1 }.map { it.tweets[0] }
        return ThreadPage(entries[at].tweets[0], ancestors, entries.drop(at + 1), out.bottom)
    }

    /** Una cuenta (UserByScreenName, UserByRestId). */
    fun user(body: String): XUser? {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return null
        return user(root.optJSONObject("data")?.optJSONObject("user")?.optJSONObject("result"))
    }

    private fun instructions(body: String): JSONArray? {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return null
        return findInstructions(root.opt("data"), 0)
    }

    /** Las listas de X van siempre bajo una clave "instructions", pero a distinta profundidad según la consulta. */
    private fun findInstructions(o: Any?, depth: Int): JSONArray? {
        if (depth > 8) return null
        when (o) {
            is JSONObject -> {
                o.optJSONArray("instructions")?.let { return it }
                for (k in o.keys()) findInstructions(o.opt(k), depth + 1)?.let { return it }
            }
            is JSONArray -> for (i in 0 until o.length()) findInstructions(o.opt(i), depth + 1)?.let { return it }
        }
        return null
    }

    private class Collector {
        val entries = ArrayList<XEntry>()
        var bottom: String? = null
        var top: String? = null
        var pinned: XTweet? = null

        fun entry(e: JSONObject?) {
            val c = e?.optJSONObject("content") ?: return
            val id = e.optString("entryId")
            when (c.optString("entryType").ifEmpty { c.optString("__typename") }) {
                "TimelineTimelineItem" -> itemTweet(c.optJSONObject("itemContent"))?.let { entries += XEntry(id, listOf(it)) }
                "TimelineTimelineModule" -> module(id, c.optJSONArray("items"))
                "TimelineTimelineCursor" -> cursor(c, id)
            }
        }

        fun module(id: String, items: JSONArray?) {
            items ?: return
            val tweets = ArrayList<XTweet>()
            for (i in 0 until items.length()) {
                val it = items.optJSONObject(i)?.optJSONObject("item")?.optJSONObject("itemContent") ?: continue
                itemTweet(it)?.let(tweets::add)
            }
            if (tweets.isNotEmpty()) entries += XEntry(id.ifEmpty { "module-" + tweets[0].id }, tweets)
        }

        private fun cursor(c: JSONObject, id: String) {
            val value = c.optString("value").takeIf { it.isNotEmpty() } ?: return
            when (c.optString("cursorType")) {
                "Bottom" -> if (!id.contains("cursor-top")) bottom = value
                "Top" -> top = value
            }
        }
    }

    /** El post de un elemento de lista; los promocionados y lo que no son posts (cuentas, avisos) se saltan. */
    private fun itemTweet(ic: JSONObject?): XTweet? {
        if (ic == null || ic.optString("itemType") != "TimelineTweet") return null
        if (ic.has("promotedMetadata")) return null
        return tweet(ic.optJSONObject("tweet_results")?.optJSONObject("result"))
    }

    private fun firstTweet(entry: JSONObject): XTweet? {
        val c = entry.optJSONObject("content") ?: return null
        itemTweet(c.optJSONObject("itemContent"))?.let { return it }
        val items = c.optJSONArray("items") ?: return null
        for (i in 0 until items.length()) {
            itemTweet(items.optJSONObject(i)?.optJSONObject("item")?.optJSONObject("itemContent"))?.let { return it }
        }
        return null
    }

    // ---- Un post ----

    private val dateFormat = ThreadLocal.withInitial { SimpleDateFormat("EEE MMM dd HH:mm:ss Z yyyy", Locale.US) }

    fun tweet(result: JSONObject?): XTweet? {
        var r = result ?: return null
        if (r.optString("__typename") == "TweetWithVisibilityResults") r = r.optJSONObject("tweet") ?: return null
        val legacy = r.optJSONObject("legacy") ?: return null
        val id = r.optString("rest_id").ifEmpty { legacy.optString("id_str") }
        if (id.isEmpty()) return null
        val author = user(r.optJSONObject("core")?.optJSONObject("user_results")?.optJSONObject("result")) ?: return null
        // Un repost: lo que se enseña es el post original, con quien lo reposteó encima.
        legacy.optJSONObject("retweeted_status_result")?.optJSONObject("result")?.let { inner ->
            tweet(inner)?.let { return it.copy(repostedBy = author) }
        }
        val note = r.optJSONObject("note_tweet")?.optJSONObject("note_tweet_results")?.optJSONObject("result")
        val quoted = r.optJSONObject("quoted_status_result")?.optJSONObject("result")?.let { tweet(it) }
        val urls = linksOf(if (note != null) note.optJSONObject("entity_set")?.optJSONArray("urls") ?: legacy.optJSONObject("entities")?.optJSONArray("urls") else legacy.optJSONObject("entities")?.optJSONArray("urls"))
        var text = baseText(legacy, note)
        // Los t.co de las fotos y del post citado no se enseñan: la foto y la cita van aparte.
        legacy.optJSONObject("entities")?.optJSONArray("media")?.let { m ->
            for (i in 0 until m.length()) m.optJSONObject(i)?.optString("url")?.takeIf { it.isNotEmpty() }?.let { text = text.replace(it, "") }
        }
        legacy.optJSONObject("quoted_status_permalink")?.optString("url")?.takeIf { it.isNotEmpty() }?.let { text = text.replace(it, "") }
        val links = ArrayList<XLink>()
        for ((tco, link) in urls) {
            if (quoted != null && link.url.contains("/status/${quoted.id}")) {
                text = text.replace(tco, "")
                continue
            }
            text = text.replace(tco, link.display)
            links += link
        }
        var card = if (quoted == null) card(r.optJSONObject("card"), urls) else null
        if (card != null) {
            // Con tarjeta, X esconde el enlace si es lo último del texto.
            val shown = links.firstOrNull { it.url == card!!.url }
            if (shown != null && text.trimEnd().endsWith(shown.display)) text = text.trimEnd().removeSuffix(shown.display)
        }
        text = unescape(text).trim()
        val views = r.optJSONObject("views")?.optString("count")?.toIntOrNull() ?: 0
        return XTweet(
            id = id,
            author = author,
            text = text,
            createdAt = date(legacy.optString("created_at")),
            replies = legacy.optInt("reply_count"),
            reposts = legacy.optInt("retweet_count"),
            quotes = legacy.optInt("quote_count"),
            likes = legacy.optInt("favorite_count"),
            views = views,
            bookmarks = legacy.optInt("bookmark_count"),
            liked = legacy.optBoolean("favorited"),
            reposted = legacy.optBoolean("retweeted"),
            bookmarked = legacy.optBoolean("bookmarked"),
            media = media(legacy),
            links = links,
            quoted = quoted,
            replyTo = legacy.optString("in_reply_to_screen_name").takeIf { it.isNotEmpty() },
            card = card,
            conversationId = legacy.optString("conversation_id_str").ifEmpty { id },
            lang = legacy.optString("lang"),
            long = note != null,
        )
    }

    private fun linksOf(arr: JSONArray?): List<Pair<String, XLink>> {
        arr ?: return emptyList()
        val out = ArrayList<Pair<String, XLink>>()
        for (i in 0 until arr.length()) {
            val u = arr.optJSONObject(i) ?: continue
            val tco = u.optString("url")
            val expanded = u.optString("expanded_url").ifEmpty { tco }
            if (tco.isEmpty()) continue
            out += tco to XLink(u.optString("display_url").ifEmpty { expanded.removePrefix("https://").removePrefix("http://") }, expanded)
        }
        return out
    }

    /** El texto visible: el del post largo si lo hay, o full_text recortado a display_text_range (índices en puntos de código). */
    private fun baseText(legacy: JSONObject, note: JSONObject?): String {
        note?.optString("text")?.takeIf { it.isNotEmpty() }?.let { return it }
        val full = legacy.optString("full_text")
        val range = legacy.optJSONArray("display_text_range") ?: return full
        return runCatching {
            val count = full.codePointCount(0, full.length)
            val a = range.optInt(0).coerceIn(0, count)
            val b = range.optInt(1, count).coerceIn(a, count)
            full.substring(full.offsetByCodePoints(0, a), full.offsetByCodePoints(0, b))
        }.getOrDefault(full)
    }

    fun unescape(s: String): String = s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'")

    fun date(s: String): Long = runCatching { dateFormat.get()!!.parse(s)?.time }.getOrNull() ?: 0L

    private fun media(legacy: JSONObject): List<XMedia> {
        val arr = legacy.optJSONObject("extended_entities")?.optJSONArray("media") ?: legacy.optJSONObject("entities")?.optJSONArray("media") ?: return emptyList()
        val out = ArrayList<XMedia>()
        for (i in 0 until arr.length()) {
            val m = arr.optJSONObject(i) ?: continue
            val url = m.optString("media_url_https").ifEmpty { m.optString("media_url") }
            if (url.isEmpty()) continue
            val size = m.optJSONObject("original_info")
            val w = size?.optInt("width") ?: 0
            val h = size?.optInt("height") ?: 0
            val alt = m.optString("ext_alt_text")
            val kind = when (m.optString("type")) {
                "video" -> XMediaKind.Video
                "animated_gif" -> XMediaKind.Gif
                else -> XMediaKind.Photo
            }
            val info = m.optJSONObject("video_info")
            out += XMedia(kind, url, w, h, bestVideo(info?.optJSONArray("variants")), alt, info?.optLong("duration_millis") ?: 0L)
        }
        return out
    }

    /** El mp4 de más calidad (X manda varias versiones y una lista HLS, que la app no reproduce). */
    private fun bestVideo(variants: JSONArray?): String? {
        variants ?: return null
        var best: String? = null
        var bitrate = -1
        for (i in 0 until variants.length()) {
            val v = variants.optJSONObject(i) ?: continue
            if (v.optString("content_type") != "video/mp4") continue
            val b = v.optInt("bitrate", 0)
            if (b > bitrate) {
                bitrate = b
                best = v.optString("url")
            }
        }
        return best
    }

    private fun card(c: JSONObject?, urls: List<Pair<String, XLink>>): XCard? {
        val legacy = c?.optJSONObject("legacy") ?: return null
        val values = HashMap<String, JSONObject>()
        legacy.optJSONArray("binding_values")?.let { arr ->
            for (i in 0 until arr.length()) {
                val b = arr.optJSONObject(i) ?: continue
                b.optJSONObject("value")?.let { values[b.optString("key")] = it }
            }
        }
        fun str(k: String) = values[k]?.optString("string_value")?.takeIf { it.isNotEmpty() }
        fun img(k: String) = values[k]?.optJSONObject("image_value")?.optString("url")?.takeIf { it.isNotEmpty() }
        val title = str("title") ?: return null
        val tco = str("card_url") ?: legacy.optString("url")
        val url = urls.firstOrNull { it.first == tco }?.second?.url ?: tco
        val name = legacy.optString("name")
        val image = img("summary_photo_image_large") ?: img("photo_image_full_size_large") ?: img("player_image_large") ?: img("thumbnail_image_large") ?: img("thumbnail_image")
        val domain = str("vanity_url") ?: str("domain") ?: XWeb.host(url) ?: ""
        return XCard(url, unescape(title), domain.removePrefix("www."), image, name.contains("large") || name == "player", str("description")?.let(::unescape) ?: "")
    }

    fun user(result: JSONObject?): XUser? {
        val u = result ?: return null
        if (u.optString("__typename") == "UserUnavailable") return null
        val legacy = u.optJSONObject("legacy") ?: JSONObject()
        val core = u.optJSONObject("core") ?: JSONObject()
        val handle = core.optString("screen_name").ifEmpty { legacy.optString("screen_name") }
        if (handle.isEmpty()) return null
        val avatar = (u.optJSONObject("avatar")?.optString("image_url") ?: "").ifEmpty { legacy.optString("profile_image_url_https") }
        val verified = u.optBoolean("is_blue_verified") || legacy.optBoolean("verified") || u.optJSONObject("verification")?.optBoolean("verified") == true
        return XUser(
            id = u.optString("rest_id"),
            name = unescape(core.optString("name").ifEmpty { legacy.optString("name") }),
            handle = handle,
            avatar = avatar.replace("_normal.", "_bigger."),
            verified = verified,
            bio = unescape(legacy.optString("description")),
            followers = legacy.optInt("followers_count"),
            following = legacy.optInt("friends_count"),
            banner = legacy.optString("profile_banner_url"),
            location = (u.optJSONObject("location")?.optString("location") ?: "").ifEmpty { legacy.optString("location") },
            website = legacy.optJSONObject("entities")?.optJSONObject("url")?.optJSONArray("urls")?.optJSONObject(0)?.optString("expanded_url") ?: "",
            posts = legacy.optInt("statuses_count"),
            followed = legacy.optBoolean("following") || u.optJSONObject("relationship_perspectives")?.optBoolean("following") == true,
        )
    }
}
