package com.lastasylum.alliance.ui.util

import java.time.Duration
import java.time.Instant

/** Окно свежести пинга оверлея (~1.5× heartbeat оверлея 60 с). */
const val OVERLAY_INGAME_PRESENCE_STALE_MS = 90_000L

/** Как часто обновлять список «Участники онлайн», пока панель открыта. */
const val OVERLAY_ONLINE_PANEL_POLL_MS = 60_000L

/** Короткая подпись свежести пинга для списка «Участники онлайн». */
fun formatOverlayPresenceAgeRu(lastOverlayPresenceAt: String?): String {
    val iso = lastOverlayPresenceAt?.trim().orEmpty()
    if (iso.isEmpty()) return ""
    return runCatching {
        val instant = parseIsoInstant(iso) ?: return ""
        val mins = Duration.between(instant, Instant.now()).toMinutes().coerceAtLeast(0)
        when {
            mins < 1 -> "только что"
            mins < 60 -> "$mins мин назад"
            else -> {
                val hours = mins / 60
                if (hours < 24) "$hours ч назад" else formatPresenceTimestampRu(iso)
            }
        }
    }.getOrDefault("")
}

fun isOverlayIngameNow(
    presenceStatus: String?,
    lastOverlayPresenceAt: String?,
    staleMs: Long = OVERLAY_INGAME_PRESENCE_STALE_MS,
): Boolean {
    val s = presenceStatus?.trim()?.lowercase() ?: return false
    if (s != "ingame") return false
    val iso = lastOverlayPresenceAt?.trim().orEmpty()
    if (iso.isEmpty()) return false
    return runCatching {
        val instant = parseIsoInstant(iso) ?: return false
        Duration.between(instant, Instant.now()).toMillis() <= staleMs
    }.getOrDefault(false)
}
