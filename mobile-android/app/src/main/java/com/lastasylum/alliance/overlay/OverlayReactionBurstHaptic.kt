package com.lastasylum.alliance.overlay

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.core.content.ContextCompat

internal object OverlayReactionBurstHaptic {
    fun lightTap(context: Context, anchorView: View?) {
        runCatching {
            if (anchorView?.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) == true) return
            if (!hasVibratePermission(context)) return
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            } ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(10L)
            }
        }
    }

    private fun hasVibratePermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.VIBRATE) ==
            PackageManager.PERMISSION_GRANTED
}
