package com.lastasylum.alliance.data.auth

class AuthRepository(
    private val authApi: AuthApi,
    private val tokenStore: TokenStore,
) {
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
        runCatching { authApi.logout() }
        tokenStore.clearTokens()
    }
}
