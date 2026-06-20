package com.lastasylum.alliance.ui.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatGapDetectionTest {
    @Test
    fun gapReconcile_whenLargeObjectIdJump() {
        val older = "507f1f77bcf86cd799439011"
        val newer = "657a00000000000000000001"
        assertTrue(
            shouldTriggerGapReconcile(
                visibleNewestId = older,
                incomingId = newer,
                knownMessageIds = emptySet(),
            ),
        )
    }

    @Test
    fun noGapReconcile_forDuplicateIncoming() {
        val id = "507f1f77bcf86cd799439011"
        assertFalse(
            shouldTriggerGapReconcile(
                visibleNewestId = id,
                incomingId = id,
                knownMessageIds = setOf(id),
            ),
        )
    }

    @Test
    fun gapReconcile_whenObjectIdCounterIncrementsByOne() {
        val older = "507f1f77bcf86cd799439011"
        val newer = "507f1f77bcf86cd799439012"
        assertTrue(
            shouldTriggerGapReconcile(
                visibleNewestId = older,
                incomingId = newer,
                knownMessageIds = setOf(older),
                thresholdMs = RAID_GAP_RECONCILE_THRESHOLD_MS,
            ),
        )
    }

    @Test
    fun gapReconcile_raidUsesShorterThreshold() {
        val older = "507f1f77bcf86cd799439011"
        val newer = "657a00000000000000000001"
        assertTrue(
            shouldTriggerGapReconcile(
                visibleNewestId = older,
                incomingId = newer,
                knownMessageIds = emptySet(),
                thresholdMs = RAID_GAP_RECONCILE_THRESHOLD_MS,
            ),
        )
    }

    @Test
    fun gapReconcile_whenObjectIdCounterSkipsSequence() {
        val older = "507f1f77bcf86cd799439011"
        val newer = "507f1f77bcf86cd799439013"
        assertTrue(
            shouldTriggerGapReconcile(
                visibleNewestId = older,
                incomingId = newer,
                knownMessageIds = setOf(older),
                thresholdMs = RAID_GAP_RECONCILE_THRESHOLD_MS,
            ),
        )
    }

    @Test
    fun fingerprint_stableForSameMessage() {
        val fp = incomingMessageFingerprint("u1", "hello", "2026-01-01T00:00:00Z")
        assertTrue(fp.contains("u1"))
        assertTrue(fp.contains("hello"))
    }
}
