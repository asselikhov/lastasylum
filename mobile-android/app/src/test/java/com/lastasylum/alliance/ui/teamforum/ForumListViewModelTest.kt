package com.lastasylum.alliance.ui.teamforum

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.lastasylum.alliance.data.cache.LaunchDiskCache
import com.lastasylum.alliance.data.chat.store.SquadRelayDatabase
import com.lastasylum.alliance.data.teams.TeamForumPreferences
import com.lastasylum.alliance.data.teams.TeamForumTopicActivityEvent
import com.lastasylum.alliance.data.teams.TeamForumTopicDto
import com.lastasylum.alliance.data.teams.TeamsApiUnusedStub
import com.lastasylum.alliance.data.teams.TeamsRepository
import com.lastasylum.alliance.data.teams.forum.ForumRepository
import com.lastasylum.alliance.data.telemetry.DeliveryLatencyTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ForumListViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var forumRepository: ForumRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val context: Context = ApplicationProvider.getApplicationContext()
        val db = SquadRelayDatabase.createInMemory(context)
        val teamsApi = object : TeamsApiUnusedStub() {
            override suspend fun listForumTopics(teamId: String, view: String): List<TeamForumTopicDto> =
                listOf(sampleTopic("t1", "General"))
        }
        forumRepository = ForumRepository(
            db = db,
            teamsRepository = TeamsRepository(teamsApi),
            launchDiskCache = LaunchDiskCache(context),
            forumPrefs = TeamForumPreferences(context),
            latencyTracker = DeliveryLatencyTracker(db, CoroutineScope(Dispatchers.Unconfined)),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun onSearchQueryChange_updatesState() = runTest {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = ForumListViewModel(app, forumRepository, "user1")
        vm.bindTeam("team1")
        vm.onSearchQueryChange("raid")
        assertEquals("raid", vm.state.value.searchQuery)
    }

    @Test
    fun reload_success_updatesTopics() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = ForumListViewModel(app, forumRepository, "user1")
        vm.bindTeam("team1")
        vm.reload(force = true)
        withTimeout(5_000) {
            while (vm.state.value.topics.isEmpty() && vm.state.value.error == null) {
                delay(10)
            }
        }
        assertEquals(1, vm.state.value.topics.size)
        assertEquals("General", vm.state.value.topics.first().title)
    }

    @Test
    fun applyTopicReadLocal_zerosUnreadImmediately() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = ForumListViewModel(app, forumRepository, "user1")
        vm.bindTeam("team1")
        vm.reload(force = true)
        withTimeout(5_000) {
            while (vm.state.value.topics.isEmpty()) delay(10)
        }
        vm.applyTopicReadLocal("t1", "507f1f77bcf86cd799439011")
        val topic = vm.state.value.topics.first()
        assertEquals(0, topic.unreadCount)
        assertEquals("507f1f77bcf86cd799439011", topic.lastReadMessageId)
    }

    @Test
    fun applyTopicActivity_bumpsUnreadImmediately() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = ForumListViewModel(app, forumRepository, "user1")
        vm.bindTeam("team1")
        vm.reload(force = true)
        withTimeout(5_000) {
            while (vm.state.value.topics.isEmpty()) delay(10)
        }
        vm.applyTopicActivity(
            TeamForumTopicActivityEvent(
                teamId = "team1",
                topicId = "t1",
                messageId = "507f1f77bcf86cd799439012",
                senderUserId = "peer-2",
            ),
        )
        assertEquals(1, vm.state.value.topics.first().unreadCount)
        assertEquals(1, vm.state.value.optimisticUnreadFloorByTopic["t1"])
    }

    @Test
    fun applyTopicActivity_skipsOpenTopic() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = ForumListViewModel(app, forumRepository, "user1")
        vm.bindTeam("team1")
        vm.reload(force = true)
        withTimeout(5_000) {
            while (vm.state.value.topics.isEmpty()) delay(10)
        }
        vm.setOpenTopicId("t1")
        vm.applyTopicActivity(
            TeamForumTopicActivityEvent(
                teamId = "team1",
                topicId = "t1",
                messageId = "507f1f77bcf86cd799439012",
                senderUserId = "peer-2",
            ),
        )
        assertEquals(0, vm.state.value.topics.first().unreadCount)
    }

    private fun sampleTopic(id: String, title: String) = TeamForumTopicDto(
        id = id,
        teamId = "team1",
        title = title,
        createdByUserId = "user1",
        messageCount = 1,
        unreadCount = 0,
        createdAt = "2026-01-01T00:00:00Z",
        updatedAt = "2026-01-01T00:00:00Z",
    )
}
