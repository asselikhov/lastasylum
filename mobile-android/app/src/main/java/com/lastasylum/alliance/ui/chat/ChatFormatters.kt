package com.lastasylum.alliance.ui.chat

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Calendar day key `yyyy-MM-dd` from ISO-ish server string, or null. */
fun chatDayKey(createdAt: String?): String? {
    if (createdAt.isNullOrBlank()) return null
    val t = createdAt.trim()
    val i = t.indexOf('T')
    if (i >= 10) return t.substring(0, 10)
    return if (t.length >= 10 && t[4] == '-' && t[7] == '-') t.substring(0, 10) else null
}

/** Chip label for a day separator (e.g. «17 апреля»). */
fun formatChatDaySeparator(createdAt: String?): String {
    val key = chatDayKey(createdAt) ?: return ""
    return runCatching {
        val d = LocalDate.parse(key)
        d.format(DateTimeFormatter.ofPattern("d MMMM", Locale("ru")))
    }.getOrDefault("")
}

/** Best-effort short time from ISO-ish server string. */
fun formatChatTime(createdAt: String?): String {
    if (createdAt.isNullOrBlank()) return ""
    val t = createdAt.trim()
    val tIndex = t.indexOf('T')
    if (tIndex in 1 until t.length - 2) {
        val slice = t.substring(tIndex + 1)
        val end = minOf(5, slice.length)
        if (end == 5) return slice.substring(0, 5)
    }
    return ""
}
