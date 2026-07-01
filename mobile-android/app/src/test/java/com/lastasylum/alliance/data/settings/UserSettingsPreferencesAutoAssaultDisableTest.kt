package com.lastasylum.alliance.data.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UserSettingsPreferencesAutoAssaultDisableTest {
    private lateinit var prefs: UserSettingsPreferences

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        prefs = UserSettingsPreferences(ctx).also { it.bindUser("assault-disable-test") }
    }

    @Test
    fun durationZero_clearsDisableAtOnOverlayCommit() {
        prefs.setAutoAssaultDurationMin(60)
        prefs.setAutoAssaultEnabled(true)
        assertTrue(prefs.getAutoAssaultDisableAtMs() > 0L)

        prefs.commitAutoAssaultOverlayDraft(sampleDraft(durationMin = 0))

        assertEquals(0L, prefs.getAutoAssaultDisableAtMs())
        assertTrue(prefs.isAutoAssaultEnabled())
    }

    @Test
    fun durationZero_staysEnabledAfterClearStaleDisableAt() {
        prefs.setAutoAssaultDurationMin(0)
        prefs.setAutoAssaultEnabled(true)
        prefs.commitAutoAssaultOverlayDraft(sampleDraft(durationMin = 0))
        prefs.clearStaleAutoAssaultDisableAt()
        assertTrue(prefs.isAutoAssaultEnabled())
        assertEquals(0L, prefs.getAutoAssaultDisableAtMs())
    }

    private fun sampleDraft(durationMin: Int) = AutoAssaultOverlayDraft(
        squads = setOf(0),
        allowedMemberIds = emptySet(),
        targetTypes = UserSettingsPreferences.AUTO_ASSAULT_TYPES_ALL,
        squadPowerMin = listOf(0L, 0L, 0L),
        squadPowerMax = List(3) { UserSettingsPreferences.AUTO_ASSAULT_POWER_MAX_DEFAULT },
        maxDistanceCreator = 50,
        maxDistanceTarget = 50,
        levelMin = 0,
        levelMax = 0,
        minRemainingSec = 30,
        cooldownSec = 1,
        maxConcurrent = 0,
        durationMin = durationMin,
    )
}
