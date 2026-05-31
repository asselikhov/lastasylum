package com.lastasylum.alliance.overlay

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.lastasylum.alliance.ui.util.parseIsoInstant

fun formatOverlayReactionLogTimeLine(createdAtIso: String): Pair<String, String> {
    val iso = createdAtIso.trim()
    if (iso.isEmpty()) return "" to ""
    val instant = parseIsoInstant(iso) ?: return "" to ""
    val zone = ZoneId.systemDefault()
    val zdt = instant.atZone(zone)
    val now = Instant.now().atZone(zone)
    val clock = DateTimeFormatter.ofPattern("HH:mm", Locale("ru"))
    val absolute = zdt.format(clock)
    val relative = when {
        zdt.toLocalDate() == now.toLocalDate() -> {
            val mins = java.time.Duration.between(instant, Instant.now()).toMinutes().coerceAtLeast(0)
            when {
                mins < 1 -> "только что"
                mins < 60 -> "$mins мин назад"
                else -> absolute
            }
        }
        zdt.toLocalDate() == now.toLocalDate().minusDays(1) -> "вчера $absolute"
        else -> zdt.format(DateTimeFormatter.ofPattern("d MMM · HH:mm", Locale("ru")))
    }
    return absolute to relative
}

fun overlayReactionLogDateHeaderKey(createdAtIso: String): String {
    val instant = parseIsoInstant(createdAtIso.trim()) ?: return ""
    val zdt = instant.atZone(ZoneId.systemDefault())
    val today = Instant.now().atZone(ZoneId.systemDefault()).toLocalDate()
    return when (zdt.toLocalDate()) {
        today -> "today"
        today.minusDays(1) -> "yesterday"
        else -> zdt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    }
}
