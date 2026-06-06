package com.lastasylum.alliance.ui.util

fun telegramDisplayHandle(telegramUsername: String?): String? {
    val u = telegramUsername?.trim()?.removePrefix("@")?.lowercase().orEmpty()
    if (u.isBlank()) return null
    return "@$u"
}
