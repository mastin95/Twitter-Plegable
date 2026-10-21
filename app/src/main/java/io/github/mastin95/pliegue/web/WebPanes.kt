package io.github.mastin95.pliegue.web

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.graphics.Typeface
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.DragEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import io.github.mastin95.pliegue.model.Cleanup
import java.util.concurrent.Executors

/** WebView de un panel. No acepta arrastres: así caen en la zona de soltar de Compose que hay encima. */
class PaneWebView(context: Context) : WebView(context) {
    override fun onDragEvent(event: DragEvent?): Boolean = false
}

/**
 * Qué quiere cada panel de su web: avisar de los toques en posts, esconder la barra inferior de la
 * web y, en el inicio de sesión, no mandar nunca una página al navegador de fuera (`keepInside`).
 * `external` es una web ajena abierta desde un enlace de un post: sin el puente de X, y sus enlaces
 * se quedan en ella.
 */
data class PaneOptions(val interceptPosts: Boolean = true, val hideChrome: Boolean = true, val keepInside: Boolean = false, val debugLogin: Boolean = false, val external: Boolean = false) {
    companion object {
        /** Una web ajena abierta desde un enlace: tal cual, sin tocar nada. */
        val EXTERNAL = PaneOptions(interceptPosts = false, hideChrome = false, external = true)
    }
}

/**
 * Guarda una WebView por "ranura" (pane0, pane1, col:…, mesa0…) para que sobrevivan a plegar y
 * desplegar: Compose las quita y las vuelve a colgar, pero la página y el scroll se conservan.
 */
class WebPanes(
    private val activity: Activity,
    private val chooseFile: (WebChromeClient.FileChooserParams, ValueCallback<Array<Uri>>) -> Boolean = { _, _ -> false },
) {
    /** Un post tocado en una ranura; la app decide en qué mitad abrirlo. */
    var onOpenPost: (slot: String, url: String) -> Unit = { _, _ -> }

    /** Se empezó a arrastrar un usuario desde una ranura. */
    var onDragUser: (slot: String, handle: String) -> Unit = { _, _ -> }

    /** Un enlace a otra web tocado en una ranura; la app dice si lo enseña ella (en la otra mitad) o va al navegador. */
    var onOpenLink: (slot: String, url: String) -> Boolean = { _, _ -> false }

    private val slots = HashMap<String, PaneWebView>()
    private val options = HashMap<String, PaneOptions>()
    private val requested = HashMap<String, String>()
    private val main = Handler(Looper.getMainLooper())

    /** Los posts que las webs se han descargado, para la interfaz propia. */
    val store = XStore()

    // Las respuestas de X pesan cientos de KB: se leen fuera del hilo principal, de una en una.
    private val parser = Executors.newSingleThreadExecutor()

    /** Dirección actual y progreso de carga de cada ranura. */
    val urls = mutableStateMapOf<String, String>()

    /** No leídos que X marca en sus enlaces ("notifications", "messages"); lo dice la última web que los vio. */
    val unread = mutableStateMapOf<String, Int>()
    val progress = mutableStateMapOf<String, Int>()

    var loggedIn by mutableStateOf(false)
        private set
    var userId by mutableStateOf<String?>(null)
        private set

    /** Última ranura en la que pasó algo; a ella va el botón atrás. */
    var lastActive by mutableStateOf("pane0")
        private set

    /** Presentarse como Chrome de escritorio en todas las webs (recurso si X no carga como móvil). */
    var desktop by mutableStateOf(false)
        private set

    /**
     * Solo pruebas en el Android virtual: las webs no salen a internet; cada página es un documento vacío en
     * la dirección pedida (así la app, el puente y el escucha funcionan igual, y las cookies de la sesión de
     * prueba no las borra X). Las peticiones se interceptan fuera del hilo principal.
     */
    @Volatile var simulatedNet = false
        private set

    fun simulateNetwork() {
        simulatedNet = true
        log("red simulada: las webs no salen a internet (solo pruebas)")
    }

    /** Limpieza de X elegida en Ajustes; se aplica a todas las webs sin recargarlas. */
    var cleanup by mutableStateOf(Cleanup())
        private set

    fun useCleanup(c: Cleanup) {
        if (cleanup == c) return
        cleanup = c
        slots.forEach { (slot, v) -> inject(v, slot) }
    }

    /** Últimos avisos de las webs (consola, errores de carga), para saber qué pasa sin un depurador. */
    val log = mutableStateListOf<String>()

    private var custom by mutableStateOf<View?>(null)
    private var customCallback: WebChromeClient.CustomViewCallback? = null

    /** Un vídeo a pantalla completa está tapando la app. */
    val inFullscreen: Boolean get() = custom != null

    /**
     * Ventana que una web abrió aparte (`window.open`): así entra "Iniciar sesión con Google", que
     * elige la cuenta en una ventana propia y avisa a la de X al cerrarse. La app la enseña encima.
     */
    var popup by mutableStateOf<PaneWebView?>(null)
        private set
    var popupUrl by mutableStateOf("")
        private set
    var popupProgress by mutableStateOf(100)
        private set

    init {
        // Si la última vez la app se cerró por un error, que se vea en el registro.
        runCatching {
            val crash = java.io.File(activity.filesDir, CRASH_FILE)
            if (crash.exists()) {
                log("La app se cerró por un error la última vez · ${crash.readText().take(1500)}")
                crash.delete()
            }
        }
        checkLogin()
    }

    companion object {
        /** Archivo donde MainActivity apunta el último cierre inesperado. */
        const val CRASH_FILE = "ultimo-cierre.txt"
        /** La página vacía de la red simulada (pruebas). */
        private const val BLANK_PAGE = "<!doctype html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"><title>X</title></head><body></body></html>"
    }

    private fun cookies(): String? = runCatching { CookieManager.getInstance().getCookie("https://x.com") }.getOrNull()

    private var cookieNames = ""

    fun checkLogin(): Boolean {
        val c = cookies()
        loggedIn = XWeb.loggedIn(c)
        userId = XWeb.userId(c)
        // Qué cookies hay (solo los nombres), para saber si la sesión llegó a ponerse.
        val names = c.orEmpty().split(';').map { it.substringBefore('=').trim() }.filter { it.isNotEmpty() }.sorted().joinToString(", ")
        if (names != cookieNames) {
            cookieNames = names
            log("cookies de x.com: " + names.ifEmpty { "ninguna" })
        }
        return loggedIn
    }

    fun has(slot: String) = slot in slots

    /**
     * Las WebViews tienen que ocupar todo su hueco con MATCH_PARENT. Sin parámetros, Compose las cuelga
     * con WRAP_CONTENT, y entonces la WebView fuerza a 0 el alto que ve la página: 100vh y 100dvh valen 0.
     * El formulario de login de X mide su caja con calc(100dvh - 32px), así que se quedaba con alto 0 y la
     * pantalla en negro (visto en el Android virtual con las herramientas de Chrome).
     */
    private fun fullSize() = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

    /** La WebView de una ranura; se crea y carga `url` la primera vez. */
    fun get(slot: String, url: String, opts: PaneOptions = PaneOptions()): PaneWebView {
        // Una WebView que ya existe cambia de sitio al plegar o desplegar (la mitad izquierda pasa a
        // ser la pantalla entera y al revés): sus opciones se aplican a la página que ya tiene, no
        // solo a la siguiente que cargue. Antes se guardaban aquí sin más y configure() ya no veía
        // el cambio, así que la mitad izquierda seguía con los estilos de la pantalla plegada.
        slots[slot]?.let {
            configure(slot, opts)
            return it
        }
        options[slot] = opts
        val v = PaneWebView(activity).apply { layoutParams = fullSize() }
        setUp(v, slot)
        slots[slot] = v
        requested[slot] = url
        urls[slot] = url
        v.loadUrl(url)
        return v
    }

    /** Carga `url` solo si no es lo último que se pidió a esa ranura (navegar dentro no cuenta). */
    fun ensure(slot: String, url: String) {
        if (requested[slot] == url) return
        load(slot, url)
    }

    fun load(slot: String, url: String) {
        val v = slots[slot] ?: return
        requested[slot] = url
        urls[slot] = url
        store.clear(slot)
        v.loadUrl(url)
    }

    /**
     * La interfaz propia llegó al final de lo que tiene: se baja la web escondida hasta el final para
     * que X cargue la página siguiente (llega por el puente). No más de una vez cada pocos segundos.
     */
    fun loadMore(slot: String) {
        val now = System.currentTimeMillis()
        if (now - (store.asked[slot] ?: 0L) < 2500) return
        store.asked[slot] = now
        slots[slot]?.evaluateJavascript("window.__pliegue&&window.__pliegue.more&&window.__pliegue.more()", null)
    }

    /** Pestaña de Inicio de la web (0 "Para ti", 1 "Siguiendo"), para que cargue ese feed. */
    fun homeTab(slot: String, index: Int) {
        slots[slot]?.evaluateJavascript("window.__pliegue&&window.__pliegue.tab&&window.__pliegue.tab($index)", null)
    }

    fun configure(slot: String, opts: PaneOptions) {
        if (options[slot] == opts) return
        options[slot] = opts
        slots[slot]?.let { inject(it, slot) }
        log("[$slot] " + when {
            opts.external -> "web de fuera"
            opts.hideChrome -> "sin las barras de X"
            else -> "con las barras de X"
        })
    }

    fun textZoom(slot: String, percent: Int) {
        slots[slot]?.settings?.textZoom = percent
    }

    fun useDesktop(on: Boolean) {
        if (desktop == on) return
        desktop = on
        slots.forEach { (slot, v) ->
            presentAsChrome(v)
            requested[slot]?.let { v.loadUrl(it) }
        }
        popup?.let(::presentAsChrome)
        log("Agente de usuario: " + if (on) "Chrome de escritorio" else "Chrome para Android")
    }

    private fun userAgent(v: WebView): String {
        val base = WebSettings.getDefaultUserAgent(v.context)
        return if (desktop) XWeb.desktopUa(base) else XWeb.chromeUa(base)
    }

    /** Agente de usuario y "client hints" a juego: la web ve un Chrome, no una WebView. */
    private fun presentAsChrome(v: WebView) {
        v.settings.userAgentString = userAgent(v)
        val how = ClientHints.apply(v.settings, WebSettings.getDefaultUserAgent(v.context), desktop)
        if (how != hintsLogged) {
            hintsLogged = how
            log("Se presenta como $how")
        }
    }

    private var hintsLogged = ""
    private fun log(line: String) {
        val stamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.ROOT).format(java.util.Date())
        log.add(0, "$stamp $line")
        // También a logcat, para leerlo en las pruebas con el Android virtual.
        android.util.Log.i("Pliegue", line)
        while (log.size > 200) log.removeAt(log.lastIndex)
    }

    private fun short(url: String) = url.removePrefix("https://").take(160)

    fun reload(slot: String) {
        slots[slot]?.reload()
    }

    /** Vuelve arriba del todo en una ranura (tocar otra vez la pestaña activa, o doble toque en la cabecera). */
    fun scrollTop(slot: String) {
        slots[slot]?.evaluateJavascript("window.scrollTo({top:0,behavior:'smooth'})", null)
    }

    /** Apunta en el registro qué enseña ahora la web: títulos, avisos, campos (nunca lo escrito) y botones. */
    fun describe(slot: String) {
        val v = slots[slot] ?: return
        v.evaluateJavascript(XWeb.describeScript()) { json ->
            val text = runCatching { org.json.JSONTokener(json).nextValue() as? String }.getOrNull() ?: json
            log("[$slot] pantalla: " + text.orEmpty().take(700))
        }
    }

    fun canGoBack(slot: String) = slots[slot]?.canGoBack() == true

    fun goBack(slot: String) {
        slots[slot]?.goBack()
    }

    fun drop(slot: String) {
        slots.remove(slot)?.let {
            (it.parent as? ViewGroup)?.removeView(it)
            it.destroy()
        }
        store.clear(slot)
        options.remove(slot)
        requested.remove(slot)
        urls.remove(slot)
        progress.remove(slot)
    }

    fun destroyAll() {
        exitFullscreen()
        closePopup()
        slots.keys.toList().forEach(::drop)
    }

    fun closePopup() {
        val p = popup ?: return
        popup = null
        popupUrl = ""
        popupProgress = 100
        (p.parent as? ViewGroup)?.removeView(p)
        p.destroy()
        // La ventana pudo dejar puesta la sesión (Google, Apple) sin que la web de X cambie de página.
        checkLogin()
    }

    /** Borra cookies, almacenamiento y caché de las webs sin cerrarlas (para empezar el login de cero). */
    fun clearData(done: () -> Unit) {
        val cm = CookieManager.getInstance()
        cm.removeAllCookies {
            WebStorage.getInstance().deleteAllData()
            slots.values.forEach { it.clearCache(true) }
            cm.setCookie("https://x.com", "night_mode=2; Domain=.x.com; Path=/; Secure")
            cm.flush()
            checkLogin()
            log("datos de las webs borrados")
            done()
        }
    }

    /** Borra la sesión de X (cookies y almacenamiento) y cierra todas las webs. */
    fun logout(done: () -> Unit) {
        destroyAll()
        val cm = CookieManager.getInstance()
        cm.removeAllCookies {
            WebStorage.getInstance().deleteAllData()
            cm.flush()
            checkLogin()
            done()
        }
    }

    fun exitFullscreen() {
        custom?.let { (activity.window.decorView as ViewGroup).removeView(it) }
        custom = null
        customCallback?.onCustomViewHidden()
        customCallback = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun applySettings(v: WebView, popupWindow: Boolean) {
        v.setBackgroundColor(Color.BLACK)
        v.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = true
            useWideViewPort = true
            loadWithOverviewMode = true
            // Las webs pueden abrir ventanas aparte (Google elige la cuenta en una); la ventana
            // que abre una ventana ya no abre más.
            setSupportMultipleWindows(!popupWindow)
            javaScriptCanOpenWindowsAutomatically = !popupWindow
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
        }
        presentAsChrome(v)
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(v, true)
            // El tema "Noche" de X, para que la web vaya a juego con el negro de la app.
            if (cookies()?.contains("night_mode=") != true) setCookie("https://x.com", "night_mode=2; Domain=.x.com; Path=/; Secure")
        }
    }

    private fun openOutside(from: String, url: Uri) {
        log("[$from] fuera de la app: ${short(url.toString())}")
        runCatching { activity.startActivity(Intent(Intent.ACTION_VIEW, url)) }
    }

    /** Abre una dirección en el navegador del móvil (botón "Abrir en el navegador" de una web ajena). */
    fun openInBrowser(url: String) = openOutside("app", Uri.parse(url))

    /**
     * Un enlace a otra web tocado en una ranura. Dentro de una web ajena sigue en ella (con su historial);
     * desde X, la app lo abre en la otra mitad si puede. Si no, al navegador.
     */
    private fun takeLink(view: WebView, slot: String, url: String): Boolean {
        if (!XWeb.isWeb(url)) return false
        if (options[slot]?.external == true) {
            view.loadUrl(url)
            return true
        }
        lastActive = slot
        return onOpenLink(slot, url)
    }

    /**
     * La ventana aparte que pidió una web. Si fue un enlace de un post a otra web (se sabe por el
     * punto tocado), va directamente al navegador y no hay ventana; si no, se crea y se enseña encima.
     */
    private fun createWindow(opener: WebView, slot: String, resultMsg: android.os.Message): Boolean {
        val hit = runCatching { opener.hitTestResult }.getOrNull()
        val tapped = hit?.extra?.takeIf { hit.type == WebView.HitTestResult.SRC_ANCHOR_TYPE || hit.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE }
        if (tapped != null && XWeb.opensOutside(tapped)) {
            if (!takeLink(opener, slot, tapped)) openOutside(slot, Uri.parse(tapped))
            return false
        }
        closePopup()
        val p = PaneWebView(activity).apply { layoutParams = fullSize() }
        applySettings(p, popupWindow = true)
        // Una vez la ventana está en X, Google o Apple, todo lo que pase dentro se queda dentro: el
        // inicio de sesión salta por páginas que no conocemos y abrirlas fuera lo rompe.
        var settled = false
        p.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (!request.isForMainFrame) return false
                val url = request.url.toString()
                if (!XWeb.isWeb(url)) {
                    // Esquemas internos de Google (storagerelay:…) se ignoran sin cerrar la ventana.
                    if (XWeb.isExternalScheme(url)) openOutside("ventana", request.url) else log("[ventana] ignora ${short(url)}")
                    return true
                }
                if (XWeb.isOwn(url) || settled) return false
                openOutside("ventana", request.url)
                main.post { if (popup === view) closePopup() }
                return true
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                popupUrl = url
                popupProgress = 5
                log("[ventana] carga ${short(url)}")
            }

            override fun onPageFinished(view: WebView, url: String) {
                popupUrl = url
                popupProgress = 100
                if (XWeb.isOwn(url) && XWeb.host(url) != "t.co") settled = true
                log("[ventana] cargada ${short(url)}")
                checkLogin()
            }

            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
                if (request.isForMainFrame) log("[ventana] HTTP ${errorResponse.statusCode} en ${short(request.url.toString())}")
            }
        }
        p.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                popupProgress = newProgress
            }

            override fun onCloseWindow(window: WebView) {
                // La ventana se cierra sola al acabar (Google ya avisó a la web de X). Se quita en
                // el siguiente paso del hilo: destruirla desde su propio aviso no es seguro.
                log("[ventana] cerrada por la web")
                main.post { if (popup === window) closePopup() }
            }

            override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                logConsole("ventana", message)
                return true
            }
        }
        popup = p
        popupUrl = ""
        popupProgress = 5
        log("[$slot] abre una ventana aparte")
        (resultMsg.obj as WebView.WebViewTransport).webView = p
        resultMsg.sendToTarget()
        return true
    }

    private var startScriptLogged = false

    private fun setUp(v: PaneWebView, slot: String) {
        applySettings(v, popupWindow = false)
        v.addJavascriptInterface(Bridge(slot), "Pliegue")
        // El escucha de la API de X tiene que estar antes de que corra el código de la web, que es quien pide
        // los posts; si el motor no lo permite, se pone al cargar (puede perderse la primera petición).
        val supported = runCatching { WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT) }.getOrDefault(false)
        val ok = supported && runCatching {
            WebViewCompat.addDocumentStartJavaScript(v, XWeb.listenScript(), setOf("https://x.com", "https://*.x.com", "https://twitter.com", "https://*.twitter.com"))
        }.isSuccess
        if (!startScriptLogged) {
            startScriptLogged = true
            log(if (ok) "escucha de X puesto al arrancar cada página" else "el motor no admite scripts de arranque: el escucha se pone al cargar")
        }
        v.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                // Los marcos internos (captchas, inicio de sesión con Google) se quedan donde están.
                if (!request.isForMainFrame) return false
                val url = request.url.toString()
                val o = options[slot]
                if (XWeb.isWeb(url) && (XWeb.isOwn(url) || o?.keepInside == true || o?.external == true)) return false
                if (!takeLink(view, slot, url)) openOutside(slot, request.url)
                return true
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                urls[slot] = url
                progress[slot] = 5
                lastActive = slot
                log("[$slot] carga ${short(url)}")
                if (options[slot]?.debugLogin == true) view.evaluateJavascript(XWeb.loginHookScript(), null)
            }

            override fun onPageFinished(view: WebView, url: String) {
                urls[slot] = url
                progress[slot] = 100
                inject(view, slot)
                checkLogin()
                log("[$slot] cargada ${short(url)}" + if (loggedIn) " · con sesión" else " · sin sesión")
            }

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                if (!simulatedNet) return null
                return if (request.isForMainFrame) WebResourceResponse("text/html", "utf-8", 200, "OK", emptyMap(), BLANK_PAGE.byteInputStream())
                else WebResourceResponse("text/plain", "utf-8", 204, "No Content", emptyMap(), ByteArray(0).inputStream())
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) log("[$slot] error ${error.errorCode} ${error.description} en ${short(request.url.toString())}")
                else if (options[slot]?.debugLogin == true && XWeb.isXRequest(request.url.toString())) log("[$slot] falla ${request.url.host}${request.url.path}: ${error.description}")
            }

            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
                if (request.isForMainFrame) log("[$slot] HTTP ${errorResponse.statusCode} en ${short(request.url.toString())}")
                // En el login también las peticiones internas de X que fallan (solo la ruta, sin parámetros):
                // así se ve si X rechaza un paso del formulario sin tocar la página.
                else if (options[slot]?.debugLogin == true && XWeb.isXRequest(request.url.toString())) log("[$slot] X responde ${errorResponse.statusCode} en ${request.url.host}${request.url.path}")
            }

            override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                urls[slot] = url
                lastActive = slot
                inject(view, slot)
                checkLogin()
            }
        }
        v.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progress[slot] = newProgress
                if (newProgress > 60) inject(view, slot)
            }

            override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                logConsole(slot, message)
                return true
            }

            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                if (custom != null) {
                    callback.onCustomViewHidden()
                    return
                }
                custom = view
                customCallback = callback
                view.setBackgroundColor(Color.BLACK)
                (activity.window.decorView as ViewGroup).addView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            }

            override fun onHideCustomView() {
                exitFullscreen()
            }

            override fun onShowFileChooser(webView: WebView, filePathCallback: ValueCallback<Array<Uri>>, fileChooserParams: FileChooserParams): Boolean =
                chooseFile(fileChooserParams, filePathCallback)

            override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message): Boolean =
                createWindow(view, slot, resultMsg)
        }
    }

    private fun inject(view: WebView, slot: String) {
        val o = options[slot] ?: PaneOptions()
        if (o.external) return
        view.evaluateJavascript(XWeb.listenScript(), null)
        view.evaluateJavascript(XWeb.bridgeScript(o.interceptPosts, o.hideChrome, cleanup), null)
        if (o.debugLogin) view.evaluateJavascript(XWeb.loginHookScript(), null)
    }

    /** Errores de la web y, en el inicio de sesión, lo que apunta nuestro gancho (líneas "[pliegue]"). */
    private fun logConsole(from: String, message: ConsoleMessage) {
        val text = message.message()
        when {
            text.startsWith("[pliegue]") -> log("[$from] ${text.removePrefix("[pliegue] ").take(600)}")
            message.messageLevel() == ConsoleMessage.MessageLevel.ERROR ->
                log("[$from] js: ${text.take(300)} (${message.sourceId().substringAfterLast('/').take(50)}:${message.lineNumber()})")
        }
    }

    private inner class Bridge(private val slot: String) {
        @JavascriptInterface
        fun openPost(url: String) {
            main.post {
                lastActive = slot
                onOpenPost(slot, url)
            }
        }

        @JavascriptInterface
        fun dragUser(handle: String) {
            main.post { startDrag(slot, handle) }
        }

        @JavascriptInterface
        fun unread(notifications: Int, messages: Int) {
            main.post {
                if (notifications >= 0) unread["notifications"] = notifications
                if (messages >= 0) unread["messages"] = messages
            }
        }

        /**
         * Una respuesta de la API interna de X que la web acaba de recibir (nombre de la consulta,
         * dirección con sus variables y el JSON). Se lee aparte y se guarda en el hilo principal.
         */
        @JavascriptInterface
        fun data(name: String, url: String, body: String) = data(name, url, body, "")

        @JavascriptInterface
        fun data(name: String, url: String, body: String, request: String) {
            val req = XParse.request(url, request)
            if (req.name.isEmpty()) return
            parser.execute {
                val parsed = runCatching { XStore.parse(req, body) }.getOrNull()
                main.post {
                    if (parsed == null) {
                        if (req.name in XParse.FEEDS || req.name == XParse.THREAD) log("[$slot] ${req.name}: no entendí la respuesta (${body.length} caracteres)")
                    } else {
                        log("[$slot] " + store.put(slot, req, parsed, System.currentTimeMillis()))
                    }
                }
            }
        }

        /** La foto de un post: la app la abre en la otra mitad; donde no hay otra, en el navegador. */
        @JavascriptInterface
        fun openLink(url: String) {
            main.post {
                lastActive = slot
                if (!onOpenLink(slot, url)) openOutside(slot, Uri.parse(url))
            }
        }
    }

    private fun startDrag(slot: String, handle: String) {
        val v = slots[slot] ?: return
        val clean = handle.removePrefix("@")
        // Si la propia web ya empezó un arrastre con el enlace, este devuelve false y vale el suyo.
        val started = v.startDragAndDrop(ClipData.newPlainText("pliegue-user", clean), HandleShadow(v, "@$clean"), null, 0)
        if (started) onDragUser(slot, clean)
    }
}

/** Sombra del arrastre: una píldora azul con el @usuario, como la etiqueta de un post. */
private class HandleShadow(view: View, private val text: String) : View.DragShadowBuilder(view) {
    private val density = view.resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 16f * view.resources.displayMetrics.scaledDensity
        color = Color.WHITE
        typeface = Typeface.DEFAULT_BOLD
    }
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1D9BF0.toInt() }
    private val padX = 20f * density
    private val padY = 12f * density
    private val w = (paint.measureText(text) + 2 * padX).toInt()
    private val h = (paint.textSize + 2 * padY).toInt()

    override fun onProvideShadowMetrics(size: Point, touch: Point) {
        size.set(w, h)
        touch.set(w / 2, h)
    }

    override fun onDrawShadow(canvas: Canvas) {
        canvas.drawRoundRect(0f, 0f, w.toFloat(), h.toFloat(), h / 2f, h / 2f, bg)
        canvas.drawText(text, padX, h / 2f - (paint.descent() + paint.ascent()) / 2, paint)
    }
}
