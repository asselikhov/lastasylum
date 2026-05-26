package com.lastasylum.alliance.data.users

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.lastasylum.alliance.data.cache.LaunchDiskCache
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SessionProfileCacheTest {

    private lateinit var disk: LaunchDiskCache
    private val userId = "user_test_cache"

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        disk = LaunchDiskCache(ctx)
        disk.clearUser(userId)
    }

    @Test
    fun parallelGet_singleNetworkFetch() = runTest {
        var networkCalls = 0
        val profile = sampleProfile()
        val cache = SessionProfileCache(
            launchDiskCache = disk,
            resolveUserId = { userId },
            fetchFromNetwork = {
                networkCalls++
                profile
            },
        )
        val results = (1..10).map {
            async { cache.get() }
        }.awaitAll()
        assertEquals(1, networkCalls)
        results.forEach { assertEquals(profile, it.getOrNull()) }
    }

    @Test
    fun peekFresh_afterPut_withoutNetwork() {
        val profile = sampleProfile()
        val cache = SessionProfileCache(
            launchDiskCache = disk,
            resolveUserId = { userId },
            fetchFromNetwork = { error("network") },
        )
        cache.put(profile)
        assertSame(profile, cache.peekFresh())
        assertEquals(profile.id, disk.loadProfile(userId)?.id)
    }

    @Test
    fun diskWarm_thenNetwork() = runTest {
        val diskProfile = sampleProfile().copy(username = "disk")
        val netProfile = sampleProfile().copy(username = "net")
        disk.saveProfile(userId, diskProfile)
        var networkCalls = 0
        val cache = SessionProfileCache(
            launchDiskCache = disk,
            resolveUserId = { userId },
            fetchFromNetwork = {
                networkCalls++
                netProfile
            },
        )
        val result = cache.get()
        assertEquals(1, networkCalls)
        assertEquals(netProfile, result.getOrNull())
        assertEquals(netProfile.username, disk.loadProfile(userId)?.username)
    }

    private fun sampleProfile() = MyProfileDto(
        id = userId,
        username = "test",
        email = "test@example.com",
        role = "MEMBER",
        allianceName = "ally",
        membershipStatus = "ACTIVE",
    )
}
