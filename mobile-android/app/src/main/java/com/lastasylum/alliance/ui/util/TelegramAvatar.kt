package com.lastasylum.alliance.ui.util

/** Public avatar URL via unavatar (HTTPS); null if username missing. */
fun telegramAvatarUrl(telegramUsername: String?): String? {
    val u = telegramUsername?.trim()?.removePrefix("@")?.lowercase().orEmpty()
    if (u.isBlank()) return null
    return "https://unavatar.io/telegram/$u"
}

fun telegramDisplayHandle(telegramUsername: String?): String? {
    val u = telegramUsername?.trim()?.removePrefix("@")?.lowercase().orEmpty()
    if (u.isBlank()) return null
    return "@$u"
}
