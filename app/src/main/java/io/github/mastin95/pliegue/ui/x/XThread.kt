package io.github.mastin95.pliegue.ui.x

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.mastin95.pliegue.AppViewModel
import io.github.mastin95.pliegue.model.XThread
import io.github.mastin95.pliegue.ui.theme.X
import io.github.mastin95.pliegue.ui.web.LocalWebPanes

/**
 * La conversación de un post pintada por la app: lo que hay encima, el post en grande, la caja de
 * responder y las respuestas agrupadas, con más al bajar. Hasta que X la manda, la rueda.
 */
@Composable
fun XThreadScreen(vm: AppViewModel, slot: String, statusId: String, taps: PostTaps, header: @Composable () -> Unit = {}) {
    val web = LocalWebPanes.current
    val thread = web.store.thread(slot)?.takeIf { it.focal.id == statusId }
    val state = vm.feedState("$slot/hilo/$statusId")
    val now = System.currentTimeMillis()
    Column(Modifier.fillMaxSize().background(X.Bg)) {
        header()
        if (thread == null) Waiting(vm, slot) else ThreadList(slot, thread, state, taps, now)
    }
}

@Composable
private fun ThreadList(slot: String, thread: XThread, state: LazyListState, taps: PostTaps, now: Long) {
    val web = LocalWebPanes.current
    val nearEnd by remember(state, thread.replies.size) {
        derivedStateOf {
            val info = state.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 3
        }
    }
    LaunchedEffect(nearEnd, thread.cursorBottom) {
        if (nearEnd && thread.cursorBottom != null) web.loadMore(slot)
    }
    LazyColumn(state = state, modifier = Modifier.fillMaxSize()) {
            itemsIndexed(thread.ancestors, key = { _, t -> "a:" + t.id }) { i, t -> XPostCard(t, now, taps, lineBelow = true, lineAbove = i > 0) }
            item("focal") { XFocalPost(thread.focal, now, taps) }
            item("reply") { XReplyBox(thread.focal, web.store.byId(web.userId)?.avatar ?: "") { taps.onReply(thread.focal) } }
            items(thread.replies, key = { it.id }) { e -> XEntryCards(e.tweets, now, taps) }
            item("fin") {
                Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.TopCenter) {
                    if (thread.cursorBottom != null) CircularProgressIndicator(color = X.Blue, strokeWidth = 2.dp, modifier = Modifier.padding(top = 24.dp).size(24.dp))
                }
            }
            item("hueco") { Spacer(Modifier.height(60.dp)) }
    }
}
