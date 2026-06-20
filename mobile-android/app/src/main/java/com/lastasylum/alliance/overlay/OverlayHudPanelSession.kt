package com.lastasylum.alliance.overlay

/**
 * Guards async overlay panel close cleanup against a newer open session.
 * Without this, [CombatOverlayService.runOverlayPanelClosePipeline] can reset
 * [OverlayHudPane.Chat] after the user already opened another pane.
 */
internal object OverlayHudPanelSession {
    fun shouldApplyCloseCleanup(
        closingSession: Int,
        currentSession: Int,
        panelVisible: Boolean,
    ): Boolean = closingSession == currentSession && !panelVisible
}
