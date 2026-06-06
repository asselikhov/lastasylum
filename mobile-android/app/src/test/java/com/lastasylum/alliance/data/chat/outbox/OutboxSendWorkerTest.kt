package com.lastasylum.alliance.data.chat.outbox

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.workDataOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OutboxSendWorkerTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
    }

    @Test
    fun schedule_enqueuesUniqueWork() {
        OutboxResumeScheduler.schedule(context, "user1")
        val workInfos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork("chat_outbox_resume")
            .get()
        assertEquals(1, workInfos.size)
        assertTrue(
            workInfos[0].state == WorkInfo.State.ENQUEUED ||
                workInfos[0].state == WorkInfo.State.RUNNING ||
                workInfos[0].state == WorkInfo.State.SUCCEEDED,
        )
    }

    @Test
    fun doWork_succeedsWithExplicitUserIdWithoutToken() = runBlocking {
        val worker = TestListenableWorkerBuilder<OutboxSendWorker>(context)
            .setInputData(workDataOf(OutboxSendWorker.KEY_USER_ID to "user1"))
            .build()
        val result = worker.doWork()
        assertTrue(result is ListenableWorker.Result.Success)
    }

    @Test
    fun doWork_succeedsWhenNoUserAndNoToken() = runBlocking {
        val worker = TestListenableWorkerBuilder<OutboxSendWorker>(context).build()
        val result = worker.doWork()
        assertTrue(result is ListenableWorker.Result.Success)
    }
}
