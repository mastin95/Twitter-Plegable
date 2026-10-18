package io.github.mastin95.pliegue.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.mastin95.pliegue.AppViewModel
import io.github.mastin95.pliegue.model.AppMode
import io.github.mastin95.pliegue.ui.XIcons
import io.github.mastin95.pliegue.ui.components.Logo
import io.github.mastin95.pliegue.ui.components.PillButton
import io.github.mastin95.pliegue.ui.components.PlainField
import io.github.mastin95.pliegue.ui.components.bottomBorder
import io.github.mastin95.pliegue.ui.theme.X
import io.github.mastin95.pliegue.ui.theme.XType
import io.github.mastin95.pliegue.ui.web.LocalWebPanes
import io.github.mastin95.pliegue.ui.web.WebPane
import io.github.mastin95.pliegue.web.PaneOptions
import io.github.mastin95.pliegue.web.XWeb

/** Primera pantalla: entrar en X o ver la demo con posts de ejemplo. */
@Composable
fun WelcomeScreen(vm: AppViewModel) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Column(Modifier.widthIn(max = 480.dp).align(Alignment.CenterHorizontally)) {
            Logo(56.dp)
            Spacer(Modifier.height(24.dp))
            Text("Pliegue", style = XType.title.copy(fontSize = 31.sp, lineHeight = 36.sp))
            Spacer(Modifier.height(12.dp))
            Text(
                "X para tu móvil plegable: dos mitades, sobremesa, columnas, la mesa y Mi feed, con tu cuenta de verdad.",
                style = XType.detail.copy(color = X.Secondary),
            )
            Spacer(Modifier.height(32.dp))
            PillButton(
                "Iniciar sesión en X",
                onClick = vm::startLogin,
                background = X.Blue,
                content = Color.White,
                height = 48.dp,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            PillButton(
                "Ver la demo",
                onClick = vm::chooseDemo,
                background = X.Bg,
                content = X.Text,
                border = X.OutlineButton,
                height = 48.dp,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(20.dp))
            Text(
                "Entras en la web oficial de X con tu usuario y contraseña. La app no ve la contraseña: solo guarda la sesión del navegador, como hace Chrome.",
                style = XType.small,
            )
        }
    }
}

/** La página de iniciar sesión de X dentro de la app. En cuanto hay sesión, pasamos a X real. */
@Composable
fun LoginScreen(vm: AppViewModel) {
    val web = LocalWebPanes.current
    val expired = vm.appMode == AppMode.Real
    LaunchedEffect(web.loggedIn) {
        if (web.loggedIn) {
            web.drop("login")
            vm.loggedIn()
        }
    }
    val leave = {
        web.drop("login")
        if (expired) vm.loggedOut() else vm.cancelLogin()
    }
    // Atrás primero deshace dentro de la web (un paso del formulario); si no hay nada, sale.
    BackHandler { if (web.canGoBack("login")) web.goBack("login") else leave() }
    DisposableEffect(Unit) { onDispose { web.drop("login") } }
    var menu by remember { mutableStateOf(false) }
    var showLog by rememberSaveable { mutableStateOf(false) }
    val url = web.urls["login"] ?: XWeb.LOGIN
    val progress = web.progress["login"] ?: 0
    val inFlow = XWeb.isLoginPage(url)
    // La sesión puede llegar sin que la página cambie (la ventana de Google la pone y se cierra): la cookie se mira cada poco.
    LaunchedEffect(Unit) {
        while (true) {
            delay(700)
            web.checkLogin()
        }
    }
    // La flecha de X saca del formulario a una página que aquí se queda en negro: volvemos solos
    // al formulario, un par de veces como mucho para no dar vueltas si X insiste.
    var recoveries by remember { mutableStateOf(0) }
    LaunchedEffect(url, progress) {
        if (progress == 100 && !web.loggedIn && XWeb.isLoginDeadEnd(url) && recoveries < 2) {
            recoveries++
            web.load("login", XWeb.LOGIN)
        }
    }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(53.dp)
                .bottomBorder()
                .padding(start = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = leave) {
                Icon(XIcons.Back, contentDescription = "Atrás", tint = X.Text, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("Iniciar sesión en X", style = XType.title, maxLines = 1)
                // Qué página hay debajo y si sigue cargando: con la web en negro es la única pista.
                Text(
                    when {
                        expired -> "Tu sesión caducó; vuelve a entrar."
                        progress < 100 -> "${url.removePrefix("https://")} · cargando $progress %"
                        else -> url.removePrefix("https://")
                    },
                    style = XType.small,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!inFlow && progress == 100) {
                PillButton("Al formulario", onClick = { web.load("login", XWeb.LOGIN) }, background = X.Blue, content = Color.White, height = 32.dp)
                Spacer(Modifier.width(4.dp))
            }
            IconButton(onClick = { web.reload("login") }) {
                Icon(XIcons.Reload, contentDescription = "Recargar", tint = X.Text, modifier = Modifier.size(20.dp))
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(XIcons.More, contentDescription = "Más opciones", tint = X.Text, modifier = Modifier.size(20.dp))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, modifier = Modifier.background(X.Bg)) {
                    DropdownMenuItem(
                        text = { Text(if (web.desktop) "Volver al modo móvil" else "Probar en modo escritorio", style = XType.body) },
                        onClick = {
                            web.useDesktop(!web.desktop)
                            menu = false
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(if (showLog) "Ocultar el registro" else "Ver el registro", style = XType.body) },
                        onClick = {
                            showLog = !showLog
                            menu = false
                        },
                    )
                }
            }
        }
        if (showLog) WebLog(Modifier.fillMaxWidth().height(180.dp).bottomBorder())
        WebPane("login", XWeb.LOGIN, Modifier.weight(1f), PaneOptions(interceptPosts = false, hideChrome = false, keepInside = true))
    }
}

/** Los últimos avisos de las webs, para contar qué pasa cuando algo no carga. */
@Composable
fun WebLog(modifier: Modifier = Modifier) {
    val web = LocalWebPanes.current
    LazyColumn(modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        if (web.log.isEmpty()) item { Text("Sin avisos todavía.", style = XType.small) }
        items(web.log) { line -> Text(line, style = XType.small.copy(fontSize = 12.sp, lineHeight = 15.sp)) }
    }
}

/** Ajustes: cuenta, demo o X real, y las listas que guardamos nosotros (Mi feed, fijados, hilos). */
@Composable
fun SettingsDialog(vm: AppViewModel) {
    val web = LocalWebPanes.current
    var newHandle by rememberSaveable { mutableStateOf("") }
    Dialog(
        onDismissRequest = { vm.settingsOpen = false },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.9f)
                .clip(RoundedCornerShape(16.dp))
                .background(X.Bg)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.settingsOpen = false }) {
                    Icon(XIcons.Close, contentDescription = "Cerrar", tint = X.Text, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text("Ajustes", style = XType.title)
            }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Section("Cuenta")
                    if (vm.appMode == AppMode.Real) {
                        Text(if (web.loggedIn) "Sesión iniciada en X." else "Sin sesión en X.", style = XType.body)
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PillButton("Cerrar sesión", onClick = { web.logout { vm.loggedOut() } }, background = X.Bg, content = Color(0xFFF4212E), border = X.OutlineButton, height = 32.dp)
                            PillButton("Ver la demo", onClick = vm::chooseDemo, background = X.Bg, content = X.Text, border = X.OutlineButton, height = 32.dp)
                        }
                    } else {
                        Text("Estás en la demo, con posts de ejemplo.", style = XType.body)
                        Spacer(Modifier.height(10.dp))
                        PillButton(
                            if (web.loggedIn) "Volver a X" else "Iniciar sesión en X",
                            onClick = { if (web.loggedIn) vm.loggedIn() else vm.startLogin() },
                            background = X.Blue,
                            content = Color.White,
                            height = 32.dp,
                        )
                    }
                }
                item {
                    Section("Creadores de Mi feed")
                    Text("Lo último de estas personas, en orden. Añádelas arrastrando su avatar o escribiendo su usuario.", style = XType.small)
                }
                items(vm.handles, key = { "h:$it" }) { h ->
                    ListRow("@$h") { vm.removeHandle(h) }
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PlainField(newHandle, { newHandle = it }, "@usuario", XType.body, Modifier.weight(1f))
                        PillButton("Añadir", onClick = {
                            vm.addHandle(newHandle)
                            newHandle = ""
                        }, enabled = newHandle.isNotBlank(), height = 32.dp)
                    }
                }
                item { Section("Fijados en la mesa") }
                if (vm.pinned.isEmpty()) item { Text("Nada fijado. En un post ampliado, toca \"Fijar en la mesa\".", style = XType.small) }
                items(vm.pinned, key = { "p:$it" }) { url ->
                    ListRow(XWeb.shortLabel(url)) { vm.togglePinned(url) }
                }
                item { Section("Hilos que sigues") }
                if (vm.threads.isEmpty()) item { Text("Ninguno. En un post ampliado, toca \"Seguir hilo\".", style = XType.small) }
                items(vm.threads, key = { "t:$it" }) { url ->
                    ListRow(XWeb.shortLabel(url)) { vm.toggleThread(url) }
                }
                item {
                    Section("Si X no carga")
                    Text("Las webs se presentan como Chrome para Android. Si alguna se queda en negro, prueba el modo escritorio y mira el registro.", style = XType.small)
                    Spacer(Modifier.height(10.dp))
                    PillButton(
                        if (web.desktop) "Volver al modo móvil" else "Modo escritorio",
                        onClick = { web.useDesktop(!web.desktop) },
                        background = X.Bg,
                        content = X.Text,
                        border = X.OutlineButton,
                        height = 32.dp,
                    )
                }
                item { Section("Registro de la web") }
                item { WebLog(Modifier.fillMaxWidth().height(220.dp)) }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = XType.name.copy(fontSize = 17.sp), modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
}

@Composable
private fun ListRow(text: String, onRemove: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .bottomBorder()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = XType.body.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f), maxLines = 1)
        IconButton(onClick = onRemove) {
            Icon(XIcons.Close, contentDescription = "Quitar", tint = X.Secondary, modifier = Modifier.size(18.dp))
        }
    }
}
