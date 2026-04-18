package com.lastasylum.alliance.push

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.lastasylum.alliance.di.AppContainer
import kotlinx.coroutines.tasks.await

object FcmTokenManager {
    suspend fun registerWithBackend(context: Context) {
        if (FirebaseApp.getApps(context.applicationContext).isEmpty()) return
        val token = runCatching { FirebaseMessaging.getInstance().token.await() }.getOrNull()
            ?: return
        AppContainer.from(context).usersRepository.registerPushToken(token)
    }

    suspend fun unregister(context: Context) {
        val app = context.applicationContext
        runCatching { AppContainer.from(app).usersRepository.clearPushTokens() }
        if (FirebaseApp.getApps(app).isNotEmpty()) {
            runCatching { FirebaseMessaging.getInstance().deleteToken().await() }
        }
    }
}
