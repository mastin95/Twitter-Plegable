package io.github.mastin95.pliegue

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import io.github.mastin95.pliegue.model.ColumnKind
import io.github.mastin95.pliegue.model.Creator
import io.github.mastin95.pliegue.model.Drag
import io.github.mastin95.pliegue.model.FeedId
import io.github.mastin95.pliegue.model.OpenMode
import io.github.mastin95.pliegue.model.OpenPost
import io.github.mastin95.pliegue.model.PanelState
import io.github.mastin95.pliegue.model.Post
import io.github.mastin95.pliegue.model.PostMark
import io.github.mastin95.pliegue.model.SampleData
import io.github.mastin95.pliegue.model.markFor
import io.github.mastin95.pliegue.model.moved
import io.github.mastin95.pliegue.model.myFeedPosts
import io.github.mastin95.pliegue.model.suggestions
import io.github.mastin95.pliegue.ui.nowLabel

data class Toast(val text: String, val id: Long)

class AppViewModel : ViewModel() {

    /** Mitad izquierda [0] y derecha [1] con el móvil abierto. Plegado usa la [0]. */
    var panels by mutableStateOf(listOf(PanelState(FeedId.ParaTi), PanelState(FeedId.MiFeed)))
        private set

    /** Abierto: doble feed, un feed con el post al lado, o columnas. */
    var mode by mutableStateOf(OpenMode.Doble)
        private set

    var columns by mutableStateOf(listOf(ColumnKind.Notificaciones, ColumnKind.Diseno, ColumnKind.Tendencias, ColumnKind.Busqueda))
        private set

    /** Post abierto desde una columna: se muestra en la mitad `side` (0 izquierda, 1 derecha). */
    var columnPost by mutableStateOf<Pair<Int, OpenPost>?>(null)
        private set

    val liked = mutableStateMapOf<String, Boolean>()
    val reposted = mutableStateMapOf<String, Boolean>()
    val saved = mutableStateMapOf<String, Boolean>()
    val following = mutableStateMapOf<String, Boolean>().apply {
        SampleData.initialFollowing.forEach { put(it, true) }
    }
    val myPosts = mutableStateListOf<Post>()
    val myReplies = mutableStateMapOf<String, List<Post>>()

    /** Creadores de Mi feed. */
    val myFeed = mutableStateMapOf<String, Creator>().apply {
        SampleData.initialMyFeed.forEach { put(it, Creator()) }
    }

    /** Creadores cuyo resalte de "nuevo" ya viste: abriste uno de sus posts. */
    val seenCreators = mutableStateMapOf<String, Boolean>()

    /** Post que se está arrastrando hacia Mi feed, si hay alguno. */
    var dragging by mutableStateOf<Drag?>(null)

    /** Se oculta la pista de arrastrar en cuanto añades a alguien así. */
    var dropHintSeen by mutableStateOf(false)
        private set

    var composing by mutableStateOf(false)
    var toast by mutableStateOf<Toast?>(null)
        private set

    private var seq = 0L

    // El scroll de cada feed vive aquí para no perderlo al plegar o desplegar.
    private val listStates = mutableMapOf<String, LazyListState>()

    fun listState(panel: Int, feed: FeedId): LazyListState =
        listStates.getOrPut("$panel:${feed.name}") { LazyListState() }

    fun columnState(kind: ColumnKind): LazyListState =
        listStates.getOrPut("col:${kind.name}") { LazyListState() }

    fun post(id: String): Post? = myPosts.firstOrNull { it.id == id } ?: SampleData.posts[id]

    fun feedPosts(feed: FeedId): List<Post> {
        if (feed == FeedId.MiFeed) return myFeedPosts(SampleData.posts.values, myFeed, suggestions(), System.currentTimeMillis())
        val mine = if (feed == FeedId.ParaTi || feed == FeedId.Siguiendo) myPosts.toList() else emptyList()
        return mine + SampleData.feeds.getValue(feed).mapNotNull { SampleData.posts[it] }
    }

    fun suggestions(): Map<String, String> = suggestions(SampleData.users, myFeed, SampleData.me.id)

    fun mark(post: Post, feed: FeedId): PostMark? =
        markFor(post, feed, myFeed, suggestions(), SampleData.users, seenCreators.keys, System.currentTimeMillis())

    /** Añade al autor de un post a Mi feed; desde ahí salen sugerencias de creadores parecidos. */
    fun addCreator(userId: String, fromLabel: String) {
        if (userId == SampleData.me.id || userId in myFeed) return
        myFeed[userId] = Creator(System.currentTimeMillis(), fromLabel)
        dropHintSeen = true
        showToast("Añadiste a ${SampleData.users.getValue(userId).handle} a Mi feed.")
    }

    fun dropPost(postId: String, fromLabel: String) {
        post(postId)?.let { addCreator(it.userId, fromLabel) }
        dragging = null
    }

    fun removeCreator(userId: String) {
        myFeed.remove(userId)
    }

    fun columnPosts(kind: ColumnKind): List<Post> = when (kind) {
        ColumnKind.Diseno -> feedPosts(FeedId.Diseno)
        ColumnKind.ParaTi -> feedPosts(FeedId.ParaTi)
        ColumnKind.Siguiendo -> feedPosts(FeedId.Siguiendo)
        ColumnKind.Plegables -> feedPosts(FeedId.Plegables)
        ColumnKind.MiFeed -> feedPosts(FeedId.MiFeed)
        ColumnKind.Busqueda -> SampleData.search("sobremesa")
        ColumnKind.Notificaciones, ColumnKind.Tendencias -> emptyList()
    }

    fun replies(postId: String): List<Post> = SampleData.replies(postId) + (myReplies[postId] ?: emptyList())

    fun replyCount(post: Post): Int = post.replies + (myReplies[post.id]?.size ?: 0)

    fun selectFeed(panel: Int, feed: FeedId) {
        panels = panels.toMutableList().also { it[panel] = PanelState(feed) }
    }

    /** El post se abre en la otra mitad; el feed de esa mitad queda debajo para volver. */
    fun openPost(fromPanel: Int, postId: String) {
        val to = 1 - fromPanel
        val from = panels[fromPanel].feedId
        post(postId)?.let { seenCreators[it.userId] = true }
        panels = panels.toMutableList().also { it[to] = it[to].copy(post = OpenPost(postId, from.label)) }
    }

    fun closePost(panel: Int) {
        panels = panels.toMutableList().also { it[panel] = it[panel].copy(post = null) }
    }

    /** Post que se ve con el móvil plegado o en sobremesa: el abierto más reciente. */
    fun focusedPanel(): Int? = when {
        panels[1].post != null -> 1
        panels[0].post != null -> 0
        else -> null
    }

    fun swap() {
        panels = listOf(panels[1], panels[0])
    }

    fun updateMode(m: OpenMode) {
        mode = m
    }

    /** Un post tocado en una columna se abre en la otra mitad, igual que en el doble feed. */
    fun openFromColumn(side: Int, postId: String, column: ColumnKind) {
        columnPost = (1 - side) to OpenPost(postId, column.title)
    }

    fun closeColumnPost() {
        columnPost = null
    }

    fun moveColumn(index: Int, delta: Int) {
        columns = columns.moved(index, delta)
    }

    fun removeColumn(kind: ColumnKind) {
        if (columns.size > 1) columns = columns - kind
    }

    fun addColumn(kind: ColumnKind) {
        if (kind !in columns) columns = columns + kind
    }

    fun toggle(map: MutableMap<String, Boolean>, id: String) {
        map[id] = !(map[id] ?: false)
    }

    fun publish(text: String) {
        val post = Post(id = "yo-${++seq}", userId = SampleData.me.id, text = text.trim(), time = "ahora", date = nowLabel())
        myPosts.add(0, post)
        composing = false
        showToast("Se envió tu post.")
    }

    fun reply(postId: String, text: String) {
        val reply = Post(id = "yo-${++seq}", userId = SampleData.me.id, text = text.trim(), time = "ahora", date = nowLabel(), replyTo = postId)
        myReplies[postId] = (myReplies[postId] ?: emptyList()) + reply
        showToast("Se envió tu respuesta.")
    }

    fun showToast(text: String) {
        toast = Toast(text, ++seq)
    }

    fun dismissToast(id: Long) {
        if (toast?.id == id) toast = null
    }
}
