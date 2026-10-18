package io.github.mastin95.pliegue.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {

    @Test
    fun recuentosComoEnX() {
        assertEquals("980", count(980))
        assertEquals("1 mil", count(1000))
        assertEquals("1,2 mil", count(1200))
        assertEquals("34 mil", count(34000))
        assertEquals("1,5 M", count(1_500_000))
    }

    @Test
    fun inicialesDelNombre() {
        assertEquals("LR", initials("Lucía R."))
        assertEquals("CF", initials("Cocina Fácil"))
    }

    @Test
    fun textoOscuroSobreAmarillo() {
        assertEquals(true, isLight(0xFFFFD400))
        assertEquals(false, isLight(0xFF1D9BF0))
    }
}
