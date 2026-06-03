package com.lastasylum.alliance.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.lastasylum.alliance.R
import com.lastasylum.alliance.push.GameEventPushNotifications

/** Foreground-уведомление боевого оверлея (микрофон / FGS). */
object OverlayForegroundNotifications {
    /** v3: IMPORTANCE_MIN — без всплывашек при обновлении FGS при смене игры. */
    const val CHANNEL_ID = "combat_overlay_channel_v3"
    const val NOTIFICATION_ID = 7001

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            GameEventPushNotifications.dropLegacyUngroupedChannels(manager)
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.overlay_channel_name),
                NotificationManager.IMPORTANCE_MIN,
            )
            // HyperOS/MIUI: без явного отключения вибрации иногда уходит паттерн [0] и падает system_server.
            channel.enableVibration(false)
            channel.enableLights(false)
            channel.setSound(null, null)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                channel.setAllowBubbles(false)
            }
            manager.createNotificationChannel(channel)
        }
    }

    fun build(context: Context, content: String, quietMode: Boolean): Notification {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(context.getString(R.string.overlay_notif_title))
            .setContentText(content)
            .setOngoing(true)
            // PRIORITY_MIN на части прошивок даёт «тихое» уведомление с кривым вибропаттерном.
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setDefaults(0)
            .setSound(null)
            .setOnlyAlertOnce(true)
            .setSilent(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_DEFERRED)
        }
        if (quietMode) {
            builder.setSilent(true)
        }
        @Suppress("DEPRECATION")
        builder.setVibrate(null)
        return builder.build()
    }

    fun notify(context: Context, content: String, quietMode: Boolean) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, build(context, content, quietMode))
    }
}
