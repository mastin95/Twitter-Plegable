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

/** WebView de un panel. No acepta arrastres: así caen en la zona de soltar de Compose que hay encima. */
class PaneWebView(context: Context) : WebView(context) {
    override fun onDragEvent(event: DragEvent?): Boolean = false
}

/**
 * Qué quiere cada panel de su web: avisar de los toques en posts, esconder la barra inferior de la
 * web y, en el inicio de sesión, no mandar nunca una página al navegador de fuera (`keepInside`).
 */
data class PaneOptions(val interceptPosts: Boolean = true, val hideChrome: Boolean = true, val keepInside: Boolean = false)

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

    private val slots = HashMap<String, PaneWebView>()
    private val options = HashMap<String, PaneOptions>()
    private val requested = HashMap<String, String>()
    private val main = Handler(Looper.getMainLooper())

    /** Dirección actual y progreso de carga de cada ranura. */
    val urls = mutableStateMapOf<String, String>()
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
        checkLogin()
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

    /** La WebView de una ranura; se crea y carga `url` la primera vez. */
    fun get(slot: String, url: String, opts: PaneOptions = PaneOptions()): PaneWebView {
        options[slot] = opts
        slots[slot]?.let { return it }
        val v = PaneWebView(activity)
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
        v.loadUrl(url)
    }

    fun configure(slot: String, opts: PaneOptions) {
        if (options[slot] == opts) return
        options[slot] = opts
        slots[slot]?.let { inject(it, slot) }
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
        while (log.size > 80) log.removeAt(log.lastIndex)
    }

    private fun short(url: String) = url.removePrefix("https://").take(160)

    fun reload(slot: String) {
        slots[slot]?.reload()
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

    /**
     * La ventana aparte que pidió una web. Si fue un enlace de un post a otra web (se sabe por el
     * punto tocado), va directamente al navegador y no hay ventana; si no, se crea y se enseña encima.
     */
    private fun createWindow(opener: WebView, slot: String, resultMsg: android.os.Message): Boolean {
        val hit = runCatching { opener.hitTestResult }.getOrNull()
        val tapped = hit?.extra?.takeIf { hit.type == WebView.HitTestResult.SRC_ANCHOR_TYPE || hit.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE }
        if (tapped != null && XWeb.opensOutside(tapped)) {
            openOutside(slot, Uri.parse(tapped))
            return false
        }
        closePopup()
        val p = PaneWebView(activity)
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
                if (message.messageLevel() == ConsoleMessage.MessageLevel.ERROR) log("[ventana] js: ${message.message().take(160)}")
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

    private fun setUp(v: PaneWebView, slot: String) {
        applySettings(v, popupWindow = false)
        v.addJavascriptInterface(Bridge(slot), "Pliegue")
        v.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                // Los marcos internos (captchas, inicio de sesión con Google) se quedan donde están.
                if (!request.isForMainFrame) return false
                val url = request.url.toString()
                if (XWeb.isWeb(url) && (XWeb.isOwn(url) || options[slot]?.keepInside == true)) return false
                openOutside(slot, request.url)
                return true
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                urls[slot] = url
                progress[slot] = 5
                lastActive = slot
                log("[$slot] carga ${short(url)}")
            }

            override fun onPageFinished(view: WebView, url: String) {
                urls[slot] = url
                progress[slot] = 100
                inject(view, slot)
                checkLogin()
                log("[$slot] cargada ${short(url)}" + if (loggedIn) " · con sesión" else " · sin sesión")
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) log("[$slot] error ${error.errorCode} ${error.description} en ${short(request.url.toString())}")
            }

            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
                if (request.isForMainFrame) log("[$slot] HTTP ${errorResponse.statusCode} en ${short(request.url.toString())}")
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
                if (message.messageLevel() == ConsoleMessage.MessageLevel.ERROR) log("[$slot] js: ${message.message().take(160)}")
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
        view.evaluateJavascript(XWeb.bridgeScript(o.interceptPosts, o.hideChrome), null)
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
