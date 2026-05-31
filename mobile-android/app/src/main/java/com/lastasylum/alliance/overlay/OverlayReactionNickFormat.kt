package com.lastasylum.alliance.overlay

internal object OverlayReactionNickFormat {
    fun format(displayName: String): String {
        val trimmed = displayName.trim().ifBlank { "—" }
        return trimmed.removePrefix("@").trim().ifBlank { "—" }
    }
}
