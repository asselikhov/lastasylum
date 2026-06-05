package com.lastasylum.alliance.data.users

import com.lastasylum.alliance.data.cache.LaunchDiskCache
import com.lastasylum.alliance.data.teams.TeamMembershipNotifier
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

        var deferred: CompletableDeferred<Result<MyProfileDto>>? = null
        var isLeader = false
        mutex.withLock {
            if (!forceRefresh) {
                peekFresh()?.let {
                    deferred = CompletableDeferred(Result.success(it))
                    isLeader = false
                    return@withLock
                }
            }
            val existing = inFlight
            if (existing != null && !existing.isCompleted) {
                @Suppress("UNCHECKED_CAST")
                deferred = existing as CompletableDeferred<Result<MyProfileDto>>
                isLeader = false
                return@withLock
            }
            val leader = CompletableDeferred<Result<MyProfileDto>>()
            deferred = leader
            inFlight = leader
            isLeader = true
        }
        val waitDeferred = deferred ?: return Result.failure(IllegalStateException("profile_fetch_unavailable"))

        if (isLeader) {
            try {
                if (!forceRefresh) {
                    peekDisk()?.let { put(it) }
                }
                val result = runCatching { fetchFromNetwork() }
                result.onSuccess { put(it) }
                waitDeferred.complete(result)
            } catch (e: Throwable) {
                if (!waitDeferred.isCompleted) {
                    waitDeferred.complete(Result.failure(e))
                }
                throw e
            } finally {
                mutex.withLock {
                    if (inFlight === waitDeferred) {
                        inFlight = null
                    }
                }
            }
        }

        return waitDeferred.await()
    }

    fun put(profile: MyProfileDto) {
        val prevTeamId = memoryProfile?.playerTeamId?.trim().orEmpty()
        val nextTeamId = profile.playerTeamId?.trim().orEmpty()
        memoryProfile = profile
        memoryAtMs = System.currentTimeMillis()
        val userId = resolveUserId()?.trim().orEmpty()
        if (userId.isNotEmpty()) {
            launchDiskCache.saveProfile(userId, profile)
        }
        if (prevTeamId != nextTeamId) {
            TeamMembershipNotifier.notifyChanged(profile.playerTeamId)
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
