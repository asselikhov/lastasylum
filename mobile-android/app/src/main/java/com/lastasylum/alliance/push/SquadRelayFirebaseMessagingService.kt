package com.lastasylum.alliance.push

import com.lastasylum.alliance.di.AppContainer
import com.lastasylum.alliance.overlay.CombatOverlayService
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SquadRelayFirebaseMessagingService : FirebaseMessagingService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        ExcavationPushNotifications.ensureChannel(this)
    }

    override fun onNewToken(token: String) {
        scope.launch {
            runCatching {
                AppContainer.from(applicationContext).usersRepository.registerPushToken(token)
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val dataType = message.data["type"]
        if (dataType == "excavation_alert") {
            val app = applicationContext
            val prefs = AppContainer.from(app).userSettingsPreferences
            if (!prefs.isExcavationPushEnabled()) {
                return
            }
            if (CombatOverlayService.overlayVisible.value) {
                return
            }
            val title = message.notification?.title
                ?: message.data["title"]
                ?: getString(com.lastasylum.alliance.R.string.excavation_push_default_title)
            val body = message.notification?.body
                ?: message.data["body"]
                ?: message.data["senderName"]
                ?: ""
            ExcavationPushNotifications.show(
                context = applicationContext,
                title = title,
                body = body,
                roomId = message.data["roomId"],
            )
            return
        }
        super.onMessageReceived(message)
    }
}
