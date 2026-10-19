package io.github.mastin95.pliegue

import android.app.Application
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import io.github.mastin95.pliegue.model.AppMode
import io.github.mastin95.pliegue.model.Cleanup
import io.github.mastin95.pliegue.model.ColumnKind
import io.github.mastin95.pliegue.model.Creator
import io.github.mastin95.pliegue.model.Drag
import io.github.mastin95.pliegue.model.FeedId
import io.github.mastin95.pliegue.model.Note
import io.github.mastin95.pliegue.model.OpenMode
import io.github.mastin95.pliegue.model.OpenPost
import io.github.mastin95.pliegue.model.PanelState
import io.github.mastin95.pliegue.model.Post
import io.github.mastin95.pliegue.model.PostMark
import io.github.mastin95.pliegue.model.SampleData
import io.github.mastin95.pliegue.model.Source
import io.github.mastin95.pliegue.model.WebPanel
import io.github.mastin95.pliegue.model.markFor
import io.github.mastin95.pliegue.model.moved
import io.github.mastin95.pliegue.model.myFeedPosts
import io.github.mastin95.pliegue.model.suggestions
import io.github.mastin95.pliegue.ui.nowLabel
import io.github.mastin95.pliegue.web.Prefs
import io.github.mastin95.pliegue.web.XWeb

data class Toast(val text: String, val id: Long)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = Prefs(app)

    // ---- Arranque: demo o X real ----

    var appMode by mutableStateOf(prefs.mode?.let { m -> AppMode.entries.firstOrNull { it.name == m } } ?: AppMode.Elegir)
        private set

    /** La pantalla de iniciar sesión está abierta. */
    var loggingIn by mutableStateOf(false)
        private set

    var settingsOpen by mutableStateOf(false)

    /** Limpieza de la web de X (Ajustes). */
    var cleanup by mutableStateOf(Cleanup.fromNames(prefs.cleanup))
        private set

    fun updateCleanup(c: Cleanup) {
        cleanup = c
        prefs.cleanup = c.toNames()
    }

    fun chooseDemo() {
        appMode = AppMode.Demo
        prefs.mode = AppMode.Demo.name
        loggingIn = false
        settingsOpen = false
    }

    fun startLogin() {
        loggingIn = true
        settingsOpen = false
    }

    fun cancelLogin() {
        loggingIn = false
    }

    /** La web de X ya tiene la cookie de sesión: pasamos a X real. */
    fun loggedIn() {
        appMode = AppMode.Real
        prefs.mode = AppMode.Real.name
        loggingIn = false
        showToast("Sesión iniciada en X.")
        pendingLink?.let {
            pendingLink = null
            showLink(it)
        }
    }

    /** Enlace que llegó de fuera antes de tener sesión: se abre en cuanto se entra. */
    private var pendingLink: String? = null

    /**
     * Un enlace que llega de otra app (un "abrir con" o un "compartir"). Uno de X se abre en la mitad
     * derecha, como un post tocado en la izquierda; cualquier otro va al redactor con el enlace puesto.
     * Sin sesión todavía, se guarda y se abre al entrar.
     */
    fun openFromOutside(url: String) {
        if (appMode != AppMode.Real) {
            pendingLink = url
            if (appMode == AppMode.Elegir) startLogin()
            return
        }
        showLink(url)
    }

    private fun showLink(url: String) {
        expanded = null
        webColumnPost = null
        if (mode == OpenMode.Columnas || mode == OpenMode.Mesa) mode = OpenMode.Doble
        if (XWeb.isOwn(url)) openWebPost(0, url) else composeWeb(1, quoting = url)
    }

    fun loggedOut() {
        appMode = AppMode.Elegir
        prefs.mode = AppMode.Elegir.name
        settingsOpen = false
        webPanels = listOf(WebPanel(Source.Inicio), WebPanel(Source.MiFeed))
        webColumnPost = null
        expanded = null
    }

    // ---- X real: paneles web ----

    /** Mitad izquierda [0] y derecha [1] con X real. Plegado enseña la que tenga algo abierto, o la [0]. */
    var webPanels by mutableStateOf(listOf(WebPanel(Source.Inicio), WebPanel(Source.MiFeed)))
        private set

    var webColumns by mutableStateOf(sources(prefs.webColumns).ifEmpty { listOf(Source.Notificaciones, Source.MiFeed, Source.Explorar, Source.Guardados) })
        private set

    /** Post abierto desde una columna web: mitad donde se enseña y su dirección. */
    var webColumnPost by mutableStateOf<Pair<Int, String>?>(null)
        private set

    /** Los cuatro recuadros de la mesa, de izquierda a derecha y de arriba abajo. */
    var mesa by mutableStateOf(sources(prefs.mesa).take(4).let { if (it.size == 4) it else listOf(Source.Inicio, Source.Notificaciones, Source.MiFeed, Source.Guardados) })
        private set

    /** Post ampliado a las dos mitades, con tus notas al lado. */
    var expanded by mutableStateOf<String?>(null)
        private set

    /** Creadores de Mi feed real, por @usuario. */
    var handles by mutableStateOf(prefs.handles)
        private set

    var notes by mutableStateOf(prefs.notes)
        private set

    /** Posts fijados en la mesa. */
    var pinned by mutableStateOf(prefs.pinned)
        private set

    /** Hilos que sigues (post ampliado). */
    var threads by mutableStateOf(prefs.threads)
        private set

    /** Usuario que se está arrastrando desde una web, si lo sabemos. */
    var dragHandle by mutableStateOf<String?>(null)

    private fun sources(names: List<String>): List<Source> = names.mapNotNull { n -> Source.entries.firstOrNull { it.name == n } }

    /** Dirección de una fuente; nula si Mi feed aún no tiene creadores. */
    fun sourceUrl(source: Source, userId: String?): String? = when (source) {
        Source.MiFeed -> XWeb.myFeedUrl(handles)
        Source.Perfil -> userId?.let(XWeb::profileUrl) ?: XWeb.HOME
        else -> source.url
    }

    /** Lo que enseña una mitad: el post abierto encima o su fuente. */
    fun paneUrl(panel: Int, userId: String?): String? = webPanels[panel].openUrl ?: sourceUrl(webPanels[panel].source, userId)

    fun selectSource(panel: Int, source: Source) {
        webPanels = webPanels.toMutableList().also { it[panel] = WebPanel(source) }
    }

    fun swapWeb() {
        webPanels = listOf(webPanels[1], webPanels[0])
    }

    /**
     * Un post tocado en una web. Desde un feed se abre en la otra mitad; dentro de un post abierto
     * (una respuesta) se sigue en la misma mitad, como en X.
     */
    fun onWebPost(slot: String, url: String) {
        when {
            slot == "pane0" || slot == "pane1" -> {
                val from = if (slot == "pane0") 0 else 1
                if (webPanels[from].openUrl != null) {
                    webPanels = webPanels.toMutableList().also { it[from] = it[from].copy(openUrl = url) }
                } else {
                    openWebPost(from, url)
                }
            }
            slot.startsWith("col:") -> {
                val side = slot.removePrefix("col:").substringBefore(':').toIntOrNull() ?: 0
                webColumnPost = (1 - side) to url
            }
            slot == "colpost" -> webColumnPost = webColumnPost?.copy(second = url)
            slot == "focus" -> expanded = url
        }
    }

    /**
     * Un enlace de un post a otra web. Desde una mitad se abre en la otra, como un post, y así el post
     * con sus respuestas sigue a la vista; en las columnas, en el hueco del post abierto. Donde no hay
     * otra mitad (post ampliado, la mesa) va al navegador.
     */
    fun onWebLink(slot: String, link: String): Boolean {
        val url = if (XWeb.isMedia(link)) XWeb.largeImage(link) else link
        when {
            slot == "pane0" || slot == "pane1" -> openWebPost(if (slot == "pane0") 0 else 1, url)
            slot.startsWith("col:") -> {
                val side = slot.removePrefix("col:").substringBefore(':').toIntOrNull() ?: 0
                webColumnPost = (1 - side) to url
            }
            slot == "colpost" -> webColumnPost = webColumnPost?.copy(second = url)
            else -> return false
        }
        return true
    }

    fun openWebPost(fromPanel: Int, url: String) {
        val to = 1 - fromPanel
        webPanels = webPanels.toMutableList().also { it[to] = it[to].copy(openUrl = url, fromLabel = it[fromPanel].source.label) }
    }

    fun closeWebPost(panel: Int) {
        webPanels = webPanels.toMutableList().also { it[panel] = it[panel].copy(openUrl = null) }
    }

    /** Mitad con algo abierto encima: la que se ve plegado, y la que cierra el botón atrás. */
    fun webFocused(): Int? = when {
        webPanels[1].openUrl != null -> 1
        webPanels[0].openUrl != null -> 0
        else -> null
    }

    /** El redactor de X en una mitad; con `quoting`, el enlace del post fuente ya va en el texto. */
    fun composeWeb(panel: Int = 0, quoting: String? = null) {
        webPanels = webPanels.toMutableList().also { it[panel] = it[panel].copy(openUrl = XWeb.composeUrl(url = quoting), fromLabel = "Redactar") }
    }

    fun closeWebColumnPost() {
        webColumnPost = null
    }

    fun addWebColumn(s: Source) {
        if (s !in webColumns) saveWebColumns(webColumns + s)
    }

    fun removeWebColumn(s: Source) {
        if (webColumns.size > 1) saveWebColumns(webColumns - s)
    }

    fun moveWebColumn(index: Int, delta: Int) {
        if (index !in webColumns.indices) return
        val to = (index + delta).coerceIn(0, webColumns.lastIndex)
        if (to != index) saveWebColumns(webColumns.toMutableList().apply { add(to, removeAt(index)) })
    }

    private fun saveWebColumns(list: List<Source>) {
        webColumns = list
        prefs.webColumns = list.map { it.name }
    }

    fun setMesa(index: Int, source: Source) {
        mesa = mesa.toMutableList().also { it[index] = source }
        prefs.mesa = mesa.map { it.name }
    }

    fun expand(url: String) {
        expanded = url
    }

    fun collapse() {
        expanded = null
    }

    fun addHandle(handle: String, fromLabel: String = "") {
        val h = handle.removePrefix("@").trim()
        if (h.isEmpty()) return
        if (handles.any { it.equals(h, ignoreCase = true) }) {
            showToast("@$h ya está en Mi feed.")
            return
        }
        handles = handles + h
        prefs.handles = handles
        dropHintSeen = true
        prefs.dropHintSeen = true
        showToast("Añadiste a @$h a Mi feed" + if (fromLabel.isNotEmpty()) " desde $fromLabel." else ".")
    }

    /** Lo que llega a la zona de soltar: un @usuario nuestro o el enlace de perfil que arrastra la web. */
    fun dropText(text: String?, fromLabel: String) {
        val t = text?.trim().orEmpty()
        val handle = when {
            t.isEmpty() -> dragHandle
            t.startsWith("http") -> XWeb.handleOf(t) ?: dragHandle
            else -> t
        }
        dragHandle = null
        if (handle == null) {
            showToast("Arrastra el avatar o el nombre de alguien.")
            return
        }
        addHandle(handle, fromLabel)
    }

    fun removeHandle(handle: String) {
        handles = handles.filterNot { it.equals(handle, ignoreCase = true) }
        prefs.handles = handles
    }

    fun note(url: String): Note = notes[url] ?: Note()

    fun saveNote(url: String, note: Note) {
        notes = if (note.isEmpty) notes - url else notes + (url to note)
        prefs.notes = notes
    }

    fun togglePinned(url: String) {
        pinned = if (url in pinned) pinned - url else listOf(url) + pinned
        prefs.pinned = pinned
        showToast(if (url in pinned) "Fijado en la mesa." else "Quitado de la mesa.")
    }

    fun toggleThread(url: String) {
        threads = if (url in threads) threads - url else listOf(url) + threads
        prefs.threads = threads
        showToast(if (url in threads) "Sigues este hilo." else "Ya no sigues este hilo.")
    }

    // ---- Demo: posts de ejemplo ----

    /** Mitad izquierda [0] y derecha [1] con el móvil abierto. Plegado usa la [0]. */
    var panels by mutableStateOf(listOf(PanelState(FeedId.ParaTi), PanelState(FeedId.MiFeed)))
        private set

    /** Abierto: doble feed, un feed con el post al lado, columnas, la mesa o el libro. */
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
    var dropHintSeen by mutableStateOf(prefs.dropHintSeen)
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
