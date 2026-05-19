package com.lastasylum.alliance.overlay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Поднимает [CombatOverlayService] после перезагрузки или обновления APK, если панель включена
 * и пользователь авторизован — без открытия SquadRelay.
 */
class OverlayBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        val pending = goAsync()
        try {
            val started = CombatOverlayService.ensureRuntimeIfUserEnabled(context.applicationContext)
            Log.i(TAG, "onReceive action=$action overlayRuntimeStarted=$started")
        } catch (t: Throwable) {
            Log.w(TAG, "onReceive failed action=$action", t)
        } finally {
            pending.finish()
        }
    }

    companion object {
        private const val TAG = "OverlayBootReceiver"
    }
}
