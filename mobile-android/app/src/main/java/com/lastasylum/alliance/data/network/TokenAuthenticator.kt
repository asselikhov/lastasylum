package com.lastasylum.alliance.data.network

import com.lastasylum.alliance.data.auth.AuthApi
import com.lastasylum.alliance.data.auth.TokenStore
import okhttp3.Authenticator
import com.lastasylum.alliance.data.network.TokenRefreshCoordinator.refreshTokensBlocking
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

            val refreshed = refreshTokensBlocking(tokenStore, authApi)
            if (!refreshed) return null
            runCatching { onAccessTokenRefreshed() }
            val accessToken = tokenStore.getAccessToken() ?: return null
            return response.request.newBuilder()
                .header("Authorization", "Bearer $accessToken")
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
