package io.github.mastin95.pliegue.ui.x

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.mastin95.pliegue.model.XNoteKind
import io.github.mastin95.pliegue.model.XNotification
import io.github.mastin95.pliegue.model.XTweet
import io.github.mastin95.pliegue.ui.XIcons
import io.github.mastin95.pliegue.ui.components.bottomBorder
import io.github.mastin95.pliegue.ui.theme.X
import io.github.mastin95.pliegue.ui.theme.XType

/**
 * Un aviso de Notificaciones como lo pinta X: el icono a la izquierda (corazón, repost, persona), las
 * fotos de quienes lo provocan, el texto con los nombres en negrita y, debajo en gris, el post al que
 * se refiere. Tocarlo abre ese post (o el perfil de quien te siguió) en la otra mitad.
 */
@Composable
fun NotificationRow(n: XNotification, target: XTweet?, taps: PostTaps) {
    val open: () -> Unit = {
        when {
            target != null -> taps.onOpen(target)
            n.kind == XNoteKind.Follow && n.users.isNotEmpty() -> taps.onUser(n.users.first().handle)
            n.url != null -> taps.onLink(n.url)
            else -> {}
        }
    }
    val (icon, tint) = when (n.kind) {
        XNoteKind.Like -> XIcons.LikeFilled to X.Pink
        XNoteKind.Repost -> XIcons.Repost to X.Green
        XNoteKind.Follow -> XIcons.User to X.Blue
        XNoteKind.Other -> XIcons.Bell to X.Blue
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Abrir", onClick = open)
            .bottomBorder()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
    ) {
        Box(Modifier.width(40.dp), contentAlignment = Alignment.TopEnd) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (n.users.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    n.users.take(8).forEach { u -> XAvatar(u.avatar, 32.dp, onClick = { taps.onUser(u.handle) }) }
                }
            }
            Text(boldText(n.text, n.bold), style = XType.body)
            if (target != null && target.text.isNotEmpty()) {
                Text(target.text, style = XType.secondary, maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private fun boldText(text: String, bold: List<IntRange>) = buildAnnotatedString {
    append(text)
    for (r in bold) {
        val start = r.first.coerceIn(0, text.length)
        val end = (r.last + 1).coerceIn(start, text.length)
        if (end > start) addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
    }
}
