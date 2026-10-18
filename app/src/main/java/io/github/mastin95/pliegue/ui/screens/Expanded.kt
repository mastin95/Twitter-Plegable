package io.github.mastin95.pliegue.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mastin95.pliegue.AppViewModel
import io.github.mastin95.pliegue.model.Note
import io.github.mastin95.pliegue.model.tagChips
import io.github.mastin95.pliegue.ui.XIcons
import io.github.mastin95.pliegue.ui.components.PillButton
import io.github.mastin95.pliegue.ui.components.PlainField
import io.github.mastin95.pliegue.ui.components.bottomBorder
import io.github.mastin95.pliegue.ui.components.endBorder
import io.github.mastin95.pliegue.ui.components.startBorder
import io.github.mastin95.pliegue.ui.theme.X
import io.github.mastin95.pliegue.ui.theme.XType
import io.github.mastin95.pliegue.ui.web.WebPane
import io.github.mastin95.pliegue.web.PaneOptions
import io.github.mastin95.pliegue.web.XWeb

/**
 * Post ampliado a las dos mitades: a la izquierda el post real con su conversación; a la derecha
 * lo que añades tú y solo ves tú (nota, etiquetas, carpeta, recordatorio) y tus fijados e hilos.
 */
@Composable
fun ExpandedScreen(vm: AppViewModel, url: String, leftWidth: Dp, gap: Dp) {
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.width(leftWidth).fillMaxHeight().endBorder()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(53.dp)
                    .bottomBorder()
                    .padding(start = 4.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = vm::collapse) {
                    Icon(XIcons.Back, contentDescription = "Atrás", tint = X.Text, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text("Post ampliado", style = XType.title, modifier = Modifier.weight(1f))
                PillButton("Volver", onClick = vm::collapse, background = X.Bg, content = X.Text, border = X.OutlineButton, height = 32.dp, icon = XIcons.Collapse)
            }
            WebPane("focus", url, Modifier.weight(1f), PaneOptions(interceptPosts = true, hideChrome = true), textZoom = 105)
        }
        Spacer(Modifier.width(gap))
        Extras(vm, url, Modifier.weight(1f).fillMaxHeight().startBorder())
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Extras(vm: AppViewModel, url: String, modifier: Modifier) {
    val clipboard = LocalClipboardManager.current
    var note by remember(url) { mutableStateOf(vm.note(url)) }
    fun update(n: Note) {
        note = n
        vm.saveNote(url, n)
    }
    val pinned = url in vm.pinned
    val followed = url in vm.threads
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(XWeb.shortLabel(url), style = XType.small)
        // Lo privado, como la caja "Añadido por ti" del diseño.
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, X.Border, RoundedCornerShape(16.dp))
                .background(X.Card)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(XIcons.Note, contentDescription = null, tint = X.Secondary, modifier = Modifier.size(16.dp))
                Text("Añadido por ti · solo lo ves tú", style = XType.small.copy(fontWeight = FontWeight.Bold))
            }
            Field("Nota", note.text, "Escribe lo que quieras recordar de este post", { update(note.copy(text = it)) }, minHeight = 72.dp)
            Field("Etiquetas", note.tags, "#ideas, #plegables", { update(note.copy(tags = it)) })
            Field("Carpeta", note.folder, "Plegables", { update(note.copy(folder = it)) })
            Field("Recordatorio", note.remind, "mañana, el lunes, 14 de octubre…", { update(note.copy(remind = it)) })
            val chips = tagChips(note.tags) + listOfNotNull(
                note.folder.takeIf { it.isNotBlank() }?.let { "Carpeta: $it" },
                note.remind.takeIf { it.isNotBlank() }?.let { "Recordar $it" },
            )
            if (chips.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    chips.forEach { Chip(it) }
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton(if (pinned) "Fijado en la mesa" else "Fijar en la mesa", onClick = { vm.togglePinned(url) }, background = if (pinned) X.Blue else X.Bg, content = if (pinned) Color.White else X.Text, border = if (pinned) null else X.OutlineButton, height = 32.dp, icon = XIcons.Pin)
            PillButton(if (followed) "Siguiendo el hilo" else "Seguir hilo", onClick = { vm.toggleThread(url) }, background = if (followed) X.Blue else X.Bg, content = if (followed) Color.White else X.Text, border = if (followed) null else X.OutlineButton, height = 32.dp, icon = XIcons.Reply)
            PillButton("Citar en un post", onClick = {
                vm.collapse()
                vm.composeWeb(0, quoting = url)
            }, background = X.Bg, content = X.Text, border = X.OutlineButton, height = 32.dp, icon = XIcons.Compose)
            PillButton("Copiar enlace", onClick = {
                clipboard.setText(AnnotatedString(url))
                vm.showToast("Enlace copiado.")
            }, background = X.Bg, content = X.Text, border = X.OutlineButton, height = 32.dp, icon = XIcons.Share)
        }
        val others = vm.notes.filterKeys { it != url }
        if (others.isNotEmpty()) {
            Text("Tus otras notas", style = XType.name)
            others.entries.forEach { (u, n) ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClickLabel = "Abrir post") { vm.expand(u) }
                        .bottomBorder()
                        .padding(vertical = 10.dp),
                ) {
                    Text(XWeb.shortLabel(u), style = XType.small.copy(fontWeight = FontWeight.Bold))
                    if (n.text.isNotBlank()) Text(n.text, style = XType.body, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val c = tagChips(n.tags)
                    if (c.isNotEmpty()) Text(c.joinToString(" "), style = XType.small.copy(color = X.Blue))
                }
            }
        }
        val pins = (vm.pinned + vm.threads).filter { it != url }.distinct()
        if (pins.isNotEmpty()) {
            Text("Fijados e hilos", style = XType.name)
            pins.forEach { u ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClickLabel = "Abrir post") { vm.expand(u) }
                        .bottomBorder()
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(if (u in vm.pinned) XIcons.Pin else XIcons.Reply, contentDescription = null, tint = X.Secondary, modifier = Modifier.size(16.dp))
                    Text(XWeb.shortLabel(u), style = XType.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun Field(label: String, value: String, placeholder: String, onChange: (String) -> Unit, minHeight: Dp = 0.dp) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = XType.small)
        PlainField(value, onChange, placeholder, XType.body, Modifier.fillMaxWidth().heightIn(min = minHeight))
    }
}

@Composable
private fun Chip(text: String) {
    Text(
        text,
        style = XType.small.copy(color = X.Blue, fontWeight = FontWeight.Bold, fontSize = 13.sp),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .border(1.dp, X.Border, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}
