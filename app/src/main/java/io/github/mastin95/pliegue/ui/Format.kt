package io.github.mastin95.pliegue.ui

import java.util.Calendar
import java.util.Locale

/** Recuentos como los muestra X en español: 980, 1,2 mil, 34 mil, 1,5 M. */
fun count(n: Int): String = when {
    n < 1_000 -> n.toString()
    n < 10_000 -> "${oneDecimal(n / 1_000.0)} mil"
    n < 1_000_000 -> "${Math.round(n / 1_000.0)} mil"
    else -> "${oneDecimal(n / 1_000_000.0)} M"
}

private fun oneDecimal(v: Double): String =
    String.format(Locale.ROOT, "%.1f", v).removeSuffix(".0").replace('.', ',')

/** Tiempo de un vídeo como en la barra de X: "0:17", "12:05", "1:02:33". */
fun clock(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, sec) else String.format(Locale.ROOT, "%d:%02d", m, sec)
}

fun initials(name: String): String {
    val parts = name.replace(".", "").split(" ").filter { it.isNotBlank() }
    return ((parts.getOrNull(0)?.take(1) ?: "") + (parts.getOrNull(1)?.take(1) ?: "")).uppercase()
}

/** true si conviene texto oscuro sobre ese color (avatares amarillos). */
fun isLight(argb: Long): Boolean {
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    return 0.299 * r + 0.587 * g + 0.114 * b > 170
}

fun nowLabel(): String {
    val c = Calendar.getInstance()
    val h = c.get(Calendar.HOUR_OF_DAY)
    val m = c.get(Calendar.MINUTE).toString().padStart(2, '0')
    val months = listOf("ene.", "feb.", "mar.", "abr.", "may.", "jun.", "jul.", "ago.", "sept.", "oct.", "nov.", "dic.")
    val hour12 = if (h % 12 == 0) 12 else h % 12
    val ampm = if (h < 12) "a. m." else "p. m."
    return "$hour12:$m $ampm · ${c.get(Calendar.DAY_OF_MONTH)} ${months[c.get(Calendar.MONTH)]} ${c.get(Calendar.YEAR)}"
}
