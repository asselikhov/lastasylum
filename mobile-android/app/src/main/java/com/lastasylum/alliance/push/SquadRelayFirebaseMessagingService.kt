package com.lastasylum.alliance.push

import com.lastasylum.alliance.di.AppContainer
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SquadRelayFirebaseMessagingService : FirebaseMessagingService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        scope.launch {
            runCatching {
                AppContainer.from(applicationContext).usersRepository.registerPushToken(token)
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        // Notifications with notification payload are shown by the system when app in background.
        super.onMessageReceived(message)
    }
}
