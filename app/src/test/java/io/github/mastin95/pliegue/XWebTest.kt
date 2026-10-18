package io.github.mastin95.pliegue

import io.github.mastin95.pliegue.web.XWeb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XWebTest {
    @Test
    fun `la sesión se reconoce por la cookie auth_token`() {
        assertTrue(XWeb.loggedIn("guest_id=v1; auth_token=abc123; twid=u%3D42"))
        assertFalse(XWeb.loggedIn("guest_id=v1; ct0=xyz"))
        assertFalse(XWeb.loggedIn(null))
    }

    @Test
    fun `el id de la cuenta sale de twid`() {
        assertEquals("42", XWeb.userId("auth_token=a; twid=u%3D42; ct0=b"))
        assertEquals("7", XWeb.userId("twid=\"u=7\""))
        assertNull(XWeb.userId("ct0=b"))
    }

    @Test
    fun `id de post y usuario de una dirección`() {
        assertEquals("1234567890", XWeb.statusId("https://x.com/javit/status/1234567890?s=20"))
        assertNull(XWeb.statusId("https://x.com/javit"))
        assertEquals("javit", XWeb.handleOf("https://x.com/javit"))
        assertEquals("javit", XWeb.handleOf("https://x.com/javit/"))
        assertNull(XWeb.handleOf("https://x.com/home"))
        assertNull(XWeb.handleOf("https://x.com/javit/status/1"))
    }

    @Test
    fun `Mi feed es una búsqueda from con lo último`() {
        assertNull(XWeb.myFeedUrl(emptyList()))
        val url = XWeb.myFeedUrl(listOf("javit", "@anam"))!!
        assertTrue(url.startsWith("https://x.com/search?q="))
        assertTrue(url.contains("from%3Ajavit%20OR%20from%3Aanam"))
        assertTrue(url.endsWith("&f=live&src=typed_query"))
    }

    @Test
    fun `dentro de la app solo quedan X y sus dominios`() {
        assertTrue(XWeb.isOwn("https://x.com/home"))
        assertTrue(XWeb.isOwn("https://t.co/abc"))
        assertTrue(XWeb.isOwn("https://pbs.twimg.com/media/a.jpg"))
        assertFalse(XWeb.isOwn("https://example.com/"))
        assertFalse(XWeb.isOwn("mailto:a@b.c"))
    }

    @Test
    fun `el agente de usuario deja de parecer una WebView`() {
        val wv = "Mozilla/5.0 (Linux; Android 15; SM-F956B Build/AP3A; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/130.0.6723.86 Mobile Safari/537.36"
        assertEquals(
            "Mozilla/5.0 (Linux; Android 15; SM-F956B Build/AP3A) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.6723.86 Mobile Safari/537.36",
            XWeb.chromeUa(wv),
        )
        assertEquals(XWeb.DESKTOP_UA, XWeb.chromeUa(XWeb.DESKTOP_UA))
    }

    @Test
    fun `redactar con una fuente lleva el enlace`() {
        assertEquals("https://x.com/compose/post", XWeb.composeUrl())
        assertEquals("https://x.com/intent/post?url=https%3A%2F%2Fx.com%2Fa%2Fstatus%2F1", XWeb.composeUrl(url = "https://x.com/a/status/1"))
        assertEquals("@javit · post 7890", XWeb.shortLabel("https://x.com/javit/status/1234567890"))
    }
}
