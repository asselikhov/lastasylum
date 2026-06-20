package com.lastasylum.alliance.overlay

/**
 * Когда после закрытия fullscreen-панели снова показывать HUD/ленту «в игре».
 *
 * [CombatOverlayService.softHideOverlayUiBecauseNotInGame] может скрыть chips (GONE);
 * при закрытии панели их нужно явно восстановить, если сессия ещё «в игре» или на hold.
 */
internal object OverlayHudChromeRestorePolicy {
    fun shouldRestoreInGameWindowVisibility(
        inGameActive: Boolean,
        holdActive: Boolean,
        fullscreenPanelObscuring: Boolean,
    ): Boolean = (inGameActive || holdActive) && !fullscreenPanelObscuring
}
