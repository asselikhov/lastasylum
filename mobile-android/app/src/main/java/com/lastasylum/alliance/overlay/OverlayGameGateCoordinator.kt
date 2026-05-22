package com.lastasylum.alliance.overlay

/**
 * Thin coordinator for overlay game-gate UX (permissions / not in game).
 * Core detection remains in [GameForegroundGate]; [CombatOverlayService] applies window policy.
 */
internal class OverlayGameGateCoordinator(
    private val notifier: OverlayGateUserNotifier,
    private val onBlocked: (OverlayGateUserNotifier.BlockReason) -> Unit,
) {
    fun onMissingUsageAccess() = onBlocked(OverlayGateUserNotifier.BlockReason.NO_USAGE_ACCESS)

    fun onMissingDrawOverlay() = onBlocked(OverlayGateUserNotifier.BlockReason.NO_DRAW_OVERLAY)

    fun onWaitingForGame() = onBlocked(OverlayGateUserNotifier.BlockReason.WAITING_FOR_GAME)

    fun notificationText(reason: OverlayGateUserNotifier.BlockReason?): String =
        notifier.notificationText(reason)
}
