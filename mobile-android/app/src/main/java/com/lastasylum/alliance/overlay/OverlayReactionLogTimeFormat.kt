package com.lastasylum.alliance.overlay

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.lastasylum.alliance.ui.util.APP_DISPLAY_ZONE
import com.lastasylum.alliance.ui.util.chatDayKeyMsk
import com.lastasylum.alliance.ui.util.formatChatTimeMsk
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

fun formatOverlayReactionLogTimeLabel(createdAtIso: String): String {
    val (absolute, relative) = formatOverlayReactionLogTimeLine(createdAtIso)
    return when {
        relative.isBlank() -> absolute
        absolute.isBlank() -> relative
        relative == absolute -> absolute
        else -> listOf(absolute, relative).joinToString(" · ")
    }
}

/** Одна метка времени для карточек ленты (без «15:30 · 5 мин назад»). */
fun formatOverlayReactionLogTimeLabelCompact(createdAtIso: String): String {
    val (absolute, relative) = formatOverlayReactionLogTimeLine(createdAtIso)
    return when {
        relative.isNotBlank() && relative != absolute -> relative
        absolute.isNotBlank() -> absolute
        else -> relative
    }
}

/** Ключ дня по МСК — как [chatDayKeyMsk] в чат-комнатах. */
fun overlayReactionLogDateHeaderKey(createdAtIso: String): String =
    chatDayKeyMsk(createdAtIso).orEmpty()

/** Время для sheet превью реакции (МСК, как в чате). */
fun formatOverlayReactionLogPreviewTime(createdAtIso: String, incoming: Boolean): String {
    val instant = parseIsoInstant(createdAtIso.trim()) ?: return ""
    val zdt = instant.atZone(APP_DISPLAY_ZONE)
    val now = Instant.now().atZone(APP_DISPLAY_ZONE)
    val clock = formatChatTimeMsk(createdAtIso)
    if (clock.isBlank()) return ""
    val dayLabel = when (zdt.toLocalDate()) {
        now.toLocalDate() -> null
        now.toLocalDate().minusDays(1) -> "вчера"
        else ->
            zdt.format(DateTimeFormatter.ofPattern("d MMM", Locale("ru")))
    }
    val prefix = when {
        dayLabel == null -> if (incoming) "получено " else "отправлено "
        else -> if (incoming) "получено $dayLabel, " else "отправлено $dayLabel, "
    }
    return prefix + clock
}
