package io.github.mastin95.pliegue

import io.github.mastin95.pliegue.model.ColumnKind
import io.github.mastin95.pliegue.model.columnsPerHalf
import io.github.mastin95.pliegue.model.moved
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ColumnsTest {

    private val cols = listOf(ColumnKind.Notificaciones, ColumnKind.Diseno, ColumnKind.Tendencias, ColumnKind.Busqueda)

    @Test
    fun moverALaDerechaIntercambiaConLaSiguiente() {
        assertEquals(
            listOf(ColumnKind.Diseno, ColumnKind.Notificaciones, ColumnKind.Tendencias, ColumnKind.Busqueda),
            cols.moved(0, 1),
        )
    }

    @Test
    fun moverALaIzquierdaDesdeElFinal() {
        assertEquals(
            listOf(ColumnKind.Notificaciones, ColumnKind.Diseno, ColumnKind.Busqueda, ColumnKind.Tendencias),
            cols.moved(3, -1),
        )
    }

    @Test
    fun noSeSaleDeLaLista() {
        assertSame(cols, cols.moved(0, -1))
        assertSame(cols, cols.moved(3, 1))
        assertSame(cols, cols.moved(9, 1))
    }

    @Test
    fun columnasPorMitadSegunElAncho() {
        assertEquals(1, columnsPerHalf(345f))
        assertEquals(1, columnsPerHalf(420f))
        assertEquals(2, columnsPerHalf(480f))
        assertEquals(2, columnsPerHalf(640f))
    }
}
