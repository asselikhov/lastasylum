package com.lastasylum.alliance.push

import com.lastasylum.alliance.di.AppContainer
import com.lastasylum.alliance.gameevents.GameEventCatalog
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
        GameEventPushNotifications.ensureChannels(this)
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
        when (dataType) {
            "game_event_alert" -> handleGameEventAlert(message)
            "excavation_alert" -> handleGameEventAlert(
                message,
                fallbackEventId = "hq_excavation",
            )
            else -> super.onMessageReceived(message)
        }
    }

    private fun handleGameEventAlert(
        message: RemoteMessage,
        fallbackEventId: String? = null,
    ) {
        val eventId = message.data["eventId"]?.trim()
            ?: fallbackEventId
            ?: return
        val event = GameEventCatalog.byId(eventId) ?: return
        val app = applicationContext
        val prefs = AppContainer.from(app).userSettingsPreferences
        if (!prefs.isGameEventPushEnabled(eventId)) {
            return
        }
        if (CombatOverlayService.inGameOverlayUiActive.value) {
            return
        }
        val title = message.data["title"]
            ?: message.notification?.title
            ?: event.messageText
        val body = message.data["body"]
            ?: message.notification?.body
            ?: message.data["senderName"]
            ?: event.messageText
        GameEventPushNotifications.show(
            context = app,
            event = event,
            title = title,
            body = body,
            roomId = message.data["roomId"],
        )
    }
}
