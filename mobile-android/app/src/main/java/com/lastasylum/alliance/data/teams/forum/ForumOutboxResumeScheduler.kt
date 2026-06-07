package com.lastasylum.alliance.data.teams.forum

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf

object ForumOutboxResumeScheduler {
    private const val WORK_NAME = "forum_outbox_resume"

    fun schedule(context: Context, userId: String) {
        val uid = userId.trim()
        if (uid.isEmpty()) return
        val request = OneTimeWorkRequestBuilder<ForumOutboxSendWorker>()
            .setInputData(workDataOf(ForumOutboxSendWorker.KEY_USER_ID to uid))
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }
}
