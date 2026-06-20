package com.lastasylum.alliance.push

import android.content.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Single entry for FCM token registration — dedupes parallel callers and honors backoff. */
object PushTokenRegistrationCoordinator {
    private val mutex = Mutex()

    @Volatile
    private var lastSuccessAtMs: Long = 0L

    private const val MIN_SUCCESS_INTERVAL_MS = 30_000L

    suspend fun registerWithBackend(context: Context, force: Boolean = false): Result<Unit> =
        mutex.withLock {
            val now = System.currentTimeMillis()
            if (!force && lastSuccessAtMs > 0L && now - lastSuccessAtMs < MIN_SUCCESS_INTERVAL_MS) {
                return@withLock Result.success(Unit)
            }
            FcmTokenManager.registerWithBackend(context).also { result ->
                if (result.isSuccess) {
                    lastSuccessAtMs = System.currentTimeMillis()
                }
            }
        }
}
