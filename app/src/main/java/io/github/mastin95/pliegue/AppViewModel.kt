package io.github.mastin95.pliegue

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import io.github.mastin95.pliegue.model.FeedId
import io.github.mastin95.pliegue.model.OpenPost
import io.github.mastin95.pliegue.model.PanelState
import io.github.mastin95.pliegue.model.Post
import io.github.mastin95.pliegue.model.SampleData
import io.github.mastin95.pliegue.ui.nowLabel

data class Toast(val text: String, val id: Long)

class AppViewModel : ViewModel() {

    /** Mitad izquierda [0] y derecha [1] con el móvil abierto. Plegado usa la [0]. */
    var panels by mutableStateOf(listOf(PanelState(FeedId.ParaTi), PanelState(FeedId.Diseno)))
        private set

    /** Abierto: true = doble feed; false = un feed y el post abierto al lado. */
    var dual by mutableStateOf(true)
        private set

    val liked = mutableStateMapOf<String, Boolean>()
    val reposted = mutableStateMapOf<String, Boolean>()
    val saved = mutableStateMapOf<String, Boolean>()
    val following = mutableStateMapOf<String, Boolean>().apply {
        SampleData.initialFollowing.forEach { put(it, true) }
    }
    val myPosts = mutableStateListOf<Post>()
    val myReplies = mutableStateMapOf<String, List<Post>>()

    var composing by mutableStateOf(false)
    var toast by mutableStateOf<Toast?>(null)
        private set

    private var seq = 0L

    // El scroll de cada feed vive aquí para no perderlo al plegar o desplegar.
    private val listStates = mutableMapOf<String, LazyListState>()

    fun listState(panel: Int, feed: FeedId): LazyListState =
        listStates.getOrPut("$panel:${feed.name}") { LazyListState() }

    fun post(id: String): Post? = myPosts.firstOrNull { it.id == id } ?: SampleData.posts[id]

    fun feedPosts(feed: FeedId): List<Post> {
        val mine = if (feed == FeedId.ParaTi || feed == FeedId.Siguiendo) myPosts.toList() else emptyList()
        return mine + SampleData.feeds.getValue(feed).mapNotNull { SampleData.posts[it] }
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
        panels = panels.toMutableList().also { it[to] = it[to].copy(post = OpenPost(postId, from)) }
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

    fun updateDual(on: Boolean) {
        dual = on
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
