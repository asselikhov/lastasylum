package com.lastasylum.alliance.ui.chat

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
