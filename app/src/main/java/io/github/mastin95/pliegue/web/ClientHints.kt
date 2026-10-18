package io.github.mastin95.pliegue.web

import android.os.Build
import android.webkit.WebSettings
import androidx.webkit.UserAgentMetadata
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature

/**
 * Las "client hints" (cabeceras Sec-CH-UA y `navigator.userAgentData`) delatan la WebView aunque el
 * agente de usuario diga Chrome: por defecto llevan la marca "Android WebView". Google las compara
 * con el agente de usuario y, si no cuadran, corta el inicio de sesión con un "400 malformed" en
 * el paso /restart. Aquí se ponen las de Chrome, con la misma versión que el motor, y se deja de
 * mandar la cabecera X-Requested-With, que lleva el nombre del paquete de la app.
 */
object ClientHints {
    /** Devuelve una descripción corta de cómo quedó, para el registro. */
    fun apply(settings: WebSettings, defaultUa: String, desktop: Boolean): String {
        val full = XWeb.chromeVersion(defaultUa) ?: "141.0.0.0"
        val major = full.substringBefore('.')
        val hints = runCatching {
            if (!WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)) return@runCatching false
            fun brand(name: String, maj: String, ver: String) =
                UserAgentMetadata.BrandVersion.Builder().setBrand(name).setMajorVersion(maj).setFullVersion(ver).build()
            val metadata = UserAgentMetadata.Builder()
                .setBrandVersionList(listOf(brand("Chromium", major, full), brand("Google Chrome", major, full), brand("Not=A?Brand", "24", "24.0.0.0")))
                .setFullVersion(full)
                .setPlatform(if (desktop) "Windows" else "Android")
                .setPlatformVersion(if (desktop) "15.0.0" else androidVersion())
                .setArchitecture(if (desktop) "x86" else "")
                .setModel(if (desktop) "" else Build.MODEL.orEmpty())
                .setMobile(!desktop)
                .setBitness(64)
                .setWow64(false)
                .build()
            WebSettingsCompat.setUserAgentMetadata(settings, metadata)
            true
        }.getOrDefault(false)
        val header = runCatching {
            if (!WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) return@runCatching false
            WebSettingsCompat.setRequestedWithHeaderOriginAllowList(settings, emptySet())
            true
        }.getOrDefault(false)
        return "Chrome $major" + (if (desktop) " de escritorio" else "") +
            (if (hints) ", client hints de Chrome" else ", client hints de WebView (esta WebView no deja cambiarlas)") +
            (if (header) ", sin X-Requested-With" else ", con X-Requested-With")
    }

    /** "16.0.0": el formato que usa Chrome en Sec-CH-UA-Platform-Version. */
    private fun androidVersion(): String {
        val parts = Build.VERSION.RELEASE.orEmpty().split('.').filter { it.all(Char::isDigit) && it.isNotEmpty() }
        return (parts + listOf("0", "0", "0")).take(3).joinToString(".")
    }
}
