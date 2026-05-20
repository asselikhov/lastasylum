package com.lastasylum.alliance.ui.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Окно свежести пинга оверлея (~3× heartbeat оверлея 60 с). */
const val OVERLAY_INGAME_PRESENCE_STALE_MS = 180_000L

fun formatPresenceTimestampRu(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    return runCatching {
        val z = java.time.ZonedDateTime.ofInstant(Instant.parse(iso.trim()), ZoneId.systemDefault())
        z.format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale("ru")))
    }.getOrDefault("")
}

/** Онлайн в игре = активный оверлей (status ingame + свежий lastPresenceAt). */
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
        val instant = Instant.parse(iso)
        java.time.Duration.between(instant, Instant.now()).toMillis() <= staleMs
    }.getOrDefault(false)
}
