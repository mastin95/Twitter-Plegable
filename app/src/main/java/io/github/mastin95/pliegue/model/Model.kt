package io.github.mastin95.pliegue.model

enum class FeedId(val label: String) {
    ParaTi("Para ti"),
    Siguiendo("Siguiendo"),
    Diseno("Diseño"),
    Plegables("Plegables"),
}

data class User(
    val id: String,
    val name: String,
    val handle: String,
    /** Color del avatar en ARGB. */
    val color: Long,
    val verified: Boolean = false,
    val bio: String = "",
)

enum class MediaKind { Amanecer, Boceto, Plegable, Paleta, Ciudad, Comida }

data class Post(
    val id: String,
    val userId: String,
    val text: String,
    val time: String,
    val date: String = "",
    val replies: Int = 0,
    val reposts: Int = 0,
    val likes: Int = 0,
    val views: Int = 0,
    val bookmarks: Int = 0,
    val media: MediaKind? = null,
    val repostedBy: String? = null,
    val replyTo: String? = null,
)

/** Post abierto en una mitad de la pantalla, y el feed desde el que se abrió. */
data class OpenPost(val id: String, val from: FeedId)

/** Lo que muestra cada mitad con el móvil abierto: su feed y, encima, quizá un post. */
data class PanelState(val feedId: FeedId, val post: OpenPost? = null)
