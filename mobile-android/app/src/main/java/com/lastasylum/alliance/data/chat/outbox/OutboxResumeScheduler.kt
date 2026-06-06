package com.lastasylum.alliance.data.chat.outbox

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf

/** Schedules durable outbox resume when VM may be absent (process death, overlay FGS). */
object OutboxResumeScheduler {
    private const val UNIQUE_WORK = "chat_outbox_resume"

    fun schedule(context: Context, userId: String) {
        val uid = userId.trim()
        if (uid.isEmpty()) return
        val request = OneTimeWorkRequestBuilder<OutboxSendWorker>()
            .setInputData(workDataOf(OutboxSendWorker.KEY_USER_ID to uid))
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            UNIQUE_WORK,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }
}
