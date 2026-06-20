package com.lastasylum.alliance.push

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lastasylum.alliance.di.AppContainer
import com.lastasylum.alliance.overlay.OverlayRuntimeDiagnostics

/** Periodic FCM token sync when overlay FGS / MainActivity may be absent. */
class PushTokenRefreshWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = AppContainer.from(applicationContext)
        if (!container.authRepository.hasSession()) {
            return Result.success()
        }
        val result = FcmTokenManager.registerWithBackend(applicationContext)
        return if (result.isSuccess) {
            OverlayRuntimeDiagnostics.recordFcmTokenRegistered()
            Result.success()
        } else {
            Result.retry()
        }
    }
}
