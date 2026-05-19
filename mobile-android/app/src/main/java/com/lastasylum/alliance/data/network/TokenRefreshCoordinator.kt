package com.lastasylum.alliance.data.network

import com.lastasylum.alliance.data.auth.AuthApi
import com.lastasylum.alliance.data.auth.RefreshRequest
import com.lastasylum.alliance.data.auth.TokenStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Single in-flight refresh for parallel 401s (OkHttp authenticator + sockets).
 */
object TokenRefreshCoordinator {
    private val mutex = Mutex()
    private var inFlight: CompletableDeferred<Boolean>? = null

    /** @return true if tokens were refreshed, false if refresh failed. */
    suspend fun refreshTokens(tokenStore: TokenStore, authApi: AuthApi): Boolean =
        mutex.withLock {
            inFlight?.await()?.let { return@withLock it }
            val deferred = CompletableDeferred<Boolean>()
            inFlight = deferred
            try {
                val refreshToken = tokenStore.getRefreshToken()
                if (refreshToken.isNullOrBlank()) {
                    deferred.complete(false)
                    false
                } else {
                    val refreshed = runCatching {
                        authApi.refresh(RefreshRequest(refreshToken))
                    }.getOrNull()
                    if (refreshed == null) {
                        deferred.complete(false)
                        false
                    } else {
                        tokenStore.saveTokens(refreshed.accessToken, refreshed.refreshToken)
                        deferred.complete(true)
                        true
                    }
                }
            } finally {
                inFlight = null
            }
        }
}
