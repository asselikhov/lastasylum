package com.lastasylum.alliance.ui.util

private val ACCOUNT_EMAIL = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

fun looksLikeAccountEmail(value: String): Boolean {
    val trimmed = value.trim()
    if (!trimmed.contains('@')) return false
    return ACCOUNT_EMAIL.matches(trimmed)
}

/** Never show account login/email as a public nickname in UI. */
fun sanitizePublicDisplayName(value: String, fallback: String = "Союзник"): String {
    val trimmed = value.trim()
    if (trimmed.isEmpty() || looksLikeAccountEmail(trimmed)) return fallback
    return trimmed
}
