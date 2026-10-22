package io.github.mastin95.pliegue.web

import android.content.Context
import io.github.mastin95.pliegue.model.Note
import io.github.mastin95.pliegue.model.decodeList
import io.github.mastin95.pliegue.model.decodeNotes
import io.github.mastin95.pliegue.model.encodeList
import io.github.mastin95.pliegue.model.encodeNotes

/** Lo que debe sobrevivir a cerrar la app: modo elegido, creadores de Mi feed, notas, fijados. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("pliegue", Context.MODE_PRIVATE)

    var mode: String?
        get() = sp.getString("mode", null)
        set(v) = sp.edit().putString("mode", v).apply()

    var handles: List<String>
        get() = decodeList(sp.getString("handles", "") ?: "")
        set(v) = sp.edit().putString("handles", encodeList(v)).apply()

    var notes: Map<String, Note>
        get() = decodeNotes(sp.getString("notes", "") ?: "")
        set(v) = sp.edit().putString("notes", encodeNotes(v)).apply()

    var pinned: List<String>
        get() = decodeList(sp.getString("pinned", "") ?: "")
        set(v) = sp.edit().putString("pinned", encodeList(v)).apply()

    var threads: List<String>
        get() = decodeList(sp.getString("threads", "") ?: "")
        set(v) = sp.edit().putString("threads", encodeList(v)).apply()

    var mesa: List<String>
        get() = decodeList(sp.getString("mesa", "") ?: "")
        set(v) = sp.edit().putString("mesa", encodeList(v)).apply()

    var webColumns: List<String>
        get() = decodeList(sp.getString("webColumns", "") ?: "")
        set(v) = sp.edit().putString("webColumns", encodeList(v)).apply()

    var cleanup: List<String>
        get() = decodeList(sp.getString("cleanup", "") ?: "")
        set(v) = sp.edit().putString("cleanup", encodeList(v)).apply()

    /** Interfaz propia (los posts los pinta la app). Activada de inicio. */
    var nativeUi: Boolean
        get() = sp.getBoolean("nativeUi", true)
        set(v) = sp.edit().putBoolean("nativeUi", v).apply()

    /** La pantalla de vídeos sin sonido (se pone y se quita con el altavoz). */
    var videoMuted: Boolean
        get() = sp.getBoolean("videoMuted", false)
        set(v) = sp.edit().putBoolean("videoMuted", v).apply()

    /** Vídeos que se mueven solos en los feeds: "siempre", "wifi" (por defecto) o "nunca". */
    var autoplay: String
        get() = sp.getString("autoplay", "wifi") ?: "wifi"
        set(v) = sp.edit().putString("autoplay", v).apply()

    var dropHintSeen: Boolean
        get() = sp.getBoolean("dropHintSeen", false)
        set(v) = sp.edit().putBoolean("dropHintSeen", v).apply()
}
