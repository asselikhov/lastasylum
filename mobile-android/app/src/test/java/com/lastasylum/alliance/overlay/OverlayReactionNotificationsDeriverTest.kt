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

    @Test
    fun filterEntries_replyScope_returnsOnlyReplies() {
        val self = "self-1"
        val reply = entry(
            id = "2",
            sender = "alice",
            target = self,
            visibility = OverlayReactionLogVisibility.Personal,
        ).copy(
            replyToLog = com.lastasylum.alliance.data.chat.OverlayReactionLogReplyTo(
                logId = "1",
                reaction = "heart",
                visibility = OverlayReactionLogVisibility.Personal,
                senderUserId = self,
                senderUsername = "Self",
                targetUserId = "alice",
                targetUsername = "Alice",
            ),
        )
        val plain = entry("1", "alice", self, OverlayReactionLogVisibility.Personal)
        val filtered = OverlayReactionNotificationsDeriver.filterEntries(
            entries = listOf(plain, reply),
            selfUserId = self,
            directionFilter = OverlayReactionLogFilter.All,
            scopeFilter = OverlayReactionLogScopeFilter.Reply,
            searchQuery = "",
        )
        assertEquals(1, filtered.size)
        assertEquals("2", filtered.single().id)
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
