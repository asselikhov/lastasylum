package com.lastasylum.alliance.data.auth

import com.lastasylum.alliance.data.chat.ChatRoomPreferences

class AuthRepository(
    private val authApi: AuthApi,
    private val authorizedAuthApi: AuthApi,
    private val tokenStore: TokenStore,
    private val chatRoomPreferences: ChatRoomPreferences,
) {
    suspend fun register(
        username: String,
        email: String,
        password: String,
    ): Result<RegisterResult> {
        return runCatching {
            val body = authApi.register(
                RegisterRequest(username = username.trim(), email = email.trim(), password = password),
            )
            if (body.approvalRequired == true) {
                return@runCatching RegisterResult.PendingApproval
            }
            val access = body.accessToken ?: error("Missing access token")
            val refresh = body.refreshToken ?: error("Missing refresh token")
            val user = body.user ?: error("Missing user")
            tokenStore.saveTokens(access, refresh)
            RegisterResult.LoggedIn(user)
        }
    }

    suspend fun login(email: String, password: String): Result<AuthUser> {
        return runCatching {
            val response = authApi.login(LoginRequest(email = email, password = password))
            tokenStore.saveTokens(response.accessToken, response.refreshToken)
            response.user
        }
    }

    suspend fun refreshSession(): Result<AuthUser> {
        val refreshToken = tokenStore.getRefreshToken()
            ?: return Result.failure(IllegalStateException("Missing refresh token"))
        return runCatching {
            val response = authApi.refresh(RefreshRequest(refreshToken))
            tokenStore.saveTokens(response.accessToken, response.refreshToken)
            response.user
        }
    }

    fun hasSession(): Boolean = tokenStore.getRefreshToken() != null

    suspend fun logout() {
        runCatching { authorizedAuthApi.logout() }
        tokenStore.clearTokens()
        chatRoomPreferences.clear()
    }
}
