package com.lastasylum.alliance.overlay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameForegroundGateTest {
    @Test
    fun eligible_whenTargetInForeground() {
        assertTrue(
            GameForegroundGate.isEligibleForegroundResume(
                lastResumedPackage = "com.lastasylum.plague",
                alliancePackage = "com.lastasylum.alliance",
                targetGamePackage = "com.lastasylum.plague",
            ),
        )
    }

    @Test
    fun eligible_whenAllianceInForeground() {
        assertTrue(
            GameForegroundGate.isEligibleForegroundResume(
                lastResumedPackage = "com.lastasylum.alliance",
                alliancePackage = "com.lastasylum.alliance",
                targetGamePackage = "com.lastasylum.plague",
            ),
        )
    }

    @Test
    fun notEligible_forOtherApp() {
        assertFalse(
            GameForegroundGate.isEligibleForegroundResume(
                lastResumedPackage = "com.android.chrome",
                alliancePackage = "com.lastasylum.alliance",
                targetGamePackage = "com.lastasylum.plague",
            ),
        )
    }

    @Test
    fun notEligible_whenLastUnknown() {
        assertFalse(
            GameForegroundGate.isEligibleForegroundResume(
                lastResumedPackage = null,
                alliancePackage = "com.lastasylum.alliance",
                targetGamePackage = "com.lastasylum.plague",
            ),
        )
    }
}
