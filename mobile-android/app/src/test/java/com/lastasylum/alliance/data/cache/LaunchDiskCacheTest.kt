package com.lastasylum.alliance.data.cache

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.lastasylum.alliance.data.auth.AuthUser
import com.lastasylum.alliance.data.chat.ChatMessage
import com.lastasylum.alliance.data.chat.ChatRoomDto
import com.lastasylum.alliance.data.teams.TeamDetailDto
import com.lastasylum.alliance.data.teams.TeamForumTopicDto
import com.lastasylum.alliance.data.teams.TeamNewsListItemDto
import com.lastasylum.alliance.data.teams.TeamNewsListPageDto
import com.lastasylum.alliance.data.users.MyProfileDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LaunchDiskCacheTest {

    private lateinit var cache: LaunchDiskCache
    private val userId = "user_test_1"
    private val teamId = "team_abc"

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        cache = LaunchDiskCache(ctx)
        cache.clearUser(userId)
    }

    @Test
    fun authUserRoundTripAndClearUser() {
        val user = AuthUser(
            id = userId,
            email = "a@example.com",
            username = "alice",
            role = "member",
            teamTag = "TAG",
        )
        cache.saveAuthUser(userId, user)
        assertEquals("TAG", cache.loadAuthUser(userId)?.teamTag)
        cache.clearUser(userId)
        assertNull(cache.loadAuthUser(userId))
    }

    @Test
    fun profileRoundTripAndClearUser() {
        val profile = sampleProfile()
        cache.saveProfile(userId, profile)
        assertEquals(profile.id, cache.loadProfile(userId)?.id)
        cache.clearUser(userId)
        assertNull(cache.loadProfile(userId))
    }

    @Test
    fun chatRoomsAndMessagesRoundTrip() {
        val rooms = listOf(
            ChatRoomDto(id = "room1", allianceId = "a1", title = "Hub", sortOrder = 0),
        )
        cache.saveChatRooms(userId, rooms)
        assertEquals(1, cache.loadChatRooms(userId)?.size)

        val messages = listOf(
            ChatMessage(
                _id = "m1",
                allianceId = "a1",
                roomId = "room1",
                senderId = "u1",
                senderUsername = "alice",
                senderRole = "member",
                text = "hi",
            ),
        )
        cache.saveRoomMessages(userId, "room1", messages, hasMoreOlder = false)
        val loaded = cache.loadRoomMessages(userId, "room1")
        assertEquals(1, loaded?.messages?.size)
        assertEquals(false, loaded?.hasMoreOlder)
    }

    @Test
    fun teamNewsAndForumRoundTrip() {
        val page = TeamNewsListPageDto(
            items = listOf(
                TeamNewsListItemDto(
                    id = "n1",
                    teamId = teamId,
                    title = "News",
                    excerpt = "ex",
                    authorUserId = "u1",
                    authorUsername = "alice",
                    createdAt = "2025-01-01T00:00:00Z",
                    updatedAt = "2025-01-01T00:00:00Z",
                    hasPoll = false,
                    firstImageRelativeUrl = null,
                ),
            ),
            nextCursor = null,
        )
        cache.saveTeamNews(userId, teamId, page)
        assertEquals(1, cache.loadTeamNews(userId, teamId)?.items?.size)

        val topics = listOf(
            TeamForumTopicDto(
                id = "t1",
                teamId = teamId,
                title = "Topic",
                createdByUserId = "u1",
                messageCount = 1,
                createdAt = "2025-01-01T00:00:00Z",
                updatedAt = "2025-01-01T00:00:00Z",
            ),
        )
        cache.saveForumTopics(userId, teamId, topics)
        assertEquals(1, cache.loadForumTopics(userId, teamId)?.size)

        val forumMessages = listOf(
            com.lastasylum.alliance.data.teams.TeamForumMessageDto(
                id = "m1",
                topicId = "t1",
                teamId = teamId,
                senderUserId = "u1",
                senderUsername = "user",
                senderRole = "R1",
                text = "hi",
                createdAt = "2025-01-01T00:00:00Z",
                updatedAt = "2025-01-01T00:00:00Z",
            ),
        )
        cache.saveForumMessages(userId, teamId, "t1", forumMessages, hasMoreOlder = false)
        assertEquals(1, cache.loadForumMessages(userId, teamId, "t1")?.messages?.size)
    }

    @Test
    fun isStaleAfterSoftTtl() {
        val old = System.currentTimeMillis() - LaunchDiskCache.SOFT_TTL_MS - 1
        assertTrue(LaunchDiskCache.isStale(old))
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
