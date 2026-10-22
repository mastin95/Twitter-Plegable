package io.github.mastin95.pliegue

import io.github.mastin95.pliegue.model.Source
import io.github.mastin95.pliegue.model.WebPanel
import io.github.mastin95.pliegue.model.opensBeside
import io.github.mastin95.pliegue.web.XWeb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebPanelTest {
    private val post = "https://x.com/a/status/1"
    private val profile = "https://x.com/a"
    private val videos = XWeb.videosUrl("1", "pane0/HomeTimeline")

    @Test
    fun `dentro de una mitad hay historial y atrás lo deshace paso a paso`() {
        val p = WebPanel(Source.Inicio).push(post).push(profile).push(XWeb.replyUrl("1"))
        assertEquals(listOf(post, profile), p.history)
        assertTrue(XWeb.isCompose(p.openUrl!!))
        val back1 = p.back()
        assertEquals(profile, back1.openUrl)
        assertEquals(post, back1.back().openUrl)
        assertNull(back1.back().back().openUrl)
        // El botón de la fuente cierra todo de golpe.
        assertNull(p.closed().openUrl)
        assertTrue(p.closed().history.isEmpty())
    }

    @Test
    fun `dónde se abre lo tocado según la mitad, la otra y la postura`() {
        val feed = WebPanel(Source.Inicio)
        val open = feed.push(post)
        val reel = feed.push(videos)
        // Desde un feed, al lado.
        assertTrue(opensBeside(feed, open, folded = false))
        assertTrue(opensBeside(feed, feed, folded = true))
        // Plegado, desde algo abierto, encima (también desde los vídeos).
        assertFalse(opensBeside(open, feed, folded = true, link = true))
        assertFalse(opensBeside(reel, feed, folded = true))
        // Desde los vídeos con el móvil abierto, al lado: el vídeo sigue.
        assertTrue(opensBeside(reel, feed, folded = false))
        assertTrue(opensBeside(reel, open, folded = false, link = true))
        // Con los vídeos al lado, encima: no se pisa el reproductor (ni para responder ni para una foto).
        assertFalse(opensBeside(open, reel, folded = false))
        assertFalse(opensBeside(open, reel, folded = false, link = true))
        // Si no, posts y cuentas encima; respuestas, fotos y enlaces al lado.
        assertFalse(opensBeside(open, feed, folded = false))
        assertTrue(opensBeside(open, feed, folded = false, link = true))
    }

    @Test
    fun `el redactor se reconoce por la dirección`() {
        assertTrue(XWeb.isCompose(XWeb.composeUrl()))
        assertTrue(XWeb.isCompose(XWeb.composeUrl(url = post)))
        assertTrue(XWeb.isCompose(XWeb.replyUrl("5")))
        assertFalse(XWeb.isCompose(post))
        assertFalse(XWeb.isCompose("https://example.com/intent/post"))
    }
}
