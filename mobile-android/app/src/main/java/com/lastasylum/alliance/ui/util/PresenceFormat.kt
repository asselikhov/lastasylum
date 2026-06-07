package com.lastasylum.alliance.ui.util

import java.time.Duration
import java.time.Instant

/** «В игре» — свежий ingame ping (синхрон с push exclusion / heartbeat). */
const val OVERLAY_INGAME_LIVE_MS = 45_000L

/** Окно «недавно активен» (~2.5× heartbeat оверлея 45 с). */
const val OVERLAY_INGAME_PRESENCE_STALE_MS = 120_000L

/** Fallback poll when panel is backgrounded and socket is disconnected. */
const val OVERLAY_ONLINE_PANEL_POLL_MS = 60_000L

/** Poll interval when presence socket is connected (socket drives most updates). */
const val OVERLAY_ONLINE_PANEL_POLL_SOCKET_MS = 180_000L

/** Faster poll when panel is open but socket is disconnected. */
const val OVERLAY_ONLINE_PANEL_POLL_FAST_MS = 45_000L

/** UI tick for relative last-seen labels without network refresh. */
const val OVERLAY_ONLINE_DISPLAY_CLOCK_MS = 60_000L

fun resolveOverlayOnlinePanelPollMs(
    socketConnected: Boolean,
    panelForeground: Boolean,
): Long = when {
    socketConnected -> OVERLAY_ONLINE_PANEL_POLL_SOCKET_MS
    panelForeground -> OVERLAY_ONLINE_PANEL_POLL_FAST_MS
    else -> OVERLAY_ONLINE_PANEL_POLL_MS
}

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
    staleMs: Long = OVERLAY_INGAME_LIVE_MS,
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
