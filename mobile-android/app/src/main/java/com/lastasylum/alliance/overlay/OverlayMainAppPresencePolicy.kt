package com.lastasylum.alliance.overlay

/**
 * Main-app presence pings must not downgrade overlay ingame when the target game is still foreground.
 */
internal object OverlayMainAppPresencePolicy {
    /** Defer away ping so FGS game gate can finish a probe after ON_START. */
    const val AWAY_PING_DEFER_MS = 2_500L

    fun shouldSkipAwayPing(
        inGameOverlayUiActive: Boolean,
        targetGameForeground: Boolean,
        overlayForegroundServiceActive: Boolean = false,
        overlayIngamePresenceActive: Boolean = false,
    ): Boolean =
        inGameOverlayUiActive ||
            targetGameForeground ||
            overlayForegroundServiceActive ||
            overlayIngamePresenceActive

    fun shouldSkipOnlinePing(
        inGameOverlayUiActive: Boolean,
        targetGameForeground: Boolean,
        overlayForegroundServiceActive: Boolean = false,
        overlayIngamePresenceActive: Boolean = false,
    ): Boolean =
        inGameOverlayUiActive ||
            targetGameForeground ||
            overlayForegroundServiceActive ||
            overlayIngamePresenceActive
}
