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
    fun fingerprint_stableForSameMessage() {
        val fp = incomingMessageFingerprint("u1", "hello", "2026-01-01T00:00:00Z")
        assertTrue(fp.contains("u1"))
        assertTrue(fp.contains("hello"))
    }
}
