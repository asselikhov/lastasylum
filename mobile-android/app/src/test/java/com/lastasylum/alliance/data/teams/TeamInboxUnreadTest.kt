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
    @Test
    fun isNewsItemUnread_falseForAuthor() {
        val prefs = prefs()
        val item = newsItem(authorUserId = "me", createdAt = Instant.now().toString())
        assertFalse(TeamInboxUnread.isNewsItemUnread(item, prefs, "me"))
    }

    @Test
    fun isNewsItemUnread_trueWhenNewerThanLastSeen() {
        val prefs = prefs().also { it.setLastSeenTeamNewsCreatedAt("2020-01-01T00:00:00Z") }
        val item = newsItem(authorUserId = "other", createdAt = "2026-01-01T00:00:00Z")
        assertTrue(TeamInboxUnread.isNewsItemUnread(item, prefs, "me"))
    }

    @Test
    fun countUnreadNews_excludesAuthorPosts() {
        val prefs = prefs()
        val items = listOf(
            newsItem(authorUserId = "me", createdAt = "2026-01-02T00:00:00Z"),
            newsItem(authorUserId = "other", createdAt = "2026-01-01T00:00:00Z"),
        )
        assertEquals(1, TeamInboxUnread.countUnreadNews(items, prefs, "me"))
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

    private fun newsItem(authorUserId: String, createdAt: String) = TeamNewsListItemDto(
        id = "n1",
        teamId = "t1",
        authorUserId = authorUserId,
        authorUsername = "u",
        title = "t",
        excerpt = "",
        createdAt = createdAt,
        updatedAt = createdAt,
        hasPoll = false,
        firstImageRelativeUrl = null,
    )
}
