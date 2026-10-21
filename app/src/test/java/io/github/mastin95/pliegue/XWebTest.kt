package io.github.mastin95.pliegue

import io.github.mastin95.pliegue.model.Cleanup
import io.github.mastin95.pliegue.model.Source
import io.github.mastin95.pliegue.model.XView
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
    fun `el inicio de sesión con Google o Apple no sale al navegador`() {
        assertTrue(XWeb.isOwn("https://accounts.google.com/gsi/select?client_id=1"))
        assertTrue(XWeb.isOwn("https://accounts.google.es/accounts/SetSID"))
        assertTrue(XWeb.isOwn("https://accounts.youtube.com/accounts/SetSID"))
        assertTrue(XWeb.isOwn("https://myaccount.google.com/signinoptions"))
        assertTrue(XWeb.isOwn("https://appleid.apple.com/auth/authorize"))
        assertTrue(XWeb.isOwn("https://idmsa.apple.com/appleauth"))
        assertFalse(XWeb.isOwn("https://www.google.com/search?q=x"))
        assertFalse(XWeb.isOwn("https://www.youtube.com/watch?v=1"))
    }

    @Test
    fun `las ventanas aparte se quedan dentro salvo los enlaces a otras webs`() {
        assertFalse(XWeb.opensOutside("https://accounts.google.com/gsi/select"))
        assertFalse(XWeb.opensOutside("https://x.com/i/flow/login"))
        assertTrue(XWeb.opensOutside("https://t.co/abc"))
        assertTrue(XWeb.opensOutside("https://example.com/"))
        assertTrue(XWeb.opensOutside("intent://x#Intent;end"))
        assertTrue(XWeb.isWeb("http://x.com"))
        assertFalse(XWeb.isWeb("mailto:a@b.c"))
    }

    @Test
    fun `los enlaces de los posts a otras webs se abren en la otra mitad`() {
        assertTrue(XWeb.isExternal("https://t.co/abc"))
        assertTrue(XWeb.isExternal("https://www.elpais.com/articulo"))
        assertFalse(XWeb.isExternal("https://x.com/user/status/1"))
        assertFalse(XWeb.isExternal("https://accounts.google.com/gsi/select"))
        assertFalse(XWeb.isExternal("mailto:a@b.c"))
        assertEquals("elpais.com", XWeb.siteLabel("https://www.elpais.com/articulo?x=1"))
        assertEquals("t.co", XWeb.siteLabel("https://t.co/abc"))
    }

    @Test
    fun `un enlace de fuera llega por abrir con o por compartir`() {
        assertEquals("https://x.com/a/status/1", XWeb.linkFromIntent("android.intent.action.VIEW", "https://x.com/a/status/1", null))
        assertNull(XWeb.linkFromIntent("android.intent.action.VIEW", "mailto:a@b.c", null))
        assertEquals("https://x.com/a/status/1", XWeb.linkFromIntent("android.intent.action.SEND", null, "Mira esto https://x.com/a/status/1."))
        assertEquals("https://elpais.com/x", XWeb.linkFromIntent("android.intent.action.SEND", null, "https://elpais.com/x"))
        assertNull(XWeb.linkFromIntent("android.intent.action.SEND", null, "sin enlace"))
        assertNull(XWeb.linkFromIntent("android.intent.action.MAIN", null, null))
    }

    @Test
    fun `la foto de un post se abre a tamaño grande`() {
        assertTrue(XWeb.isMedia("https://pbs.twimg.com/media/abc?format=jpg&name=small"))
        assertFalse(XWeb.isMedia("https://x.com/a/status/1/photo/1"))
        assertEquals("https://pbs.twimg.com/media/abc?format=jpg&name=large", XWeb.largeImage("https://pbs.twimg.com/media/abc?format=jpg&name=small"))
        assertEquals("https://pbs.twimg.com/media/abc?name=large&format=png", XWeb.largeImage("https://pbs.twimg.com/media/abc?name=360x360&format=png"))
        assertEquals("https://pbs.twimg.com/media/abc.jpg?name=large", XWeb.largeImage("https://pbs.twimg.com/media/abc.jpg"))
    }

    @Test
    fun `la limpieza del feed entra en el script y se guarda por nombres`() {
        val none = XWeb.bridgeScript(interceptPosts = true, hideChrome = true)
        assertTrue(none.contains("clean: {ads:false,sug:false,premium:false,following:false}"))
        val all = XWeb.bridgeScript(interceptPosts = true, hideChrome = true, cleanup = Cleanup(ads = true, suggestions = true, premium = true, following = true))
        assertTrue(all.contains("clean: {ads:true,sug:true,premium:true,following:true}"))
        assertTrue(all.contains("placementTracking"))
        assertTrue(all.contains("window.Pliegue.unread(n, m)"))
        assertEquals(listOf("ads", "following"), Cleanup(ads = true, following = true).toNames())
        assertEquals(Cleanup(premium = true), Cleanup.fromNames(listOf("premium")))
    }

    @Test
    fun `salir del formulario de inicio de sesión se reconoce`() {
        assertTrue(XWeb.isLoginFlow("https://x.com/i/flow/login?redirect_after_login=%2Fhome"))
    }

    @Test
    fun `la portada sin sesión cuenta como formulario de entrada`() {
        assertTrue(XWeb.isLoginPage("https://x.com/"))
        assertTrue(XWeb.isLoginPage("https://x.com"))
        assertTrue(XWeb.isLoginPage("https://x.com/i/flow/login?redirect_after_login=%2Fhome"))
        assertTrue(XWeb.isLoginPage("https://x.com/login"))
        assertFalse(XWeb.isLoginPage("https://x.com/home"))
        // El formulario de X para móvil: la app no debe recargarlo mientras se escribe en él.
        assertTrue(XWeb.isLoginPage("https://x.com/i/jf/onboarding/web?mode=login"))
        assertFalse(XWeb.isLoginPage("https://x.com/i/flow/password_reset"))
        assertFalse(XWeb.isLoginPage("https://accounts.google.com/"))
        assertTrue(XWeb.isExternalScheme("mailto:a@b.c"))
        assertFalse(XWeb.isExternalScheme("storagerelay://https/x.com?id=auth1"))
    }

    @Test
    fun `el agente de usuario deja de parecer una WebView`() {
        val wv = "Mozilla/5.0 (Linux; Android 15; SM-F956B Build/AP3A; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/130.0.6723.86 Mobile Safari/537.36"
        assertEquals(
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36",
            XWeb.chromeUa(wv),
        )
        assertEquals("130.0.6723.86", XWeb.chromeVersion(wv))
        assertEquals(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36",
            XWeb.desktopUa(wv),
        )
        // Sin versión de Chrome a la vista, al menos se quitan las marcas de WebView.
        assertEquals("Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Mobile Safari/537.36", XWeb.chromeUa("Mozilla/5.0 (Linux; Android 15; wv) AppleWebKit/537.36 Version/4.0 Mobile Safari/537.36"))
        assertEquals(XWeb.DESKTOP_UA, XWeb.desktopUa("Mozilla/5.0 (Linux; Android 15; wv) AppleWebKit/537.36"))
    }

    @Test
    fun `redactar con una fuente lleva el enlace`() {
        assertEquals("https://x.com/compose/post", XWeb.composeUrl())
        assertEquals("https://x.com/intent/post?url=https%3A%2F%2Fx.com%2Fa%2Fstatus%2F1", XWeb.composeUrl(url = "https://x.com/a/status/1"))
        assertEquals("@javit · post 7890", XWeb.shortLabel("https://x.com/javit/status/1234567890"))
    }

    @Test
    fun `las mitades esconden la columna de iconos de X sea cual sea el ancho`() {
        val script = XWeb.bridgeScript(interceptPosts = true, hideChrome = true)
        assertTrue(script.contains("header[role=\"banner\"]:not(:has(main)){display:none!important}"))
        assertFalse(script.contains("@media"))
        assertTrue(script.contains("[data-testid=\"BottomBar\"]"))
    }

    @Test
    fun `qué pinta la interfaz propia en cada dirección`() {
        assertEquals(XView.Home, XWeb.view("https://x.com/home"))
        assertEquals(XView.Home, XWeb.view("https://x.com/", Source.Inicio))
        assertEquals(XView.Search("(from:lucia OR from:anam)"), XWeb.view(XWeb.myFeedUrl(listOf("lucia", "anam"))!!))
        assertEquals(XView.Bookmarks, XWeb.view("https://x.com/i/bookmarks"))
        assertEquals(XView.Profile("javit", null), XWeb.view("https://x.com/javit"))
        assertEquals(XView.Profile(null, "42"), XWeb.view("https://x.com/i/user/42"))
        assertEquals(XView.Thread("123"), XWeb.view("https://x.com/javit/status/123?s=20"))
        assertNull(XWeb.view("https://x.com/notifications"))
        assertNull(XWeb.view("https://x.com/messages"))
        assertNull(XWeb.view("https://x.com/explore"))
        assertNull(XWeb.view("https://x.com/compose/post"))
        assertNull(XWeb.view("https://example.com/"))
        assertEquals("https://x.com/intent/post?in_reply_to=5", XWeb.replyUrl("5"))
        assertTrue(XWeb.isVideo("https://video.twimg.com/v/1.mp4"))
        assertFalse(XWeb.isVideo("https://pbs.twimg.com/media/a.jpg"))
    }

    @Test
    fun `el escucha de la API de X va en todas las páginas y no se duplica`() {
        val js = XWeb.listenScript()
        assertTrue(js.contains("window.__pliegueNet"))
        assertTrue(js.contains("window.Pliegue.data(m[1], String(url), String(text))"))
        assertTrue(js.contains("/i\\/api\\/graphql"))
        assertTrue(XWeb.bridgeScript(true, true).contains("P.more = function"))
    }
}
