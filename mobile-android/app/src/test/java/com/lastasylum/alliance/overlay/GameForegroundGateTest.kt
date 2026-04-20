package com.lastasylum.alliance.overlay

import org.junit.Assert.assertEquals
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

    @Test
    fun targetNearLeader_whenTiedWithinSlop() {
        assertTrue(
            GameForegroundGate.isTargetLastUsedNearLeader(
                lastUsedByPackage = mapOf(
                    "com.android.systemui" to 1000L,
                    "com.lastasylum.plague" to 998L,
                ),
                target = "com.lastasylum.plague",
                slopMs = 5L,
            ),
        )
    }

    @Test
    fun targetNotNearLeader_whenFarBehind() {
        assertFalse(
            GameForegroundGate.isTargetLastUsedNearLeader(
                lastUsedByPackage = mapOf(
                    "com.android.chrome" to 10_000L,
                    "com.lastasylum.plague" to 100L,
                ),
                target = "com.lastasylum.plague",
                slopMs = 5L,
            ),
        )
    }

    @Test
    fun meaningfulResume_prefersGameOverTrailingSystemUi() {
        assertEquals(
            "com.phs.global",
            GameForegroundGate.selectMeaningfulForegroundResume(
                listOf("com.phs.global", "com.android.systemui"),
            ),
        )
    }

    @Test
    fun meaningfulResume_whenOnlyDecor_returnsLastDecor() {
        assertEquals(
            "com.android.systemui",
            GameForegroundGate.selectMeaningfulForegroundResume(
                listOf("com.android.systemui", "com.android.systemui"),
            ),
        )
    }

    @Test
    fun meaningfulResume_launcherAfterGame_isLauncher() {
        assertEquals(
            "com.miui.home",
            GameForegroundGate.selectMeaningfulForegroundResume(
                listOf("com.phs.global", "com.miui.home"),
            ),
        )
    }

    @Test
    fun resumeTimeline_true_whenOnlyGameResumes() {
        assertTrue(
            GameForegroundGate.targetWinsResumeTimelineFromPairs(
                listOf(100L to "com.phs.global"),
                targets = setOf("com.phs.global"),
                alliance = "com.lastasylum.alliance",
            ),
        )
    }

    @Test
    fun resumeTimeline_false_whenOtherAppResumedAfterGame() {
        assertFalse(
            GameForegroundGate.targetWinsResumeTimelineFromPairs(
                listOf(
                    100L to "com.phs.global",
                    200L to "com.android.chrome",
                ),
                targets = setOf("com.phs.global"),
                alliance = "com.lastasylum.alliance",
            ),
        )
    }

    @Test
    fun resumeTimeline_true_whenGameResumedAfterOther() {
        assertTrue(
            GameForegroundGate.targetWinsResumeTimelineFromPairs(
                listOf(
                    100L to "com.android.chrome",
                    200L to "com.phs.global",
                ),
                targets = setOf("com.phs.global"),
                alliance = "com.lastasylum.alliance",
            ),
        )
    }

    @Test
    fun resumeTimeline_skipsAllianceInOtherTrack() {
        assertTrue(
            GameForegroundGate.targetWinsResumeTimelineFromPairs(
                listOf(
                    100L to "com.phs.global",
                    150L to "com.lastasylum.alliance",
                ),
                targets = setOf("com.phs.global"),
                alliance = "com.lastasylum.alliance",
            ),
        )
    }

    @Test
    fun totalTimeForegroundIncreased_whenGrowing() {
        assertTrue(GameForegroundGate.totalTimeForegroundIncreased(100L, 200L))
    }

    @Test
    fun totalTimeForegroundIncreased_falseOnFirstSample() {
        assertFalse(GameForegroundGate.totalTimeForegroundIncreased(null, 200L))
    }

    @Test
    fun totalTimeForegroundIncreased_falseWhenFlat() {
        assertFalse(GameForegroundGate.totalTimeForegroundIncreased(200L, 200L))
    }
}
