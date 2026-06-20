package com.lastasylum.alliance.data.teams

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.lastasylum.alliance.data.settings.UserSettingsPreferences
import com.lastasylum.alliance.overlay.OverlayInboxBadgeCoordinator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TeamInboxUnreadTest {
    private val teamId = "t1"

    @Test
    fun isNewsItemUnread_falseForAuthor() {
        val prefs = prefs()
        val item = newsItem(authorUserId = "me", createdAt = Instant.now().toString())
        assertFalse(TeamInboxUnread.isNewsItemUnread(item, prefs, teamId, "me"))
    }

    @Test
    fun isNewsItemUnread_trueWhenNewerThanLastSeen() {
        val prefs = prefs().also { it.setLastSeenTeamNewsCreatedAt(teamId, "2020-01-01T00:00:00Z") }
        val item = newsItem(authorUserId = "other", createdAt = "2026-01-01T00:00:00Z")
        assertTrue(TeamInboxUnread.isNewsItemUnread(item, prefs, teamId, "me"))
    }

    @Test
    fun sumForumUnread_zeroWhenLocalReadCursorAtOrPastServer() {
        val topic = TeamForumTopicDto(
            id = "topic1",
            teamId = "team1",
            title = "Topic",
            createdByUserId = "u1",
            messageCount = 12,
            unreadCount = 4,
            lastReadMessageId = "000000000000000000000010",
            createdAt = "2020-01-01T00:00:00Z",
            updatedAt = "2020-01-01T00:00:00Z",
        )
        val localRead = mapOf("topic1" to "000000000000000000000020")
        assertEquals(0, TeamInboxUnread.sumForumUnread(listOf(topic), localRead))
    }

    @Test
    fun countUnreadNews_excludesAuthorPosts() {
        val prefs = prefs()
        val items = listOf(
            newsItem(authorUserId = "me", createdAt = "2026-01-02T00:00:00Z"),
            newsItem(authorUserId = "other", createdAt = "2026-01-01T00:00:00Z"),
        )
        assertEquals(1, TeamInboxUnread.countUnreadNews(items, prefs, teamId, "me"))
    }

    @Test
    fun countUnreadNews_reactsWhenLastSeenAdvances() {
        val prefs = prefs()
        val items = listOf(
            newsItem(authorUserId = "other", createdAt = "2026-06-01T00:00:00Z"),
            newsItem(authorUserId = "other", createdAt = "2026-05-01T00:00:00Z"),
        )
        assertEquals(2, TeamInboxUnread.countUnreadNews(items, prefs, teamId, "me"))
        prefs.setLastSeenTeamNewsCreatedAt(teamId, "2026-05-15T00:00:00Z")
        assertEquals(1, TeamInboxUnread.countUnreadNews(items, prefs, teamId, "me"))
    }

    @Test
    fun sortNewsFeedNewestFirst_dedupesDuplicateIdsAfterAppend() {
        val duplicateId = "507f1f77bcf86cd799439099"
        val items = listOf(
            newsItem(id = duplicateId, authorUserId = "other", createdAt = "2026-01-01T00:00:00Z", title = "older"),
            newsItem(id = duplicateId, authorUserId = "other", createdAt = "2026-06-01T00:00:00Z", title = "newer"),
            newsItem(id = "other-id", authorUserId = "other", createdAt = "2026-05-01T00:00:00Z", title = "mid"),
        )
        val sorted = TeamInboxUnread.sortNewsFeedNewestFirst(items)
        assertEquals(2, sorted.size)
        assertEquals(duplicateId, sorted.first().id)
        assertEquals("newer", sorted.first().title)
    }

    @Test
    fun coordinator_mergeNews_doesNotDropForumFloor() {
        val coordinator = OverlayInboxBadgeCoordinator()
        coordinator.bumpForumOptimistic(3)
        val forumMerged = coordinator.mergeHudForum(
            authoritative = 0,
            prevDisplayed = 3,
            useAuthoritative = true,
        )
        assertEquals(3, forumMerged)
    }

    private fun prefs(): UserSettingsPreferences {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        return UserSettingsPreferences(ctx).also { it.bindUser("test-user") }
    }

    private fun newsItem(
        authorUserId: String,
        createdAt: String,
        id: String = "n1",
        title: String = "t",
    ) = TeamNewsListItemDto(
        id = id,
        teamId = "t1",
        authorUserId = authorUserId,
        authorUsername = "u",
        title = title,
        excerpt = "",
        createdAt = createdAt,
        updatedAt = createdAt,
        hasPoll = false,
        firstImageRelativeUrl = null,
    )
}
