package io.github.mastin95.pliegue.model

enum class FeedId(val label: String) {
    ParaTi("Para ti"),
    Siguiendo("Siguiendo"),
    Diseno("Diseño"),
    Plegables("Plegables"),
    MiFeed("Mi feed"),
}

data class User(
    val id: String,
    val name: String,
    val handle: String,
    /** Color del avatar en ARGB. */
    val color: Long,
    val verified: Boolean = false,
    val bio: String = "",
    /** Temas de los que habla; sirven para sugerir creadores parecidos en Mi feed. */
    val topics: Set<String> = emptySet(),
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

/** Post abierto en una mitad de la pantalla, y el feed o columna desde el que se abrió. */
data class OpenPost(val id: String, val fromLabel: String)

/** Lo que muestra cada mitad con el móvil abierto: su feed y, encima, quizá un post. */
data class PanelState(val feedId: FeedId, val post: OpenPost? = null)

/** Cómo se reparte la pantalla interior con el móvil abierto. Mesa y Libro solo existen con X real. */
enum class OpenMode(val label: String, val short: String) {
    Doble("Doble feed", "Doble"),
    Uno("Un solo feed", "Uno"),
    Columnas("Columnas", "Columnas"),
    Mesa("La mesa", "Mesa"),
    Libro("Libro", "Libro"),
}

/** Columnas al estilo de X Pro. */
enum class ColumnKind(val title: String) {
    Notificaciones("Notificaciones"),
    Diseno("Lista: Diseño"),
    Tendencias("Tendencias"),
    Busqueda("Búsqueda: modo sobremesa"),
    MiFeed("Mi feed"),
    ParaTi("Para ti"),
    Siguiendo("Siguiendo"),
    Plegables("Lista: Plegables"),
}

data class Mention(val userId: String, val text: String, val time: String)

data class Trend(val category: String, val tag: String, val posts: Int)

/** Mueve la columna `index` `delta` posiciones, sin salirse de la lista. */
fun List<ColumnKind>.moved(index: Int, delta: Int): List<ColumnKind> {
    if (index !in indices) return this
    val to = (index + delta).coerceIn(0, lastIndex)
    if (to == index) return this
    return toMutableList().apply { add(to, removeAt(index)) }
}

/** Cuántas columnas caben en cada mitad: dos si cada una tiene al menos 240 dp. */
fun columnsPerHalf(halfWidthDp: Float): Int = if (halfWidthDp >= 480f) 2 else 1
