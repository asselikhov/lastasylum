package com.lastasylum.alliance.data.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayReactionLogClusterPolicyTest {
    private val self = "self-id"
    private val other = "other-id"

    private fun entry(
        id: String,
        sender: String,
        createdAt: String,
        visibility: OverlayReactionLogVisibility = OverlayReactionLogVisibility.Personal,
        targetUserId: String? = self,
    ) = OverlayReactionLogEntry(
        id = id,
        senderUserId = sender,
        senderUsername = "User",
        targetUserId = targetUserId,
        targetUsername = "Target",
        reaction = "heart",
        visibility = visibility,
        createdAt = createdAt,
    )

    @Test
    fun mergesEntriesWithinTwoSecondsSameSender() {
        val a = entry("2", other, "2026-01-01T12:00:02.000Z")
        val b = entry("1", other, "2026-01-01T12:00:01.000Z")
        val clusters = OverlayReactionLogClusterPolicy.clusterEntries(listOf(a, b), self)
        assertEquals(1, clusters.size)
        assertEquals(2, clusters.first().mergeCount)
    }

    @Test
    fun doesNotMergeDifferentVisibility() {
        val personal = entry("2", other, "2026-01-01T12:00:02.000Z")
        val broadcast = entry(
            "1",
            other,
            "2026-01-01T12:00:01.000Z",
            visibility = OverlayReactionLogVisibility.Broadcast,
            targetUserId = null,
        )
        val clusters = OverlayReactionLogClusterPolicy.clusterEntries(listOf(personal, broadcast), self)
        assertEquals(2, clusters.size)
    }

    @Test
    fun doesNotMergeOutsideWindow() {
        val newer = entry("2", other, "2026-01-01T12:00:05.000Z")
        val older = entry("1", other, "2026-01-01T12:00:01.000Z")
        assertFalse(OverlayReactionLogClusterPolicy.canMerge(newer, older, self))
    }
}
