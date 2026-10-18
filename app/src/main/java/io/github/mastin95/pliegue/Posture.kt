package io.github.mastin95.pliegue

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo

enum class Posture { Plegado, Abierto, Sobremesa }

/** Bisagra tal como la informa Jetpack WindowManager, ya pasada a dp. */
data class Fold(
    /** true si la bisagra es vertical (abierto como un libro); false si es horizontal. */
    val vertical: Boolean,
    val halfOpened: Boolean,
    val separating: Boolean,
    /** Distancia en dp desde el borde izquierdo (vertical) o superior (horizontal). */
    val start: Float,
    /** Grosor de la bisagra en dp; 0 en pantallas continuas. */
    val size: Float,
)

data class ScreenInfo(
    val posture: Posture,
    /** Ancho (abierto) o alto (sobremesa) del primer panel cuando hay bisagra real. */
    val firstPane: Dp? = null,
    val hinge: Dp = 0.dp,
)

/**
 * Decide la postura:
 * 1. Bisagra horizontal medio doblada = sobremesa.
 * 2. Bisagra vertical que separa la pantalla = abierto, con el corte justo en la bisagra.
 * 3. Sin bisagra útil, el ancho: 600 dp o más = abierto (pantalla interior), menos = plegado.
 */
fun computeScreen(fold: Fold?, widthDp: Int): ScreenInfo {
    if (fold != null) {
        if (!fold.vertical && fold.halfOpened) {
            return ScreenInfo(Posture.Sobremesa, firstPane = fold.start.dp, hinge = fold.size.dp)
        }
        if (fold.vertical && fold.separating) {
            return ScreenInfo(Posture.Abierto, firstPane = fold.start.dp, hinge = fold.size.dp)
        }
    }
    return ScreenInfo(if (widthDp >= 600) Posture.Abierto else Posture.Plegado)
}

@Composable
fun rememberScreenInfo(activity: Activity): ScreenInfo {
    val flow = remember(activity) { WindowInfoTracker.getOrCreate(activity).windowLayoutInfo(activity) }
    val layout: WindowLayoutInfo? by flow.collectAsState(initial = null)
    val config = LocalConfiguration.current
    val density = LocalDensity.current.density
    val feature = layout?.displayFeatures?.filterIsInstance<FoldingFeature>()?.firstOrNull()
    val fold = feature?.let {
        val vertical = it.orientation == FoldingFeature.Orientation.VERTICAL
        val b = it.bounds
        Fold(
            vertical = vertical,
            halfOpened = it.state == FoldingFeature.State.HALF_OPENED,
            separating = it.isSeparating,
            start = (if (vertical) b.left else b.top) / density,
            size = (if (vertical) b.width() else b.height()) / density,
        )
    }
    return remember(fold, config.screenWidthDp) { computeScreen(fold, config.screenWidthDp) }
}
