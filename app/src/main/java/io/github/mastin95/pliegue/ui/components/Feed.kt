package io.github.mastin95.pliegue.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.mastin95.pliegue.AppViewModel
import io.github.mastin95.pliegue.model.FeedId
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

/**
 * Un feed con sus pestañas. `panel` decide qué mitad lo muestra; plegado usa la 0,
 * así el scroll se conserva al abrir y cerrar el móvil.
 */
@Composable
fun FeedColumn(
    vm: AppViewModel,
    panel: Int,
    selectedPostId: String?,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit = {},
) {
    val feed = vm.panels[panel].feedId
    val state = vm.listState(panel, feed)
    Column(modifier.fillMaxSize()) {
        header()
        FeedTabs(feed) { vm.selectFeed(panel, it) }
        LazyColumn(state = state, modifier = Modifier.weight(1f)) {
            items(vm.feedPosts(feed), key = { it.id }) { post ->
                PostCard(post, vm, selected = post.id == selectedPostId, onOpen = { onOpen(post.id) })
            }
            item { Spacer(Modifier.height(140.dp)) }
        }
    }
}

