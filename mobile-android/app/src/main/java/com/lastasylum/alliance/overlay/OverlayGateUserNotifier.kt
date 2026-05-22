package com.lastasylum.alliance.overlay

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.lastasylum.alliance.R

/**
 * Throttled user-visible feedback when the game gate hides overlay UI (permissions / not in game).
 */
internal class OverlayGateUserNotifier(
    private val context: Context,
    private val mainHandler: Handler = Handler(Looper.getMainLooper()),
) {
    enum class BlockReason {
        NO_USAGE_ACCESS,
        NO_DRAW_OVERLAY,
        WAITING_FOR_GAME,
    }

    private var lastToastAtMs = 0L
    private var lastToastReason: BlockReason? = null

    fun notificationText(reason: BlockReason?): String = when (reason) {
        BlockReason.NO_USAGE_ACCESS -> context.getString(R.string.overlay_gate_no_usage_access)
        BlockReason.NO_DRAW_OVERLAY -> context.getString(R.string.overlay_gate_no_draw_overlay)
        BlockReason.WAITING_FOR_GAME -> context.getString(R.string.overlay_gate_waiting_for_game)
        null -> context.getString(R.string.overlay_notif_fgs_idle)
    }

    fun maybeToast(reason: BlockReason, openOverlaySettings: (() -> Unit)? = null) {
        val now = System.currentTimeMillis()
        if (reason == lastToastReason && now - lastToastAtMs < TOAST_MIN_INTERVAL_MS) return
        lastToastReason = reason
        lastToastAtMs = now
        val message = notificationText(reason)
        mainHandler.post {
            Toast.makeText(context.applicationContext, message, Toast.LENGTH_LONG).show()
            if (reason == BlockReason.NO_USAGE_ACCESS || reason == BlockReason.NO_DRAW_OVERLAY) {
                openOverlaySettings?.invoke()
            }
        }
    }

    private companion object {
        const val TOAST_MIN_INTERVAL_MS = 30_000L
    }
}
