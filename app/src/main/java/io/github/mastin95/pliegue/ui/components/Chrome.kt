package io.github.mastin95.pliegue.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.mastin95.pliegue.AppViewModel
import io.github.mastin95.pliegue.model.AppMode
import io.github.mastin95.pliegue.model.OpenMode
import io.github.mastin95.pliegue.model.SampleData
import io.github.mastin95.pliegue.ui.XIcons
import io.github.mastin95.pliegue.ui.theme.X
import io.github.mastin95.pliegue.ui.theme.XType
import kotlinx.coroutines.delay

/** Barra superior de X en el móvil: tu avatar (abre los ajustes), el logotipo en el centro. */
@Composable
fun FoldedTopBar(onAvatar: () -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(53.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.clip(CircleShape).clickable(onClickLabel = "Ajustes", role = Role.Button, onClick = onAvatar)) {
            Avatar(SampleData.me.id, 32.dp)
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Logo() }
        Spacer(Modifier.size(32.dp))
    }
}

@Composable
fun BottomNav(vm: AppViewModel) {
    val soon = { vm.showToast(SOON) }
    Row(
        Modifier
            .fillMaxWidth()
            .height(53.dp)
            .background(X.Bg)
            .topBorder(),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NavIcon(XIcons.HomeFilled, "Inicio", {})
        NavIcon(XIcons.Search, "Explorar", soon)
        NavIcon(XIcons.People, "Comunidades", soon)
        NavIcon(XIcons.Bell, "Notificaciones", soon)
        NavIcon(XIcons.Mail, "Mensajes", soon)
    }
}

/** Barra lateral de X web, para pantallas interiores anchas. */
@Composable
fun NavRail(vm: AppViewModel) {
    val soon = { vm.showToast(SOON) }
    Column(
        Modifier
            .width(72.dp)
            .fillMaxHeight()
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.size(50.dp), contentAlignment = Alignment.Center) { Logo() }
        NavIcon(XIcons.HomeFilled, "Inicio", {})
        NavIcon(XIcons.Search, "Explorar", soon)
        NavIcon(XIcons.Bell, "Notificaciones", soon)
        NavIcon(XIcons.Mail, "Mensajes", soon)
        NavIcon(XIcons.Bookmark, "Guardados", soon)
        NavIcon(XIcons.People, "Comunidades", soon)
        NavIcon(XIcons.User, "Perfil", soon)
        Spacer(Modifier.weight(1f))
        Fab(onClick = { vm.composing = true }, size = 50)
        Spacer(Modifier.height(12.dp))
        Avatar(SampleData.me.id)
    }
}

/** Botón flotante azul para postear. */
@Composable
fun Fab(onClick: () -> Unit, modifier: Modifier = Modifier, size: Int = 56) {
    Box(
        modifier
            .size(size.dp)
            .shadow(6.dp, CircleShape)
            .clip(CircleShape)
            .background(X.Blue)
            .clickable(onClickLabel = "Postear", role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(XIcons.Compose, contentDescription = "Postear", tint = Color.White, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun Segment(label: String, active: Boolean, icon: ImageVector? = null, description: String = label, onClick: () -> Unit) {
    Row(
        Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (active) X.Blue else X.Bg)
            .clickable(onClickLabel = description, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = if (label.isEmpty()) description else null, tint = if (active) Color.White else X.Text, modifier = Modifier.size(16.dp))
        if (label.isNotEmpty()) Text(label, style = XType.button.copy(fontSize = 14.sp), color = if (active) Color.White else X.Text, maxLines = 1)
    }
}

/**
 * Barra sobre la bisagra: doble feed, un solo feed, columnas y, con X real, la mesa y el libro;
 * más intercambiar mitades y los ajustes. `compact` acorta los textos en pantallas estrechas.
 */
@Composable
fun OpenToolbar(vm: AppViewModel, compact: Boolean, modifier: Modifier = Modifier) {
    val real = vm.appMode == AppMode.Real
    val modes = if (real) OpenMode.entries else listOf(OpenMode.Doble, OpenMode.Uno, OpenMode.Columnas)
    val shape = RoundedCornerShape(26.dp)
    Row(
        modifier
            .shadow(12.dp, shape, ambientColor = Color.White, spotColor = Color.White)
            .clip(shape)
            .background(X.Bg)
            .border(1.dp, X.Border, shape)
            .padding(5.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        modes.forEach { m ->
            Segment(if (compact) m.short else m.label, active = vm.mode == m, description = m.label) { vm.updateMode(m) }
        }
        if (vm.mode != OpenMode.Columnas && vm.mode != OpenMode.Mesa) {
            Segment(if (compact) "" else "Intercambiar", active = false, icon = XIcons.Swap, description = "Intercambiar") { if (real) vm.swapWeb() else vm.swap() }
        }
        Segment("", active = false, icon = XIcons.More, description = "Ajustes") { vm.settingsOpen = true }
    }
}

/** Aviso azul de X ("Se envió tu post."). Desaparece solo. */
@Composable
fun ToastHost(vm: AppViewModel, modifier: Modifier = Modifier) {
    val toast = vm.toast ?: return
    LaunchedEffect(toast.id) {
        delay(2500)
        vm.dismissToast(toast.id)
    }
    Box(
        modifier
            .padding(16.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(X.Blue)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(toast.text, style = XType.body.copy(color = Color.White))
    }
}

private val composerTools = listOf(
    XIcons.Image to "Añadir imagen",
    XIcons.Gif to "Añadir GIF",
    XIcons.Poll to "Encuesta",
    XIcons.Smile to "Emoji",
    XIcons.Calendar to "Programar",
    XIcons.Pin to "Etiquetar ubicación",
)

/** Herramientas azules bajo el campo de texto, como en el redactor de X. */
@Composable
fun ComposerTools(remaining: Int, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        composerTools.forEach { (icon, label) ->
            Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = label, tint = X.Blue, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.weight(1f))
        Text(remaining.toString(), style = XType.small, color = if (remaining < 0) Color(0xFFF4212E) else X.Secondary)
    }
}

/** Redactor de X. A pantalla completa con el móvil plegado; ventana centrada si está abierto. */
@Composable
fun ComposerDialog(vm: AppViewModel, fullScreen: Boolean) {
    var text by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val remaining = 280 - text.length
    Dialog(
        onDismissRequest = { vm.composing = false },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            (if (fullScreen) Modifier.fillMaxSize() else Modifier.widthIn(max = 600.dp).fillMaxWidth(0.92f).clip(RoundedCornerShape(16.dp)))
                .background(X.Bg)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.composing = false }) {
                    Icon(XIcons.Close, contentDescription = "Cerrar", tint = X.Text, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.weight(1f))
                Text("Borradores", style = XType.button.copy(fontSize = 14.sp), color = X.Blue)
                Spacer(Modifier.width(16.dp))
                PillButton(
                    "Postear",
                    onClick = { vm.publish(text) },
                    enabled = text.isNotBlank() && remaining >= 0,
                    background = X.Blue,
                    content = Color.White,
                )
            }
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Avatar(SampleData.me.id)
                PlainField(
                    text,
                    { text = it },
                    "¿Qué está pasando?",
                    XType.title.copy(fontWeight = FontWeight.Normal),
                    Modifier
                        .weight(1f)
                        .heightIn(min = 120.dp),
                    focus,
                )
            }
            if (fullScreen) Spacer(Modifier.weight(1f))
            Row(
                Modifier.padding(start = 52.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(XIcons.Globe, contentDescription = null, tint = X.Blue, modifier = Modifier.size(16.dp))
                Text("Cualquier persona puede responder", style = XType.small.copy(color = X.Blue, fontWeight = FontWeight.Bold))
            }
            HorizontalDivider(color = X.Border)
            ComposerTools(remaining, Modifier.padding(vertical = 6.dp))
        }
        LaunchedEffect(Unit) {
            delay(150)
            runCatching { focus.requestFocus() }
        }
    }
}
