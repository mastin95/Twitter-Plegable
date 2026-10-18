package io.github.mastin95.pliegue

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PostureTest {

    @Test
    fun pantallaExteriorEstrechaEsPlegado() {
        assertEquals(Posture.Plegado, computeScreen(null, 344).posture)
    }

    @Test
    fun pantallaInteriorSinBisagraEsAbiertoPorAncho() {
        val s = computeScreen(null, 690)
        assertEquals(Posture.Abierto, s.posture)
        assertNull(s.firstPane)
    }

    @Test
    fun bisagraVerticalQueSeparaCortaLosPaneles() {
        val s = computeScreen(Fold(vertical = true, halfOpened = false, separating = true, start = 420f, size = 12f), 852)
        assertEquals(Posture.Abierto, s.posture)
        assertEquals(420.dp, s.firstPane)
        assertEquals(12.dp, s.hinge)
    }

    @Test
    fun bisagraHorizontalMedioDobladaEsSobremesa() {
        val s = computeScreen(Fold(vertical = false, halfOpened = true, separating = true, start = 400f, size = 0f), 840)
        assertEquals(Posture.Sobremesa, s.posture)
        assertEquals(400.dp, s.firstPane)
    }

    @Test
    fun bisagraPlanaQueNoSeparaUsaElAncho() {
        val s = computeScreen(Fold(vertical = true, halfOpened = false, separating = false, start = 345f, size = 0f), 690)
        assertEquals(Posture.Abierto, s.posture)
        assertNull(s.firstPane)
    }
}
