package com.lastasylum.alliance.data.teams.forum

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lastasylum.alliance.data.auth.JwtAccessTokenClaims
import com.lastasylum.alliance.di.AppContainer

class ForumOutboxSendWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = AppContainer.from(applicationContext)
        val accessToken = runCatching { container.tokenStore.getAccessToken() }.getOrNull()
        val userId = inputData.getString(KEY_USER_ID)?.trim()?.takeIf { it.isNotEmpty() }
            ?: runCatching { JwtAccessTokenClaims.sub(accessToken) }.getOrNull()?.trim()
            ?: return Result.success()
        if (accessToken.isNullOrBlank()) return Result.success()
        container.forumOutbox.resumePendingSync(userId) { entry ->
            container.forumRepository.postForumMessageWithRetries(
                userId = userId,
                teamId = entry.teamId,
                topicId = entry.topicId,
                text = entry.text,
                replyToMessageId = entry.replyToMessageId,
                imageFileId = null,
                imageFileIds = entry.imageFileIds,
                fileFileId = entry.fileFileId,
                clientMessageId = entry.clientMessageId,
            )
        }
        return Result.success()
    }

    companion object {
        const val KEY_USER_ID = "user_id"
    }
}
