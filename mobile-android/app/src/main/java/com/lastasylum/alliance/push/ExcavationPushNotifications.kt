package com.lastasylum.alliance.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.lastasylum.alliance.MainActivity
import com.lastasylum.alliance.R

/** High-importance channel for alliance excavation coords (off-game allies). */
object ExcavationPushNotifications {
    const val CHANNEL_ID = "excavation_alerts"
    private const val NOTIFICATION_ID_BASE = 42_001

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.excavation_push_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.excavation_push_channel_desc)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 120, 80, 120)
            enableLights(true)
            lightColor = Color.parseColor("#FFB86B00")
            setShowBadge(true)
        }
        manager.createNotificationChannel(channel)
    }

    fun show(
        context: Context,
        title: String,
        body: String,
        roomId: String?,
    ) {
        ensureChannel(context)
        val launch = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_CHAT_ROOM_ID, roomId)
        }
        val pending = PendingIntent.getActivity(
            context,
            roomId?.hashCode() ?: 0,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title.ifBlank { context.getString(R.string.excavation_push_default_title) })
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setColor(Color.parseColor("#FF3D5AFE"))
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()
        val id = NOTIFICATION_ID_BASE + (roomId?.hashCode()?.and(0x7FFF) ?: 0)
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    const val EXTRA_OPEN_CHAT_ROOM_ID = "open_chat_room_id"
}
