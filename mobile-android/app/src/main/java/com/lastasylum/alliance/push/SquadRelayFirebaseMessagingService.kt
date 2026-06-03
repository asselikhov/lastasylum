package com.lastasylum.alliance.push

import com.lastasylum.alliance.di.AppContainer
import com.lastasylum.alliance.ui.util.chatSenderDisplayLine
import com.lastasylum.alliance.gameevents.GameEventCatalog
import com.lastasylum.alliance.overlay.CombatOverlayService
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
            "game_event_alert" -> handleGameEventAlertAsync(message)
            "excavation_alert" -> handleGameEventAlertAsync(
                message,
                fallbackEventId = "hq_excavation",
            )
            else -> super.onMessageReceived(message)
        }
    }

    private fun handleGameEventAlertAsync(
        message: RemoteMessage,
        fallbackEventId: String? = null,
    ) {
        scope.launch { handleGameEventAlert(message, fallbackEventId) }
    }

    private suspend fun handleGameEventAlert(
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
        val eventText = message.data["title"]?.trim()?.takeIf { it.isNotEmpty() }
            ?: message.data["eventText"]?.trim()?.takeIf { it.isNotEmpty() }
            ?: message.notification?.title?.trim()?.takeIf { it.isNotEmpty() }
            ?: event.messageText
        val nickname = message.data["senderName"]?.trim().orEmpty()
        val teamTag = message.data["senderTeamTag"]?.trim()?.ifBlank { null }
        val serverNumber = message.data["senderServerNumber"]?.toIntOrNull()?.takeIf { it > 0 }
        val senderLineColored = PushNotificationSenderLineSpans.build(
            teamTag = teamTag,
            username = nickname,
            serverNumber = serverNumber,
        )
        val telegram = message.data["senderTelegramUsername"]?.trim().orEmpty()
        val squadRole = message.data["senderSquadRole"]?.trim().orEmpty()
        val largeIcon = PushNotificationSenderAvatar.loadLargeIcon(
            context = app,
            telegramUsername = telegram.ifBlank { null },
            squadRole = squadRole.ifBlank { null },
            fallbackName = nickname.ifBlank { null },
        )
        withContext(Dispatchers.Main) {
            GameEventPushNotifications.show(
                context = app,
                event = event,
                eventText = eventText,
                roomId = message.data["roomId"],
                senderLine = senderLineColored,
                senderNickname = nickname,
                senderLargeIcon = largeIcon,
            )
        }
    }
}
