package com.lastasylum.alliance.data.teams

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.lastasylum.alliance.data.settings.UserSettingsPreferences
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TeamNewsReadCursorSyncTest {
    private lateinit var prefs: UserSettingsPreferences
    private val teamId = "team-1"
    private var advancedIso: String? = null
    private lateinit var teamsRepository: TeamsRepository

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        prefs = UserSettingsPreferences(ctx).also { it.bindUser("test-user") }
        TeamNewsReadCursorSync.cancelPendingJobs()
        advancedIso = null
        val teamsApi = object : TeamsApiUnusedStub() {
            override suspend fun advanceTeamNewsReadCursor(
                teamId: String,
                body: AdvanceTeamNewsReadCursorBody,
            ): TeamNewsReadCursorDto {
                advancedIso = body.createdAt
                return TeamNewsReadCursorDto(lastSeenCreatedAt = body.createdAt)
            }
        }
        teamsRepository = TeamsRepository(teamsApi)
    }

    @Test
    fun markNewsSeen_neverRegressesCursorWhenOpeningOlderPost() = runTest {
        val newer = "2026-06-01T12:00:00Z"
        val older = "2026-05-01T12:00:00Z"
        prefs.setLastSeenTeamNewsCreatedAt(teamId, newer)

        TeamNewsReadCursorSync.markNewsSeen(
            teamsRepository = teamsRepository,
            prefs = prefs,
            teamId = teamId,
            createdAt = older,
        )

        assertEquals(newer, prefs.getLastSeenTeamNewsCreatedAt(teamId))
        assertEquals(newer, advancedIso)
    }

    @Test
    fun markNewsSeen_advancesWhenIncomingIsNewer() = runTest {
        val prev = "2026-05-01T12:00:00Z"
        val newer = "2026-06-01T12:00:00Z"
        prefs.setLastSeenTeamNewsCreatedAt(teamId, prev)

        TeamNewsReadCursorSync.markNewsSeen(
            teamsRepository = teamsRepository,
            prefs = prefs,
            teamId = teamId,
            createdAt = newer,
        )

        assertEquals(newer, prefs.getLastSeenTeamNewsCreatedAt(teamId))
        assertEquals(newer, advancedIso)
    }

    @Test
    fun markNewsSeenUpTo_doesNotRegressExistingCursor() {
        val newer = "2026-06-01T12:00:00Z"
        val older = "2026-05-01T12:00:00Z"
        prefs.setLastSeenTeamNewsCreatedAt(teamId, newer)

        TeamNewsReadCursorSync.markNewsSeenUpTo(
            teamsRepository = teamsRepository,
            prefs = prefs,
            teamId = teamId,
            createdAt = older,
        )

        assertEquals(newer, prefs.getLastSeenTeamNewsCreatedAt(teamId))
    }
}
