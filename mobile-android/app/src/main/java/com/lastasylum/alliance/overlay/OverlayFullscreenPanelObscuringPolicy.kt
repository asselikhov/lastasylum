package com.lastasylum.alliance.overlay

/**
 * When the fullscreen HUD pane (News/Forum/Chat) is treated as obscuring corner overlay buttons.
 *
 * Root [android.view.View.VISIBLE] alone is not enough: picker resume / z-order repair can leave
 * the panel window visible briefly after [CombatOverlayService.hideOverlayChatTeamPanelNow]
 * cleared session flags — that blocked [restoreOverlayHudChromeAfterPanel].
 */
internal object OverlayFullscreenPanelObscuringPolicy {
    fun isObscuring(
        servicePanelVisible: Boolean,
        holdPanelVisible: Boolean,
        panelRootVisible: Boolean,
        hasActiveHudPane: Boolean,
    ): Boolean =
        servicePanelVisible ||
            holdPanelVisible ||
            (panelRootVisible && hasActiveHudPane)
}
