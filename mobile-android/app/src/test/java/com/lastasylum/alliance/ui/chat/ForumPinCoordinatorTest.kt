package com.lastasylum.alliance.ui.chat

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.lastasylum.alliance.data.chat.PinHistoryPreferences
import com.lastasylum.alliance.data.chat.PinnedMessagePreviewDto
import com.lastasylum.alliance.data.teams.TeamForumTopicDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ForumPinCoordinatorTest {
    private lateinit var prefs: PinHistoryPreferences
    private lateinit var coordinator: ForumPinCoordinator

    private val preview = PinnedMessagePreviewDto(
        id = "507f1f77bcf86cd799439014",
        text = "pinned",
        senderUsername = "alice",
        createdAt = "2026-01-01T00:00:00Z",
    )

    private fun topicWithPin() = TeamForumTopicDto(
        id = "topic1",
        teamId = "team1",
        title = "Raid plan",
        createdByUserId = "user1",
        messageCount = 3,
        createdAt = "2026-01-01T00:00:00Z",
        updatedAt = "2026-01-02T00:00:00Z",
        pinnedMessageId = preview.id,
        pinnedAt = "2026-01-02T00:00:00Z",
        pinnedByUserId = "user1",
        pinnedMessage = preview,
        pinnedMessages = listOf(preview),
    )

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        prefs = PinHistoryPreferences(ctx)
        prefs.bindUser("forum-pin-test")
        coordinator = ForumPinCoordinator(prefs, prefs.forumScopeKey("team1", "topic1"))
    }

    @Test
    fun onEnterTopic_restoresPinFromSnapshot() {
        coordinator.onEnterTopic(topicWithPin())
        assertEquals(preview.id, coordinator.pinnedMessageId)
        assertNotNull(coordinator.pinBarPreview)
        assertEquals(preview.id, coordinator.pinBarPreview?.id)
    }

    @Test
    fun onUnpinSuccess_clearsPinHistory() {
        coordinator.onEnterTopic(topicWithPin())
        val unpinned = topicWithPin().copy(
            pinnedMessageId = null,
            pinnedAt = null,
            pinnedByUserId = null,
            pinnedMessage = null,
            pinnedMessages = emptyList(),
        )
        coordinator.onUnpinSuccess(unpinned)
        assertTrue(coordinator.pinnedMessages.isEmpty())
        assertNull(coordinator.pinBarPreview)
        assertEquals(0, coordinator.pinHistoryCount)
    }

    @Test
    fun pinnedMessageIds_includesAllHistoryEntries() {
        val second = PinnedMessagePreviewDto(
            id = "507f1f77bcf86cd799439015",
            text = "second",
            senderUsername = "alice",
            createdAt = "2026-01-02T00:00:00Z",
        )
        coordinator.onEnterTopic(
            topicWithPin().copy(
                pinnedMessages = listOf(preview, second),
            ),
        )
        assertTrue(coordinator.pinnedMessageIds().contains(preview.id))
        assertTrue(coordinator.pinnedMessageIds().contains(second.id))
    }

    @Test
    fun applyPinBarUi_showsBarAfterReentryWithEmptyMessages() {
        coordinator.onEnterTopic(topicWithPin())
        coordinator.applyPinBarUi(emptyList())
        assertNotNull(coordinator.pinBarPreview)
        assertEquals(preview.id, coordinator.pinBarPreview?.id)
    }
}
