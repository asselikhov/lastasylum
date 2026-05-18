package com.lastasylum.alliance.push

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.lastasylum.alliance.BuildConfig
import com.lastasylum.alliance.di.AppContainer
import kotlinx.coroutines.tasks.await

object FcmTokenManager {
    private const val TAG = "FcmTokenManager"

    suspend fun registerWithBackend(context: Context): Result<Unit> {
        val app = context.applicationContext
        if (FirebaseApp.getApps(app).isEmpty()) {
            if (BuildConfig.DEBUG) {
                Log.w(TAG, "Firebase not initialized (missing google-services.json / BuildConfig)")
            }
            return Result.failure(IllegalStateException("firebase_not_configured"))
        }
        val token = runCatching { FirebaseMessaging.getInstance().token.await() }
            .getOrElse { e ->
                if (BuildConfig.DEBUG) Log.w(TAG, "FCM token fetch failed", e)
                return Result.failure(e)
            }
        if (token.isBlank()) {
            return Result.failure(IllegalStateException("empty_fcm_token"))
        }
        return AppContainer.from(context).usersRepository.registerPushToken(token).also { r ->
            r.onFailure { e ->
                if (BuildConfig.DEBUG) Log.w(TAG, "registerPushToken API failed", e)
            }
            if (BuildConfig.DEBUG && r.isSuccess) {
                Log.d(TAG, "push token registered (${token.take(12)}…)")
            }
        }
    }

    suspend fun unregister(context: Context) {
        val app = context.applicationContext
        runCatching { AppContainer.from(app).usersRepository.clearPushTokens() }
        if (FirebaseApp.getApps(app).isNotEmpty()) {
            runCatching { FirebaseMessaging.getInstance().deleteToken().await() }
        }
    }
}
