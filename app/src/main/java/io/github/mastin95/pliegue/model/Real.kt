package io.github.mastin95.pliegue.model

import io.github.mastin95.pliegue.web.XWeb

/** Con qué arranca la app: aún sin elegir, la demo con posts de ejemplo, o X de verdad con tu sesión. */
enum class AppMode { Elegir, Demo, Real }

/** Limpieza de la web de X que elige el usuario en Ajustes; vale para todas las webs de la app. */
data class Cleanup(val ads: Boolean = false, val suggestions: Boolean = false, val premium: Boolean = false, val following: Boolean = false) {
    fun toNames(): List<String> = listOfNotNull("ads".takeIf { ads }, "suggestions".takeIf { suggestions }, "premium".takeIf { premium }, "following".takeIf { following })

    companion object {
        fun fromNames(names: Collection<String>) = Cleanup("ads" in names, "suggestions" in names, "premium" in names, "following" in names)
    }
}

/** Lo que puede enseñar un panel de X real. `url` nula = se calcula (Mi feed, Perfil). */
enum class Source(val label: String, val url: String?) {
    Inicio("Inicio", XWeb.HOME),
    MiFeed("Mi feed", null),
    Notificaciones("Notificaciones", "https://x.com/notifications"),
    Guardados("Guardados", "https://x.com/i/bookmarks"),
    Explorar("Explorar", "https://x.com/explore"),
    Mensajes("Mensajes", "https://x.com/messages"),
    Perfil("Perfil", null),
    Fijados("Fijados", null),
}

/** Una mitad con X real: su fuente y, encima, quizá un post abierto desde la otra mitad. */
data class WebPanel(val source: Source, val openUrl: String? = null, val fromLabel: String = "")

/** Lo que añades tú a un post y solo ves tú. */
data class Note(val text: String = "", val tags: String = "", val folder: String = "", val remind: String = "") {
    val isEmpty: Boolean get() = text.isBlank() && tags.isBlank() && folder.isBlank() && remind.isBlank()
}

private const val FIELD = '\u001F'
private const val RECORD = '\u001E'

// Guardado en texto plano con separadores de control; nada de lo que escribes los contiene.
fun encodeNotes(notes: Map<String, Note>): String =
    notes.entries.joinToString(RECORD.toString()) { (url, n) ->
        listOf(url, n.text, n.tags, n.folder, n.remind).joinToString(FIELD.toString()) { it.replace(FIELD, ' ').replace(RECORD, ' ') }
    }

fun decodeNotes(s: String): Map<String, Note> {
    if (s.isEmpty()) return emptyMap()
    return s.split(RECORD).mapNotNull { rec ->
        val f = rec.split(FIELD)
        if (f.size < 5 || f[0].isEmpty()) null else f[0] to Note(f[1], f[2], f[3], f[4])
    }.toMap()
}

fun encodeList(items: List<String>): String = items.joinToString("\n") { it.replace('\n', ' ') }

fun decodeList(s: String): List<String> = s.split('\n').map { it.trim() }.filter { it.isNotEmpty() }

/** Etiquetas sueltas de una nota: "#ideas, plegables" → ["#ideas", "#plegables"]. */
fun tagChips(tags: String): List<String> =
    tags.split(',', ' ', ';').map { it.trim().removePrefix("#") }.filter { it.isNotEmpty() }.map { "#$it" }
