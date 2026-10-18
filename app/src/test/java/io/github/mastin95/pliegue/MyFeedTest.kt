package io.github.mastin95.pliegue

import io.github.mastin95.pliegue.model.Creator
import io.github.mastin95.pliegue.model.FeedId
import io.github.mastin95.pliegue.model.Mark
import io.github.mastin95.pliegue.model.SampleData
import io.github.mastin95.pliegue.model.ago
import io.github.mastin95.pliegue.model.markFor
import io.github.mastin95.pliegue.model.myFeedPosts
import io.github.mastin95.pliegue.model.suggestions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MyFeedTest {

    private val users = SampleData.users
    private val me = SampleData.me.id
    private val now = 1_000_000L
    private val initial = SampleData.initialMyFeed.associateWith { Creator() }

    @Test
    fun sinCreadoresAnadidosPorTiNoHaySugerencias() {
        assertTrue(suggestions(users, initial, me).isEmpty())
    }

    @Test
    fun anadirALuciaSugiereGenteDeDisenoYPlegables() {
        val members = initial + ("lucia" to Creator(now, "Para ti"))
        val s = suggestions(users, members, me)
        assertEquals("lucia", s["ikerp"])
        assertEquals("lucia", s["elenao"])
        assertEquals("lucia", s["plegables_es"])
        assertFalse("ya está en Mi feed", "javit" in s)
        assertFalse("no se sugiere a uno mismo", me in s)
        assertFalse("no comparte temas", "cocinafacil" in s)
    }

    @Test
    fun resaltesEnMiFeedYEnParaTi() {
        val members = initial + ("lucia" to Creator(now - 5 * 60_000, "Para ti"))
        val s = suggestions(users, members, me)
        val lucia = SampleData.posts.getValue("p1")
        val iker = SampleData.posts.getValue("p7")
        val javi = SampleData.posts.getValue("p9")

        val enMiFeed = markFor(lucia, FeedId.MiFeed, members, s, users, emptySet(), now)!!
        assertEquals(Mark.NuevoEnTuFeed, enMiFeed.mark)
        assertEquals("Añadido hace 5 min desde Para ti", enMiFeed.context)

        val sugerido = markFor(iker, FeedId.MiFeed, members, s, users, emptySet(), now)!!
        assertEquals(Mark.Sugerido, sugerido.mark)
        assertEquals("Parecido a @lucia · tócalo para añadirlo", sugerido.context)

        val enParaTi = markFor(iker, FeedId.ParaTi, members, s, users, emptySet(), now)!!
        assertEquals(Mark.NuevoParaTi, enParaTi.mark)
        assertEquals("Porque añadiste a @lucia a Mi feed", enParaTi.context)

        assertNull("los de siempre no se resaltan", markFor(javi, FeedId.MiFeed, members, s, users, emptySet(), now))
        assertNull("una vez visto deja de ser nuevo", markFor(lucia, FeedId.MiFeed, members, s, users, setOf("lucia"), now))
    }

    @Test
    fun miFeedOrdenaNuevosSugeridosYResto() {
        val members = initial + ("lucia" to Creator(now, "Para ti"))
        val s = suggestions(users, members, me)
        val list = myFeedPosts(SampleData.posts.values, members, s, now)
        assertEquals("lucia", list.first().userId)
        val firstSuggested = list.indexOfFirst { it.userId in s }
        val firstOld = list.indexOfFirst { it.userId in SampleData.initialMyFeed }
        assertTrue(firstSuggested < firstOld)
        assertTrue(list.none { it.userId == "cocinafacil" })
    }

    @Test
    fun haceCuanto() {
        assertEquals("ahora", ago(30_000))
        assertEquals("hace 5 min", ago(5 * 60_000))
    }
}
