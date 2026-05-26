package com.lastasylum.alliance.data.users

import com.lastasylum.alliance.data.cache.LaunchDiskCache
import com.lastasylum.alliance.data.auth.JwtAccessTokenClaims
import com.lastasylum.alliance.data.auth.TokenStore

class UsersRepository(
    private val usersApi: UsersApi,
    launchDiskCache: LaunchDiskCache,
    tokenStore: TokenStore,
) {
    private val sessionProfileCache = SessionProfileCache(
        launchDiskCache = launchDiskCache,
        resolveUserId = {
            JwtAccessTokenClaims.sub(tokenStore.getAccessToken())
        },
        fetchFromNetwork = { usersApi.getMyProfile() },
    )

    /** Cached profile if still within [SessionProfileCache.MEMORY_TTL_MS]. */
    fun peekMyProfile(): MyProfileDto? = sessionProfileCache.peekFresh()

  /** Disk snapshot without network. */
    fun peekMyProfileDisk(): MyProfileDto? = sessionProfileCache.peekDisk()

    suspend fun getMyProfile(forceRefresh: Boolean = false): Result<MyProfileDto> =
        sessionProfileCache.get(forceRefresh)

    /** Memory or disk snapshot without forcing network when possible. */
    suspend fun resolveMyProfilePreferCache(): MyProfileDto? =
        peekMyProfile()
            ?: peekMyProfileDisk()
            ?: getMyProfile().getOrNull()

    fun invalidateProfileCache() {
        sessionProfileCache.invalidate()
    }

    private suspend fun applyProfileMutation(block: suspend () -> MyProfileDto): Result<MyProfileDto> =
        runCatching {
            block().also { sessionProfileCache.put(it) }
        }

    suspend fun updateMyTelegram(username: String): Result<MyProfileDto> =
        applyProfileMutation {
            usersApi.updateMyTelegram(UpdateTelegramBody(username = username))
        }

    suspend fun updateMyUsername(username: String): Result<MyProfileDto> =
        applyProfileMutation {
            usersApi.updateMyUsername(UpdateUsernameBody(username = username.trim()))
        }

    suspend fun addGameIdentity(serverNumber: Int, gameNickname: String): Result<MyProfileDto> =
        applyProfileMutation {
            usersApi.addGameIdentity(
                CreateGameIdentityBody(
                    serverNumber = serverNumber,
                    gameNickname = gameNickname.trim(),
                ),
            )
        }

    suspend fun updateGameIdentity(
        identityId: String,
        serverNumber: Int?,
        gameNickname: String?,
    ): Result<MyProfileDto> =
        applyProfileMutation {
            usersApi.updateGameIdentity(
                identityId,
                UpdateGameIdentityBody(
                    serverNumber = serverNumber,
                    gameNickname = gameNickname?.trim(),
                ),
            )
        }

    suspend fun deleteGameIdentity(identityId: String): Result<MyProfileDto> =
        applyProfileMutation { usersApi.deleteGameIdentity(identityId) }

    suspend fun switchActiveGameIdentity(identityId: String): Result<MyProfileDto> =
        applyProfileMutation {
            usersApi.switchActiveGameIdentity(
                SwitchActiveGameIdentityBody(gameIdentityId = identityId),
            )
        }

    suspend fun updateExcavationPushEnabled(enabled: Boolean): Result<MyProfileDto> =
        applyProfileMutation {
            usersApi.updateNotificationPreferences(
                UpdateNotificationPreferencesBody(excavationPushEnabled = enabled),
            )
        }

    suspend fun registerPushToken(token: String): Result<Unit> =
        runCatching { usersApi.registerPushToken(PushTokenBody(token)) }

    suspend fun clearPushTokens(): Result<Unit> =
        runCatching { usersApi.clearPushTokens() }

    suspend fun updatePresence(status: String): Result<Unit> =
        runCatching { usersApi.updatePresence(PresenceBody(status)) }

    suspend fun listMembers(
        allianceCode: String? = null,
        q: String? = null,
        skip: Int = 0,
        limit: Int = 300,
    ): Result<List<TeamMemberDto>> =
        runCatching {
            usersApi.listMembers(
                allianceCode = allianceCode?.takeIf { it.isNotBlank() },
                q = q?.trim()?.takeIf { it.isNotEmpty() },
                skip = skip,
                limit = limit,
            )
        }

    suspend fun updateMembership(userId: String, status: String): Result<TeamMemberDto> =
        runCatching {
            usersApi.updateMembership(userId, UpdateMembershipBody(status = status))
        }

    suspend fun updateRole(userId: String, role: String): Result<TeamMemberDto> =
        runCatching {
            usersApi.updateRole(UpdateRoleBody(userId = userId, role = role))
        }

    suspend fun updateUsername(userId: String, username: String): Result<TeamMemberDto> =
        runCatching {
            usersApi.updateUsername(userId, UpdateUsernameBody(username = username.trim()))
        }

    suspend fun deleteUser(userId: String): Result<Unit> =
        runCatching { usersApi.deleteUser(userId).let { } }
}
