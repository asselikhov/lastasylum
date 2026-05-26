package com.lastasylum.alliance.data.users

import com.lastasylum.alliance.data.cache.LaunchDiskCache
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory + disk profile cache with single-flight network fetch.
 * Cuts duplicate GET /users/me bursts on launch and overlay.
 */
class SessionProfileCache(
    private val launchDiskCache: LaunchDiskCache,
    private val resolveUserId: () -> String?,
    private val fetchFromNetwork: suspend () -> MyProfileDto,
) {
    @Volatile
    private var memoryProfile: MyProfileDto? = null

    @Volatile
    private var memoryAtMs: Long = 0L

    private val mutex = Mutex()

    @Volatile
    private var inFlight: Deferred<Result<MyProfileDto>>? = null

    fun peekFresh(): MyProfileDto? {
        val profile = memoryProfile ?: return null
        if (System.currentTimeMillis() - memoryAtMs > MEMORY_TTL_MS) return null
        return profile
    }

    fun peekDisk(): MyProfileDto? {
        val userId = resolveUserId()?.trim().orEmpty()
        if (userId.isEmpty()) return null
        return launchDiskCache.loadProfile(userId)
    }

    suspend fun get(forceRefresh: Boolean = false): Result<MyProfileDto> {
        if (!forceRefresh) {
            peekFresh()?.let { return Result.success(it) }
        }
        return mutex.withLock {
            if (!forceRefresh) {
                peekFresh()?.let { return@withLock Result.success(it) }
            }
            val existing = inFlight
            if (existing != null && !existing.isCompleted) {
                return@withLock existing.await()
            }
            val deferred = CompletableDeferred<Result<MyProfileDto>>()
            inFlight = deferred
            val diskProfile = if (!forceRefresh) peekDisk() else null
            if (diskProfile != null) {
                put(diskProfile)
            }
            val result = runCatching { fetchFromNetwork() }
            result.onSuccess { put(it) }
            deferred.complete(result)
            inFlight = null
            result
        }
    }

    fun put(profile: MyProfileDto) {
        memoryProfile = profile
        memoryAtMs = System.currentTimeMillis()
        val userId = resolveUserId()?.trim().orEmpty()
        if (userId.isNotEmpty()) {
            launchDiskCache.saveProfile(userId, profile)
        }
    }

    fun invalidate() {
        memoryProfile = null
        memoryAtMs = 0L
        inFlight = null
    }

    companion object {
        const val MEMORY_TTL_MS = 60_000L
    }
}
