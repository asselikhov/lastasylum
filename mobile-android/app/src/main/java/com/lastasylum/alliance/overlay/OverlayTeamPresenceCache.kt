package com.lastasylum.alliance.overlay

import com.lastasylum.alliance.data.teams.PlayerTeamMemberDto
import com.lastasylum.alliance.data.teams.TeamOverlayPresenceDto
import com.lastasylum.alliance.data.teams.TeamsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Short TTL cache for overlay presence API (avoids 429 when panel polls / reaction picker opens). */
internal object OverlayTeamPresenceCache {
    /** Slightly below [OVERLAY_ONLINE_PANEL_POLL_MS] so panel poll usually hits cache. */
    private const val TTL_MS = 55_000L

    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    @Volatile
    private var cachedTeamId: String? = null

    @Volatile
    private var cachedPresence: TeamOverlayPresenceDto? = null

    @Volatile
    private var cachedAtMs: Long = 0L

    fun invalidate() {
        cachedTeamId = null
        cachedPresence = null
        cachedAtMs = 0L
        bumpRevision()
    }

    fun findMember(userId: String): PlayerTeamMemberDto? {
        val id = userId.trim()
        if (id.isEmpty()) return null
        val presence = cachedPresence ?: return null
        presence.ingame.firstOrNull { it.userId == id }?.let { return it }
        return presence.recentlyActive.firstOrNull { it.userId == id }
    }

    internal fun isCacheValid(
        teamId: String,
        nowMs: Long = System.currentTimeMillis(),
    ): Boolean {
        val tid = teamId.trim()
        if (tid.isEmpty()) return false
        if (cachedPresence == null) return false
        return cachedTeamId == tid && nowMs - cachedAtMs < TTL_MS
    }

    internal fun seedCacheForTest(
        teamId: String,
        presence: TeamOverlayPresenceDto,
        atMs: Long = System.currentTimeMillis(),
    ) {
        cachedTeamId = teamId.trim()
        cachedPresence = presence
        cachedAtMs = atMs
        bumpRevision()
    }

    suspend fun ingameCount(
        teamId: String,
        teamsRepository: TeamsRepository,
        forceRefresh: Boolean = false,
    ): Int = load(teamId, teamsRepository, forceRefresh)
        .getOrNull()
        ?.ingame
        ?.size
        ?: 0

    suspend fun load(
        teamId: String,
        teamsRepository: TeamsRepository,
        forceRefresh: Boolean = false,
    ): Result<TeamOverlayPresenceDto> = runCatching {
        val tid = teamId.trim()
        if (tid.isEmpty()) error("no_team")
        val now = System.currentTimeMillis()
        if (!forceRefresh) {
            val hit = cachedPresence
            if (hit != null && cachedTeamId == tid) {
                return@runCatching hit
            }
        }
        teamsRepository.getTeamOverlayPresence(tid).getOrThrow().also { presence ->
            cachedTeamId = tid
            cachedPresence = presence
            cachedAtMs = now
            bumpRevision()
        }
    }

    private fun bumpRevision() {
        _revision.value = _revision.value + 1
    }
}
