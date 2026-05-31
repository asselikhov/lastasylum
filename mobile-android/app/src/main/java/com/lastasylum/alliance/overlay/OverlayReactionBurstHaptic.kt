package com.lastasylum.alliance.overlay

import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.core.content.ContextCompat

internal object OverlayReactionBurstHaptic {
    private val mainHandler = Handler(Looper.getMainLooper())

    fun lightTap(context: android.content.Context, anchorView: View?) {
        runCatching {
            if (anchorView?.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) == true) return
            vibrateOnce(context, VibrationEffect.EFFECT_TICK)
        }
    }

    fun mergePulse(context: android.content.Context, anchorView: View?) {
        runCatching {
            if (anchorView?.performHapticFeedback(HapticFeedbackConstants.CONFIRM) == true) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                vibrateOnce(context, VibrationEffect.EFFECT_DOUBLE_CLICK)
            } else {
                vibrateOnce(context, VibrationEffect.EFFECT_TICK)
                mainHandler.postDelayed({
                    vibrateOnce(context, VibrationEffect.EFFECT_TICK)
                }, 40L)
            }
        }
    }

    private fun vibrateOnce(context: android.content.Context, effectId: Int) {
        if (!hasVibratePermission(context)) return
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? Vibrator
        } ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createPredefined(effectId))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(10L)
        }
    }

    private fun hasVibratePermission(context: android.content.Context): Boolean =
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.VIBRATE) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
}
