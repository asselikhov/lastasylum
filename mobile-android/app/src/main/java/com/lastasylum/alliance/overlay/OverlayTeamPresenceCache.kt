package com.lastasylum.alliance.overlay

import com.lastasylum.alliance.data.cache.LaunchDiskCache
import com.lastasylum.alliance.data.teams.PlayerTeamMemberDto
import com.lastasylum.alliance.data.teams.TeamOverlayPresenceDto
import com.lastasylum.alliance.data.teams.TeamPresenceSocketEvent
import com.lastasylum.alliance.data.teams.TeamsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Short TTL cache for overlay presence API (avoids 429 when panel polls / reaction picker opens). */
internal object OverlayTeamPresenceCache {
    /** Slightly above [OVERLAY_ONLINE_PANEL_POLL_MS] so panel poll usually hits cache. */
    private const val TTL_MS = 65_000L

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

    fun peek(teamId: String): TeamOverlayPresenceDto? {
        if (!isCacheValid(teamId)) return null
        return cachedPresence
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

    internal fun seedFromDisk(
        teamId: String,
        presence: TeamOverlayPresenceDto,
        atMs: Long = System.currentTimeMillis(),
    ) {
        if (cachedPresence != null && isCacheValid(teamId, atMs)) return
        seedCacheForTest(teamId, presence, atMs)
    }

    suspend fun ingameCount(
        teamId: String,
        teamsRepository: TeamsRepository,
        launchDiskCache: LaunchDiskCache? = null,
        userId: String? = null,
        forceRefresh: Boolean = false,
    ): Int = load(teamId, teamsRepository, launchDiskCache, userId, forceRefresh)
        .getOrNull()
        ?.ingame
        ?.let(::countFreshIngameMembers)
        ?: 0

    suspend fun load(
        teamId: String,
        teamsRepository: TeamsRepository,
        launchDiskCache: LaunchDiskCache? = null,
        userId: String? = null,
        forceRefresh: Boolean = false,
    ): Result<TeamOverlayPresenceDto> {
        val tid = teamId.trim()
        if (tid.isEmpty()) return Result.failure(IllegalStateException("no_team"))
        val now = System.currentTimeMillis()
        if (!forceRefresh && isCacheValid(tid, now)) {
            return Result.success(cachedPresence!!)
        }
        val network = runCatching {
            teamsRepository.getTeamOverlayPresence(tid).getOrThrow().also { presence ->
                store(tid, presence, now)
                persistToDisk(launchDiskCache, userId, tid, presence)
            }
        }
        if (network.isSuccess) return network
        val disk = loadFromDisk(launchDiskCache, userId, tid)
        if (disk != null) {
            seedFromDisk(tid, disk, now)
            return Result.success(disk)
        }
        return network
    }

    private fun store(teamId: String, presence: TeamOverlayPresenceDto, atMs: Long) {
        cachedTeamId = teamId
        cachedPresence = presence
        cachedAtMs = atMs
        bumpRevision()
    }

    /** Keep HTTP poll + socket merges in one cache for all overlay presence consumers. */
    fun storeMergedLists(
        teamId: String,
        ingame: List<PlayerTeamMemberDto>,
        recentlyActive: List<PlayerTeamMemberDto>,
        atMs: Long = System.currentTimeMillis(),
    ) {
        val tid = teamId.trim()
        if (tid.isEmpty()) return
        val reconciled = reconcilePresenceLists(ingame, recentlyActive)
        store(
            tid,
            TeamOverlayPresenceDto(
                ingame = reconciled.ingame,
                recentlyActive = reconciled.recentlyActive,
            ),
            atMs,
        )
    }

    fun applySocketEvent(
        teamId: String,
        event: TeamPresenceSocketEvent,
        fallbackMember: PlayerTeamMemberDto? = null,
    ) {
        val tid = teamId.trim()
        if (tid.isEmpty()) return
        val current = peek(tid) ?: TeamOverlayPresenceDto()
        val merged = mergePresenceSocketEvent(
            lists = OverlayOnlinePresenceLists(current.ingame, current.recentlyActive),
            event = event,
            fallbackMember = fallbackMember,
        )
        storeMergedLists(tid, merged.ingame, merged.recentlyActive)
    }

    private fun persistToDisk(
        launchDiskCache: LaunchDiskCache?,
        userId: String?,
        teamId: String,
        presence: TeamOverlayPresenceDto,
    ) {
        val uid = userId?.trim().orEmpty()
        if (uid.isEmpty() || launchDiskCache == null) return
        launchDiskCache.saveOverlayPresence(uid, teamId, presence)
    }

    private fun loadFromDisk(
        launchDiskCache: LaunchDiskCache?,
        userId: String?,
        teamId: String,
    ): TeamOverlayPresenceDto? {
        val uid = userId?.trim().orEmpty()
        if (uid.isEmpty() || launchDiskCache == null) return null
        return launchDiskCache.loadOverlayPresence(uid, teamId)
    }

    private fun bumpRevision() {
        _revision.value = _revision.value + 1
    }
}
