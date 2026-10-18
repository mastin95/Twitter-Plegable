package io.github.mastin95.pliegue.model

// Contenido de ejemplo. La interfaz copia la de X; los posts son inventados.
object SampleData {

    val me = User("yo", "Martin", "@martin", 0xFFC2410C)

    val users: Map<String, User> = listOf(
        me,
        User("lucia", "Lucía R.", "@lucia", 0xFF1D9BF0, verified = true, bio = "Diseño producto para pantallas raras. Hilos sobre plegables."),
        User("dgarcia", "Diego G.", "@dgarcia", 0xFF00BA7C, bio = "Música, café y Valencia."),
        User("nerea_a", "Nerea A.", "@nerea_a", 0xFF7856FF, bio = "Preguntas serias a horas raras."),
        User("pablom", "Pablo M.", "@pablom", 0xFFFFD400, bio = "Fotografía de paisaje en Almería."),
        User("saracm", "Sara C.", "@saracm", 0xFFF91880, bio = "Desarrollo web."),
        User("rubenb", "Rubén B.", "@rubenb", 0xFF536471),
        User("ikerp", "Iker P.", "@ikerp", 0xFFF91880, verified = true, bio = "Tipógrafo. Hago letras para pantallas."),
        User("martav", "Marta V.", "@martav", 0xFF7856FF, bio = "UX en apps de banca."),
        User("javit", "Javi T.", "@javit", 0xFF00BA7C),
        User("anam", "Ana M.", "@anam", 0xFFFFD400, bio = "Accesibilidad y color."),
        User("elenao", "Elena O.", "@elenao", 0xFF00BA7C, bio = "Plantillas y recursos gratis."),
        User("tecnohoy", "Tecnología Hoy", "@tecnohoy", 0xFF1D9BF0, verified = true),
        User("plegables_es", "Plegables ES", "@plegables_es", 0xFF7856FF, bio = "Trucos para móviles plegables."),
        User("carlaf", "Carla F.", "@carlaf", 0xFFF91880),
        User("hugol", "Hugo L.", "@hugol", 0xFF00BA7C),
        User("cocinafacil", "Cocina Fácil", "@cocinafacil", 0xFFFFD400, verified = true),
    ).associateBy { it.id }

    private const val TODAY = "2:30 p. m. · 18 oct. 2026"

    val posts: Map<String, Post> = listOf(
        Post("p1", "lucia", "Hilo: lo que aprendí diseñando para pantallas que se doblan. Spoiler: la bisagra es una decisión de diseño, no un estorbo.", "2h", "4:12 p. m. · 18 oct. 2026", 48, 120, 1200, 34000, 210, MediaKind.Plegable),
        Post("p2", "dgarcia", "Esta tarde Space sobre música indie de Valencia. Traed recomendaciones, yo pongo el café.", "35m", TODAY, 6, 14, 88, 2100, 11),
        Post("p3", "nerea_a", "Pregunta seria: ¿cuántas pestañas tenéis abiertas ahora mismo?", "1h", TODAY, 230, 12, 410, 18000, 51),
        Post("p4", "pablom", "Fotos del amanecer en el Cabo de Gata. Hoy ha merecido la pena madrugar.", "3h", "8:12 a. m. · 18 oct. 2026", 19, 40, 760, 9000, 35, MediaKind.Amanecer),
        Post("p5", "saracm", "Nueva versión de mi web, ahora con modo oscuro. Me ha costado más elegir el gris que programarlo.", "4h", TODAY, 3, 5, 61, 1400, 8),
        Post("p6", "rubenb", "Recomendadme podcasts para el coche. Nada de true crime, que luego no duermo.", "5h", TODAY, 52, 2, 30, 3000, 4),
        Post("p7", "ikerp", "Nueva tipografía libre para interfaces, con una versión para pantallas pequeñas. Enlace en la respuesta.", "2h", TODAY, 11, 33, 290, 6000, 36, MediaKind.Boceto),
        Post("p8", "martav", "Antes y después de un rediseño de onboarding: quitamos tres pantallas y nadie las ha echado de menos.", "4h", TODAY, 9, 21, 180, 4000, 22),
        Post("p9", "javit", "Mis atajos favoritos en Figma: Mayús+A para auto layout, Ctrl+Alt+G para agrupar en un marco y R para rectángulos.", "6h", TODAY, 4, 18, 95, 2000, 12),
        Post("p10", "anam", "Paletas accesibles: así compruebo el contraste antes de pasar nada a desarrollo.", "7h", TODAY, 7, 26, 140, 3000, 17, MediaKind.Paleta),
        Post("p11", "elenao", "Plantilla gratis para diseñar apps de plegables: marcos para la pantalla exterior, la interior y el modo sobremesa.", "5h", TODAY, 4, 30, 220, 5100, 27),
        Post("p12", "tecnohoy", "¿Usaríais un plegable como único móvil? Contadnos por qué sí o por qué no.", "1h", TODAY, 96, 18, 240, 21000, 30),
        Post("p13", "plegables_es", "Truco: en modo sobremesa, el vídeo arriba y los comentarios abajo. Una vez lo pruebas no vuelves.", "3h", TODAY, 21, 64, 530, 12000, 66, repostedBy = "lucia"),
        Post("p14", "carlaf", "Hoy he aprendido que «tipografía variable» no es una tipografía que cambia de humor.", "20m", TODAY, 15, 22, 340, 5600, 42),
        Post("p15", "hugol", "Valencia desde el Miguelete esta mañana.", "50m", TODAY, 8, 31, 620, 8800, 77, MediaKind.Ciudad),
        Post("p16", "cocinafacil", "Arroz al horno en 40 minutos. La receta completa, en el hilo.", "2h", TODAY, 44, 150, 2100, 41000, 262, MediaKind.Comida),
        Post("p18", "nerea_a", "Lo mejor de trabajar desde casa: el gato. Lo peor: el gato.", "9h", TODAY, 12, 40, 980, 15000, 122),
        Post("p19", "lucia", "Para quien preguntaba: sí, compartiré los archivos del hilo. Dadme unos días.", "20h", TODAY, 9, 3, 120, 4000, 15),
        Post("p20", "anam", "Recordatorio amable: el texto gris claro sobre blanco no es minimalismo, es ilegible.", "9h", TODAY, 30, 210, 1800, 26000, 225),
    ).associateBy { it.id }

    val feeds: Map<FeedId, List<String>> = mapOf(
        FeedId.ParaTi to listOf("p1", "p16", "p13", "p4", "p3", "p7", "p20", "p15", "p11", "p18", "p8", "p14"),
        FeedId.Siguiendo to listOf("p14", "p2", "p15", "p3", "p4", "p5", "p6", "p18", "p19"),
        FeedId.Diseno to listOf("p1", "p7", "p8", "p9", "p10", "p11", "p20", "p5", "p19"),
        FeedId.Plegables to listOf("p13", "p12", "p1", "p11", "p19"),
    )

    val initialFollowing = setOf("pablom", "dgarcia", "nerea_a", "saracm", "rubenb", "carlaf", "hugol", "lucia")

    private data class R(val userId: String, val text: String, val time: String, val likes: Int)

    private val replySeeds: Map<String, List<R>> = mapOf(
        "p1" to listOf(
            R("javit", "Lo de que cada panel termine antes del pliegue debería ser ley.", "1h", 34),
            R("anam", "¿Y el modo sobremesa para vídeos? Es lo que más uso.", "58m", 12),
            R("elenao", "Justo estoy con esto. ¿Cómo resolviste las tablas en la pantalla exterior?", "30m", 8),
            R("lucia", "Las parto en tarjetas, una fila por tarjeta. Lo cuento más abajo en el hilo.", "25m", 21),
            R("rubenb", "Guardado para leerlo con calma esta noche.", "10m", 2),
        ),
        "p3" to listOf(
            R("rubenb", "37, y no pienso leer ninguna.", "58m", 120),
            R("saracm", "Solo 4. Soy una persona ordenada (es mentira).", "45m", 64),
            R("carlaf", "Tengo una pestaña con la lista de pestañas que tengo que cerrar.", "30m", 210),
            R("javit", "Prefiero no contestar a eso.", "12m", 9),
        ),
        "p4" to listOf(
            R("ikerp", "¿Desde qué playa? Esos colores son increíbles.", "2h", 18),
            R("pablom", "Mónsul, a las 7:40. Merece la pena el madrugón.", "2h", 25),
            R("anam", "Me lo apunto para el puente de noviembre.", "1h", 4),
        ),
        "p2" to listOf(
            R("hugol", "Me apunto. ¿A qué hora empieza?", "30m", 3),
            R("dgarcia", "A las 19:00. Os dejo el enlace aquí mismo.", "28m", 6),
        ),
        "p13" to listOf(
            R("nerea_a", "Confirmo, ya no veo vídeos de otra manera.", "2h", 40),
            R("elenao", "Y el teclado abajo para escribir es comodísimo.", "1h", 15),
        ),
        "p7" to listOf(
            R("ikerp", "Descarga y licencia: libre para uso comercial, también en apps.", "2h", 30),
            R("martav", "La versión pequeña se lee genial a 11 px.", "1h", 7),
        ),
    )

    private val genericReplies = listOf(
        R("hugol", "Totalmente de acuerdo.", "40m", 3),
        R("carlaf", "Esto hay que compartirlo.", "25m", 5),
        R("saracm", "¿Hay más información sobre esto?", "15m", 1),
    )

    fun replies(postId: String): List<Post> {
        if (postId.startsWith("yo-")) return emptyList()
        val seeds = replySeeds[postId] ?: genericReplies.take(2 + postId.length % 2)
        return seeds.mapIndexed { i, r ->
            Post(
                id = "$postId-r$i",
                userId = r.userId,
                text = r.text,
                time = r.time,
                reposts = r.likes / 10,
                likes = r.likes,
                views = r.likes * 40 + 120,
                replyTo = postId,
            )
        }
    }
}
