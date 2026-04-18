package com.lastasylum.alliance.data.network

import com.lastasylum.alliance.data.auth.AuthApi
import com.lastasylum.alliance.data.auth.RefreshRequest
import com.lastasylum.alliance.data.auth.TokenStore
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

class TokenAuthenticator(
    private val tokenStore: TokenStore,
    private val authApi: AuthApi,
    private val onAccessTokenRefreshed: () -> Unit = {},
) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        if (responseCount(response) >= 2) {
            return null
        }

        val requestToken = response.request.header("Authorization")
            ?.removePrefix("Bearer ")
            ?.trim()
        val storedAccessToken = tokenStore.getAccessToken()
        if (!storedAccessToken.isNullOrBlank() && storedAccessToken != requestToken) {
            return response.request.newBuilder()
                .header("Authorization", "Bearer $storedAccessToken")
                .build()
        }

        synchronized(this) {
            val latestAccessToken = tokenStore.getAccessToken()
            if (!latestAccessToken.isNullOrBlank() && latestAccessToken != requestToken) {
                return response.request.newBuilder()
                    .header("Authorization", "Bearer $latestAccessToken")
                    .build()
            }

            val refreshToken = tokenStore.getRefreshToken() ?: return null
            val refreshed = runBlocking {
                runCatching { authApi.refresh(RefreshRequest(refreshToken)) }.getOrNull()
            } ?: return null

            tokenStore.saveTokens(refreshed.accessToken, refreshed.refreshToken)
            runCatching { onAccessTokenRefreshed() }
            return response.request.newBuilder()
                .header("Authorization", "Bearer ${refreshed.accessToken}")
                .build()
        }
    }

    private fun responseCount(response: Response): Int {
        var result = 1
        var prior = response.priorResponse
        while (prior != null) {
            result++
            prior = prior.priorResponse
        }
        return result
    }
}
