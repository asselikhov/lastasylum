package com.lastasylum.alliance.overlay

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.lastasylum.alliance.data.cache.LaunchDiskCache
import com.lastasylum.alliance.data.chat.ChatMessage
import com.lastasylum.alliance.data.chat.ChatRoomDto
import com.lastasylum.alliance.data.chat.ChatSessionCache
import com.lastasylum.alliance.data.teams.TeamDetailDto
import com.lastasylum.alliance.data.teams.TeamForumTopicDto
import com.lastasylum.alliance.data.teams.TeamNewsListItemDto
import com.lastasylum.alliance.data.teams.TeamNewsListPageDto
import com.lastasylum.alliance.data.users.MyProfileDto
import com.lastasylum.alliance.di.AppContainer
import kotlinx.coroutines.runBlocking
import org.junit.After
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
@Config(sdk = [34])
class OverlayColdStartHydratorTest {

    private lateinit var context: Context
    private lateinit var disk: LaunchDiskCache
    private val userId = "user_hydrate_1"
    private val teamId = "team_hydrate_1"
    private val hubRoomId = "room_hub_1"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        disk = AppContainer.from(context).launchDiskCache
        disk.clearUser(userId)
        OverlayTeamContextCache.invalidate()
        ChatSessionCache.clear()
        OverlayGameStatusHudRefresh.invalidateNewsForumCache()
    }

    @After
    fun tearDown() {
        disk.clearUser(userId)
        OverlayTeamContextCache.invalidate()
        ChatSessionCache.clear()
        OverlayGameStatusHudRefresh.invalidateNewsForumCache()
    }

    @Test
    fun hydrate_seedsContextRoomsAndBadgesFromDisk() = runBlocking {
        val profile = sampleProfile()
        val team = sampleTeam()
        disk.saveProfile(userId, profile)
        disk.saveTeam(userId, team)
        disk.saveChatRooms(
            userId,
            listOf(
                ChatRoomDto(
                    id = hubRoomId,
                    allianceId = "pt:$teamId",
                    title = "Hub",
                    sortOrder = 1,
                ),
            ),
        )
        disk.saveRoomMessages(
            userId,
            hubRoomId,
            listOf(
                ChatMessage(
                    _id = "m1",
                    allianceId = "pt:$teamId",
                    roomId = hubRoomId,
                    senderId = "u2",
                    senderUsername = "bob",
                    senderRole = "member",
                    text = "hello",
                ),
            ),
            hasMoreOlder = false,
        )
        disk.saveTeamNews(
            userId,
            teamId,
            TeamNewsListPageDto(
                items = listOf(
                    TeamNewsListItemDto(
                        id = "n1",
                        teamId = teamId,
                        title = "News",
                        excerpt = "ex",
                        authorUserId = "u2",
                        authorUsername = "bob",
                        createdAt = "2099-01-01T00:00:00Z",
                        updatedAt = "2099-01-01T00:00:00Z",
                        hasPoll = false,
                        firstImageRelativeUrl = null,
                    ),
                ),
                nextCursor = null,
            ),
        )
        disk.saveForumTopics(
            userId,
            teamId,
            listOf(
                TeamForumTopicDto(
                    id = "t1",
                    teamId = teamId,
                    title = "Topic",
                    createdByUserId = "u2",
                    messageCount = 1,
                    unreadCount = 2,
                    createdAt = "2025-01-01T00:00:00Z",
                    updatedAt = "2025-01-01T00:00:00Z",
                ),
            ),
        )

        val result = OverlayColdStartHydrator.hydrate(context, userId)

        assertTrue(result.seededContext)
        assertTrue(result.seededRooms)
        assertTrue(result.seededBadges)
        assertEquals(false, result.needsNetworkPrefetch)
        assertNotNull(OverlayTeamContextCache.peekForPanel())
        assertEquals(teamId, OverlayTeamContextCache.peekForPanel()?.teamId)
        assertNotNull(ChatSessionCache.getFreshRooms())
        assertNotNull(ChatSessionCache.getFreshMessages(hubRoomId))
        assertTrue(OverlayGameStatusHudRefresh.hasRecentDiskSeed())
        val instant = OverlayGameStatusHudRefresh.buildInstantLocalState(context)
        assertNotNull(instant)
        assertTrue(instant!!.teamNewsUnread >= 1)
        assertTrue(instant.forumUnread >= 2)
    }

    @Test
    fun hydrate_emptyDisk_requestsNetworkPrefetch() = runBlocking {
        val result = OverlayColdStartHydrator.hydrate(context, userId)
        assertEquals(false, result.seededContext)
        assertEquals(false, result.seededRooms)
        assertEquals(false, result.seededBadges)
        assertTrue(result.needsNetworkPrefetch)
        assertNull(OverlayTeamContextCache.peekForPanel())
    }

    private fun sampleProfile(): MyProfileDto = MyProfileDto(
        id = userId,
        username = "tester",
        email = "t@test.com",
        role = "member",
        allianceName = "Alliance",
        membershipStatus = "active",
        playerTeamId = teamId,
    )

    private fun sampleTeam(): TeamDetailDto = TeamDetailDto(
        id = teamId,
        tag = "TAG",
        displayName = "Team",
        leaderUserId = userId,
        members = emptyList(),
    )
}
