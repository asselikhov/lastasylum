package com.lastasylum.alliance.overlay

import com.lastasylum.alliance.data.cache.LaunchDiskCache
import com.lastasylum.alliance.data.teams.TeamDetailDto
import com.lastasylum.alliance.data.teams.TeamsRepository
import com.lastasylum.alliance.data.users.MyProfileDto
import com.lastasylum.alliance.data.users.UsersRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Cached team/profile context for overlay panels (news/forum/online). */
data class OverlayTeamHudContext(
    val teamId: String,
    val currentUserId: String,
    val myTeamRole: String,
    val canPublishNews: Boolean,
    val canManageForumTopics: Boolean,
    val canModerateForumMessages: Boolean,
    val enabledStickerPackKeys: Set<String>,
    val teamTag: String,
    val teamDisplayName: String,
)

internal object OverlayTeamContextCache {
    /** Fresh window before background revalidate (stale still paintable). */
    private const val FRESH_TTL_MS = 5 * 60_000L

    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    @Volatile
    private var cached: OverlayTeamHudContext? = null

    @Volatile
    private var cachedAtMs: Long = 0L

    @Volatile
    private var cachedTeam: TeamDetailDto? = null

    @Volatile
    private var cachedTeamAtMs: Long = 0L

    fun invalidate() {
        cached = null
        cachedAtMs = 0L
        cachedTeam = null
        cachedTeamAtMs = 0L
        bumpRevision()
    }

    fun seedFromDisk(profile: MyProfileDto, team: TeamDetailDto) {
        val teamId = profile.playerTeamId?.trim().orEmpty()
        if (teamId.isEmpty() || team.id != teamId) return
        val myTeamRole = team.members.find { it.userId == profile.id }?.teamRole ?: "R1"
        val now = System.currentTimeMillis()
        cached = OverlayTeamHudContext(
            teamId = teamId,
            currentUserId = profile.id,
            myTeamRole = myTeamRole,
            canPublishNews = myTeamRole == "R4" || myTeamRole == "R5",
            canManageForumTopics = myTeamRole == "R4" || myTeamRole == "R5",
            canModerateForumMessages = myTeamRole == "R4" || myTeamRole == "R5",
            enabledStickerPackKeys = profile.enabledStickerPacks.toSet(),
            teamTag = team.tag,
            teamDisplayName = team.displayName,
        )
        cachedAtMs = now
        cachedTeam = team
        cachedTeamAtMs = now
        bumpRevision()
    }

    fun hydrateFromDisk(
        userId: String,
        usersRepository: UsersRepository,
        launchDiskCache: LaunchDiskCache,
    ): OverlayTeamHudContext? {
        peekForPanel()?.let { return it }
        val uid = userId.trim()
        if (uid.isEmpty()) return null
        val profile = launchDiskCache.loadProfile(uid)
            ?: usersRepository.peekMyProfile()
            ?: usersRepository.peekMyProfileDisk()
        val team = launchDiskCache.loadTeam(uid)
        if (profile != null && team != null) {
            seedFromDisk(profile, team)
        }
        return peekForPanel()
    }

    fun isFresh(nowMs: Long = System.currentTimeMillis()): Boolean {
        val ctx = cached ?: return false
        return nowMs - cachedAtMs < FRESH_TTL_MS
    }

    /** Any in-memory HUD context for instant paint (includes stale). */
    fun peekForPanel(): OverlayTeamHudContext? = cached

    fun peekStale(): OverlayTeamHudContext? = cached

    /** Valid in-memory HUD context within fresh TTL. */
    fun peekValid(nowMs: Long = System.currentTimeMillis()): OverlayTeamHudContext? {
        val ctx = cached ?: return null
        if (nowMs - cachedAtMs >= FRESH_TTL_MS) return null
        return ctx
    }

    fun peekCachedTeam(): TeamDetailDto? = cachedTeam

    /** Ник из кэша состава команды (для подписи входящей реакции без блокирующего API). */
    fun memberUsername(userId: String): String? {
        val id = userId.trim()
        if (id.isEmpty()) return null
        return cachedTeam?.members
            ?.firstOrNull { it.userId == id }
            ?.username
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    /** Cached profile avatar URL (team roster). */
    fun memberAvatarRelativeUrl(userId: String): String? {
        val id = userId.trim()
        if (id.isEmpty()) return null
        return cachedTeam?.members
            ?.firstOrNull { it.userId == id }
            ?.avatarRelativeUrl
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    fun memberDto(userId: String): com.lastasylum.alliance.data.teams.PlayerTeamMemberDto? {
        val id = userId.trim()
        if (id.isEmpty()) return null
        return cachedTeam?.members?.firstOrNull { it.userId == id }
    }

    suspend fun loadTeamDetail(
        teamId: String,
        teamsRepository: TeamsRepository,
        forceRefresh: Boolean = false,
    ): Result<TeamDetailDto> = runCatching {
        if (!forceRefresh) {
            cachedTeam?.let { team ->
                if (team.id == teamId) return@runCatching team
            }
        }
        val now = System.currentTimeMillis()
        teamsRepository.getTeam(teamId).getOrThrow().also { team ->
            cachedTeam = team
            cachedTeamAtMs = now
            bumpRevision()
        }
    }

    suspend fun load(
        usersRepository: UsersRepository,
        teamsRepository: TeamsRepository,
        forceRefresh: Boolean = false,
    ): Result<OverlayTeamHudContext> = runCatching {
        if (!forceRefresh) {
            cached?.let { ctx -> return@runCatching ctx }
        }
        val profile = usersRepository.peekMyProfile()
            ?: usersRepository.peekMyProfileDisk()
            ?: usersRepository.getMyProfile().getOrThrow()
        val teamId = profile.playerTeamId?.trim().orEmpty()
        if (teamId.isEmpty()) {
            error("no_team")
        }
        val team = teamsRepository.getTeam(teamId).getOrThrow()
        val myTeamRole = team.members.find { it.userId == profile.id }?.teamRole ?: "R1"
        val now = System.currentTimeMillis()
        OverlayTeamHudContext(
            teamId = teamId,
            currentUserId = profile.id,
            myTeamRole = myTeamRole,
            canPublishNews = myTeamRole == "R4" || myTeamRole == "R5",
            canManageForumTopics = myTeamRole == "R4" || myTeamRole == "R5",
            canModerateForumMessages = myTeamRole == "R4" || myTeamRole == "R5",
            enabledStickerPackKeys = profile.enabledStickerPacks.toSet(),
            teamTag = team.tag,
            teamDisplayName = team.displayName,
        ).also { ctx ->
            cached = ctx
            cachedAtMs = now
            cachedTeam = team
            cachedTeamAtMs = now
            bumpRevision()
        }
    }

    private fun bumpRevision() {
        _revision.value = _revision.value + 1
    }
}
