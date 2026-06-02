package com.lastasylum.alliance.overlay

/**
 * Когда восстанавливать [CombatOverlayService] полноэкранное окно панели (VISIBLE + touchable flags).
 *
 * После [CombatOverlayService.hideOverlayChatTeamPanelNow] оба флага сброшены — повторный restore
 * не должен снова показывать окно (иначе крестик «не закрывает»).
 */
internal object OverlayFullscreenPanelRestorePolicy {
    fun shouldRestorePanelWindow(
        servicePanelVisible: Boolean,
        holdPanelVisible: Boolean,
    ): Boolean = servicePanelVisible || holdPanelVisible
}
