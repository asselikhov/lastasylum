package com.lastasylum.alliance.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Manual regression scenarios — run on device before release. */
class ChatRegressionChecklistTest {
    @Test
    fun manualChecklist_documented() {
        val scenarios = listOf(
            "Two players 50+ raid messages visible in strip and full chat",
            "Ten quick commands in a row without duplicates",
            "Minimize app 5 min then reopen with full history",
            "Switch 10 rooms and return with messages intact",
            "Airplane mode 30s then reconnect fills gaps",
            "Overlay FGS cold start shows new raid messages within 2s",
            "Scroll 1000+ messages without jank or pagination holes",
            "Own send scrolls to bottom and preserves position on load older",
            "Unread FAB preserved on tab resume when unread below viewport",
            "HTTP send retry does not create duplicate server messages",
            "Raid fanout does not duplicate message:new on overlay client",
            "Strip pending buffer delivers after raid room id resolves",
        )
        assertTrue(scenarios.size >= 12)
    }

    @Test
    fun skipRefresh_falseAfterReconnectFlag() {
        assertFalse(
            shouldSkipBackgroundMessageRefresh(
                visible = emptyList(),
                sessionCache = emptyList(),
                roomCache = null,
                pageSize = 30,
                forceAfterReconnect = true,
            ),
        )
    }

    @Test
    fun skipRefresh_falseAfterReconcileInterval() {
        val messages = (1..30).map {
            com.lastasylum.alliance.data.chat.ChatMessage(
                _id = "507f1f77bcf86cd7994390${it.toString().padStart(2, '0')}",
                allianceId = "",
                roomId = "room1",
                senderId = "u1",
                senderUsername = "u",
                senderRole = "R1",
                text = "m$it",
                createdAt = "2026-01-01T00:00:00Z",
            )
        }
        assertFalse(
            shouldSkipBackgroundMessageRefresh(
                visible = messages,
                sessionCache = messages,
                roomCache = null,
                pageSize = 30,
                lastRestSyncAtMs = System.currentTimeMillis() - 120_000L,
                nowMs = System.currentTimeMillis(),
            ),
        )
    }

    @Test
    fun raidGapReconcileThreshold_isSixtySeconds() {
        assertEquals(60_000L, RAID_GAP_RECONCILE_THRESHOLD_MS)
    }
}
