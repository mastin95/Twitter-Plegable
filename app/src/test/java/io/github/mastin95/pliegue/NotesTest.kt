package io.github.mastin95.pliegue

import io.github.mastin95.pliegue.model.Note
import io.github.mastin95.pliegue.model.decodeList
import io.github.mastin95.pliegue.model.decodeNotes
import io.github.mastin95.pliegue.model.encodeList
import io.github.mastin95.pliegue.model.encodeNotes
import io.github.mastin95.pliegue.model.tagChips
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotesTest {
    @Test
    fun `las notas van y vuelven enteras`() {
        val notes = mapOf(
            "https://x.com/a/status/1" to Note("usar el pliegue\ncomo separador", "ideas, plegables", "Plegables", "mañana"),
            "https://x.com/b/status/2" to Note(text = "solo texto"),
        )
        assertEquals(notes, decodeNotes(encodeNotes(notes)))
        assertTrue(decodeNotes("").isEmpty())
    }

    @Test
    fun `las listas ignoran líneas vacías`() {
        assertEquals(listOf("javit", "anam"), decodeList(encodeList(listOf("javit", "", " anam "))))
        assertTrue(decodeList("").isEmpty())
    }

    @Test
    fun `las etiquetas se normalizan con almohadilla`() {
        assertEquals(listOf("#ideas", "#plegables"), tagChips("#ideas, plegables"))
        assertTrue(tagChips("").isEmpty())
        assertTrue(Note().isEmpty)
    }
}
