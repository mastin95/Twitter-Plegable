package io.github.mastin95.pliegue

import io.github.mastin95.pliegue.model.XFeed
import io.github.mastin95.pliegue.model.XMediaKind
import io.github.mastin95.pliegue.model.videoPosts
import io.github.mastin95.pliegue.model.XThread
import io.github.mastin95.pliegue.model.XTime
import io.github.mastin95.pliegue.web.XParse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import io.github.mastin95.pliegue.model.withTweet
import io.github.mastin95.pliegue.model.XNoteKind

/** Las respuestas de ejemplo (src/test/resources/x) imitan el formato de la API interna de la web de X. */
class XParseTest {
    private fun fixture(name: String): String = javaClass.getResourceAsStream("/x/$name.json")!!.bufferedReader().readText()

    @Test
    fun `la petición dice qué consulta es y sus variables`() {
        val r = XParse.request("https://x.com/i/api/graphql/abc123/TweetDetail?variables=%7B%22focalTweetId%22%3A%221008%22%2C%22cursor%22%3A%22DB%22%7D&features=%7B%7D")
        assertEquals("TweetDetail", r.name)
        assertEquals("1008", r.focalTweetId)
        assertEquals("DB", r.cursor)
        val s = XParse.request("https://x.com/i/api/graphql/xyz/SearchTimeline?variables=%7B%22rawQuery%22%3A%22(from%3Alucia)%22%2C%22count%22%3A20%7D")
        assertEquals("SearchTimeline", s.name)
        assertEquals("(from:lucia)", s.query)
        assertNull(s.cursor)
        // Inicio va por POST: las variables están en el cuerpo de la petición, no en la dirección.
        val h = XParse.request("https://x.com/i/api/graphql/h1/HomeTimeline", """{"variables":{"count":20,"cursor":"DAABCgABG","includePromotedContent":true},"features":{"x":true},"queryId":"h1"}""")
        assertEquals("HomeTimeline", h.name)
        assertEquals("DAABCgABG", h.cursor)
        assertNull(XParse.request("https://x.com/i/api/graphql/h1/HomeTimeline", "").cursor)
        assertNull(XParse.request("https://x.com/i/api/graphql/h1/HomeTimeline", "esto no es json").cursor)
    }

    @Test
    fun `Inicio saca los posts en orden, sin anuncios ni módulos de cuentas, con cursores`() {
        val t = XParse.timeline(fixture("home"))!!
        assertEquals(listOf("tweet-1001", "tweet-1002", "tweet-1004", "home-conversation-1007-1008", "tweet-1005", "tweet-1006", "tweet-1009"), t.entries.map { it.id })
        assertEquals("CURSOR-TOP-1", t.cursorTop)
        assertEquals("CURSOR-BOTTOM-1", t.cursorBottom)
        assertEquals(2, t.entries[3].tweets.size)
        assertEquals("dgarcia", t.entries[3].tweets[1].replyTo)
    }

    @Test
    fun `un post con enlace y tarjeta`() {
        val p = XParse.timeline(fixture("home"))!!.entries[0].tweets[0]
        assertEquals("1001", p.id)
        assertEquals("Lucía R.", p.author.name)
        assertEquals("lucia", p.author.handle)
        assertTrue(p.author.verified)
        assertEquals("https://pbs.twimg.com/profile_images/11/foto_bigger.jpg", p.author.avatar)
        // El t.co se cambia por el enlace legible, el & se desescapa y, con tarjeta, el enlace final no se repite.
        assertEquals("Hilo: lo que aprendí diseñando para pantallas que se doblan & más.", p.text)
        assertEquals(1, p.links.size)
        assertEquals("https://www.elpais.com/plegables", p.links[0].url)
        assertEquals("Diseñar para plegables: guía & trucos", p.card!!.title)
        assertEquals("elpais.com", p.card!!.domain)
        assertEquals("https://www.elpais.com/plegables", p.card!!.url)
        assertTrue(p.card!!.large)
        assertEquals(1200, p.likes)
        assertEquals(34000, p.views)
        assertEquals(48, p.replies)
        assertEquals("https://x.com/lucia/status/1001", p.url)
        assertTrue(p.createdAt > 0)
    }

    @Test
    fun `fotos, vídeo, repost, cita y post largo`() {
        val t = XParse.timeline(fixture("home"))!!
        val fotos = t.entries[1].tweets[0]
        assertEquals("Fotos del amanecer en el Cabo de Gata.", fotos.text)
        assertEquals(2, fotos.media.size)
        assertEquals(XMediaKind.Photo, fotos.media[0].kind)
        assertEquals("Un plegable abierto", fotos.media[0].alt)
        assertEquals(1600, fotos.media[0].width)
        val repost = t.entries[2].tweets[0]
        assertEquals("1003", repost.id)
        assertEquals("nerea_a", repost.author.handle)
        assertEquals("dgarcia", repost.repostedBy!!.handle)
        assertTrue(repost.liked)
        val video = t.entries[4].tweets[0]
        assertEquals(XMediaKind.Video, video.media[0].kind)
        assertEquals("https://video.twimg.com/v/2176.mp4", video.media[0].video)
        assertEquals(12345L, video.media[0].durationMs)
        assertEquals("¿Usaríais un plegable como único móvil?", video.text)
        val cita = t.entries[5].tweets[0]
        assertEquals("Esto, exactamente esto.", cita.text)
        assertEquals("1005", cita.quoted!!.id)
        assertNull(cita.card)
        val largo = t.entries[6].tweets[0]
        assertTrue(largo.long)
        assertTrue(largo.text.startsWith("Este es un post largo"))
        assertTrue(largo.text.contains("enlace a example.com/largo y"))
        assertEquals("https://example.com/largo", largo.links[0].url)
    }

    @Test
    fun `los posts con vídeo o GIF de un feed, sin repetir, para verlos en vertical`() {
        val t = XParse.timeline(fixture("home"))!!
        // El 1005 sale suelto y citado por el 1009: una sola vez. El 1006 lleva un GIF.
        assertEquals(listOf("1005", "1006"), videoPosts(t.tweets).map { it.id })
        assertEquals(XMediaKind.Gif, videoPosts(t.tweets)[1].video!!.kind)
        assertEquals("https://video.twimg.com/tweet_video/G1.mp4", videoPosts(t.tweets)[1].video!!.video)
        assertNull(t.tweets.first { it.id == "1002" }.video)
    }

    @Test
    fun `sin mp4 el vídeo es la lista HLS`() {
        val body = fixture("home").replace(Regex(""", \{"bitrate": \d+, "content_type": "video/mp4", "url": "[^"]*"\}"""), "")
        val t = XParse.timeline(body)!!
        assertEquals("https://video.twimg.com/v.m3u8", t.entries[4].tweets[0].media[0].video)
    }

    @Test
    fun `las páginas siguientes se añaden sin repetir y las de arriba van delante`() {
        val now = 1_000L
        var feed = XFeed().merge(XParse.timeline(fixture("home"))!!, null, now)
        assertEquals(7, feed.entries.size)
        assertEquals(1, feed.pages)
        feed = feed.merge(XParse.timeline(fixture("home2"))!!, "CURSOR-BOTTOM-1", now + 1)
        assertEquals(8, feed.entries.size)
        assertEquals("tweet-1010", feed.entries.last().id)
        assertEquals("CURSOR-BOTTOM-2", feed.cursorBottom)
        assertEquals("CURSOR-TOP-1", feed.cursorTop)
        assertFalse(feed.exhausted)
        // Una página vacía al final: no hay más.
        val vacia = XParse.timeline("""{"data":{"home":{"home_timeline_urt":{"instructions":[{"type":"TimelineAddEntries","entries":[]}]}}}}""")!!
        assertTrue(feed.merge(vacia, "CURSOR-BOTTOM-2", now + 2).exhausted)
        // Lo nuevo de arriba va delante (y lo que ya estaba, 1002, no se repite).
        val arriba = XFeed().merge(XParse.timeline(fixture("home"))!!, null, now).merge(XParse.timeline(fixture("home2"))!!, "CURSOR-TOP-1", now + 3)
        assertEquals(8, arriba.entries.size)
        assertEquals("tweet-1010", arriba.entries.first().id)
        assertEquals("CURSOR-TOP-2", arriba.cursorTop)
        assertEquals("CURSOR-BOTTOM-1", arriba.cursorBottom)
        // Sin cursor, se empieza de cero.
        assertEquals(2, feed.merge(XParse.timeline(fixture("home2"))!!, null, now + 4).entries.size)
    }

    @Test
    fun `búsqueda, guardados y perfil con post fijado`() {
        assertEquals(listOf("1003", "1007"), XParse.timeline(fixture("search"))!!.tweets.map { it.id })
        assertEquals(listOf("1002"), XParse.timeline(fixture("bookmarks"))!!.tweets.map { it.id })
        val perfil = XParse.timeline(fixture("profile"))!!
        assertEquals("1001", perfil.pinned!!.id)
        assertTrue(perfil.pinned!!.pinned)
        assertEquals(listOf("tweet-1006", "profile-conversation-1007"), perfil.entries.map { it.id })
        assertEquals("PB", perfil.cursorBottom)
    }

    @Test
    fun `la conversación de un post`() {
        val page = XParse.thread(fixture("detail"), "1008")!!
        assertEquals("1008", page.focal!!.id)
        assertEquals(listOf("1007"), page.ancestors.map { it.id })
        assertEquals(listOf("conversationthread-1011", "conversationthread-1013"), page.replies.map { it.id })
        assertEquals(2, page.replies[0].tweets.size)
        assertEquals("DB", page.cursorBottom)
        var thread = XThread(page.focal!!, page.ancestors, page.replies, page.cursorBottom, 1)
        val more = XParse.thread(fixture("detail2"), "1008")!!
        assertNull(more.focal)
        thread = thread.merge(XThread(thread.focal, replies = more.replies, cursorBottom = more.cursorBottom), "DB", 2)
        assertEquals(3, thread.replies.size)
        assertEquals("DB2", thread.cursorBottom)
    }

    @Test
    fun `las respuestas de un vídeo, para pintarlas debajo de él`() {
        val page = XParse.thread(fixture("respuestas"), "1006")!!
        assertEquals("1006", page.focal!!.id)
        assertEquals(listOf("1021", "1022", "1023"), page.replies.flatMap { it.tweets }.map { it.id })
    }

    @Test
    fun `una cuenta con el formato nuevo de X`() {
        val u = XParse.user(fixture("user"))!!
        assertEquals("lucia", u.handle)
        assertEquals("Lucía R.", u.name)
        assertEquals(1200, u.followers)
        assertEquals(300, u.following)
        assertTrue(u.verified)
        assertEquals("Diseño producto", u.bio)
        assertEquals("https://example.org/perfil", u.website)
        assertTrue(u.followed)
        assertFalse(XParse.timeline(fixture("home"))!!.tweets.first().author.followed)
    }

    @Test
    fun `las notificaciones traen me gusta agrupado, repost, seguidor y una mención`() {
        val t = XParse.timeline(fixture("notifications"))!!
        assertEquals(4, t.entries.size)
        val like = t.entries[0].note!!
        assertEquals(XNoteKind.Like, like.kind)
        assertEquals("A Nerea A. y a Pablo M. les gustó tu post", like.text)
        assertEquals(listOf("nerea_a", "pablom"), like.users.map { it.handle })
        assertEquals(listOf(2 until 10, 15 until 23), like.bold)
        assertEquals("1001", t.entries[0].tweets.single().id)
        assertEquals("https://x.com/i/status/1001", like.url)
        assertEquals(1760954100000L, like.createdAt)
        assertEquals(XNoteKind.Repost, t.entries[1].note!!.kind)
        val follow = t.entries[2].note!!
        assertEquals(XNoteKind.Follow, follow.kind)
        assertTrue(t.entries[2].tweets.isEmpty())
        assertEquals("tecnohoy", follow.users.single().handle)
        assertNull(t.entries[3].note)
        assertEquals("1030", t.entries[3].tweets.single().id)
        assertEquals("NB-1", t.cursorBottom)
        assertEquals("NT-1", t.cursorTop)
        val r = XParse.request("https://x.com/i/api/graphql/n/NotificationsTimeline", """{"variables":{"timeline_type":"Mentions","count":40}}""")
        assertEquals("Mentions", r.timelineType)
        // Página 2: otro aviso sobre el mismo post no se pierde, y el mismo aviso no se repite.
        val feed = XFeed().merge(t, null, 1L).merge(XParse.timeline(fixture("notifications2"))!!, "NB-1", 2L)
        assertEquals(5, feed.entries.size)
        assertEquals("NB-2", feed.cursorBottom)
        assertEquals(3, feed.entries.count { e -> e.tweets.any { it.id == "1001" } })
        assertEquals(5, feed.merge(XParse.timeline(fixture("notifications2"))!!, "NB-2", 3L).entries.size)
    }

    @Test
    fun `un me gusta cambia el post allá donde esté y deja el resto igual`() {
        val feed = XFeed().merge(XParse.timeline(fixture("home"))!!, null, 1L)
        assertTrue(feed.withTweet("no-existe") { it.copy(liked = true) } === feed)
        val liked = feed.withTweet("1002") { it.copy(liked = true, likes = it.likes + 1) }
        val t = liked.tweets.first { it.id == "1002" }
        assertTrue(t.liked)
        assertEquals(761, t.likes)
        assertEquals(feed.tweets.count { it.liked } + 1, liked.tweets.count { it.liked })
        assertEquals(feed.entries.size, liked.entries.size)
        // El post citado dentro de otro también cambia.
        val saved = feed.withTweet("1005") { it.copy(bookmarked = true) }
        assertTrue(saved.tweets.first { it.id == "1006" }.quoted!!.bookmarked)
        assertTrue(saved.tweets.first { it.id == "1005" }.bookmarked)
        // Y en una conversación.
        val page = XParse.thread(fixture("detail"), "1008")!!
        val th = XThread(page.focal!!, page.ancestors, page.replies, page.cursorBottom, 1L)
        assertTrue(th.withTweet("zzz") { it.copy(reposted = true) } === th)
        val th2 = th.withTweet("1008") { it.copy(reposted = true, reposts = it.reposts + 1) }
        assertTrue(th2.focal.reposted)
        assertEquals(th.focal.reposts + 1, th2.focal.reposts)
        assertFalse(th2.ancestors.first().reposted)
    }

    @Test
    fun `lo que no se entiende no rompe nada`() {
        assertNull(XParse.timeline("esto no es json"))
        assertNull(XParse.timeline("""{"data":{}}"""))
        assertNotNull(XParse.timeline("""{"data":{"x":{"instructions":[{"type":"Raro"}]}}}"""))
        assertEquals(0, XParse.timeline("""{"data":{"x":{"instructions":[{"type":"TimelineAddEntries","entries":[{"entryId":"a","content":{"entryType":"TimelineTimelineItem","itemContent":{"itemType":"TimelineTweet","tweet_results":{}}}}]}]}}}""")!!.entries.size)
    }

    @Test
    fun `las horas como en X`() {
        val now = XParse.date("Tue Oct 20 12:00:00 +0000 2026")
        assertEquals("ahora", XTime.ago(now - 30_000, now))
        assertEquals("35 min", XTime.ago(now - 35 * 60_000, now))
        assertEquals("2 h", XTime.ago(now - 2 * 3_600_000, now))
        assertEquals("18 oct", XTime.ago(XParse.date("Sun Oct 18 12:00:00 +0000 2026"), now))
        assertEquals("18 oct 2024", XTime.ago(XParse.date("Fri Oct 18 12:00:00 +0000 2024"), now))
        assertEquals("", XTime.ago(0, now))
    }
}
