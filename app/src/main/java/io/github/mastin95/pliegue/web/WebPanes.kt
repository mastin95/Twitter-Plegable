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
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** WebView de un panel. No acepta arrastres: así caen en la zona de soltar de Compose que hay encima. */
class PaneWebView(context: Context) : WebView(context) {
    override fun onDragEvent(event: DragEvent?): Boolean = false
}

/** Qué quiere cada panel de su web: avisar de los toques en posts y esconder la barra inferior de la web. */
data class PaneOptions(val interceptPosts: Boolean = true, val hideChrome: Boolean = true)

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

    private var custom by mutableStateOf<View?>(null)
    private var customCallback: WebChromeClient.CustomViewCallback? = null

    /** Un vídeo a pantalla completa está tapando la app. */
    val inFullscreen: Boolean get() = custom != null

    init {
        checkLogin()
    }

    private fun cookies(): String? = runCatching { CookieManager.getInstance().getCookie("https://x.com") }.getOrNull()

    fun checkLogin(): Boolean {
        val c = cookies()
        loggedIn = XWeb.loggedIn(c)
        userId = XWeb.userId(c)
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
        slots.keys.toList().forEach(::drop)
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
    private fun setUp(v: PaneWebView, slot: String) {
        v.setBackgroundColor(Color.BLACK)
        v.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = true
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(v, true)
            // El tema "Noche" de X, para que la web vaya a juego con el negro de la app.
            if (cookies()?.contains("night_mode=") != true) setCookie("https://x.com", "night_mode=2; Domain=.x.com; Path=/; Secure")
        }
        v.addJavascriptInterface(Bridge(slot), "Pliegue")
        v.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                if (XWeb.isOwn(url)) return false
                runCatching { activity.startActivity(Intent(Intent.ACTION_VIEW, request.url)) }
                return true
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                urls[slot] = url
                progress[slot] = 5
                lastActive = slot
            }

            override fun onPageFinished(view: WebView, url: String) {
                urls[slot] = url
                progress[slot] = 100
                inject(view, slot)
                checkLogin()
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
