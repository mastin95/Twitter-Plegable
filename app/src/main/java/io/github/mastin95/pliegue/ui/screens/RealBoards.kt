package io.github.mastin95.pliegue.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.mastin95.pliegue.AppViewModel
import io.github.mastin95.pliegue.model.Source
import io.github.mastin95.pliegue.model.columnsPerHalf
import io.github.mastin95.pliegue.ui.XIcons
import io.github.mastin95.pliegue.ui.components.PillButton
import io.github.mastin95.pliegue.ui.components.bottomBorder
import io.github.mastin95.pliegue.ui.components.endBorder
import io.github.mastin95.pliegue.ui.components.startBorder
import io.github.mastin95.pliegue.ui.components.topBorder
import io.github.mastin95.pliegue.ui.theme.X
import io.github.mastin95.pliegue.ui.theme.XType
import io.github.mastin95.pliegue.ui.web.EmptyMyFeed
import io.github.mastin95.pliegue.ui.web.LocalWebPanes
import io.github.mastin95.pliegue.ui.web.WebPane
import io.github.mastin95.pliegue.web.PaneOptions
import io.github.mastin95.pliegue.web.XWeb

fun sourceIcon(s: Source): ImageVector = when (s) {
    Source.Inicio -> XIcons.Home
    Source.MiFeed -> XIcons.Sparkle
    Source.Notificaciones -> XIcons.Bell
    Source.Guardados -> XIcons.Bookmark
    Source.Explorar -> XIcons.Search
    Source.Mensajes -> XIcons.Mail
    Source.Perfil -> XIcons.User
    Source.Fijados -> XIcons.Pin
}

/** Menú con las fuentes posibles, para columnas y recuadros de la mesa. */
@Composable
fun SourceMenu(expanded: Boolean, onDismiss: () -> Unit, options: List<Source>, onPick: (Source) -> Unit) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss, modifier = Modifier.background(X.Bg)) {
        options.forEach { s ->
            DropdownMenuItem(
                text = { Text(s.label, style = XType.body) },
                leadingIcon = { Icon(sourceIcon(s), contentDescription = null, tint = X.Text, modifier = Modifier.size(18.dp)) },
                onClick = { onPick(s) },
            )
        }
    }
}

/** Tus posts fijados y los hilos que sigues, como lista nativa; tocar uno lo abre. */
@Composable
fun PinnedList(vm: AppViewModel, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(modifier.fillMaxSize()) {
        if (vm.pinned.isEmpty() && vm.threads.isEmpty()) {
            item {
                Column(Modifier.padding(24.dp)) {
                    Text("Nada fijado todavía", style = XType.title)
                    Spacer(Modifier.height(8.dp))
                    Text("Abre un post, toca Ampliar y luego \"Fijar en la mesa\" o \"Seguir hilo\". Aparecerán aquí.", style = XType.secondary)
                }
            }
        }
        if (vm.pinned.isNotEmpty()) item { ListTitle("Fijados en la mesa") }
        items(vm.pinned, key = { "p:$it" }) { url -> PinnedRow(vm, url, XIcons.Pin, onOpen) }
        if (vm.threads.isNotEmpty()) item { ListTitle("Hilos que sigues") }
        items(vm.threads, key = { "t:$it" }) { url -> PinnedRow(vm, url, XIcons.Reply, onOpen) }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun ListTitle(text: String) {
    Text(text, style = XType.name, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 6.dp))
}

@Composable
private fun PinnedRow(vm: AppViewModel, url: String, icon: ImageVector, onOpen: (String) -> Unit) {
    val note = vm.notes[url]
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Abrir post") { onOpen(url) }
            .bottomBorder()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = X.Secondary, modifier = Modifier.size(18.dp))
        Column(Modifier.weight(1f)) {
            Text(XWeb.shortLabel(url), style = XType.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (note != null && note.text.isNotBlank()) Text(note.text, style = XType.small, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * Columnas con X real, como X Pro: cada columna es una web (notificaciones, Mi feed, explorar…).
 * La bisagra queda siempre entre columnas y un post tocado se abre en la otra mitad.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WebColumnsBoard(vm: AppViewModel, leftWidth: Dp, gap: Dp, modifier: Modifier = Modifier) {
    val perHalf = columnsPerHalf(leftWidth.value)
    val perPage = perHalf * 2
    val pages = (vm.webColumns.size + perPage - 1) / perPage
    val pager = rememberPagerState { pages.coerceAtLeast(1) }
    val open = vm.webColumnPost
    Column(modifier.fillMaxSize()) {
        WebColumnsHeader(vm, page = pager.currentPage, pages = pages)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), key = { it }) { page ->
                val start = page * perPage
                val left = vm.webColumns.drop(start).take(perHalf)
                val right = vm.webColumns.drop(start + perHalf).take(perHalf)
                Row(Modifier.fillMaxSize()) {
                    WebGroup(vm, left, start, perHalf, Modifier.width(leftWidth).fillMaxHeight(), side = 0)
                    Spacer(Modifier.width(gap))
                    WebGroup(vm, right, start + perHalf, perHalf, Modifier.weight(1f).fillMaxHeight(), side = 1)
                }
            }
            if (open != null) {
                val (side, url) = open
                Row(Modifier.fillMaxSize()) {
                    if (side == 0) {
                        ColumnPost(vm, url, Modifier.width(leftWidth).fillMaxHeight().endBorder())
                    } else {
                        Spacer(Modifier.width(leftWidth + gap))
                        ColumnPost(vm, url, Modifier.weight(1f).fillMaxHeight().startBorder())
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnPost(vm: AppViewModel, url: String, modifier: Modifier) {
    val web = LocalWebPanes.current
    val external = XWeb.isExternal(url)
    val current = web.urls["colpost"]?.takeIf { XWeb.isExternal(it) } ?: url
    Column(modifier.background(X.Bg)) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(53.dp)
                .bottomBorder()
                .padding(start = 4.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = vm::closeWebColumnPost) {
                Icon(XIcons.Back, contentDescription = "Atrás", tint = X.Text, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(if (external) XWeb.siteLabel(current) else "Post", style = XType.title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (external) {
                IconButton(onClick = { web.openInBrowser(current) }) {
                    Icon(XIcons.Globe, contentDescription = "Abrir en el navegador", tint = X.Text, modifier = Modifier.size(20.dp))
                }
            } else {
                IconButton(onClick = { vm.expand(url) }) {
                    Icon(XIcons.Expand, contentDescription = "Ampliar", tint = X.Text, modifier = Modifier.size(20.dp))
                }
            }
            PillButton("Columnas", onClick = vm::closeWebColumnPost, background = X.Bg, content = X.Text, border = X.OutlineButton, height = 32.dp, icon = XIcons.Stack)
        }
        WebPane("colpost", url, Modifier.weight(1f), if (external) PaneOptions.EXTERNAL else PaneOptions())
    }
}

@Composable
private fun WebColumnsHeader(vm: AppViewModel, page: Int, pages: Int) {
    var adding by remember { mutableStateOf(false) }
    val missing = Source.entries.filter { it !in vm.webColumns }
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
            SourceMenu(adding, { adding = false }, missing) {
                vm.addWebColumn(it)
                adding = false
            }
        }
    }
}

@Composable
private fun WebGroup(vm: AppViewModel, sources: List<Source>, firstIndex: Int, slots: Int, modifier: Modifier, side: Int) {
    Row(modifier) {
        sources.forEachIndexed { i, s ->
            WebColumn(vm, s, firstIndex + i, side, Modifier.weight(1f).fillMaxHeight())
        }
        repeat(slots - sources.size) {
            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                Text("Añade una columna\ncon el botón azul", style = XType.small, modifier = Modifier.padding(16.dp))
            }
        }
    }
}

@Composable
private fun WebColumn(vm: AppViewModel, source: Source, index: Int, side: Int, modifier: Modifier) {
    val web = LocalWebPanes.current
    var menu by remember { mutableStateOf(false) }
    val last = vm.webColumns.lastIndex
    val slot = "col:$side:${source.name}"
    Column(modifier.endBorder()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(50.dp)
                .bottomBorder()
                .padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(sourceIcon(source), contentDescription = null, tint = X.Text, modifier = Modifier.size(18.dp))
            Text(source.label, style = XType.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
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
                    DropdownMenuItem(text = { Text("Recargar", style = XType.body) }, onClick = {
                        web.reload(slot)
                        menu = false
                    })
                    DropdownMenuItem(text = { Text("Mover a la izquierda", style = XType.body) }, enabled = index > 0, onClick = {
                        vm.moveWebColumn(index, -1)
                        menu = false
                    })
                    DropdownMenuItem(text = { Text("Mover a la derecha", style = XType.body) }, enabled = index < last, onClick = {
                        vm.moveWebColumn(index, 1)
                        menu = false
                    })
                    DropdownMenuItem(text = { Text("Quitar columna", style = XType.body.copy(color = Color(0xFFF4212E))) }, enabled = vm.webColumns.size > 1, onClick = {
                        vm.removeWebColumn(source)
                        menu = false
                    })
                }
            }
        }
        val url = vm.sourceUrl(source, web.userId)
        when {
            source == Source.Fijados -> PinnedList(vm, onOpen = { vm.onWebPost(slot, it) }, modifier = Modifier.weight(1f))
            url == null -> EmptyMyFeed(Modifier.weight(1f))
            else -> WebPane(slot, url, Modifier.weight(1f), PaneOptions(interceptPosts = true, hideChrome = true))
        }
    }
}

/**
 * La mesa: cuatro recuadros con X real, dos por mitad, para tener a la vista inicio,
 * notificaciones, Mi feed y lo que fijes. Cada recuadro elige su fuente desde su menú.
 */
@Composable
fun MesaBoard(vm: AppViewModel, leftWidth: Dp, gap: Dp, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxSize()) {
        Column(Modifier.width(leftWidth).fillMaxHeight().endBorder()) {
            MesaCell(vm, 0, Modifier.weight(1f).fillMaxWidth())
            MesaCell(vm, 1, Modifier.weight(1f).fillMaxWidth().topBorder())
        }
        Spacer(Modifier.width(gap))
        Column(Modifier.weight(1f).fillMaxHeight().startBorder()) {
            MesaCell(vm, 2, Modifier.weight(1f).fillMaxWidth())
            MesaCell(vm, 3, Modifier.weight(1f).fillMaxWidth().topBorder())
        }
    }
}

@Composable
private fun MesaCell(vm: AppViewModel, index: Int, modifier: Modifier) {
    val web = LocalWebPanes.current
    val source = vm.mesa[index]
    var menu by remember { mutableStateOf(false) }
    val slot = "mesa$index"
    Column(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(40.dp)
                .bottomBorder()
                .padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(sourceIcon(source), contentDescription = null, tint = X.Text, modifier = Modifier.size(16.dp))
            Text(source.label, style = XType.body.copy(fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            IconButton(onClick = { web.reload(slot) }, modifier = Modifier.size(32.dp)) {
                Icon(XIcons.Reload, contentDescription = "Recargar", tint = X.Secondary, modifier = Modifier.size(16.dp))
            }
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(32.dp)) {
                    Icon(XIcons.More, contentDescription = "Cambiar recuadro", tint = X.Secondary, modifier = Modifier.size(16.dp))
                }
                SourceMenu(menu, { menu = false }, Source.entries) {
                    vm.setMesa(index, it)
                    menu = false
                }
            }
        }
        val url = vm.sourceUrl(source, web.userId)
        when {
            source == Source.Fijados -> PinnedList(vm, onOpen = vm::expand, modifier = Modifier.weight(1f))
            url == null -> EmptyMyFeed(Modifier.weight(1f))
            // Dentro de un recuadro se navega como en la web: no hay otra mitad a la que mandar el post.
            else -> WebPane(slot, url, Modifier.weight(1f), PaneOptions(interceptPosts = false, hideChrome = true))
        }
    }
}
