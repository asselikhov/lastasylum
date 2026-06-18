package com.lastasylum.alliance.ui.teamforum

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.lastasylum.alliance.data.cache.LaunchDiskCache
import com.lastasylum.alliance.data.chat.store.SquadRelayDatabase
import com.lastasylum.alliance.data.teams.TeamForumPreferences
import com.lastasylum.alliance.data.teams.TeamForumTopicDto
import com.lastasylum.alliance.data.teams.TeamsApiUnusedStub
import com.lastasylum.alliance.data.teams.TeamsRepository
import com.lastasylum.alliance.data.teams.forum.ForumRepository
import com.lastasylum.alliance.data.telemetry.DeliveryLatencyTracker
import com.lastasylum.alliance.overlay.OverlayHubUnreadPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ForumTopicUnreadStabilityTest {
    private lateinit var vm: ForumListViewModel

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val db = SquadRelayDatabase.createInMemory(context)
        val teamsApi = object : TeamsApiUnusedStub() {}
        val forumPrefs = TeamForumPreferences(context)
        val forumRepository = ForumRepository(
            db = db,
            teamsRepository = TeamsRepository(teamsApi),
            launchDiskCache = LaunchDiskCache(context),
            forumPrefs = forumPrefs,
            latencyTracker = DeliveryLatencyTracker(db, CoroutineScope(Dispatchers.Unconfined)),
        )
        vm = ForumListViewModel(
            application = context.applicationContext as android.app.Application,
            forumRepository = forumRepository,
            teamForumPreferences = forumPrefs,
            currentUserId = "u1",
        )
    }

    private fun topic(id: String, unread: Int, lastRead: String? = null) = TeamForumTopicDto(
        id = id,
        teamId = "team1",
        title = "Topic $id",
        createdByUserId = "u1",
        messageCount = 1,
        unreadCount = unread,
        lastReadMessageId = lastRead,
        createdAt = "2026-01-01T00:00:00Z",
        updatedAt = "2026-01-01T00:00:00Z",
    )

    @Test
    fun mergeTopicsFromRoom_preservesOptimisticFloorWhenServerZero() {
        val current = ForumListUiState(
            topics = listOf(topic("t1", unread = 2)),
            optimisticUnreadFloorByTopic = mapOf("t1" to 2),
        )
        val merged = vm.mergeTopicsFromRoom(listOf(topic("t1", unread = 0)), current)
        assertEquals(2, merged.first().unreadCount)
    }

    @Test
    fun mergeTopicsFromRoom_keepsNewerLocalLastRead() {
        val localRead = "507f1f77bcf86cd799439099"
        val serverRead = "507f1f77bcf86cd799439011"
        val current = ForumListUiState(
            topics = listOf(topic("t1", unread = 0, lastRead = localRead)),
        )
        val merged = vm.mergeTopicsFromRoom(
            listOf(topic("t1", unread = 0, lastRead = serverRead)),
            current,
        )
        assertEquals(localRead, merged.first().lastReadMessageId)
    }

    @Test
    fun reconcileGrace_isTwoSeconds() {
        assertEquals(2_000L, OverlayHubUnreadPolicy.RECONCILE_GRACE_MS)
    }
}
