package io.github.mastin95.pliegue.web

import io.github.mastin95.pliegue.model.XTimeline
import java.io.File
import java.util.concurrent.Executor

/**
 * La última carga de Inicio (Para ti y Siguiendo) guardada en disco, para pintarla nada más abrir la
 * app mientras la web de X escondida se descarga la de verdad (que la sustituye en cuanto llega).
 * Se guarda el JSON tal cual llegó; al leerlo se vuelve a entender con XParse.
 */
class XCache(private val dir: File, private val io: Executor, private val main: (Runnable) -> Unit) {
    class Entry(val timeline: XTimeline, val time: Long)

    private val memory = HashMap<String, Entry>()
    private val missing = HashSet<String>()

    /** Una carga limpia recién llegada (sin cursor): se queda en memoria y, aparte, en disco. */
    fun keep(name: String, body: String, timeline: XTimeline, time: Long) {
        if (name !in NAMES) return
        memory[name] = Entry(timeline, time)
        missing.remove(name)
        io.execute {
            runCatching {
                dir.mkdirs()
                File(dir, "$name.json").writeText(body)
            }
        }
    }

    /**
     * Lo último guardado de `name`: al momento si está en memoria, si no tras leer el disco (la
     * respuesta llega en el hilo principal). Si no hay nada, no se llama.
     */
    fun get(name: String, cb: (Entry) -> Unit) {
        if (name !in NAMES) return
        memory[name]?.let {
            cb(it)
            return
        }
        if (name in missing) return
        io.execute {
            val f = File(dir, "$name.json")
            val entry = runCatching { if (f.isFile) XParse.timeline(f.readText()) else null }.getOrNull()?.let { Entry(it, f.lastModified()) }
            main(Runnable {
                if (entry == null) {
                    missing.add(name)
                } else {
                    // Si mientras se leía llegó una carga de verdad, manda la de verdad.
                    cb(memory[name] ?: entry.also { memory[name] = it })
                }
            })
        }
    }

    /** Al cerrar sesión o borrar datos: fuera todo (lo guardado era de esa cuenta). */
    fun clear() {
        memory.clear()
        missing.clear()
        io.execute { runCatching { dir.listFiles()?.forEach { it.delete() } } }
    }

    companion object {
        /** Las consultas que se guardan: las dos pestañas de Inicio. */
        val NAMES = setOf("HomeTimeline", "HomeLatestTimeline")
    }
}
