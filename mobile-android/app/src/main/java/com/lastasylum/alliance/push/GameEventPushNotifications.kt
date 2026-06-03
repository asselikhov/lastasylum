package com.lastasylum.alliance.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import com.lastasylum.alliance.MainActivity
import com.lastasylum.alliance.R
import com.lastasylum.alliance.gameevents.GameEventCatalog
import com.lastasylum.alliance.gameevents.GameEventDefinition

/** Per-event channels under group «Игровые события». */
object GameEventPushNotifications {
    const val CHANNEL_GROUP_ID = "game_events"

    /** Ungrouped channels removed on startup (were listed under «Прочее»). */
    private val LEGACY_UNGROUPED_CHANNEL_IDS = listOf(
        ExcavationPushNotifications.CHANNEL_ID,
        "squadrelay_push_default",
        "alliance_chat_messages",
        "fcm_fallback_notification_channel",
    )

    private const val NOTIFICATION_ID_BASE = 43_000
    private const val TAG = "GameEventPush"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        dropLegacyUngroupedChannels(manager)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val group = NotificationChannelGroup(
                CHANNEL_GROUP_ID,
                context.getString(R.string.game_event_push_group_name),
            )
            manager.createNotificationChannelGroup(group)
        }
        for (event in GameEventCatalog.all) {
            val channel = NotificationChannel(
                event.channelId,
                event.messageText,
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(
                    R.string.game_event_push_channel_desc,
                    event.messageText,
                )
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 120, 80, 120)
                enableLights(true)
                lightColor = GameEventCatalog.notificationColor(event.category)
                setShowBadge(true)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    group = CHANNEL_GROUP_ID
                }
            }
            manager.createNotificationChannel(channel)
        }
    }

    /** Drops channels that must not appear in system «Прочее» (only overlay FGS stays there). */
    fun dropLegacyUngroupedChannels(manager: NotificationManager) {
        for (id in LEGACY_UNGROUPED_CHANNEL_IDS) {
            manager.deleteNotificationChannel(id)
        }
    }

    fun show(
        context: Context,
        event: GameEventDefinition,
        title: String,
        body: String,
        roomId: String?,
        senderDisplayName: String = "",
        senderLargeIcon: Bitmap? = null,
    ) {
        ensureChannels(context)
        val launch = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(ExcavationPushNotifications.EXTRA_OPEN_CHAT_ROOM_ID, roomId)
        }
        val pending = PendingIntent.getActivity(
            context,
            (event.id + (roomId ?: "")).hashCode(),
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val color = GameEventCatalog.notificationColor(event.category)
        val eventTitle = title.ifBlank { event.messageText }
        val eventBody = body.ifBlank { event.messageText }
        val senderName = senderDisplayName.trim().ifBlank { " " }
        val senderPersonBuilder = Person.Builder().setName(senderName)
        senderLargeIcon?.let { bmp ->
            senderPersonBuilder.setIcon(IconCompat.createWithBitmap(bmp))
        }
        val senderPerson = senderPersonBuilder.build()
        val localPerson = Person.Builder().setName("").build()
        val style = NotificationCompat.MessagingStyle(localPerson)
            .setConversationTitle(eventTitle)
            .addMessage(eventBody, System.currentTimeMillis(), senderPerson)
        val builder = NotificationCompat.Builder(context, event.channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(eventTitle)
            .setContentText(eventBody)
            .setStyle(style)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setColor(color)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
        senderLargeIcon?.let { builder.setLargeIcon(it) }
        val notification = builder.build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                Log.w(TAG, "POST_NOTIFICATIONS not granted — game event push skipped")
                return
            }
        }
        val id = NOTIFICATION_ID_BASE + event.id.hashCode().and(0x7FFF)
        runCatching {
            NotificationManagerCompat.from(context).notify(id, notification)
        }.onFailure { e ->
            Log.w(TAG, "notify failed eventId=${event.id}", e)
        }
    }
}
