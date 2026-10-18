package io.github.mastin95.pliegue.web

import java.net.URLEncoder

/**
 * Lo que sabemos de la web de X: direcciones, cookies y los pequeños scripts que inyectamos.
 * Solo funciones puras; lo que toca WebView vive en [WebPanes].
 */
object XWeb {
    const val HOME = "https://x.com/home"
    const val LOGIN = "https://x.com/i/flow/login"
    const val LOGOUT = "https://x.com/logout"
    const val COMPOSE = "https://x.com/compose/post"

    /** Dominios que se abren dentro de la app; el resto va al navegador. */
    private val ownHosts = setOf("x.com", "www.x.com", "mobile.x.com", "twitter.com", "www.twitter.com", "mobile.twitter.com", "t.co", "pbs.twimg.com", "video.twimg.com", "abs.twimg.com", "accounts.google.com", "appleid.apple.com")

    fun isOwn(url: String): Boolean {
        val host = host(url) ?: return false
        return host in ownHosts || host.endsWith(".x.com") || host.endsWith(".twitter.com") || host.endsWith(".twimg.com")
    }

    fun host(url: String): String? {
        val m = Regex("^https?://([^/?#]+)").find(url) ?: return null
        return m.groupValues[1].lowercase().substringBefore(':')
    }

    /** La cookie `auth_token` solo existe con la sesión iniciada. */
    fun loggedIn(cookies: String?): Boolean = cookies?.contains(Regex("(^|;\\s*)auth_token=[^;]+")) == true

    /** La cookie `twid` lleva el id numérico de la cuenta (`u%3D123` o `u=123`). */
    fun userId(cookies: String?): String? {
        val m = Regex("(^|;\\s*)twid=\"?u(?:%3D|=)(\\d+)").find(cookies ?: return null) ?: return null
        return m.groupValues[2]
    }

    fun profileUrl(userId: String) = "https://x.com/i/user/$userId"

    /** Id del post en una dirección `.../usuario/status/123...`. */
    fun statusId(url: String): String? = Regex("/status(?:es)?/(\\d+)").find(url)?.groupValues?.get(1)

    fun isStatus(url: String) = statusId(url) != null

    /** Usuario de una dirección de perfil (`https://x.com/usuario`), o null si es otra página. */
    fun handleOf(url: String): String? {
        val path = Regex("^https?://[^/]+/([A-Za-z0-9_]{1,15})/?(?:[?#].*)?$").find(url)?.groupValues?.get(1) ?: return null
        return if (path.lowercase() in reserved) null else path
    }

    private val reserved = setOf("home", "explore", "notifications", "messages", "search", "settings", "compose", "login", "logout", "i", "intent", "hashtag", "about", "tos", "privacy", "jobs", "premium", "communities", "bookmarks", "lists", "verified")

    /** Mi feed real: lo último de los creadores elegidos, con la búsqueda de X. */
    fun myFeedUrl(handles: List<String>): String? {
        if (handles.isEmpty()) return null
        val q = handles.take(30).joinToString(" OR ") { "from:" + it.removePrefix("@") }
        return "https://x.com/search?q=${enc("($q)")}&f=live&src=typed_query"
    }

    fun searchUrl(query: String) = "https://x.com/search?q=${enc(query)}&src=typed_query"

    /** Redactor con una fuente: el enlace del post va ya puesto en el texto. */
    fun composeUrl(text: String = "", url: String? = null): String {
        val parts = buildList {
            if (text.isNotBlank()) add("text=${enc(text)}")
            if (url != null) add("url=${enc(url)}")
        }
        return if (parts.isEmpty()) COMPOSE else "https://x.com/intent/post?" + parts.joinToString("&")
    }

    /** Dirección en formato corto para enseñarla: `@usuario/status/123`. */
    fun shortLabel(url: String): String {
        val m = Regex("^https?://[^/]+/([A-Za-z0-9_]+)/status/(\\d+)").find(url) ?: return url.removePrefix("https://")
        return "@${m.groupValues[1]} · post ${m.groupValues[2].takeLast(4)}"
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    /** Escapa un texto para meterlo entre comillas simples en JavaScript. */
    fun js(s: String): String = s.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n")

    /**
     * Script que ponemos en cada página de X:
     * - un toque sobre un post avisa a la app (`Pliegue.openPost`) en vez de navegar, para abrirlo en la otra mitad;
     * - mantener pulsado un avatar o un nombre avisa con su usuario (`Pliegue.dragUser`) para arrastrarlo a Mi feed;
     * - los estilos ocultan la barra inferior y el botón flotante de la web dentro de los paneles.
     */
    fun bridgeScript(interceptPosts: Boolean, hideChrome: Boolean): String = """
        (function(){
          if (window.__pliegue) { window.__pliegue.posts = $interceptPosts; window.__pliegue.chrome = $hideChrome; window.__pliegue.style(); return; }
          var P = { posts: $interceptPosts, chrome: $hideChrome };
          window.__pliegue = P;
          P.style = function(){
            var s = document.getElementById('pliegue-style');
            if (!s) { s = document.createElement('style'); s.id = 'pliegue-style'; document.documentElement.appendChild(s); }
            s.textContent = P.chrome ? '[data-testid="BottomBar"],[data-testid="FloatingActionButton"],a[data-testid="FloatingActionButtons_Tweet_Button"],[data-testid="BottomBar"]+div{display:none!important}' : '';
          };
          P.style();
          document.addEventListener('click', function(e){
            if (!P.posts || !window.Pliegue) return;
            var t = e.target;
            if (!t || !t.closest) return;
            var art = t.closest('article[data-testid="tweet"]');
            if (!art) return;
            if (t.closest('a,button,[role="button"],[role="link"],video,[data-testid="tweetPhoto"],[data-testid="videoPlayer"],[data-testid="card.wrapper"]')) return;
            var time = art.querySelector('a[href*="/status/"] time');
            var link = time ? time.parentElement : art.querySelector('a[href*="/status/"]');
            if (!link || !link.href) return;
            if (location.pathname.indexOf('/status/') >= 0 && link.href.indexOf(location.pathname) >= 0) return;
            e.preventDefault(); e.stopPropagation();
            window.Pliegue.openPost(link.href);
          }, true);
          document.addEventListener('contextmenu', function(e){
            if (!window.Pliegue) return;
            var t = e.target;
            if (!t || !t.closest) return;
            var a = t.closest('a[href]');
            if (!a) return;
            var m = /^https?:\/\/[^\/]+\/([A-Za-z0-9_]{1,15})\/?$/.exec(a.href);
            if (!m) return;
            e.preventDefault(); e.stopPropagation();
            window.Pliegue.dragUser(m[1]);
          }, true);
        })();
    """.trimIndent()
}
