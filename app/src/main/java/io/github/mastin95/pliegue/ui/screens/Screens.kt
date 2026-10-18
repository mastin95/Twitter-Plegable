package io.github.mastin95.pliegue.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.mastin95.pliegue.AppViewModel
import io.github.mastin95.pliegue.Posture
import io.github.mastin95.pliegue.ScreenInfo
import io.github.mastin95.pliegue.model.AppMode
import io.github.mastin95.pliegue.model.OpenMode
import io.github.mastin95.pliegue.model.OpenPost
import io.github.mastin95.pliegue.model.SampleData
import io.github.mastin95.pliegue.ui.components.Actions
import io.github.mastin95.pliegue.ui.components.AuthorRow
import io.github.mastin95.pliegue.ui.components.Avatar
import io.github.mastin95.pliegue.ui.components.BottomNav
import io.github.mastin95.pliegue.ui.components.ComposerDialog
import io.github.mastin95.pliegue.ui.components.ColumnsBoard
import io.github.mastin95.pliegue.ui.components.ComposerTools
import io.github.mastin95.pliegue.ui.components.DetailHeader
import io.github.mastin95.pliegue.ui.components.EmptyDetail
import io.github.mastin95.pliegue.ui.components.Fab
import io.github.mastin95.pliegue.ui.components.FeedColumn
import io.github.mastin95.pliegue.ui.components.FoldedTopBar
import io.github.mastin95.pliegue.ui.components.MediaArt
import io.github.mastin95.pliegue.ui.components.NavRail
import io.github.mastin95.pliegue.ui.components.OpenToolbar
import io.github.mastin95.pliegue.ui.components.PillButton
import io.github.mastin95.pliegue.ui.components.PlainField
import io.github.mastin95.pliegue.ui.components.PostCard
import io.github.mastin95.pliegue.ui.components.PostDetail
import io.github.mastin95.pliegue.ui.components.ReplyBox
import io.github.mastin95.pliegue.ui.components.ToastHost
import io.github.mastin95.pliegue.ui.components.endBorder
import io.github.mastin95.pliegue.ui.components.startBorder
import io.github.mastin95.pliegue.ui.components.topBorder
import io.github.mastin95.pliegue.ui.theme.X
import io.github.mastin95.pliegue.ui.theme.XType
import io.github.mastin95.pliegue.ui.web.LocalWebPanes
import io.github.mastin95.pliegue.ui.web.PopupSheet
import androidx.compose.ui.graphics.Color

private val BottomNavHeight = 53.dp

/**
 * Raíz de la app. Primero se elige entre X real (con tu sesión) y la demo con posts de ejemplo;
 * después, cada postura del móvil tiene su pantalla.
 */
@Composable
fun PliegueApp(screen: ScreenInfo, vm: AppViewModel = viewModel()) {
    val web = LocalWebPanes.current
    LaunchedEffect(web) {
        web.onOpenPost = vm::onWebPost
        web.onDragUser = { _, handle -> vm.dragHandle = handle }
    }
    val real = vm.appMode == AppMode.Real
    Box(
        Modifier
            .fillMaxSize()
            .background(X.Bg)
            .safeDrawingPadding(),
    ) {
        when {
            vm.loggingIn -> LoginScreen(vm)
            vm.appMode == AppMode.Elegir -> WelcomeScreen(vm)
            real && !web.loggedIn -> LoginScreen(vm)
            real -> RealApp(screen, vm)
            else -> when (screen.posture) {
                Posture.Plegado -> FoldedScreen(vm)
                Posture.Abierto -> OpenScreen(vm, screen)
                Posture.Sobremesa -> TabletopScreen(vm, screen)
            }
        }
        ToastHost(vm, Modifier.align(Alignment.BottomCenter).padding(bottom = BottomNavHeight + 56.dp))
        if (vm.composing && !real) ComposerDialog(vm, fullScreen = screen.posture == Posture.Plegado)
        if (vm.settingsOpen) SettingsDialog(vm)
        PopupSheet()
    }
}

/** Plegado (pantalla exterior): una columna, barra inferior y botón de postear. */
@Composable
fun FoldedScreen(vm: AppViewModel) {
    val focused = vm.focusedPanel()
    val open = focused?.let { vm.panels[it].post }
    BackHandler(enabled = focused != null) { focused?.let(vm::closePost) }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (open != null && focused != null) {
                PostDetail(vm, open, onBack = { vm.closePost(focused) }, modifier = Modifier.weight(1f))
            } else {
                FeedColumn(
                    vm,
                    panel = 0,
                    selectedPostId = null,
                    onOpen = { vm.openPost(0, it) },
                    modifier = Modifier.weight(1f),
                    header = { FoldedTopBar { vm.settingsOpen = true } },
                )
            }
            BottomNav(vm)
        }
        if (open == null) {
            Fab(
                onClick = { vm.composing = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = BottomNavHeight + 16.dp),
            )
        }
    }
}

/**
 * Abierto: dos mitades separadas por la bisagra. Cada panel acaba antes del pliegue
 * (24 dp en pantallas grandes, 12 dp en las pequeñas). Tocar un post lo abre en la otra mitad.
 * En modo Columnas, el tablero reparte columnas a cada lado de la bisagra.
 */
@Composable
fun OpenScreen(vm: AppViewModel, screen: ScreenInfo) {
    val density = LocalDensity.current
    var originX by remember { mutableFloatStateOf(0f) }
    val columns = vm.mode == OpenMode.Columnas
    BackHandler(enabled = if (columns) vm.columnPost != null else vm.focusedPanel() != null) {
        if (columns) vm.closeColumnPost() else vm.focusedPanel()?.let(vm::closePost)
    }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { originX = it.positionInWindow().x },
    ) {
        val rail = maxWidth >= 840.dp
        val railWidth = if (rail) 72.dp else 0.dp
        val margin = if (maxWidth >= 800.dp) 24.dp else 12.dp
        val origin = with(density) { originX.toDp() }
        val hingeStart = screen.firstPane?.let { it - origin } ?: (maxWidth / 2)
        val leftWidth = (hingeStart - railWidth - margin).coerceAtLeast(200.dp)
        val gap = margin * 2 + screen.hinge

        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                if (rail) NavRail(vm)
                if (columns) {
                    ColumnsBoard(vm, leftWidth, gap, Modifier.weight(1f))
                } else {
                    Pane(vm, 0, Modifier.width(leftWidth).fillMaxHeight().endBorder())
                    Spacer(Modifier.width(gap))
                    Pane(vm, 1, Modifier.weight(1f).fillMaxHeight().startBorder())
                }
            }
            if (!rail) BottomNav(vm)
        }
        OpenToolbar(
            vm,
            compact = maxWidth < 760.dp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (rail) 24.dp else BottomNavHeight + 16.dp),
        )
        if (!rail) {
            Fab(
                onClick = { vm.composing = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = BottomNavHeight + 80.dp),
            )
        }
    }
}

@Composable
private fun Pane(vm: AppViewModel, index: Int, modifier: Modifier) {
    val panel = vm.panels[index]
    val other = vm.panels[1 - index]
    val open = panel.post
    Box(modifier) {
        when {
            open != null -> PostDetail(
                vm,
                open,
                onBack = { vm.closePost(index) },
                // En doble feed el post tapa el feed de esta mitad; el botón vuelve a él.
                returnTo = if (vm.mode == OpenMode.Doble) panel.feedId.label else null,
                showFrom = true,
            )
            index == 1 && vm.mode == OpenMode.Uno -> EmptyDetail()
            else -> FeedColumn(vm, index, selectedPostId = other.post?.id, onOpen = { vm.openPost(index, it) }, crossFeed = true)
        }
    }
}

/**
 * Sobremesa: arriba el contenido (post o feed), abajo lo que se toca (respuestas o teclado).
 */
@Composable
fun TabletopScreen(vm: AppViewModel, screen: ScreenInfo) {
    val focused = vm.focusedPanel()
    val open = focused?.let { vm.panels[it].post }
    val density = LocalDensity.current
    var originY by remember { mutableFloatStateOf(0f) }
    BackHandler(enabled = focused != null) { focused?.let(vm::closePost) }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { originY = it.positionInWindow().y },
    ) {
        val origin = with(density) { originY.toDp() }
        val split = screen.firstPane?.let { it - origin } ?: (maxHeight / 2)
        val topHeight = split.coerceIn(80.dp, (maxHeight - 80.dp).coerceAtLeast(80.dp))
        Column(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .height(topHeight)
                    .fillMaxWidth(),
            ) {
                if (open != null && focused != null) {
                    PostStage(vm, open, onBack = { vm.closePost(focused) })
                } else {
                    FeedColumn(vm, 0, selectedPostId = null, onOpen = { vm.openPost(0, it) })
                }
            }
            Spacer(Modifier.height(screen.hinge))
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .topBorder(),
            ) {
                if (open != null) {
                    Column(Modifier.fillMaxSize()) {
                        ReplyBox(vm, open.id)
                        LazyColumn(Modifier.weight(1f)) {
                            items(vm.replies(open.id), key = { it.id }) { PostCard(it, vm) }
                        }
                    }
                } else {
                    TabletopComposer(vm)
                }
            }
        }
    }
}

@Composable
private fun PostStage(vm: AppViewModel, open: OpenPost, onBack: () -> Unit) {
    val post = vm.post(open.id) ?: return
    Column(Modifier.fillMaxSize()) {
        DetailHeader(onBack)
        Row(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            post.media?.let {
                MediaArt(it, Modifier.fillMaxHeight().aspectRatio(16f / 9f, matchHeightConstraintsFirst = true))
            }
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AuthorRow(post, vm)
                Text(post.text, style = XType.detail)
                Actions(post, vm, big = true)
            }
        }
    }
}

/** Sin post abierto en sobremesa: el feed arriba y el redactor abajo, como un portátil. */
@Composable
private fun TabletopComposer(vm: AppViewModel) {
    var text by rememberSaveable { mutableStateOf("") }
    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Avatar(SampleData.me.id)
            PlainField(text, { text = it }, "¿Qué está pasando?", XType.title.copy(fontWeight = FontWeight.Normal), Modifier.weight(1f))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            ComposerTools(280 - text.length, Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            PillButton(
                "Postear",
                onClick = {
                    vm.publish(text)
                    text = ""
                },
                enabled = text.isNotBlank() && text.length <= 280,
                background = X.Blue,
                content = Color.White,
            )
        }
    }
}

