package com.lastasylum.alliance.data.chat.outbox

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lastasylum.alliance.data.auth.JwtAccessTokenClaims
import com.lastasylum.alliance.di.AppContainer

/**
 * Resumes pending outbox sends without a bound [ChatViewModel].
 * Idempotent: [ChatSyncEngine.sendOutboxEntry] confirms or marks failed per row.
 */
class OutboxSendWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = AppContainer.from(applicationContext)
        val accessToken = runCatching { container.tokenStore.getAccessToken() }.getOrNull()
        val userId = inputData.getString(KEY_USER_ID)?.trim()?.takeIf { it.isNotEmpty() }
            ?: runCatching {
                JwtAccessTokenClaims.sub(accessToken)
            }.getOrNull()?.trim()
            ?: return Result.success()
        if (accessToken.isNullOrBlank()) return Result.success()
        container.chatSyncEngine.resumePendingOutboxSync(userId)
        return Result.success()
    }

    companion object {
        const val KEY_USER_ID = "user_id"
    }
}
