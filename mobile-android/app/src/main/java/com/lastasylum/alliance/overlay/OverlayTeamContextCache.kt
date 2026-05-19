package com.lastasylum.alliance.overlay

import com.lastasylum.alliance.data.teams.TeamsRepository
import com.lastasylum.alliance.data.users.UsersRepository

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

    @Volatile
    private var cached: OverlayTeamHudContext? = null

    @Volatile
    private var cachedAtMs: Long = 0L

    fun invalidate() {
        cached = null
        cachedAtMs = 0L
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
        val profile = usersRepository.getMyProfile().getOrThrow()
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
            canModerateForumMessages = myTeamRole == "R5",
            enabledStickerPackKeys = profile.enabledStickerPacks?.toSet() ?: emptySet(),
            teamTag = team.tag,
            teamDisplayName = team.displayName,
        ).also { ctx ->
            cached = ctx
            cachedAtMs = now
        }
    }
}
