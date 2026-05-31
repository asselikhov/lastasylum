package com.lastasylum.alliance.overlay

import com.lastasylum.alliance.data.chat.OverlayReactionLogEntry
import com.lastasylum.alliance.data.chat.OverlayReactionLogFilter
import com.lastasylum.alliance.data.chat.OverlayReactionLogScopeFilter
import com.lastasylum.alliance.data.chat.OverlayReactionLogVisibility
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayReactionNotificationsDeriverTest {

    @Test
    fun filterEntries_appliesDirectionAndSearch() {
        val self = "self-1"
        val entries = listOf(
            entry(id = "2", sender = "alice", target = self, visibility = OverlayReactionLogVisibility.Personal),
            entry(id = "1", sender = self, target = "bob", visibility = OverlayReactionLogVisibility.Personal),
        )
        val incoming = OverlayReactionNotificationsDeriver.filterEntries(
            entries = entries,
            selfUserId = self,
            directionFilter = OverlayReactionLogFilter.Incoming,
            scopeFilter = OverlayReactionLogScopeFilter.All,
            searchQuery = "ali",
        )
        assertEquals(1, incoming.size)
        assertEquals("2", incoming.single().id)
    }

    @Test
    fun filterKey_changesWhenSearchDebounced() {
        val a = OverlayReactionNotificationsDeriver.filterKey(
            OverlayReactionLogFilter.All,
            OverlayReactionLogScopeFilter.All,
            "foo",
        )
        val b = OverlayReactionNotificationsDeriver.filterKey(
            OverlayReactionLogFilter.All,
            OverlayReactionLogScopeFilter.All,
            "bar",
        )
        assertTrue(a != b)
    }

    private fun entry(
        id: String,
        sender: String,
        target: String,
        visibility: OverlayReactionLogVisibility,
    ) = OverlayReactionLogEntry(
        id = id,
        senderUserId = sender,
        senderUsername = sender,
        targetUserId = target,
        targetUsername = target,
        reaction = "heart",
        visibility = visibility,
        createdAt = "2026-05-29T12:00:00Z",
    )
}
