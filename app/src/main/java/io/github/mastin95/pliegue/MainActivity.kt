package io.github.mastin95.pliegue

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import io.github.mastin95.pliegue.ui.screens.PliegueApp
import io.github.mastin95.pliegue.ui.theme.PliegueTheme
import io.github.mastin95.pliegue.ui.web.LocalWebPanes
import io.github.mastin95.pliegue.web.WebPanes

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
        web = WebPanes(this) { params, callback ->
            fileCallback?.onReceiveValue(null)
            fileCallback = callback
            runCatching { chooser.launch(params.createIntent()) }.onFailure { fileCallback = null }.isSuccess
        }
        setContent {
            PliegueTheme {
                CompositionLocalProvider(LocalWebPanes provides web) {
                    PliegueApp(rememberScreenInfo(this))
                }
            }
        }
    }

    override fun onDestroy() {
        web.destroyAll()
        super.onDestroy()
    }
}
