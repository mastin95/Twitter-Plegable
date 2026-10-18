package io.github.mastin95.pliegue.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.mastin95.pliegue.AppViewModel
import io.github.mastin95.pliegue.model.FeedId
import io.github.mastin95.pliegue.model.OpenPost
import io.github.mastin95.pliegue.model.Post
import io.github.mastin95.pliegue.model.SampleData
import io.github.mastin95.pliegue.ui.XIcons
import io.github.mastin95.pliegue.ui.count
import io.github.mastin95.pliegue.ui.theme.X
import io.github.mastin95.pliegue.ui.theme.XType

/** Cabecera "← Post" de X. Con `returnTo`, un botón para volver al feed que quedó debajo. */
@Composable
fun DetailHeader(onBack: () -> Unit, returnTo: FeedId? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(53.dp)
            .padding(start = 4.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(XIcons.Back, contentDescription = "Atrás", tint = X.Text, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(16.dp))
        Text("Post", style = XType.title, modifier = Modifier.weight(1f))
        if (returnTo != null) {
            PillButton(
                returnTo.label,
                onClick = onBack,
                background = X.Bg,
                content = X.Text,
                border = X.OutlineButton,
                height = 32.dp,
                icon = XIcons.Stack,
            )
        }
    }
}

@Composable
fun AuthorRow(post: Post, vm: AppViewModel) {
    val u = SampleData.users.getValue(post.userId)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Avatar(post.userId)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(u.name, style = XType.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (u.verified) VerifiedBadge()
            }
            Text(u.handle, style = XType.secondary, maxLines = 1)
        }
        if (post.userId != SampleData.me.id) {
            FollowButton(following = vm.following[post.userId] == true) { vm.toggle(vm.following, post.userId) }
        }
    }
}

@Composable
fun ReplyBox(vm: AppViewModel, postId: String) {
    var draft by rememberSaveable(postId) { mutableStateOf("") }
    Row(
        Modifier
            .fillMaxWidth()
            .bottomBorder()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Avatar(SampleData.me.id)
        PlainField(draft, { draft = it }, "Postea tu respuesta", XType.detail, Modifier.weight(1f))
        PillButton("Responder", onClick = {
            vm.reply(postId, draft)
            draft = ""
        }, enabled = draft.isNotBlank())
    }
}

/**
 * Post abierto con sus respuestas, como la página de un post en X.
 * `showFrom` añade la línea "Abierto desde …" cuando el post viene del otro panel.
 */
@Composable
fun PostDetail(
    vm: AppViewModel,
    open: OpenPost,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    returnTo: FeedId? = null,
    showFrom: Boolean = false,
) {
    val post = vm.post(open.id) ?: return
    Column(
        modifier
            .fillMaxSize()
            .background(X.Bg),
    ) {
        DetailHeader(onBack, returnTo)
        LazyColumn(Modifier.weight(1f)) {
            if (showFrom) {
                item {
                    Row(
                        Modifier.padding(start = 60.dp, end = 16.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(XIcons.Back, contentDescription = null, tint = X.Secondary, modifier = Modifier.size(16.dp))
                        Text("Abierto desde ${open.from.label}", style = XType.small.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }
            item {
                Column(
                    Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AuthorRow(post, vm)
                    Text(post.text, style = XType.detail)
                    post.media?.let { MediaArt(it, Modifier.fillMaxWidth()) }
                    Text(
                        buildAnnotatedString {
                            append(post.date)
                            if (post.views > 0) {
                                append(" · ")
                                withStyle(SpanStyle(color = X.Text, fontWeight = FontWeight.Bold)) { append(count(post.views)) }
                                append(" Visualizaciones")
                            }
                        },
                        style = XType.secondary,
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .topBorder()
                            .bottomBorder()
                            .padding(vertical = 4.dp),
                    ) {
                        Actions(post, vm, big = true)
                    }
                }
            }
            item { ReplyBox(vm, post.id) }
            items(vm.replies(post.id), key = { it.id }) { PostCard(it, vm) }
            item { Spacer(Modifier.height(120.dp)) }
        }
    }
}

/** Panel derecho en modo "un solo feed" cuando aún no hay post abierto. */
@Composable
fun EmptyDetail() {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Elige un post", style = XType.title.copy(fontSize = XType.title.fontSize * 1.5f))
        Spacer(Modifier.height(8.dp))
        Text("Toca un post del feed y su conversación se abrirá aquí, sin perder tu sitio.", style = XType.secondary)
    }
}
