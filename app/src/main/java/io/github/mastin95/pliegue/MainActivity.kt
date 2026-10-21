package io.github.mastin95.pliegue

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.ViewModelProvider
import io.github.mastin95.pliegue.ui.screens.PliegueApp
import io.github.mastin95.pliegue.ui.theme.PliegueTheme
import io.github.mastin95.pliegue.ui.web.LocalWebPanes
import io.github.mastin95.pliegue.web.WebPanes
import io.github.mastin95.pliegue.web.XWeb

class MainActivity : ComponentActivity() {
    private lateinit var web: WebPanes

    // Adjuntar una imagen desde el redactor de la web pasa por el selector de archivos del sistema.
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private val chooser = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        fileCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(r.resultCode, r.data))
        fileCallback = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        // Sin depurador a mano, un cierre inesperado se apunta en un archivo y el registro de la web lo
        // enseña la próxima vez que se abre la app.
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                java.io.File(filesDir, WebPanes.CRASH_FILE).writeText(
                    "${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.ROOT).format(java.util.Date())} en ${thread.name}: " +
                        android.util.Log.getStackTraceString(error).take(1500),
                )
            }
            previous?.uncaughtException(thread, error)
        }
        // Solo para las pruebas en el Android virtual: deja inspeccionar las webs con las herramientas de Chrome.
        if (intent?.getBooleanExtra("depurar_web", false) == true) WebView.setWebContentsDebuggingEnabled(true)
        web = WebPanes(this) { params, callback ->
            fileCallback?.onReceiveValue(null)
            fileCallback = callback
            runCatching { chooser.launch(params.createIntent()) }.onFailure { fileCallback = null }.isSuccess
        }
        // Solo para las pruebas en el Android virtual: las webs no salen a internet (X no deja entrar desde ahí).
        if (intent?.getBooleanExtra("red_simulada", false) == true) web.simulateNetwork()
        setContent {
            PliegueTheme {
                CompositionLocalProvider(LocalWebPanes provides web) {
                    PliegueApp(rememberScreenInfo(this))
                }
            }
        }
        openFromOutside(intent)
    }

    /** Con launchMode singleTask, un enlace que llega con la app abierta entra por aquí. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openFromOutside(intent)
    }

    /** Un enlace de X abierto con Pliegue, o algo compartido con la app: lo mismo que ve PliegueApp. */
    private fun openFromOutside(intent: Intent?) {
        val url = XWeb.linkFromIntent(intent?.action, intent?.dataString, intent?.getStringExtra(Intent.EXTRA_TEXT)) ?: return
        ViewModelProvider(this)[AppViewModel::class.java].openFromOutside(url)
    }

    override fun onDestroy() {
        web.destroyAll()
        super.onDestroy()
    }
}
