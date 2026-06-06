package com.lastasylum.alliance.overlay

/** Shared unread badge cap/label for overlay HUD, tabs, and in-panel indicators. */
object OverlayBadgeFormat {
    const val CAP = 99

    fun capped(count: Int): Int = count.coerceAtLeast(0).coerceAtMost(CAP)

    fun label(count: Int): String {
        val safe = count.coerceAtLeast(0)
        return if (safe > CAP) "$CAP+" else safe.toString()
    }
}
