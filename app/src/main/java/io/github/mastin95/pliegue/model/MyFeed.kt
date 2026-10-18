package io.github.mastin95.pliegue.model

/** Un creador de Mi feed: cuándo lo añadiste (0 = estaba de inicio) y desde dónde. */
data class Creator(val addedAt: Long = 0L, val fromLabel: String = "")

enum class Mark(val badge: String) {
    NuevoParaTi("NUEVO PARA TI"),
    NuevoEnTuFeed("NUEVO EN TU FEED"),
    Sugerido("SUGERIDO"),
}

/** Resalte de un post: etiqueta, línea de contexto y el creador al que se refiere. */
data class PostMark(val mark: Mark, val context: String, val creatorId: String)

/** Un post que se está arrastrando sobre la bisagra y el feed del que sale. */
data class Drag(val postId: String, val fromLabel: String)

const val NEW_FOR_MS = 30 * 60 * 1000L

/**
 * Creadores parecidos a los que añadiste tú: comparten algún tema y aún no están en Mi feed.
 * Devuelve cada sugerencia con el creador añadido que la provoca.
 */
fun suggestions(users: Map<String, User>, members: Map<String, Creator>, me: String): Map<String, String> {
    val result = LinkedHashMap<String, String>()
    val addedByUser = members.filterValues { it.addedAt > 0 }.keys
    for (added in addedByUser) {
        val topics = users[added]?.topics ?: continue
        users.values
            .filter { it.id != me && it.id !in members && it.id !in result && it.topics.any { t -> t in topics } }
            .forEach { result[it.id] = added }
    }
    return result
}

/**
 * Qué resalte lleva un post en un feed. En Mi feed: los creadores recién añadidos y los sugeridos.
 * En los demás feeds: los sugeridos, para que veas gente nueva también en Para ti.
 */
fun markFor(
    post: Post,
    feed: FeedId,
    members: Map<String, Creator>,
    suggestions: Map<String, String>,
    users: Map<String, User>,
    seen: Set<String>,
    now: Long,
): PostMark? {
    val author = post.userId
    val because = suggestions[author]?.let { users[it]?.handle }
    if (feed == FeedId.MiFeed) {
        val c = members[author]
        if (c != null && c.addedAt > 0 && author !in seen && now - c.addedAt < NEW_FOR_MS) {
            return PostMark(Mark.NuevoEnTuFeed, "Añadido ${ago(now - c.addedAt)} desde ${c.fromLabel}", author)
        }
        if (because != null) return PostMark(Mark.Sugerido, "Parecido a $because · tócalo para añadirlo", author)
        return null
    }
    if (because != null && author !in seen) return PostMark(Mark.NuevoParaTi, "Porque añadiste a $because a Mi feed", author)
    return null
}

fun ago(ms: Long): String {
    val min = ms / 60_000
    return if (min < 1) "ahora" else "hace $min min"
}

/** Posts de Mi feed: primero los recién añadidos, luego los sugeridos y después el resto. */
fun myFeedPosts(all: Collection<Post>, members: Map<String, Creator>, suggestions: Map<String, String>, now: Long): List<Post> {
    fun rank(p: Post): Int {
        val c = members[p.userId]
        return when {
            c != null && c.addedAt > 0 && now - c.addedAt < NEW_FOR_MS -> 0
            p.userId in suggestions -> 1
            c != null -> 2
            else -> 3
        }
    }
    return all.filter { it.userId in members || it.userId in suggestions }.sortedBy(::rank)
}
