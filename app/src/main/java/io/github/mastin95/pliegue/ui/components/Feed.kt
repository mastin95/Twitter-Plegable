package io.github.mastin95.pliegue.ui.components

import android.content.ClipDescription
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.mastin95.pliegue.AppViewModel
import io.github.mastin95.pliegue.model.Drag
import io.github.mastin95.pliegue.model.FeedId
import io.github.mastin95.pliegue.model.Mark
import io.github.mastin95.pliegue.model.SampleData
import io.github.mastin95.pliegue.ui.XIcons
import io.github.mastin95.pliegue.ui.theme.X
import io.github.mastin95.pliegue.ui.theme.XType

/** Pestañas de inicio de X: Para ti, Siguiendo y las listas fijadas. */
@Composable
fun FeedTabs(selected: FeedId, onSelect: (FeedId) -> Unit) {
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .background(X.Bg)
            .bottomBorder(),
    ) {
        // Si caben todas, se reparten el ancho como en X; si no, se desplazan.
        val fits = maxWidth >= 400.dp
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (fits) Modifier else Modifier.horizontalScroll(rememberScrollState())),
        ) {
            FeedId.entries.forEach { feed ->
                val active = feed == selected
                Box(
                    (if (fits) Modifier.weight(1f) else Modifier)
                        .height(53.dp)
                        .clickable(onClickLabel = feed.label, role = Role.Tab) { onSelect(feed) }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        Modifier
                            .widthIn(min = 56.dp)
                            .width(IntrinsicSize.Max)
                            .fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Text(
                                feed.label,
                                style = XType.body.copy(fontWeight = if (active) FontWeight.Bold else FontWeight.Medium),
                                color = if (active) X.Text else X.Secondary,
                                maxLines = 1,
                            )
                        }
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (active) X.Blue else X.Bg),
                        )
                    }
                }
            }
        }
    }
}

/** Línea bajo las pestañas de Mi feed: cuántos creadores tiene y cómo añadir más. */
@Composable
private fun MyFeedInfo(vm: AppViewModel) {
    val n = vm.myFeed.size
    val text = buildString {
        append(if (n == 1) "1 creador" else "$n creadores")
        if (!vm.dropHintSeen) append(" · Mantén pulsado un post del otro feed y arrástralo aquí")
    }
    Row(
        Modifier
            .fillMaxWidth()
            .bottomBorder()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(XIcons.Sparkle, contentDescription = null, tint = X.Secondary, modifier = Modifier.size(16.dp))
        Text(text, style = XType.small.copy(fontWeight = FontWeight.Bold))
    }
}

/** Zona punteada "Suelta para añadir a @alguien a Mi feed", visible mientras arrastras. */
@Composable
private fun DropZone(handle: String, hovering: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .height(64.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (hovering) X.NewBgHover else X.NewBg)
            .drawBehind {
                drawRoundRect(
                    color = X.Blue,
                    cornerRadius = CornerRadius(16.dp.toPx()),
                    style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 12f))),
                )
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(XIcons.Plus, contentDescription = null, tint = X.Blue, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text("Suelta para añadir a $handle a Mi feed", style = XType.body.copy(color = X.Blue, fontWeight = FontWeight.Bold), maxLines = 1)
    }
}

/**
 * Un feed con sus pestañas. `panel` decide qué mitad lo muestra; plegado usa la 0,
 * así el scroll se conserva al abrir y cerrar el móvil.
 *
 * Con `crossFeed` (móvil abierto) los posts se pueden mantener pulsados y arrastrar sobre la
 * bisagra hasta Mi feed, que los recibe en la zona punteada; los demás feeds solo cierran el
 * arrastre si se suelta fuera.
 */
@Composable
fun FeedColumn(
    vm: AppViewModel,
    panel: Int,
    selectedPostId: String?,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit = {},
    crossFeed: Boolean = false,
) {
    val feed = vm.panels[panel].feedId
    val state = vm.listState(panel, feed)
    val isMyFeed = feed == FeedId.MiFeed
    var hovering by remember { mutableStateOf(false) }
    val target = remember(feed) {
        object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean {
                hovering = false
                if (!isMyFeed) return false
                val drag = vm.dragging
                val id = event.toAndroidDragEvent().clipData?.getItemAt(0)?.text?.toString() ?: drag?.postId ?: return false
                vm.dropPost(id, drag?.fromLabel ?: FeedId.ParaTi.label)
                return true
            }

            override fun onEntered(event: DragAndDropEvent) {
                hovering = isMyFeed
            }

            override fun onExited(event: DragAndDropEvent) {
                hovering = false
            }

            override fun onEnded(event: DragAndDropEvent) {
                hovering = false
                vm.dragging = null
            }
        }
    }
    Column(
        modifier
            .fillMaxSize()
            .then(
                if (crossFeed) {
                    Modifier.dragAndDropTarget(
                        shouldStartDragAndDrop = { it.mimeTypes().contains(ClipDescription.MIMETYPE_TEXT_PLAIN) },
                        target = target,
                    )
                } else {
                    Modifier
                },
            ),
    ) {
        header()
        FeedTabs(feed) { vm.selectFeed(panel, it) }
        if (isMyFeed) MyFeedInfo(vm)
        val drag = vm.dragging
        if (isMyFeed && crossFeed && drag != null) {
            val handle = vm.post(drag.postId)?.let { SampleData.users[it.userId]?.handle } ?: ""
            DropZone(handle, hovering)
        }
        LazyColumn(state = state, modifier = Modifier.weight(1f)) {
            items(vm.feedPosts(feed), key = { it.id }) { post ->
                val mark = vm.mark(post, feed)
                PostCard(
                    post,
                    vm,
                    selected = post.id == selectedPostId,
                    onOpen = { onOpen(post.id) },
                    mark = mark,
                    onMarkTap = if (mark?.mark == Mark.Sugerido) ({ vm.addCreator(post.userId, "sugerencias") }) else null,
                    faded = drag?.postId == post.id,
                    onDragStart = if (crossFeed && !isMyFeed) ({ vm.dragging = Drag(post.id, feed.label) }) else null,
                )
            }
            item { Spacer(Modifier.height(140.dp)) }
        }
    }
}

