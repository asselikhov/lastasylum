package com.lastasylum.alliance.data.auth

import com.lastasylum.alliance.data.chat.ChatRoomPreferences
import java.util.Locale
import kotlinx.coroutines.CancellationException

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
        return suspendRunCatching {
            val body = authApi.register(
                RegisterRequest(username = username.trim(), email = email.trim(), password = password),
            )
            if (body.approvalRequired == true) {
                return@suspendRunCatching RegisterResult.PendingApproval
            }
            val access = body.accessToken ?: error("Missing access token")
            val refresh = body.refreshToken ?: error("Missing refresh token")
            val user = body.user ?: error("Missing user")
            tokenStore.saveTokens(access, refresh)
            RegisterResult.LoggedIn(user)
        }
    }

    suspend fun login(email: String, password: String): Result<AuthUser> {
        return suspendRunCatching {
            val response = authApi.login(
                LoginRequest(email = email.trim().lowercase(Locale.ROOT), password = password),
            )
            tokenStore.saveTokens(response.accessToken, response.refreshToken)
            response.user
        }
    }

    suspend fun refreshSession(): Result<AuthUser> {
        val refreshToken = tokenStore.getRefreshToken()
            ?: return Result.failure(IllegalStateException("Missing refresh token"))
        return suspendRunCatching {
            val response = authApi.refresh(RefreshRequest(refreshToken))
            tokenStore.saveTokens(response.accessToken, response.refreshToken)
            response.user
        }
    }

    fun hasSession(): Boolean = tokenStore.getRefreshToken() != null

    suspend fun forgotPassword(email: String): Result<Unit> {
        return suspendRunCatching {
            authApi.forgotPassword(ForgotPasswordRequest(email = email.trim().lowercase()))
            Unit
        }
    }

    suspend fun resetPassword(
        email: String,
        token: String,
        newPassword: String,
    ): Result<Unit> {
        return suspendRunCatching {
            authApi.resetPassword(
                ResetPasswordRequest(
                    email = email.trim().lowercase(),
                    token = token.trim(),
                    newPassword = newPassword,
                ),
            )
            Unit
        }
    }

    suspend fun logout() {
        runCatching { authorizedAuthApi.logout() }
        tokenStore.clearTokens()
        chatRoomPreferences.clear()
    }

    /** [runCatching] не должен превращать отмену корутины в [Result.failure] — иначе «тихий» logout стирает новые токены после входа. */
    private suspend fun <T> suspendRunCatching(block: suspend () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Result.failure(e)
        }
}
