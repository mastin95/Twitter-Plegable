package io.github.mastin95.pliegue.web

import io.github.mastin95.pliegue.model.Cleanup
import io.github.mastin95.pliegue.model.Source
import io.github.mastin95.pliegue.model.XView
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Lo que sabemos de la web de X: direcciones, cookies y los pequeños scripts que inyectamos.
 * Solo funciones puras; lo que toca WebView vive en [WebPanes].
 */
object XWeb {
    const val HOME = "https://x.com/home"
    const val LOGIN = "https://x.com/i/flow/login"

    /**
     * Oculta la columna de iconos de X (Inicio, Buscar, Notificaciones…) en las mitades de la app, que
     * ya tienen sus pestañas y el botón de redactar. Sin condición de ancho: en el plegable cada mitad
     * mide menos de 500 px y aun así X pone la columna, así que una regla "a partir de 500 px" no
     * llegaba a aplicarse nunca. Si alguna vez la cabecera de X envolviera la página entera, el
     * `:not(:has(main))` evita esconder también los posts.
     */
    const val RAIL_CSS = """header[role="banner"]:not(:has(main)){display:none!important}"""
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

    /** Las ventanas que la web abre aparte (el inicio de sesión con Google) se enseñan dentro; los enlaces de posts a otras webs no son ventanas. */
    fun opensOutside(url: String): Boolean = !isWeb(url) || !isOwn(url) || host(url) == "t.co"

    /** Enlace de un post a otra web (los de X van por t.co): se abre en la otra mitad, no en el navegador. */
    fun isExternal(url: String): Boolean = isWeb(url) && (!isOwn(url) || host(url) == "t.co")

    /**
     * El enlace que trae un intent de fuera: el de un "abrir con" (VIEW, con la dirección en `data`) o
     * el primero del texto compartido (SEND). Nulo si no hay ninguno.
     */
    fun linkFromIntent(action: String?, data: String?, text: String?): String? = when (action) {
        "android.intent.action.VIEW" -> data?.takeIf(::isWeb)
        "android.intent.action.SEND" -> text?.let { Regex("https?://[^\\s<>\"']+").find(it)?.value?.trimEnd('.', ',', ')') }
        else -> null
    }

    /** Una imagen de X (pbs.twimg.com), abierta sola en una mitad al tocar la foto de un post. */
    fun isMedia(url: String): Boolean = host(url) == "pbs.twimg.com"

    /** La misma imagen de X a tamaño grande: en el feed va con name=small o name=360x360. */
    fun largeImage(url: String): String = when {
        Regex("[?&]name=").containsMatchIn(url) -> url.replace(Regex("([?&])name=[^&#]*"), "$1name=large")
        '?' in url -> "$url&name=large"
        else -> "$url?name=large"
    }

    /** Un vídeo de X (video.twimg.com): se abre solo en una mitad, con el reproductor del navegador. */
    fun isVideo(url: String): Boolean = host(url) == "video.twimg.com"

    /** La búsqueda de una dirección /search?q=…, ya legible. */
    fun queryOf(url: String): String? = Regex("[?&]q=([^&#]*)").find(url)?.groupValues?.get(1)?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrNull() }

    /**
     * Qué pinta la interfaz propia en una dirección de X: Inicio, una búsqueda (Mi feed), Guardados,
     * un perfil o un post. Nulo para lo demás (notificaciones, mensajes, explorar…), que sigue en la web.
     */
    fun view(url: String, source: Source? = null): XView? {
        if (!isX(url)) return null
        statusId(url)?.let { return XView.Thread(it) }
        val p = path(url)
        return when {
            p == "/home" || (p.isEmpty() && source == Source.Inicio) -> XView.Home
            p == "/search" -> queryOf(url)?.let { XView.Search(it) }
            p == "/i/bookmarks" -> XView.Bookmarks
            p == "/notifications" || p.startsWith("/notifications/") -> XView.Notifications
            p.startsWith("/i/user/") -> XView.Profile(null, p.removePrefix("/i/user/").substringBefore('/'))
            else -> handleOf(url)?.let { XView.Profile(it, null) }
        }
    }

    /** Nombre de la web de un enlace para la cabecera: "elpais.com". */
    fun siteLabel(url: String): String = host(url)?.removePrefix("www.")?.removePrefix("m.") ?: "Enlace"

    fun host(url: String): String? {
        val m = Regex("^https?://([^/?#]+)").find(url) ?: return null
        return m.groupValues[1].lowercase().substringBefore(':')
    }

    /** Peticiones a los servidores de X (no a sus archivos estáticos de twimg). */
    fun isXRequest(url: String): Boolean {
        val h = host(url) ?: return false
        return h == "x.com" || h.endsWith(".x.com") || h == "twitter.com" || h.endsWith(".twitter.com")
    }

    /**
     * Resumen de lo que enseña la página para el registro: títulos, avisos, campos (tipo y si están
     * rellenos, nunca su contenido), botones y tamaño de la ventana.
     */
    fun describeScript(): String = """
        (function(){
          function vis(el){ var r = el.getBoundingClientRect(); return r.width > 0 && r.height > 0; }
          function txt(el){ return String(el.innerText || el.textContent || '').replace(/\s+/g, ' ').trim().slice(0, 80); }
          function all(q){ return Array.prototype.filter.call(document.querySelectorAll(q), vis); }
          var out = [];
          var h = all('h1,h2,[role=heading]').map(txt).filter(Boolean).slice(0, 3);
          out.push('títulos: ' + (h.join(' | ') || 'ninguno'));
          var a = all('[role=alert],[aria-live=assertive],[aria-live=polite]').map(txt).filter(Boolean).slice(0, 2);
          if (a.length) out.push('avisos: ' + a.join(' | '));
          var f = all('input,textarea').filter(function(i){ return i.type !== 'hidden'; }).map(function(i){
            return (i.type || 'texto') + (i.name ? ' ' + i.name : '') + (i.autocomplete ? ' (' + i.autocomplete + ')' : '') +
              (i.value ? ' · relleno' : ' · vacío') + (document.activeElement === i ? ' · con foco' : '');
          }).slice(0, 4);
          out.push('campos: ' + (f.join(' | ') || 'ninguno'));
          var b = all('button,[role=button]').map(function(x){
            var t = txt(x) || x.getAttribute('aria-label') || '';
            return t ? t + (x.disabled || x.getAttribute('aria-disabled') === 'true' ? ' (desactivado)' : '') : '';
          }).filter(Boolean).slice(0, 8);
          out.push('botones: ' + (b.join(' | ') || 'ninguno'));
          // Si hay un diálogo (el formulario de X lo es) pero no se ve: quién lo tapa o lo deja transparente.
          var d = all('[role=dialog],dialog')[0];
          if (d) {
            var oculto = '', el = d;
            while (el && el.nodeType === 1) {
              var c = getComputedStyle(el);
              if (c.opacity === '0' || c.visibility === 'hidden' || c.display === 'none') {
                oculto = el.tagName.toLowerCase() + (el.id ? '#' + el.id : '') + ' (opacidad ' + c.opacity + ', ' + c.visibility + ', ' + c.display + ')';
                break;
              }
              el = el.parentElement;
            }
            var r = d.getBoundingClientRect(), top = document.elementFromPoint(r.left + r.width / 2, r.top + Math.min(200, r.height / 2));
            out.push('diálogo: ' + d.tagName.toLowerCase() + (d.hasAttribute('popover') ? ' popover' : '') +
              (oculto ? ' · oculto por ' + oculto : ' · sin ocultar') +
              ' · encima: ' + (top ? top.tagName.toLowerCase() + (top === d || d.contains(top) ? ' (del diálogo)' : ' (otra capa)') : 'nada'));
          }
          var alto = function(u){ var e = document.createElement('div'); e.style.cssText = 'position:fixed;top:0;left:0;width:1px;visibility:hidden;height:100' + u; document.documentElement.appendChild(e); var h = e.getBoundingClientRect().height; e.remove(); return Math.round(h); };
          out.push('100vh=' + alto('vh') + ' 100dvh=' + alto('dvh') + ' · ventana ' + innerWidth + 'x' + innerHeight + ' · ' + location.host + location.pathname);
          return out.join(' · ');
        })()
    """.trimIndent()

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
     * Una página de entrada de X: el formulario (/i/flow/login), la portada sin sesión, que en el
     * móvil lleva el formulario dentro (teléfono, Google, Apple, correo), o el formulario nuevo de X
     * para móvil (/i/jf/onboarding/…), al que X pasa sola en cuanto carga. Ahí no hay que tocar nada:
     * recargar por nuestra cuenta borraría lo que la persona está escribiendo.
     */
    fun isLoginPage(url: String): Boolean {
        if (!isX(url)) return false
        val p = path(url)
        return p.isEmpty() || p == "/login" || p.startsWith("/i/flow/login") || p.startsWith("/i/jf/onboarding")
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

    /** El redactor de X como respuesta a un post. */
    fun replyUrl(tweetId: String) = "https://x.com/intent/post?in_reply_to=$tweetId"

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
          var nInit = 0;
          function wrapInit(init){
            return function(cfg){
              try {
                var c = {}; for (var k in cfg) { c[k] = typeof cfg[k] === 'function' ? 'fn' : cfg[k]; }
                say('gsi.initialize #' + (++nInit) + ' ' + JSON.stringify(c));
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
          // X solo llama a google.accounts.id.initialize una vez por página y lo apunta en window.google.isInitialized.
          // Si el botón de Google se vuelve a montar (en el móvil pasa nada más cargar), la instancia nueva no repite
          // initialize y Google sigue avisando a la instancia vieja, que ya está vacía: de ahí "Input2SSO:
          // Unsupported provider". Con la marca siempre a falso, cada montaje repite initialize (Google se queda con
          // el último) y la credencial llega a la instancia viva.
          function liberar(g){
            if (!g || typeof g !== 'object' || g.__plLibre) return; g.__plLibre = true;
            try {
              Object.defineProperty(g, 'isInitialized', {configurable: true, enumerable: true,
                get: function(){ return false; },
                set: function(v){ say('X marca google.isInitialized=' + v + ' (se ignora: cada montaje repite initialize)'); }});
            } catch(e){ say('no pude liberar isInitialized: ' + e); }
          }
          function watchAccounts(g){
            if (!g || g.__plw) return; g.__plw = true;
            liberar(g);
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
          window.addEventListener('message', function(e){
            if (!/google|apple/.test(e.origin)) return;
            var d = e.data; try { d = typeof d === 'string' ? d : JSON.stringify(d); } catch(x){ d = String(d); }
            say('mensaje de ' + e.origin + ': ' + String(d).slice(0, 300));
          }, true);
          window.addEventListener('error', function(e){ say('error ' + e.message + ' @ ' + String(e.filename).split('/').pop() + ':' + e.lineno); }, true);
          window.addEventListener('unhandledrejection', function(e){ var r = e.reason; say('promesa rechazada ' + (r && r.stack || r && r.message || r)); });
          var ce = console.error;
          console.error = function(){ try { var a = arguments[0]; if (a && a.stack) say('pila: ' + String(a.stack).slice(0, 500)); } catch(e){} return ce.apply(console, arguments); };
          var brands = ''; try { brands = (navigator.userAgentData && navigator.userAgentData.brands || []).map(function(b){ return b.brand + ' ' + b.version; }).join(', '); } catch(e){}
          say('gancho de login puesto · FedCM ' + ('IdentityCredential' in window ? 'sí' : 'no') + ' · marcas: ' + (brands || 'ninguna'));
        })();
    """.trimIndent()

    /**
     * Escucha lo que la web de X se descarga para pintar sus listas (su API interna, /i/api/graphql/…/Nombre)
     * y se lo pasa a la app (`Pliegue.data`) tal cual, sin pedir nada por nuestra cuenta: la interfaz propia
     * pinta esos mismos posts. Va al arrancar cada documento; si se vuelve a poner, no hace nada.
     */
    fun listenScript(): String = """
        (function(){
          if (window.__pliegueNet) return; window.__pliegueNet = true;
          var RE = /\/i\/api\/graphql\/[^\/?]+\/([A-Za-z0-9_]+)/;
          // Las consultas POST (Inicio) llevan las variables (el cursor) en el cuerpo de la petición, no en la dirección.
          function body(b){ try { return typeof b === 'string' ? b.slice(0, 20000) : ''; } catch(e){ return ''; } }
          function report(url, text, req){
            try {
              var m = RE.exec(url || ''); if (!m) return;
              if (window.Pliegue && window.Pliegue.data) window.Pliegue.data(m[1], String(url), String(text), String(req || ''));
            } catch(e){}
          }
          var of = window.fetch;
          if (typeof of === 'function') {
            window.fetch = function(input, init){
              var url = '', req = ''; try { url = typeof input === 'string' ? input : (input && input.url) || ''; req = body(init && init.body); } catch(e){}
              var p = of.apply(this, arguments);
              if (RE.test(url)) p.then(function(r){ try { if (r && r.ok) r.clone().text().then(function(t){ report(r.url || url, t, req); }); } catch(e){} }, function(){});
              return p;
            };
          }
          var XP = window.XMLHttpRequest && window.XMLHttpRequest.prototype;
          if (XP && XP.open && XP.send) {
            var oo = XP.open, os = XP.send;
            XP.open = function(m, u){ try { this.__plUrl = String(u); } catch(e){} return oo.apply(this, arguments); };
            XP.send = function(b){
              var x = this, req = body(b);
              try { if (x.__plUrl && RE.test(x.__plUrl)) x.addEventListener('load', function(){ try { if (x.status === 200) report(x.responseURL || x.__plUrl, x.responseText, req); } catch(e){} }); } catch(e){}
              return os.apply(this, arguments);
            };
          }
        })();
    """.trimIndent()

    /**
     * Guion para la web escondida de acciones: en la página del post (o del perfil) busca el botón de X
     * que corresponde a la acción, lo toca, y cuando X cambia el botón al contrario (me gusta → ya no me
     * gusta) avisa con `Pliegue.acted(token, true, …)`. Si en 20 s no encuentra el botón o X no cambia,
     * avisa con false y el motivo. Repostear y dejar de seguir piden una confirmación: la toca también.
     * Los botones se reconocen por los `data-testid` de la web de X; en el perfil se salta los de las
     * sugerencias ("A quién seguir") y los de los posts.
     */
    fun actScript(kind: String, target: String, token: Int): String = """
        (function(kind, id, token){
          var P = window.__pliegueAct = window.__pliegueAct || {};
          if (P.timer) { clearInterval(P.timer); P.timer = null; }
          var plan = {
            like:       {steps: [['art', '[data-testid="like"]']], done: ['art', '[data-testid="unlike"]']},
            unlike:     {steps: [['art', '[data-testid="unlike"]']], done: ['art', '[data-testid="like"]']},
            bookmark:   {steps: [['art', '[data-testid="bookmark"]']], done: ['art', '[data-testid="removeBookmark"]']},
            unbookmark: {steps: [['art', '[data-testid="removeBookmark"]']], done: ['art', '[data-testid="bookmark"]']},
            repost:     {steps: [['art', '[data-testid="retweet"]'], ['doc', '[data-testid="retweetConfirm"]']], done: ['art', '[data-testid="unretweet"]']},
            unrepost:   {steps: [['art', '[data-testid="unretweet"]'], ['doc', '[data-testid="unretweetConfirm"]']], done: ['art', '[data-testid="retweet"]']},
            follow:     {steps: [['head', '[data-testid${'$'}="-follow"]']], done: ['head', '[data-testid${'$'}="-unfollow"]']},
            unfollow:   {steps: [['head', '[data-testid${'$'}="-unfollow"]'], ['doc', '[data-testid="confirmationSheetConfirm"]']], done: ['head', '[data-testid${'$'}="-follow"]']}
          }[kind];
          function tell(ok, why){
            if (P.timer) { clearInterval(P.timer); P.timer = null; }
            try { if (window.Pliegue && window.Pliegue.acted) window.Pliegue.acted(token, !!ok, String(why || '')); } catch(e){}
          }
          if (!plan) { tell(false, 'acción desconocida'); return; }
          function article(){
            var as = document.querySelectorAll('article');
            for (var i = 0; i < as.length; i++) if (as[i].querySelector('a[href${'$'}="/status/' + id + '"]')) return as[i];
            return null;
          }
          function find(step){
            var scope = step[0], sel = step[1];
            if (scope === 'doc') return document.querySelector(sel);
            if (scope === 'art') { var a = article(); return a ? a.querySelector(sel) : null; }
            var all = document.querySelectorAll(sel);
            for (var i = 0; i < all.length; i++) if (!all[i].closest('article') && !all[i].closest('[data-testid="UserCell"]')) return all[i];
            return null;
          }
          var step = 0, clickedAt = 0, t0 = Date.now();
          function tick(){
            try {
              if (step === 0 && find(plan.done)) { tell(true, 'ya estaba'); return; }
              if (clickedAt && step >= plan.steps.length && Date.now() - clickedAt > 1200 && find(plan.done)) { tell(true, ''); return; }
              if (step < plan.steps.length) { var el = find(plan.steps[step]); if (el) { el.click(); clickedAt = Date.now(); step++; } }
              if (Date.now() - t0 > 20000) tell(false, step === 0 ? 'no encontré el botón' : 'X no lo confirmó');
            } catch(e) { tell(false, String(e)); }
          }
          P.timer = setInterval(tick, 400);
          tick();
        })(${jsString(kind)}, ${jsString(target)}, $token);
    """.trimIndent()

    private fun jsString(s: String): String = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ") + "\""

    /**
     * Script que ponemos en cada página de X:
     * - un toque sobre un post avisa a la app (`Pliegue.openPost`) en vez de navegar, para abrirlo en la otra mitad;
     * - mantener pulsado un avatar o un nombre avisa con su usuario (`Pliegue.dragUser`) para arrastrarlo a Mi feed;
     * - los estilos ocultan la barra inferior, el botón flotante y la columna de iconos de la web
     *   dentro de los paneles: las pestañas de cada mitad y nuestro botón de redactar ya hacen lo
     *   mismo y así el post ocupa todo el ancho. Una vez por página se apunta en el registro el ancho
     *   de la web y si la columna quedó escondida, para comprobarlo en el móvil.
     */
    fun bridgeScript(interceptPosts: Boolean, hideChrome: Boolean, cleanup: Cleanup = Cleanup()): String = bridge(interceptPosts, hideChrome, "{ads:${cleanup.ads},sug:${cleanup.suggestions},premium:${cleanup.premium},following:${cleanup.following}}")

    private fun bridge(interceptPosts: Boolean, hideChrome: Boolean, clean: String): String = """
        (function(){
          if (window.__pliegue) {
            var W = window.__pliegue, cambia = W.chrome !== $hideChrome;
            W.posts = $interceptPosts; W.chrome = $hideChrome; W.clean = $clean; W.style();
            if (W.followSoon) W.followSoon();
            if (cambia && W.report) setTimeout(W.report, 1500);
            return;
          }
          var P = { posts: $interceptPosts, chrome: $hideChrome, clean: $clean };
          // Lo que se esconde de X: las barras (en las mitades) y la limpieza elegida en Ajustes.
          // Los módulos de Inicio ("A quién seguir", tendencias) solo en /home, para no tocar otras listas.
          P.css = function(){
            var c = P.chrome ? '[data-testid="BottomBar"],[data-testid="FloatingActionButton"],a[data-testid="FloatingActionButtons_Tweet_Button"],[data-testid="BottomBar"]+div{display:none!important}$RAIL_CSS' : '';
            var k = P.clean || {}, home = location.pathname === '/home';
            if (k.ads) c += '[data-testid="cellInnerDiv"]:has([data-testid="placementTracking"]){display:none!important}';
            if (k.sug && home) c += '[data-testid="cellInnerDiv"]:has([data-testid="UserCell"]),[data-testid="cellInnerDiv"]:has(a[href^="/i/connect_people"]),[data-testid="cellInnerDiv"]:has([data-testid="trend"]),[data-testid="cellInnerDiv"]:has(a[href^="/explore/tabs"]),[data-testid="cellInnerDiv"]:has(h2){display:none!important}';
            if (k.premium) c += '[data-testid="cellInnerDiv"]:has(a[href^="/i/premium"]),[data-testid="cellInnerDiv"]:has([data-testid*="upsell" i]),aside:has(a[href^="/i/premium"]){display:none!important}';
            return c;
          };
          // Inicio en "Siguiendo": si al cargar /home está elegida la primera pestaña ("Para ti"), se toca la segunda.
          // Una vez por dirección: si luego vuelves a "Para ti" a mano, se respeta.
          P.follow = function(){
            var k = P.clean || {};
            if (!k.following || location.pathname !== '/home' || P.followed === location.href) return;
            var tabs = document.querySelectorAll('[role="tablist"] [role="tab"]');
            if (tabs.length < 2) return;
            if (tabs[0].getAttribute('aria-selected') === 'true') {
              P.followed = location.href;
              tabs[1].click();
              console.log('[pliegue] Inicio puesto en Siguiendo');
            } else if (tabs[1].getAttribute('aria-selected') === 'true') {
              P.followed = location.href;
            }
          };
          P.followSoon = function(){ [800, 2000, 4000].forEach(function(ms){ setTimeout(P.follow, ms); }); };
          // Para la interfaz propia, que lee lo que la web se descarga: pedir la página siguiente es bajar
          // la web hasta el final (X carga más sola), y las pestañas de Inicio se tocan en la web.
          P.more = function(){ try { window.scrollTo(0, Math.max(document.documentElement.scrollHeight, document.body.scrollHeight)); } catch(e){} };
          P.tab = function(i){
            var tabs = document.querySelectorAll('[role="tablist"] [role="tab"]');
            if (!tabs[i]) return false;
            if (tabs[i].getAttribute('aria-selected') !== 'true') tabs[i].click();
            return true;
          };
          window.__pliegue = P;
          P.style = function(){
            // Al empezar a cargar puede no haber documento todavía: entonces se pone al terminar de leerlo.
            var root = document.head || document.documentElement;
            if (!root) { document.addEventListener('DOMContentLoaded', P.style, {once: true}); return; }
            var s = document.getElementById('pliegue-style');
            if (!s) { s = document.createElement('style'); s.id = 'pliegue-style'; root.appendChild(s); }
            s.textContent = P.css();
          };
          P.style();
          P.followSoon();
          // Para comprobar en el móvil qué pasó: ancho de la web, si esta ranura esconde las barras de X
          // y cómo quedó su columna de iconos. Al cargar y cada vez que la ranura cambia de modo.
          P.report = function(){
            var h = document.querySelector('header[role="banner"]');
            var estado = !h ? 'no está' : (getComputedStyle(h).display === 'none' ? 'escondida' : 'visible');
            console.log('[pliegue] web de ' + innerWidth + ' px · ' + (P.chrome ? 'sin' : 'con') + ' barras de X · columna de iconos: ' + estado);
          };
          setTimeout(P.report, 6000);
          // No leídos de Notificaciones y Mensajes: X los pone en sus enlaces ("Notifications (3 unread items)"),
          // que siguen en la página aunque sus barras estén escondidas. Se avisa a la app cuando cambian.
          P.count = function(href){
            var a = document.querySelector('a[href="' + href + '"]');
            if (!a) return -1;
            var m = (a.getAttribute('aria-label') || '').match(/\d+/);
            if (m) return +m[0];
            var b = a.querySelector('[aria-label*="unread" i],[aria-label*="sin leer" i],[aria-label*="no le" i]');
            if (!b) return 0;
            var t = (b.textContent || '').match(/\d+/);
            return t ? +t[0] : 1;
          };
          P.unread = function(){
            if (!window.Pliegue || !window.Pliegue.unread) return;
            var n = P.count('/notifications'), m = P.count('/messages');
            if (n < 0 && m < 0) return;
            var k = n + ':' + m;
            if (k === P.lastUnread) return;
            P.lastUnread = k;
            window.Pliegue.unread(n, m);
          };
          setInterval(P.unread, 15000);
          setTimeout(P.unread, 3000);
          document.addEventListener('click', function(e){
            if (!P.posts || !window.Pliegue) return;
            var t = e.target;
            if (!t || !t.closest) return;
            var art = t.closest('article[data-testid="tweet"]');
            if (!art) return;
            // La foto de un post se abre sola en la otra mitad, a tamaño grande; los vídeos siguen en su sitio.
            var ph = t.closest('[data-testid="tweetPhoto"]');
            if (ph) {
              var img = ph.querySelector('img[src*="pbs.twimg.com/media/"]');
              if (img && img.src && window.Pliegue.openLink) {
                e.preventDefault(); e.stopPropagation();
                window.Pliegue.openLink(img.src);
              }
              return;
            }
            if (t.closest('a,button,[role="button"],[role="link"],video,[data-testid="videoPlayer"],[data-testid="card.wrapper"]')) return;
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
