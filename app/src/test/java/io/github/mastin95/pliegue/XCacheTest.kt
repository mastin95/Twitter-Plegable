package io.github.mastin95.pliegue

import io.github.mastin95.pliegue.web.XCache
import io.github.mastin95.pliegue.web.XParse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.Executor

class XCacheTest {
    private fun fixture(name: String): String = javaClass.getResourceAsStream("/x/$name.json")!!.bufferedReader().readText()

    /** En las pruebas todo va en el mismo hilo, al momento. */
    private val direct = Executor { it.run() }

    @Test
    fun `la última carga de Inicio se guarda en disco y vuelve al abrir la app`() {
        val dir = File(System.getProperty("java.io.tmpdir"), "pliegue-cache-" + System.nanoTime())
        try {
            val body = fixture("home")
            val timeline = XParse.timeline(body)!!
            val first = XCache(dir, direct) { it.run() }
            first.keep("HomeTimeline", body, timeline, 1000L)
            first.keep("SearchTimeline", body, timeline, 1000L) // no es de Inicio: no se guarda
            assertTrue(File(dir, "HomeTimeline.json").isFile)
            assertFalse(File(dir, "SearchTimeline.json").exists())
            var inMemory: XCache.Entry? = null
            first.get("HomeTimeline") { inMemory = it }
            assertEquals(1000L, inMemory!!.time)

            // Otra instancia (la app recién abierta) lo lee del disco y lo entiende igual.
            val second = XCache(dir, direct) { it.run() }
            var fromDisk: XCache.Entry? = null
            second.get("HomeTimeline") { fromDisk = it }
            assertEquals(timeline.tweets.map { it.id }, fromDisk!!.timeline.tweets.map { it.id })
            assertEquals("CURSOR-BOTTOM-1", fromDisk!!.timeline.cursorBottom)
            var none = false
            second.get("HomeLatestTimeline") { none = true }
            assertFalse(none)

            // Al borrar (cerrar sesión) no queda nada, ni en memoria ni en disco.
            second.clear()
            assertFalse(File(dir, "HomeTimeline.json").exists())
            var after: XCache.Entry? = null
            second.get("HomeTimeline") { after = it }
            assertNull(after)
        } finally {
            dir.deleteRecursively()
        }
    }
}
