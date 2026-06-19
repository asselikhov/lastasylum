package com.lastasylum.alliance.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.lastasylum.alliance.data.settings.UserSettingsPreferences
import com.lastasylum.alliance.data.teams.AdvanceTeamNewsReadCursorBody
import com.lastasylum.alliance.data.teams.TeamNewsListItemDto
import com.lastasylum.alliance.data.teams.TeamNewsListPageDto
import com.lastasylum.alliance.data.teams.TeamsApiUnusedStub
import com.lastasylum.alliance.data.teams.TeamsRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InboxUnreadReconcilerTest {
    private lateinit var prefs: UserSettingsPreferences
    private var advancedCursor: String? = null

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        prefs = UserSettingsPreferences(ctx).also { it.bindUser("u1") }
        advancedCursor = null
    }

    @Test
    fun repairNewsStaleUnread_repairsWhenLocalProvesRead() = runTest {
        prefs.setLastSeenTeamNewsCreatedAt("team1", "2026-01-02T00:00:00Z")
        val teamsRepository = TeamsRepository(
            object : TeamsApiUnusedStub() {
                override suspend fun listTeamNews(
                    teamId: String,
                    cursor: String?,
                    limit: Int?,
                ): TeamNewsListPageDto = TeamNewsListPageDto(
                    items = listOf(
                        newsItem(createdAt = "2026-01-01T00:00:00Z"),
                    ),
                    nextCursor = null,
                )

                override suspend fun advanceTeamNewsReadCursor(
                    teamId: String,
                    body: AdvanceTeamNewsReadCursorBody,
                ) = com.lastasylum.alliance.data.teams.TeamNewsReadCursorDto(
                    lastSeenCreatedAt = body.createdAt.also { advancedCursor = it },
                )
            },
        )
        InboxUnreadReconciler.repairNewsStaleUnread(
            teamsRepository = teamsRepository,
            userSettingsPreferences = prefs,
            teamId = "team1",
            currentUserId = "u1",
            apiNewsUnread = 2,
        )
        assertEquals("2026-01-02T00:00:00Z", advancedCursor)
    }

    @Test
    fun repairNewsStaleUnread_skipsWhenLocalStillUnread() = runTest {
        prefs.setLastSeenTeamNewsCreatedAt("team1", "2026-01-01T00:00:00Z")
        val teamsRepository = TeamsRepository(
            object : TeamsApiUnusedStub() {
                override suspend fun listTeamNews(
                    teamId: String,
                    cursor: String?,
                    limit: Int?,
                ): TeamNewsListPageDto = TeamNewsListPageDto(
                    items = listOf(
                        newsItem(createdAt = "2026-01-03T00:00:00Z"),
                    ),
                    nextCursor = null,
                )
            },
        )
        InboxUnreadReconciler.repairNewsStaleUnread(
            teamsRepository = teamsRepository,
            userSettingsPreferences = prefs,
            teamId = "team1",
            currentUserId = "u1",
            apiNewsUnread = 2,
        )
        assertEquals(null, advancedCursor)
    }

    private fun newsItem(createdAt: String) = TeamNewsListItemDto(
        id = "n1",
        teamId = "team1",
        authorUserId = "peer",
        authorUsername = "Peer",
        title = "t",
        excerpt = "",
        createdAt = createdAt,
        updatedAt = createdAt,
        hasPoll = false,
        firstImageRelativeUrl = null,
    )
}
