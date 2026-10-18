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

    /** Chrome de escritorio, por si la web de X se niega a cargar con el navegador del móvil. */
    const val DESKTOP_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"

    /** Versión completa del Chrome que lleva dentro la WebView ("141.0.7390.122"), o null si no se ve. */
    fun chromeVersion(ua: String): String? = Regex("Chrome/(\\d+(?:\\.\\d+)*)").find(ua)?.groupValues?.get(1)

    /**
     * El agente de usuario de WebView lleva la marca "wv", un "Version/4.0" que no tiene Chrome y el
     * modelo con su "Build/…", que Chrome dejó de mandar hace años. X y Google los usan para tratar la
     * app como navegador incrustado (X dejaba el inicio de sesión en negro; Google corta el suyo).
     * Se presenta exactamente como Chrome para Android, que es lo que es por dentro: el agente
     * reducido que manda Chrome, con la misma versión mayor que el motor.
     */
    fun chromeUa(ua: String): String {
        val major = chromeVersion(ua)?.substringBefore('.') ?: return ua.replace("; wv", "").replace(Regex("Version/\\d+(\\.\\d+)* "), "")
        val mobile = if (ua.contains("Mobile")) "Mobile " else ""
        return "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$major.0.0.0 ${mobile}Safari/537.36"
    }

    /** Chrome de escritorio con la misma versión mayor que el motor. */
    fun desktopUa(ua: String): String {
        val major = chromeVersion(ua)?.substringBefore('.') ?: return DESKTOP_UA
        return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$major.0.0.0 Safari/537.36"
    }

    /** Dominios que se abren dentro de la app; el resto va al navegador. */
    private val ownHosts = setOf("x.com", "www.x.com", "mobile.x.com", "twitter.com", "www.twitter.com", "mobile.twitter.com", "t.co", "pbs.twimg.com", "video.twimg.com", "abs.twimg.com")

    fun isOwn(url: String): Boolean {
        val host = host(url) ?: return false
        return host in ownHosts || host.endsWith(".x.com") || host.endsWith(".twitter.com") || host.endsWith(".twimg.com") || isAuthHost(host)
    }

    /**
     * Páginas por las que pasa "Iniciar sesión con Google" o "con Apple": la cuenta de Google salta
     * por accounts.google.com, accounts.youtube.com y myaccount.google.com; Apple por appleid e idmsa.
     * Si una de estas saliera al navegador, la vuelta a X se perdería (es lo que daba el "400" de Google).
     */
    fun isAuthHost(host: String): Boolean =
        host == "accounts.youtube.com" ||
            host == "myaccount.google.com" ||
            host == "gds.google.com" ||
            host == "recaptcha.net" || host == "www.recaptcha.net" ||
            Regex("^accounts\\.google\\.[a-z.]+$").matches(host) ||
            host == "apple.com" || host.endsWith(".apple.com") || host.endsWith(".cdn-apple.com")

    /** Enlaces web (http/https), frente a `mailto:`, `intent:` o `tel:`, que siempre van fuera. */
    fun isWeb(url: String) = url.startsWith("https://") || url.startsWith("http://")

    /** Las ventanas que la web abre aparte (el inicio de sesión con Google) se enseñan dentro; los enlaces de posts a otras webs van al navegador. */
    fun opensOutside(url: String): Boolean = !isWeb(url) || !isOwn(url) || host(url) == "t.co"

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

    fun isLoginFlow(url: String) = url.contains("/i/flow/login")

    /** Solo la web de X (no Google, Apple ni las imágenes). */
    fun isX(url: String): Boolean {
        val host = host(url) ?: return false
        return host == "x.com" || host == "twitter.com" || host.endsWith(".x.com") || host.endsWith(".twitter.com")
    }

    private fun path(url: String): String = Regex("^https?://[^/]+(/[^?#]*)?").find(url)?.groupValues?.get(1)?.trimEnd('/') ?: ""

    /**
     * Una página de entrada de X: el formulario (/i/flow/login) o la portada sin sesión, que en el
     * móvil lleva el formulario dentro (teléfono, Google, Apple, correo). Ahí no hay que tocar nada.
     */
    fun isLoginPage(url: String): Boolean {
        if (!isX(url)) return false
        val p = path(url)
        return p.isEmpty() || p == "/login" || p.startsWith("/i/flow/login")
    }

    /**
     * Páginas a las que X manda al salir del formulario y que en la app se quedan vacías (el
     * "jetfuel" /i/jf/…): de ahí volvemos al formulario. La portada y /home no lo son: X las
     * resuelve solo (sin sesión enseña el formulario; con sesión, el inicio).
     */
    fun isLoginDeadEnd(url: String): Boolean {
        if (!isX(url)) return false
        val p = path(url)
        return p.startsWith("/i/jf/") || p == "/i/flow"
    }

    /** Esquemas que tienen una app propia en el móvil; cualquier otro esquema raro se ignora. */
    fun isExternalScheme(url: String) = listOf("mailto:", "tel:", "sms:", "intent:", "market:", "geo:").any { url.startsWith(it) }

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
     * Gancho de diagnóstico para la pantalla de entrada: apunta en la consola (y de ahí al registro)
     * cómo arranca "Iniciar sesión con Google" (la configuración que X le da a Google, la ventana que
     * abre, lo que Google devuelve y los errores con su pila). Nunca apunta credenciales: cualquier
     * cadena larga se tapa. Es idempotente: se puede volver a inyectar.
     */
    fun loginHookScript(): String = """
        (function(){
          if (window.__pliegueHook) return; window.__pliegueHook = true;
          function red(s){ return String(s).replace(/[A-Za-z0-9_\-\.]{40,}/g, '…'); }
          function say(m){ try { console.log('[pliegue] ' + red(m)); } catch(e){} }
          function wrapInit(init){
            return function(cfg){
              try {
                var c = {}; for (var k in cfg) { c[k] = typeof cfg[k] === 'function' ? 'fn' : cfg[k]; }
                say('gsi.initialize ' + JSON.stringify(c));
                var cb = cfg && cfg.callback;
                if (typeof cb === 'function') cfg.callback = function(r){
                  try { say('gsi.callback ' + JSON.stringify({select_by: r && r.select_by, state: r && r.state, credential: r && r.credential ? 'sí, ' + r.credential.length + ' caracteres' : 'no', claves: Object.keys(r || {})})); } catch(e){}
                  try { return cb.apply(this, arguments); } catch(e){ say('gsi.callback lanza ' + (e && e.stack || e)); throw e; }
                };
              } catch(e){}
              return init.apply(this, arguments);
            };
          }
          function wrapCall(n, f){ return function(){ say('gsi.' + n + (n === 'renderButton' && arguments[1] ? ' ' + JSON.stringify(arguments[1]) : '')); return f.apply(this, arguments); }; }
          function wrapId(id){
            if (!id || typeof id !== 'object' || id.__pl) return id;
            id.__pl = true;
            // Google rellena google.accounts.id método a método: se envuelve cada uno cuando llega.
            ['initialize','prompt','renderButton'].forEach(function(n){
              var cur = id[n];
              function wrap(f){ if (typeof f !== 'function' || f.__pl) return f; var w = n === 'initialize' ? wrapInit(f) : wrapCall(n, f); w.__pl = true; return w; }
              cur = wrap(cur);
              try { Object.defineProperty(id, n, {configurable: true, enumerable: true, get: function(){ return cur; }, set: function(f){ cur = wrap(f); }}); } catch(e){}
            });
            return id;
          }
          function watchAccounts(g){
            if (!g || g.__plw) return; g.__plw = true;
            var acc = g.accounts;
            function watchId(a){ if (!a || a.__plw) return; a.__plw = true; var idv = a.id; if (idv) wrapId(idv);
              Object.defineProperty(a, 'id', {configurable: true, get: function(){ return idv; }, set: function(x){ idv = wrapId(x); }}); }
            if (acc) watchId(acc);
            Object.defineProperty(g, 'accounts', {configurable: true, get: function(){ return acc; }, set: function(v){ acc = v; watchId(v); }});
          }
          var gv = window.google;
          try { Object.defineProperty(window, 'google', {configurable: true, get: function(){ return gv; }, set: function(v){ gv = v; watchAccounts(v); }}); } catch(e){}
          if (gv) watchAccounts(gv);
          var wo = window.open;
          window.open = function(u, n, f){ say('window.open ' + (u || '(vacío)') + ' nombre=' + n); return wo.apply(window, arguments); };
          // Peticiones de la web de X que tocan el inicio de sesión: así se ve si el rechazo viene del servidor.
          function api(u){ return /onboarding|sso|jot|login|password/i.test(String(u || '')); }
          function pathOf(u){ try { var x = new URL(String(u), location.href); return x.host + x.pathname + (x.search ? x.search.slice(0, 80) : ''); } catch(e){ return String(u).slice(0, 120); } }
          function bodyInfo(b){
            if (!b) return '';
            var s = typeof b === 'string' ? b : (b instanceof URLSearchParams ? b.toString() : (b instanceof FormData ? '[formdata]' : '[' + (b.constructor && b.constructor.name) + ']'));
            var out = '', m, ids = [], re = /"subtask_id"\s*:\s*"([^"]+)"/g;
            while ((m = re.exec(s))) ids.push(m[1]);
            if (ids.length) out += 'subtareas=' + ids.join(',') + ' ';
            var prov = s.match(/.{0,60}provider.{0,60}/g); if (prov) out += 'provider: ' + prov.slice(0, 4).join(' | ') + ' ';
            var sso = s.match(/.{0,40}sso.{0,80}/gi); if (sso) out += 'sso: ' + sso.slice(0, 4).join(' | ') + ' ';
            var err = s.match(/"errors"\s*:\s*\[.{0,300}/); if (err) out += 'errores: ' + err[0];
            if (!out) out = s.slice(0, 200);
            return red(out).slice(0, 700);
          }
          var of = window.fetch;
          if (of) window.fetch = function(input, init){
            var u = typeof input === 'string' ? input : (input && input.url) || '';
            var method = (init && init.method) || (input && input.method) || 'GET';
            var isApi = api(u);
            if (isApi) say('pide ' + method + ' ' + pathOf(u) + ' ' + bodyInfo(init && init.body));
            var p = of.apply(this, arguments);
            if (isApi) p.then(function(r){ try { r.clone().text().then(function(t){ say('responde ' + r.status + ' ' + pathOf(u) + ' ' + bodyInfo(t)); }); } catch(e){} }, function(e){ say('falla ' + pathOf(u) + ' ' + e); });
            return p;
          };
          var XO = XMLHttpRequest.prototype.open, XS = XMLHttpRequest.prototype.send;
          XMLHttpRequest.prototype.open = function(m, u){ this.__plu = String(u || ''); this.__plm = m; return XO.apply(this, arguments); };
          XMLHttpRequest.prototype.send = function(b){
            var x = this, u = x.__plu || '';
            if (api(u)) {
              say('pide ' + x.__plm + ' ' + pathOf(u) + ' ' + bodyInfo(b));
              x.addEventListener('loadend', function(){ var t = ''; try { t = (x.responseType === '' || x.responseType === 'text') ? x.responseText : '[' + x.responseType + ']'; } catch(e){} say('responde ' + x.status + ' ' + pathOf(u) + ' ' + bodyInfo(t)); });
            }
            return XS.apply(this, arguments);
          };
          window.addEventListener('message', function(e){
            if (!/google|apple/.test(e.origin)) return;
            var d = e.data; try { d = typeof d === 'string' ? d : JSON.stringify(d); } catch(x){ d = String(d); }
            say('mensaje de ' + e.origin + ': ' + String(d).slice(0, 300));
          }, true);
          window.addEventListener('error', function(e){ say('error ' + e.message + ' @ ' + String(e.filename).split('/').pop() + ':' + e.lineno); }, true);
          window.addEventListener('unhandledrejection', function(e){ var r = e.reason; say('promesa rechazada ' + (r && r.stack || r && r.message || r)); });
          var ce = console.error;
          console.error = function(){
            try {
              var a = arguments[0];
              var st = String((a && a.stack) || new Error().stack || '').split('\n').filter(function(l){ return /\bat\b|@/.test(l) && l.indexOf('<anonymous>') < 0; }).slice(0, 6).join(' ⇐ ');
              say('error ' + String(a && a.message || a).slice(0, 200) + ' · pila: ' + red(st).slice(0, 600));
            } catch(e){}
            return ce.apply(console, arguments);
          };
          var brands = ''; try { brands = (navigator.userAgentData && navigator.userAgentData.brands || []).map(function(b){ return b.brand + ' ' + b.version; }).join(', '); } catch(e){}
          say('gancho de login puesto · FedCM ' + ('IdentityCredential' in window ? 'sí' : 'no') + ' · window.chrome ' + typeof window.chrome + ' · marcas: ' + (brands || 'ninguna'));
        })();
    """.trimIndent()

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
