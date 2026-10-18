package io.github.mastin95.pliegue.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mastin95.pliegue.model.SampleData
import io.github.mastin95.pliegue.ui.XIcons
import io.github.mastin95.pliegue.ui.initials
import io.github.mastin95.pliegue.ui.isLight
import io.github.mastin95.pliegue.ui.theme.X
import io.github.mastin95.pliegue.ui.theme.XType

const val SOON = "Esta sección llega en la próxima versión."

// Separadores de 1 dp como los de X.
fun Modifier.bottomBorder(color: Color = X.Border) = drawBehind {
    val w = 1.dp.toPx()
    drawLine(color, Offset(0f, size.height - w / 2), Offset(size.width, size.height - w / 2), w)
}

fun Modifier.topBorder(color: Color = X.Border) = drawBehind {
    val w = 1.dp.toPx()
    drawLine(color, Offset(0f, w / 2), Offset(size.width, w / 2), w)
}

fun Modifier.startBorder(color: Color = X.Border) = drawBehind {
    val w = 1.dp.toPx()
    drawLine(color, Offset(w / 2, 0f), Offset(w / 2, size.height), w)
}

fun Modifier.endBorder(color: Color = X.Border) = drawBehind {
    val w = 1.dp.toPx()
    drawLine(color, Offset(size.width - w / 2, 0f), Offset(size.width - w / 2, size.height), w)
}

@Composable
fun Avatar(userId: String, size: Dp = 40.dp) {
    val u = SampleData.users.getValue(userId)
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(u.color)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (userId == SampleData.me.id) "MA" else initials(u.name),
            color = if (isLight(u.color)) X.Ink else Color.White,
            fontSize = (size.value * 0.36f).sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun VerifiedBadge(size: Dp = 18.dp) {
    Box(Modifier.size(size)) {
        Icon(XIcons.Verified, contentDescription = "Cuenta verificada", tint = X.Blue, modifier = Modifier.fillMaxSize())
        Icon(XIcons.Check, contentDescription = null, tint = Color.White, modifier = Modifier.fillMaxSize())
    }
}

/** Marcador propio en lugar del logotipo de X. */
@Composable
fun Logo(size: Dp = 28.dp) {
    Box(
        Modifier
            .size(size)
            .border(2.dp, X.Text, RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text("P", color = X.Text, fontWeight = FontWeight.ExtraBold, fontSize = (size.value * 0.5f).sp)
    }
}

/** Botón redondeado de X: blanco por defecto, azul para publicar, con borde para "Siguiendo". */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    background: Color = X.FollowBg,
    content: Color = X.Ink,
    border: Color? = null,
    height: Dp = 36.dp,
    icon: ImageVector? = null,
) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .height(height)
            .alpha(if (enabled) 1f else 0.5f)
            .clip(shape)
            .background(background)
            .then(if (border != null) Modifier.border(1.dp, border, shape) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(16.dp))
        Text(text, style = XType.button.copy(fontSize = if (height < 36.dp) 14.sp else 15.sp), color = content, maxLines = 1)
    }
}

@Composable
fun FollowButton(following: Boolean, onClick: () -> Unit) {
    if (following) {
        PillButton("Siguiendo", onClick, background = Color.Transparent, content = X.Text, border = X.OutlineButton, height = 32.dp)
    } else {
        PillButton("Seguir", onClick, height = 32.dp)
    }
}

/** Campo de texto sin adornos, con el placeholder gris de X. */
@Composable
fun PlainField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        textStyle = style.copy(color = X.Text),
        cursorBrush = SolidColor(X.Blue),
        modifier = modifier.then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) Text(placeholder, style = style.copy(color = X.Secondary))
                inner()
            }
        },
    )
}

@Composable
fun NavIcon(icon: ImageVector, label: String, onClick: () -> Unit, size: Dp = 26.dp) {
    Box(
        Modifier
            .size(50.dp)
            .clip(CircleShape)
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = X.Text, modifier = Modifier.size(size))
    }
}
