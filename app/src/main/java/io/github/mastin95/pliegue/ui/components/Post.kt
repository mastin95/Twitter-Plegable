package io.github.mastin95.pliegue.ui.components

import android.content.ClipData
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mastin95.pliegue.AppViewModel
import io.github.mastin95.pliegue.model.Post
import io.github.mastin95.pliegue.model.PostMark
import io.github.mastin95.pliegue.model.SampleData
import io.github.mastin95.pliegue.ui.XIcons
import io.github.mastin95.pliegue.ui.count
import io.github.mastin95.pliegue.ui.theme.X
import io.github.mastin95.pliegue.ui.theme.XType

@Composable
private fun ActionButton(
    icon: ImageVector,
    label: String,
    n: Int?,
    active: Boolean,
    activeColor: Color,
    iconSize: Dp,
    onClick: (() -> Unit)?,
) {
    val color = if (active) activeColor else X.Secondary
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .then(if (onClick != null) Modifier.clickable(onClickLabel = label, role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(iconSize))
        if (n != null && n > 0) Text(count(n), style = XType.small, color = color)
    }
}

/** Fila de acciones de X: responder, repostear, me gusta, visualizaciones, guardar y compartir. */
@Composable
fun Actions(post: Post, vm: AppViewModel, big: Boolean = false) {
    val liked = vm.liked[post.id] == true
    val reposted = vm.reposted[post.id] == true
    val saved = vm.saved[post.id] == true
    val iconSize = if (big) 22.dp else 18.75.dp
    val share = { vm.showToast("Compartir llegará en la próxima versión.") }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ActionButton(XIcons.Reply, "Responder", vm.replyCount(post), false, X.Blue, iconSize, null)
        ActionButton(
            XIcons.Repost, if (reposted) "Deshacer repost" else "Repostear",
            post.reposts + (if (reposted) 1 else 0), reposted, X.Green, iconSize,
        ) { vm.toggle(vm.reposted, post.id) }
        ActionButton(
            if (liked) XIcons.LikeFilled else XIcons.Like, if (liked) "Ya no me gusta" else "Me gusta",
            post.likes + (if (liked) 1 else 0), liked, X.Pink, iconSize,
        ) { vm.toggle(vm.liked, post.id) }
        if (big) {
            ActionButton(
                if (saved) XIcons.BookmarkFilled else XIcons.Bookmark, "Guardar",
                post.bookmarks + (if (saved) 1 else 0), saved, X.Blue, iconSize,
            ) { vm.toggle(vm.saved, post.id) }
            ActionButton(XIcons.Share, "Compartir post", null, false, X.Blue, iconSize, share)
        } else {
            ActionButton(XIcons.Views, "Visualizaciones", post.views, false, X.Blue, iconSize, null)
            Row {
                ActionButton(if (saved) XIcons.BookmarkFilled else XIcons.Bookmark, "Guardar", null, saved, X.Blue, iconSize) {
                    vm.toggle(vm.saved, post.id)
                }
                ActionButton(XIcons.Share, "Compartir post", null, false, X.Blue, iconSize, share)
            }
        }
    }
}

@Composable
fun PostHead(post: Post, modifier: Modifier = Modifier) {
    val u = SampleData.users.getValue(post.userId)
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(u.name, style = XType.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        if (u.verified) VerifiedBadge()
        Text(
            "${u.handle} · ${post.time}",
            style = XType.secondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

/** Etiqueta azul de X para creadores nuevos: NUEVO PARA TI, NUEVO EN TU FEED, SUGERIDO. */
@Composable
fun NewBadge(text: String) {
    Box(
        Modifier
            .height(20.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(X.Blue)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.2.sp, maxLines = 1)
    }
}

/**
 * Post en un feed. `selected` lo marca cuando está abierto en la otra mitad; `mark` lo resalta
 * como creador nuevo; `onDragStart` permite mantenerlo pulsado y arrastrarlo hacia Mi feed;
 * `faded` lo atenúa mientras se arrastra.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PostCard(
    post: Post,
    vm: AppViewModel,
    selected: Boolean = false,
    onOpen: (() -> Unit)? = null,
    mark: PostMark? = null,
    onMarkTap: (() -> Unit)? = null,
    faded: Boolean = false,
    onDragStart: (() -> Unit)? = null,
) {
    val reposter = post.repostedBy?.let { SampleData.users[it] }
    Column(
        Modifier
            .fillMaxWidth()
            .alpha(if (faded) 0.35f else 1f)
            .background(
                when {
                    selected -> X.Card
                    mark != null -> X.NewBg
                    else -> X.Bg
                },
            )
            .then(if (onOpen != null) Modifier.clickable(onClickLabel = "Abrir post", onClick = onOpen) else Modifier)
            .then(
                if (onDragStart != null) {
                    Modifier.dragAndDropSource {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                onDragStart()
                                startTransfer(DragAndDropTransferData(ClipData.newPlainText("post", post.id)))
                            },
                        )
                    }
                } else {
                    Modifier
                },
            )
            .drawBehind {
                val w = 3.dp.toPx()
                if (selected) drawRect(X.Text, topLeft = Offset(size.width - w, 0f), size = Size(w, size.height))
                if (mark != null) drawRect(X.Blue, topLeft = Offset.Zero, size = Size(w, size.height))
            }
            .bottomBorder()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        if (mark != null) {
            Row(
                Modifier
                    .padding(start = 28.dp, bottom = 4.dp)
                    .then(if (onMarkTap != null) Modifier.clickable(onClickLabel = "Añadir a Mi feed", onClick = onMarkTap) else Modifier),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(XIcons.Sparkle, contentDescription = null, tint = X.Secondary, modifier = Modifier.size(16.dp))
                Text(mark.context, style = XType.small.copy(fontWeight = FontWeight.Bold))
            }
        }
        if (reposter != null) {
            Row(
                Modifier.padding(start = 28.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(XIcons.Repost, contentDescription = null, tint = X.Secondary, modifier = Modifier.size(16.dp))
                Text("${reposter.name} reposteó", style = XType.small.copy(fontWeight = FontWeight.Bold))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Anillo azul alrededor del avatar de un creador nuevo, como en el plan visual.
            Box(
                if (mark != null) {
                    Modifier.drawBehind {
                        val r = size.minDimension / 2
                        drawCircle(X.Bg, radius = r + 2.dp.toPx(), style = Stroke(2.dp.toPx()))
                        drawCircle(X.Blue, radius = r + 4.dp.toPx(), style = Stroke(2.dp.toPx()))
                    }
                } else {
                    Modifier
                },
            ) {
                Avatar(post.userId)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PostHead(post, Modifier.weight(1f))
                    if (mark != null) NewBadge(mark.mark.badge)
                    Icon(XIcons.More, contentDescription = "Más opciones", tint = X.Secondary, modifier = Modifier.size(18.75.dp))
                }
                val parentHandle = post.replyTo?.let { SampleData.posts[it] }?.let { SampleData.users[it.userId]?.handle }
                if (parentHandle != null) {
                    Text(
                        buildAnnotatedString {
                            append("Respondiendo a ")
                            withStyle(SpanStyle(color = X.Blue)) { append(parentHandle) }
                        },
                        style = XType.secondary,
                    )
                }
                Text(post.text, style = XType.body)
                post.media?.let { MediaArt(it, Modifier.fillMaxWidth().padding(top = 10.dp)) }
                Spacer(Modifier.height(2.dp))
                Actions(post, vm)
            }
        }
    }
}
