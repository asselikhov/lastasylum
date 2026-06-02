package com.lastasylum.alliance.overlay

import com.lastasylum.alliance.data.teams.TeamDetailDto
import com.lastasylum.alliance.data.teams.TeamsRepository
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
    private const val TTL_MS = 30_000L

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

    /** Valid in-memory HUD context for instant overlay panel paint (news/forum/online). */
    fun peekValid(nowMs: Long = System.currentTimeMillis()): OverlayTeamHudContext? {
        val ctx = cached ?: return null
        if (nowMs - cachedAtMs >= TTL_MS) return null
        return ctx
    }

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

    /** Cached telegram for overlay avatar (team roster). */
    fun memberTelegramUsername(userId: String): String? {
        val id = userId.trim()
        if (id.isEmpty()) return null
        return cachedTeam?.members
            ?.firstOrNull { it.userId == id }
            ?.telegramUsername
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
        val now = System.currentTimeMillis()
        if (!forceRefresh) {
            cachedTeam?.let { team ->
                if (team.id == teamId && now - cachedTeamAtMs < TTL_MS) {
                    return@runCatching team
                }
            }
        }
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
        val now = System.currentTimeMillis()
        if (!forceRefresh) {
            cached?.let { ctx ->
                if (now - cachedAtMs < TTL_MS) return@runCatching ctx
            }
        }
        val profile = usersRepository.peekMyProfile()
            ?: usersRepository.getMyProfile().getOrThrow()
        val teamId = profile.playerTeamId?.trim().orEmpty()
        if (teamId.isEmpty()) {
            error("no_team")
        }
        val team = teamsRepository.getTeam(teamId).getOrThrow()
        val myTeamRole = team.members.find { it.userId == profile.id }?.teamRole ?: "R1"
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
            bumpRevision()
        }
    }

    private fun bumpRevision() {
        _revision.value = _revision.value + 1
    }
}
