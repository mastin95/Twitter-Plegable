package io.github.mastin95.pliegue.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import io.github.mastin95.pliegue.model.ColumnKind
import io.github.mastin95.pliegue.model.Mention
import io.github.mastin95.pliegue.model.Post
import io.github.mastin95.pliegue.model.SampleData
import io.github.mastin95.pliegue.model.Trend
import io.github.mastin95.pliegue.model.columnsPerHalf
import io.github.mastin95.pliegue.ui.XIcons
import io.github.mastin95.pliegue.ui.count
import io.github.mastin95.pliegue.ui.theme.X
import io.github.mastin95.pliegue.ui.theme.XType

private fun icon(kind: ColumnKind): ImageVector = when (kind) {
    ColumnKind.Notificaciones -> XIcons.Bell
    ColumnKind.Diseno, ColumnKind.Plegables -> XIcons.ListIcon
    ColumnKind.Tendencias -> XIcons.Trend
    ColumnKind.Busqueda -> XIcons.Search
    ColumnKind.MiFeed -> XIcons.Sparkle
    ColumnKind.ParaTi -> XIcons.Home
    ColumnKind.Siguiendo -> XIcons.People
}

private val small = XType.small.copy(fontSize = 13.sp)
private val compactText = XType.body.copy(fontSize = 14.sp, lineHeight = 18.sp)

/**
 * Columnas al estilo de X Pro. Cada mitad de la pantalla lleva una o dos columnas y la bisagra
 * queda siempre entre grupos: al deslizar se pasa de página entera, nunca a medias sobre el pliegue.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ColumnsBoard(vm: AppViewModel, leftWidth: Dp, gap: Dp, modifier: Modifier = Modifier) {
    val perHalf = columnsPerHalf(leftWidth.value)
    val perPage = perHalf * 2
    val pages = (vm.columns.size + perPage - 1) / perPage
    val pager = rememberPagerState { pages.coerceAtLeast(1) }
    val open = vm.columnPost
    Column(modifier.fillMaxSize()) {
        ColumnsHeader(vm, page = pager.currentPage, pages = pages)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), key = { it }) { page ->
                val start = page * perPage
                val left = vm.columns.drop(start).take(perHalf)
                val right = vm.columns.drop(start + perHalf).take(perHalf)
                Row(Modifier.fillMaxSize()) {
                    Group(vm, left, start, perHalf, Modifier.width(leftWidth).fillMaxHeight(), side = 0)
                    Spacer(Modifier.width(gap))
                    Group(vm, right, start + perHalf, perHalf, Modifier.weight(1f).fillMaxHeight(), side = 1)
                }
            }
            if (open != null) {
                val (side, post) = open
                Row(Modifier.fillMaxSize()) {
                    if (side == 0) {
                        PostDetail(vm, post, onBack = vm::closeColumnPost, modifier = Modifier.width(leftWidth).endBorder(), returnTo = "Columnas", showFrom = true)
                    } else {
                        Spacer(Modifier.width(leftWidth + gap))
                        PostDetail(vm, post, onBack = vm::closeColumnPost, modifier = Modifier.weight(1f).startBorder(), returnTo = "Columnas", showFrom = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnsHeader(vm: AppViewModel, page: Int, pages: Int) {
    var adding by remember { mutableStateOf(false) }
    val missing = ColumnKind.entries.filter { it !in vm.columns }
    Row(
        Modifier
            .fillMaxWidth()
            .height(53.dp)
            .bottomBorder()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Columnas", style = XType.title, modifier = Modifier.weight(1f))
        if (pages > 1) Text("${page + 1} de $pages", style = XType.small)
        Box {
            PillButton(
                "Añadir columna",
                onClick = { adding = true },
                enabled = missing.isNotEmpty(),
                background = X.Blue,
                content = Color.White,
                height = 32.dp,
            )
            DropdownMenu(expanded = adding, onDismissRequest = { adding = false }, modifier = Modifier.background(X.Bg)) {
                missing.forEach { kind ->
                    DropdownMenuItem(
                        text = { Text(kind.title, style = XType.body) },
                        leadingIcon = { Icon(icon(kind), contentDescription = null, tint = X.Text, modifier = Modifier.size(18.dp)) },
                        onClick = {
                            vm.addColumn(kind)
                            adding = false
                        },
                    )
                }
            }
        }
    }
}

/** Las columnas de una mitad. Si faltan columnas para llenarla, queda un hueco para añadir. */
@Composable
private fun Group(vm: AppViewModel, kinds: List<ColumnKind>, firstIndex: Int, slots: Int, modifier: Modifier, side: Int) {
    Row(modifier) {
        kinds.forEachIndexed { i, kind ->
            BoardColumn(vm, kind, firstIndex + i, side, Modifier.weight(1f).fillMaxHeight())
        }
        repeat(slots - kinds.size) {
            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                Text("Añade una columna\ncon el botón azul", style = XType.small, modifier = Modifier.padding(16.dp))
            }
        }
    }
}

@Composable
private fun BoardColumn(vm: AppViewModel, kind: ColumnKind, index: Int, side: Int, modifier: Modifier) {
    Column(modifier.endBorder()) {
        ColumnHeader(vm, kind, index)
        LazyColumn(state = vm.columnState(kind), modifier = Modifier.weight(1f)) {
            when (kind) {
                ColumnKind.Notificaciones -> items(SampleData.mentions) { MentionRow(it) }
                ColumnKind.Tendencias -> items(SampleData.trends) { TrendRow(it) }
                else -> items(vm.columnPosts(kind), key = { it.id }) { post ->
                    val selected = vm.columnPost?.second?.id == post.id
                    CompactPost(post, selected) { vm.openFromColumn(side, post.id, kind) }
                }
            }
            item { Spacer(Modifier.height(120.dp)) }
        }
    }
}

@Composable
private fun ColumnHeader(vm: AppViewModel, kind: ColumnKind, index: Int) {
    var menu by remember { mutableStateOf(false) }
    val last = vm.columns.lastIndex
    Row(
        Modifier
            .fillMaxWidth()
            .height(50.dp)
            .bottomBorder()
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon(kind), contentDescription = null, tint = X.Text, modifier = Modifier.size(18.dp))
        Text(
            kind.title,
            style = XType.name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Box {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(50))
                    .clickable(onClickLabel = "Opciones de la columna", role = Role.Button) { menu = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(XIcons.More, contentDescription = "Opciones de la columna", tint = X.Secondary, modifier = Modifier.size(18.dp))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, modifier = Modifier.background(X.Bg)) {
                DropdownMenuItem(
                    text = { Text("Mover a la izquierda", style = XType.body) },
                    enabled = index > 0,
                    onClick = {
                        vm.moveColumn(index, -1)
                        menu = false
                    },
                )
                DropdownMenuItem(
                    text = { Text("Mover a la derecha", style = XType.body) },
                    enabled = index < last,
                    onClick = {
                        vm.moveColumn(index, 1)
                        menu = false
                    },
                )
                DropdownMenuItem(
                    text = { Text("Quitar columna", style = XType.body.copy(color = Color(0xFFF4212E))) },
                    enabled = vm.columns.size > 1,
                    onClick = {
                        vm.removeColumn(kind)
                        menu = false
                    },
                )
            }
        }
    }
}

/** Post compacto de columna: avatar pequeño, nombre, hora y texto, como en X Pro. */
@Composable
private fun CompactPost(post: Post, selected: Boolean, onOpen: () -> Unit) {
    val u = SampleData.users.getValue(post.userId)
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) X.Card else X.Bg)
            .clickable(onClickLabel = "Abrir post", onClick = onOpen)
            .bottomBorder()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Avatar(post.userId, 28.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(u.name, style = small.copy(color = X.Text, fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (u.verified) VerifiedBadge(14.dp)
                Text("· ${post.time}", style = small, maxLines = 1)
            }
            Text(post.text, style = compactText)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(top = 4.dp)) {
                MiniCount(XIcons.Reply, post.replies)
                MiniCount(XIcons.Repost, post.reposts)
                MiniCount(XIcons.Like, post.likes)
            }
        }
    }
}

@Composable
private fun MiniCount(icon: ImageVector, n: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Icon(icon, contentDescription = null, tint = X.Secondary, modifier = Modifier.size(14.dp))
        if (n > 0) Text(count(n), style = XType.small.copy(fontSize = 12.sp))
    }
}

/** Mención en la columna de notificaciones, con tu usuario resaltado en azul. */
@Composable
private fun MentionRow(m: Mention) {
    val u = SampleData.users.getValue(m.userId)
    Row(
        Modifier
            .fillMaxWidth()
            .bottomBorder()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Avatar(m.userId, 28.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = X.Text, fontWeight = FontWeight.Bold)) { append(u.name) }
                    append(" · ${m.time}")
                },
                style = small,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                buildAnnotatedString {
                    val handle = SampleData.me.handle
                    val at = m.text.indexOf(handle)
                    if (at < 0) {
                        append(m.text)
                    } else {
                        append(m.text.substring(0, at))
                        withStyle(SpanStyle(color = X.Blue)) { append(handle) }
                        append(m.text.substring(at + handle.length))
                    }
                },
                style = compactText,
            )
        }
    }
}

/** Tendencia como en "Qué está pasando" de X: categoría, nombre y número de posts. */
@Composable
private fun TrendRow(t: Trend) {
    Column(
        Modifier
            .fillMaxWidth()
            .bottomBorder()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(t.category, style = small, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(t.tag, style = XType.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${count(t.posts)} posts", style = small)
    }
}
