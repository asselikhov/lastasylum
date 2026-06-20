package com.lastasylum.alliance.ui.chat

import com.lastasylum.alliance.data.chat.ChatAllianceIds
import com.lastasylum.alliance.data.chat.ChatAttachment
import com.lastasylum.alliance.data.chat.ChatMessage
import com.lastasylum.alliance.data.chat.ChatReaction
import com.lastasylum.alliance.data.chat.isCompactReactionSocketUpdate
import com.lastasylum.alliance.data.chat.resolveFromSocketUpdate
import com.lastasylum.alliance.data.chat.ChatMessageReplyPreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatListMutationsTest {

    private fun msg(
        id: String,
        text: String = "t",
        replyTo: ChatMessageReplyPreview? = null,
    ) = ChatMessage(
        _id = id,
        allianceId = "alliance",
        roomId = "room",
        senderId = "u1",
        senderUsername = "user",
        senderRole = "R1",
        text = text,
        replyTo = replyTo,
    )

    @Test
    fun upsertMessage_prependsNewWithId() {
        val known = linkedSetOf<String>()
        val current = listOf(msg("1", "a"))
        val incoming = msg("2", "b")
        val r = upsertMessage(current, incoming, known)
        assertEquals(listOf("2", "1"), r.messages.map { it._id })
        assertEquals("2", r.newestMessageKey)
        assertTrue(known.contains("2"))
    }

    @Test
    fun upsertMessage_preservesAttachmentsWhenIncomingEmpty() {
        val known = linkedSetOf("1")
        val withImage = msg("1", "a").copy(
            attachments = listOf(
                ChatAttachment(kind = "image", url = "/chat/attachments/abc"),
            ),
        )
        val incoming = msg("1", "a")
        val r = upsertMessage(listOf(withImage), incoming, known)
        assertEquals(1, r.messages.size)
        assertEquals("/chat/attachments/abc", r.messages[0].attachments.single().url)
    }

    @Test
    fun resolveFromSocketUpdate_keepsExistingWhenIncomingCountsZero() {
        val existing = listOf(ChatReaction(emoji = "👍", count = 2, reactedByMe = false))
        val broken = listOf(ChatReaction(emoji = "👍", count = 0, reactedByMe = false))
        assertEquals(existing, broken.resolveFromSocketUpdate(existing))
        assertEquals(emptyList<ChatReaction>(), emptyList<ChatReaction>().resolveFromSocketUpdate(existing))
    }

    @Test
    fun upsertMessage_mergesReactionsFromSocket() {
        val known = linkedSetOf("1")
        val withReaction = msg("1", "a").copy(
            reactions = listOf(ChatReaction(emoji = "👍", count = 1, reactedByMe = false)),
        )
        val incoming = msg("1", "a").copy(
            reactions = listOf(ChatReaction(emoji = "👍", count = 2, reactedByMe = false)),
        )
        val r = upsertMessage(listOf(withReaction), incoming, known)
        assertEquals(2, r.messages[0].reactions.single().count)
    }

    @Test
    fun upsertMessage_compactReaction_doesNotWipeContent() {
        val known = linkedSetOf("1")
        val existing = msg("1", "hello").copy(senderUsername = "alice")
        val compact = ChatMessage(
            _id = "1",
            allianceId = "",
            roomId = "room",
            senderId = "",
            senderUsername = "",
            senderRole = "",
            text = "",
            reactions = listOf(ChatReaction(emoji = "👍", count = 1, reactedByMe = true)),
        )
        assertTrue(compact.isCompactReactionSocketUpdate())
        val r = upsertMessage(listOf(existing), compact, known)
        assertEquals("hello", r.messages[0].text)
        assertEquals("alice", r.messages[0].senderUsername)
        assertEquals(1, r.messages[0].reactions.single().count)
    }

    @Test
    fun upsertMessage_replacesExistingById() {
        val known = linkedSetOf("1")
        val current = listOf(msg("1", "old"))
        val incoming = msg("1", "new")
        val r = upsertMessage(current, incoming, known)
        assertEquals(1, r.messages.size)
        assertEquals("new", r.messages[0].text)
        assertNull(r.newestMessageKey)
    }

    @Test
    fun upsertMessagesBatch_appliesInOrderOnce() {
        val known = linkedSetOf<String>()
        val index = mutableMapOf<String, Int>()
        val current = listOf(msg("1", "a"))
        val batch = listOf(msg("2", "b"), msg("3", "c"))
        val r = upsertMessagesBatch(current, batch, known, index)
        assertEquals(listOf("3", "2", "1"), r.messages.map { it._id })
        assertEquals("3", r.newestMessageKey)
    }

    @Test
    fun upsertMessagesBatch_outOfOrderBatchSortsNewestFirst() {
        val known = linkedSetOf<String>()
        val index = mutableMapOf<String, Int>()
        val current = listOf(msg("507f1f77bcf86cd799439011", "anchor"))
        val batch = listOf(
            msg("507f1f77bcf86cd799439012", "older socket"),
            msg("507f1f77bcf86cd799439013", "newer socket"),
        )
        val r = upsertMessagesBatch(current, batch, known, index)
        assertEquals(
            listOf(
                "507f1f77bcf86cd799439013",
                "507f1f77bcf86cd799439012",
                "507f1f77bcf86cd799439011",
            ),
            r.messages.map { it._id },
        )
    }

    @Test
    fun chatMessagesListContentEqual_detectsChanges() {
        val a = listOf(msg("1", "a"), msg("2", "b"))
        val b = listOf(msg("1", "a"), msg("2", "b"))
        val c = listOf(msg("1", "a"), msg("2", "c"))
        assertTrue(chatMessagesListContentEqual(a, b))
        assertFalse(chatMessagesListContentEqual(a, c))
    }

    @Test
    fun mergeOlderPage_deduplicatesKnownIds() {
        val known = linkedSetOf("a", "b")
        val current = listOf(msg("a"), msg("b"))
        val older = listOf(msg("b"), msg("c"))
        val merged = mergeOlderPage(current, older, known)
        assertEquals(listOf("a", "b", "c"), merged.map { it._id })
        assertTrue(known.contains("c"))
    }

    @Test
    fun mergeLoadedPageWithExisting_keepsSocketRowMissingFromHttpPage() {
        val socketNew = msg("507f1f77bcf86cd799439013", "from socket")
        val existing = listOf(socketNew, msg("507f1f77bcf86cd799439011", "x"))
        val loaded = listOf(msg("507f1f77bcf86cd799439011", "x"))
        val merged = mergeLoadedPageWithExisting(existing, loaded)
        assertEquals(listOf("507f1f77bcf86cd799439013", "507f1f77bcf86cd799439011"), merged.map { it._id })
    }

    @Test
    fun mergeLoadedPageWithExisting_keepsUnionByIdWhenPartialRest() {
        val anchor = "507f1f77bcf86cd799439013"
        val socketMiddle = "507f1f77bcf86cd799439012"
        val older = "507f1f77bcf86cd799439011"
        val existing = listOf(msg(anchor), msg(socketMiddle), msg(older))
        val loaded = listOf(msg(anchor), msg(older))
        val merged = mergeLoadedPageWithExisting(existing, loaded)
        assertEquals(listOf(anchor, socketMiddle, older), merged.map { it._id })
    }

    @Test
    fun mergeLoadedPageWithExisting_dropsStaleDiskRowsOlderThanPageAnchor() {
        val anchor = "507f1f77bcf86cd799439013"
        val staleDisk = "507f1f77bcf86cd799439011"
        val existing = listOf(msg(staleDisk, "ghost"))
        val loaded = listOf(msg(anchor, "fresh"))
        val merged = mergeLoadedPageWithExisting(existing, loaded)
        assertEquals(listOf(anchor), merged.map { it._id })
    }

    @Test
    fun mergeLoadedPageWithExisting_honorsExcludedIds() {
        val stale = "507f1f77bcf86cd799439099"
        val anchor = "507f1f77bcf86cd799439011"
        val existing = listOf(msg(stale, "ghost"))
        val loaded = listOf(msg(anchor, "ok"))
        val merged = mergeLoadedPageWithExisting(
            existing = existing,
            loaded = loaded,
            excludedMessageIds = setOf(stale),
        )
        assertEquals(listOf(anchor), merged.map { it._id })
    }

    @Test
    fun mergeLoadedPageWithExisting_keepsExistingWhenServerPageEmpty() {
        val existing = listOf(msg("stale-1", "old"), msg("pending-1", "sending"))
        val merged = mergeLoadedPageWithExisting(existing, loaded = emptyList())
        assertEquals(listOf("stale-1", "pending-1"), merged.map { it._id })
    }

    @Test
    fun mergeLoadedPageWithExisting_authoritativeEmpty_clearsLocalRows() {
        val existing = listOf(msg("stale-1", "old"), msg("pending-1", "sending"))
        val merged = mergeLoadedPageWithExisting(
            existing = existing,
            loaded = emptyList(),
            authoritativeEmpty = true,
        )
        assertTrue(merged.isEmpty())
    }

    @Test
    fun outgoingMessageFingerprint_includesRoomTextAndReply() {
        val a = outgoingMessageFingerprint("room-a", "hi", "reply-1")
        val b = outgoingMessageFingerprint("room-a", "hi", "reply-1")
        val c = outgoingMessageFingerprint("room-b", "hi", "reply-1")
        assertEquals(a, b)
        assertNotEquals(a, c)
    }

    @Test
    fun mergeLoadedPageWithExisting_dropsCrossRoomRowsFromExisting() {
        val hub = "hub-room"
        val raid = "raid-room"
        val raidOnly = msg("507f1f77bcf86cd799439099", "raid").copy(roomId = raid)
        val hubAnchor = msg("507f1f77bcf86cd799439011", "hub").copy(roomId = hub)
        val loaded = listOf(hubAnchor)
        val merged = mergeLoadedPageWithExisting(
            existing = listOf(raidOnly, hubAnchor),
            loaded = loaded,
            roomId = hub,
        )
        assertEquals(listOf(hubAnchor._id), merged.map { it._id })
    }

    @Test
    fun mergeLoadedPageWithExisting_prefersLoadedTextForSameId() {
        val existing = listOf(msg("1", "socket text"))
        val loaded = listOf(msg("1", "http text"), msg("0", "older"))
        val merged = mergeLoadedPageWithExisting(existing, loaded)
        assertEquals("http text", merged.first { it._id == "1" }.text)
        assertEquals(listOf("1", "0"), merged.map { it._id })
    }

    @Test
    fun mergeVisibleMessagesWithRoomCache_returnsCachedWhenVisibleEmpty() {
        val peer = msg("507f1f77bcf86cd799439013", "from peer while away")
        val cached = listOf(peer)
        val merged = mergeVisibleMessagesWithRoomCache(
            visible = emptyList(),
            cached = cached,
            roomId = "room",
        )
        assertEquals(listOf("507f1f77bcf86cd799439013"), merged.map { it._id })
    }

    @Test
    fun mergeVisibleMessagesWithRoomCache_stripsPendingWhenCurrentUserIdSet() {
        val pending = msg("pending-1", "hello").copy(
            senderId = "u1",
            clientMessageId = "cid-1",
        )
        val confirmed = msg("server-1", "hello").copy(
            senderId = "u1",
            clientMessageId = "cid-1",
        )
        val merged = mergeVisibleMessagesWithRoomCache(
            visible = listOf(pending),
            cached = listOf(confirmed, pending),
            roomId = "room",
            currentUserId = "u1",
        )
        assertEquals(listOf("server-1"), merged.map { it._id })
    }

    @Test
    fun mergeVisibleMessagesWithRoomCache_addsCachedPeerRowsWhileVisibleStale() {
        val oldA = msg("507f1f77bcf86cd799439011", "old")
        val newB = msg("507f1f77bcf86cd799439013", "from peer")
        val visible = listOf(oldA)
        val cached = listOf(newB, oldA)
        val merged = mergeVisibleMessagesWithRoomCache(
            visible = visible,
            cached = cached,
            roomId = "room",
        )
        assertEquals(listOf("507f1f77bcf86cd799439013", "507f1f77bcf86cd799439011"), merged.map { it._id })
    }

    @Test
    fun orderRealtimeSubscriptionRoomIds_includesAllAllianceRoomsWhenSubscribeAll() {
        val rooms = listOf(
            roomDto("raid", unread = 0),
            roomDto("hub", unread = 0),
            roomDto("quiet", unread = 0),
        )
        val ids = orderRealtimeSubscriptionRoomIds(
            rooms = rooms,
            selectedRoomId = "quiet",
            raidRoomId = "raid",
            hubRoomId = "hub",
            subscribeAllRooms = true,
        )
        assertEquals("quiet", ids.first())
        assertEquals(3, ids.toSet().size)
        assertTrue(ids.containsAll(listOf("raid", "hub", "quiet")))
    }

    @Test
    fun orderRealtimeSubscriptionRoomIds_putsSelectedFirst() {
        val rooms = listOf(
            roomDto("raid", unread = 0),
            roomDto("hub", unread = 0),
            roomDto("other", unread = 1),
        )
        val ids = orderRealtimeSubscriptionRoomIds(
            rooms = rooms,
            selectedRoomId = "other",
            raidRoomId = "raid",
            hubRoomId = "hub",
            subscribeAllRooms = true,
        )
        assertEquals("other", ids.first())
        assertTrue(ids.containsAll(listOf("raid", "hub", "other")))
    }

    @Test
    fun orderRealtimeSubscriptionRoomIds_badgeOnlyWhenNotSubscribeAll() {
        val rooms = listOf(
            roomDto("raid", unread = 0),
            roomDto("hub", unread = 0),
            roomDto("unread-room", unread = 3),
            roomDto("quiet", unread = 0),
        )
        val ids = orderRealtimeSubscriptionRoomIds(
            rooms = rooms,
            selectedRoomId = "quiet",
            raidRoomId = "raid",
            hubRoomId = "hub",
            subscribeAllRooms = false,
        )
        assertEquals("quiet", ids.first())
        assertTrue(ids.containsAll(listOf("raid", "hub", "unread-room")))
        assertFalse(ids.contains("quiet") && ids.count { it == "quiet" } > 1)
        assertEquals(4, ids.toSet().size)
    }

    private fun roomDto(id: String, unread: Int) =
        com.lastasylum.alliance.data.chat.ChatRoomDto(
            id = id,
            allianceId = "a",
            title = id,
            sortOrder = 0,
            unreadCount = unread,
        )

    @Test
    fun shouldSkipBackgroundMessageRefresh_falseWhenRoomCacheAheadOfVisible() {
        val visible = listOf(msg("507f1f77bcf86cd799439011", "old"))
        val session = listOf(
            msg("507f1f77bcf86cd799439011", "old"),
            msg("507f1f77bcf86cd799439010", "older"),
        )
        val room = listOf(
            msg("507f1f77bcf86cd799439013", "peer"),
            msg("507f1f77bcf86cd799439011", "old"),
        )
        assertFalse(
            shouldSkipBackgroundMessageRefresh(
                visible = visible,
                sessionCache = session,
                roomCache = room,
                pageSize = 30,
            ),
        )
    }

    @Test
    fun shouldSkipBackgroundMessageRefresh_falseWhenIdSetsDiffer() {
        val visible = listOf(
            msg("507f1f77bcf86cd799439013", "new"),
            msg("507f1f77bcf86cd799439011", "old"),
        )
        val session = listOf(
            msg("507f1f77bcf86cd799439013", "new"),
            msg("507f1f77bcf86cd799439010", "different middle"),
        )
        assertFalse(
            shouldSkipBackgroundMessageRefresh(
                visible = visible,
                sessionCache = session,
                roomCache = session,
                pageSize = 2,
            ),
        )
    }

    @Test
    fun shouldSkipBackgroundMessageRefresh_trueWhenCachesMatchVisible() {
        val visible = listOf(
            msg("507f1f77bcf86cd799439013", "new"),
            msg("507f1f77bcf86cd799439011", "old"),
        )
        assertTrue(
            shouldSkipBackgroundMessageRefresh(
                visible = visible,
                sessionCache = visible,
                roomCache = visible,
                pageSize = 2,
            ),
        )
    }

    @Test
    fun shouldSkipBackgroundMessageRefresh_falseWhenSocketDisconnected() {
        val visible = listOf(msg("507f1f77bcf86cd799439013", "new"))
        assertFalse(
            shouldSkipBackgroundMessageRefresh(
                visible = visible,
                sessionCache = visible,
                roomCache = visible,
                pageSize = 1,
                socketConnected = false,
            ),
        )
    }

    @Test
    fun shouldSkipBackgroundMessageRefresh_respectsOverlayReconcileInterval() {
        val visible = listOf(msg("507f1f77bcf86cd799439013", "new"))
        val now = 200_000L
        assertFalse(
            shouldSkipBackgroundMessageRefresh(
                visible = visible,
                sessionCache = visible,
                roomCache = visible,
                pageSize = 1,
                lastRestSyncAtMs = now - 25_000L,
                nowMs = now,
                overlayPanelVisible = true,
            ),
        )
        assertTrue(
            shouldSkipBackgroundMessageRefresh(
                visible = visible,
                sessionCache = visible,
                roomCache = visible,
                pageSize = 1,
                lastRestSyncAtMs = now - 15_000L,
                nowMs = now,
                overlayPanelVisible = true,
            ),
        )
    }

    @Test
    fun shouldSkipBackgroundMessageRefresh_falseWhenLatestSocketNewerThanVisible() {
        val visible = listOf(msg("507f1f77bcf86cd799439011", "visible"))
        val session = visible
        assertFalse(
            shouldSkipBackgroundMessageRefresh(
                visible = visible,
                sessionCache = session,
                roomCache = session,
                pageSize = 1,
                latestSocketMessageId = "507f1f77bcf86cd799439013",
            ),
        )
    }

    @Test
    fun stripRedundantPendingOutgoing_removesPendingWhenServerEchoExists() {
        val pending = msg("pending-1", "hello").copy(clientMessageId = "client-1")
        val server = msg("server-1", "hello").copy(clientMessageId = "client-1")
        val out = stripRedundantPendingOutgoing(listOf(server, pending), "u1")
        assertEquals(listOf("server-1"), out.map { it._id })
    }

    @Test
    fun dropMatchingPendingOutgoing_ignoresReplyToNullVsEmpty() {
        val pending = msg("pending-1", "hello", replyTo = null)
        val server = msg("server-1", "hello").copy(replyToMessageId = "")
        val out = dropMatchingPendingOutgoing(listOf(pending), listOf(server), "u1")
        assertTrue(out.isEmpty())
    }

    @Test
    fun sanitizeMessagesAfterRealtimeApply_dropsServerRowRacingPendingConfirm() {
        val pending = msg("pending-abc", "hi")
        val server = msg("server-1", "hi")
        val out = sanitizeMessagesAfterRealtimeApply(
            messages = listOf(server, pending),
            currentUserId = "u1",
            activeOutgoingPendingId = "pending-abc",
        )
        assertEquals(listOf("pending-abc"), out.map { it._id })
    }

    @Test
    fun findSingleChangedMessageIndex_detectsOneRowChange() {
        val before = listOf(msg("1", "a"), msg("2", "b"))
        val after = listOf(msg("1", "a"), msg("2", "changed"))
        assertEquals(1, findSingleChangedMessageIndex(before, after))
    }

    @Test
    fun findSingleChangedMessageIndex_nullWhenMultipleRowsChange() {
        val before = listOf(msg("1", "a"), msg("2", "b"))
        val after = listOf(msg("1", "x"), msg("2", "y"))
        assertNull(findSingleChangedMessageIndex(before, after))
    }

    @Test
    fun isDuplicateOwnOutgoingDelivery_whenRowAlreadyInList() {
        val server = msg("server-1", "hello")
        val index = mapOf("server-1" to 0)
        assertTrue(isDuplicateOwnOutgoingDelivery(listOf(server), server, index))
        assertFalse(
            isDuplicateOwnOutgoingDelivery(
                listOf(server),
                server.copy(text = "other"),
                index,
            ),
        )
    }

    @Test
    fun hasMatchingPendingOutgoing_detectsOptimisticRow() {
        val pending = msg("pending-1", "hello")
        val server = msg("server-1", "hello")
        assertTrue(hasMatchingPendingOutgoing(listOf(pending), server, "u1"))
        assertFalse(hasMatchingPendingOutgoing(listOf(server), server, "u1"))
    }

    @Test
    fun mergeOutgoingConfirmation_clearsEditedAt() {
        val optimistic = msg("pending-1", "hi")
        val confirmed = msg("server-1", "hi").copy(
            editedAt = "2020-01-01T00:00:05.000Z",
            createdAt = "2020-01-01T00:00:00.000Z",
        )
        val merged = mergeOutgoingConfirmation(optimistic, confirmed)
        assertNull(merged.editedAt)
        assertEquals("server-1", merged._id)
    }

    @Test
    fun stripRacingServerEchoForPending_dropsEchoBelowOptimisticAtHead() {
        val now = "2026-06-07T12:00:00.000Z"
        val pending = msg("pending-1", "hello").copy(
            clientMessageId = "client-1",
            createdAt = now,
        )
        val echo = msg("507f1f77bcf86cd799439099", "hello").copy(createdAt = now)
        val out = stripRacingServerEchoForPending(
            messages = listOf(pending, echo),
            pending = pending,
            currentUserId = "u1",
        )
        assertEquals(listOf("pending-1"), out.map { it._id })
    }

    @Test
    fun attachPendingClientMessageIdsToOwnConfirmed_linksEchoBelowPending() {
        val now = "2026-06-07T12:00:00.000Z"
        val pending = msg("pending-1", "hello").copy(
            clientMessageId = "client-abc",
            createdAt = now,
        )
        val server = msg("server-1", "hello").copy(createdAt = now)
        val linked = attachPendingClientMessageIdsToOwnConfirmed(
            messages = listOf(pending, server),
            currentUserId = "u1",
        )
        assertEquals("client-abc", linked[1].clientMessageId)
        val stripped = stripRedundantOwnOutgoingByClientMessageId(linked, "u1")
        assertEquals(listOf("server-1"), stripped.map { it._id })
    }

    @Test
    fun collapseOwnOutgoingHeadDuplicates_removesPendingWhenConfirmedBelow() {
        val pending = msg("pending-1", "hello").copy(clientMessageId = "client-abc")
        val server = msg("server-1", "hello").copy(clientMessageId = "client-abc")
        val out = collapseOwnOutgoingHeadDuplicates(
            messages = listOf(pending, server),
            currentUserId = "u1",
        )
        assertEquals(listOf("server-1"), out.map { it._id })
    }

    @Test
    fun attachPendingClientMessageIdsToOwnConfirmed_linksRacingServerEcho() {
        val now = "2026-06-07T12:00:00.000Z"
        val pending = msg("pending-1", "hello").copy(
            clientMessageId = "client-abc",
            createdAt = now,
        )
        val server = msg("server-1", "hello").copy(createdAt = now)
        val linked = attachPendingClientMessageIdsToOwnConfirmed(
            messages = listOf(server, pending),
            currentUserId = "u1",
        )
        assertEquals("client-abc", linked[0].clientMessageId)
        val stripped = stripRedundantOwnOutgoingByClientMessageId(linked, "u1")
        assertEquals(listOf("server-1"), stripped.map { it._id })
    }

    @Test
    fun attachPendingClientMessageIdsToOwnConfirmed_skipsDistantHistoryMatch() {
        val pending = msg("pending-1", "hello").copy(clientMessageId = "client-abc")
        val old = msg("server-old", "hello")
        val linked = attachPendingClientMessageIdsToOwnConfirmed(
            messages = listOf(pending, msg("x"), msg("y"), msg("z"), old),
            currentUserId = "u1",
        )
        assertNull(linked.last().clientMessageId)
    }

    @Test
    fun attachPendingClientMessageIdsToOwnConfirmed_skipsOlderConfirmedBelowPending() {
        val pending = msg("pending-2", "hello").copy(clientMessageId = "client-2")
        val older = msg("507f1f77bcf86cd799439011", "hello")
        val linked = attachPendingClientMessageIdsToOwnConfirmed(
            messages = listOf(pending, older),
            currentUserId = "u1",
        )
        assertNull(linked[1].clientMessageId)
    }

    @Test
    fun withOutgoingClientMessageId_fillsMissingAckField() {
        val ack = msg("server-1", "hello")
        val normalized = ack.withOutgoingClientMessageId("client-abc")
        assertEquals("client-abc", normalized.clientMessageId)
        assertEquals(
            "existing",
            ack.copy(clientMessageId = "existing")
                .withOutgoingClientMessageId("other")
                .clientMessageId,
        )
    }

    @Test
    fun findOptimisticOutgoingPendingForConfirm_fallsBackToTextMatch() {
        val pending = msg("pending-1", "hello").copy(clientMessageId = "client-abc")
        val server = msg("server-1", "hello")
        val resolved = findOptimisticOutgoingPendingForConfirm(
            messages = listOf(server, pending),
            clientMessageId = "",
            confirmed = server,
            currentUserId = "u1",
        )
        assertEquals("pending-1", resolved)
    }

    @Test
    fun findOptimisticOutgoingPendingForConfirm_prefersClientMessageId() {
        val pending = msg("pending-1", "hello").copy(clientMessageId = "client-abc")
        val resolved = findOptimisticOutgoingPendingForConfirm(
            messages = listOf(pending),
            clientMessageId = "client-abc",
            confirmed = msg("server-1", "different"),
            currentUserId = "u1",
        )
        assertEquals("pending-1", resolved)
    }

    @Test
    fun replaceMatchingPendingOutgoing_swapsOptimisticRow() {
        val pending = msg("pending-1", "hello")
        val server = msg("server-1", "hello")
        val current = listOf(pending, msg("older", "x"))
        val replacement = replaceMatchingPendingOutgoing(current, server, "u1")
        requireNotNull(replacement)
        assertEquals("server-1", replacement.messages[0]._id)
        assertEquals("pending-1", replacement.pendingId)
        assertEquals(0, replacement.replacedIndex)
    }

    @Test
    fun replaceMatchingPendingOutgoing_matchesByClientMessageIdWhenTextDiffers() {
        val pending = msg("pending-1", "hello").copy(clientMessageId = "client-abc")
        val server = msg("server-1", "hello (normalized)").copy(clientMessageId = "client-abc")
        val replacement = replaceMatchingPendingOutgoing(listOf(pending), server, "u1")
        requireNotNull(replacement)
        assertEquals("server-1", replacement.messages.single()._id)
        assertEquals("client-abc", replacement.messages.single().clientMessageId)
    }

    @Test
    fun hasMatchingPendingOutgoing_matchesByClientMessageId() {
        val pending = msg("pending-1", "hello").copy(clientMessageId = "client-abc")
        val server = msg("server-1", "different text").copy(clientMessageId = "client-abc")
        assertTrue(hasMatchingPendingOutgoing(listOf(pending), server, "u1"))
    }

    @Test
    fun replaceMatchingPendingOutgoing_matchesOverlayPendingId() {
        val pending = msg("overlay-pending-1", "coords").copy(clientMessageId = "client-1")
        val server = msg("server-1", "coords").copy(clientMessageId = "client-1")
        val replacement = replaceMatchingPendingOutgoing(listOf(pending), server, "u1")
        requireNotNull(replacement)
        assertEquals("server-1", replacement.messages.single()._id)
    }

    @Test
    fun stripRedundantPendingOutgoing_removesOverlayPendingWhenConfirmed() {
        val pending = msg("overlay-pending-1", "coords").copy(clientMessageId = "client-1")
        val server = msg("server-1", "coords").copy(clientMessageId = "client-1")
        val out = stripRedundantPendingOutgoing(listOf(server, pending), "u1")
        assertEquals(listOf("server-1"), out.map { it._id })
    }

    @Test
    fun stripRedundantPendingOutgoing_keepsSecondPendingWithSameText() {
        val first = msg("server-1", "hello")
        val pending = msg("pending-2", "hello").copy(clientMessageId = "client-2")
        val out = stripRedundantPendingOutgoing(listOf(first, pending), "u1")
        assertEquals(listOf("server-1", "pending-2"), out.map { it._id })
    }

    @Test
    fun stripRacingServerEchoForPending_keepsOlderConfirmedWithSameText() {
        val older = msg("507f1f77bcf86cd799439011", "hello")
        val pending = msg("pending-2", "hello").copy(clientMessageId = "client-2")
        val echo = msg("507f1f77bcf86cd799439099", "hello")
        val out = stripRacingServerEchoForPending(
            messages = listOf(echo, pending, older),
            pending = pending,
            currentUserId = "u1",
        )
        assertEquals(listOf("pending-2", "507f1f77bcf86cd799439011"), out.map { it._id })
    }

    @Test
    fun stripRacingServerEchoForPending_dropsSingleRacingEchoByClientMessageId() {
        val pending = msg("pending-1", "hello").copy(clientMessageId = "client-1")
        val echo = msg("507f1f77bcf86cd799439099", "hello").copy(clientMessageId = "client-1")
        val out = stripRacingServerEchoForPending(
            messages = listOf(echo, pending),
            pending = pending,
            currentUserId = "u1",
        )
        assertEquals(listOf("pending-1"), out.map { it._id })
    }

    @Test
    fun sanitizeMessagesAfterRealtimeApply_keepsOlderConfirmedWithSameText() {
        val older = msg("507f1f77bcf86cd799439011", "hello")
        val pending = msg("pending-2", "hello").copy(clientMessageId = "client-2")
        val echo = msg("507f1f77bcf86cd799439099", "hello")
        val out = sanitizeMessagesAfterRealtimeApply(
            messages = listOf(echo, pending, older),
            currentUserId = "u1",
            activeOutgoingPendingId = "pending-2",
        )
        assertEquals(listOf("pending-2", "507f1f77bcf86cd799439011"), out.map { it._id })
    }

    @Test
    fun mergeLoadedPageWithExisting_stripsPendingWhenLoadedHasSameClientMessageId() {
        val pending = msg("pending-1", "Test3").copy(
            senderId = "u1",
            clientMessageId = "cid-abc",
        )
        val confirmed = msg("507f1f77bcf86cd799439099", "Test3").copy(
            senderId = "u1",
            clientMessageId = "cid-abc",
        )
        val existing = listOf(pending)
        val loaded = listOf(confirmed)
        val merged = mergeLoadedPageWithExisting(
            existing = existing,
            loaded = loaded,
            currentUserId = "u1",
        )
        assertEquals(1, merged.size)
        assertEquals("507f1f77bcf86cd799439099", merged.first()._id)
    }

    @Test
    fun upsertMessage_staleIdIndexPrependsWhenRowMissingFromList() {
        val known = linkedSetOf("507f1f77bcf86cd799439013")
        val index = mutableMapOf("507f1f77bcf86cd799439013" to 0)
        val current = listOf(msg("507f1f77bcf86cd799439011", "old"))
        val incoming = msg("507f1f77bcf86cd799439013", "from peer").copy(senderId = "peer")
        val r = upsertMessage(current, incoming, known, index)
        assertEquals(
            listOf("507f1f77bcf86cd799439013", "507f1f77bcf86cd799439011"),
            r.messages.map { it._id },
        )
    }

    @Test
    fun dedupeOwnOutgoingByClientMessageId_dropsPendingWhenConfirmedExists() {
        val pending = msg("pending-1", "hello").copy(clientMessageId = "cid-1")
        val confirmed = msg("server-1", "hello").copy(clientMessageId = "cid-1")
        val out = dedupeOwnOutgoingByClientMessageId(listOf(pending, confirmed), "u1")
        assertEquals(listOf("server-1"), out.map { it._id })
    }

    @Test
    fun dedupeOwnOutgoingByClientMessageId_keepsFirstRow() {
        val first = msg("server-1", "hello").copy(clientMessageId = "cid-1")
        val dup = msg("server-2", "hello").copy(clientMessageId = "cid-1")
        val out = dedupeOwnOutgoingByClientMessageId(listOf(first, dup), "u1")
        assertEquals(listOf("server-1"), out.map { it._id })
    }

    @Test
    fun findOptimisticOutgoingPendingId_matchesClientMessageId() {
        val pending = msg("pending-abc", "hello").copy(
            senderId = "u1",
            clientMessageId = "cid-xyz",
        )
        val found = findOptimisticOutgoingPendingId(listOf(pending), "cid-xyz", "u1")
        assertEquals("pending-abc", found)
    }

    @Test
    fun hasOwnOutgoingRowPairByClientMessageId_trueWhenPendingAndServerShareCid() {
        val pending = msg("pending-1", "hello").copy(
            senderId = "u1",
            clientMessageId = "cid-1",
        )
        val confirmed = msg("server-1", "hello").copy(
            senderId = "u1",
            clientMessageId = "cid-1",
        )
        assertTrue(
            hasOwnOutgoingRowPairByClientMessageId(
                listOf(pending, confirmed),
                "cid-1",
                "u1",
            ),
        )
    }

    @Test
    fun hasOwnOutgoingRowPairByClientMessageId_falseWhenOnlyPending() {
        val pending = msg("pending-1", "hello").copy(
            senderId = "u1",
            clientMessageId = "cid-1",
        )
        assertFalse(
            hasOwnOutgoingRowPairByClientMessageId(
                listOf(pending),
                "cid-1",
                "u1",
            ),
        )
    }

    @Test
    fun dedupeMessagesByIdNewestFirst_keepsFirstOccurrence() {
        val server = msg("same", "hello")
        val dup = server.copy(text = "hello (server)")
        val pending = msg("pending-1", "hello")
        val list = listOf(server, dup, pending)
        val out = dedupeMessagesByIdNewestFirst(list)
        assertEquals(listOf("same", "pending-1"), out.map { it._id })
    }

    @Test
    fun hasDuplicateMessageIds_trueWhenSameIdTwice() {
        val id = "6a24a23e4a984f6da85136db"
        val list = listOf(msg(id, "hello"), msg(id, "dup"))
        assertTrue(hasDuplicateMessageIds(list))
    }

    @Test
    fun capNewestFirst_keepsHead() {
        val newestFirst = (100 downTo 1).map { i -> msg("id$i", text = "$i") }
        val capped = capNewestFirst(newestFirst, 50)
        assertEquals(50, capped.size)
        assertEquals("id100", capped.first()._id)
        assertEquals("id51", capped.last()._id)
    }

    @Test
    fun scrubMessagesAfterRemove_clearsReplyTo() {
        val known = linkedSetOf("1", "2", "3")
        val reply = ChatMessageReplyPreview(
            _id = "2",
            senderId = "x",
            senderUsername = "x",
            senderRole = "R1",
            text = "quoted",
        )
        val messages = listOf(msg("1"), msg("3", replyTo = reply))
        val out = scrubMessagesAfterRemove(messages, "2", known)
        assertEquals(listOf("1", "3"), out.map { it._id })
        assertNull(out[1].replyTo)
        assertFalse(known.contains("2"))
    }

    @Test
    fun syncSelections_clearsMissingReply() {
        val state = ChatState(
            currentUserId = "u",
            currentUserRole = "R1",
            messages = listOf(msg("1")),
            replyToMessage = msg("99", "ghost"),
        )
        val synced = syncSelections(state)
        assertNull(synced.replyToMessage)
    }

    @Test
    fun syncSelections_noOpWhenNoSelections() {
        val state = ChatState(
            messages = listOf(msg("1")),
        )
        val synced = syncSelections(state)
        assertTrue(synced === state)
    }

    @Test
    fun syncSelections_prunesStaleSelectionIds() {
        val state = ChatState(
            currentUserId = "u1",
            currentUserRole = "R1",
            messages = listOf(msg("1")),
            selectedMessageIds = setOf("1", "ghost"),
        )
        val synced = syncSelections(state)
        assertEquals(setOf("1"), synced.selectedMessageIds)
    }

    @Test
    fun syncSelections_dropsOthersMessagesForNonAdminSelection() {
        val other = msg("2").copy(senderId = "other")
        val state = ChatState(
            currentUserId = "u1",
            currentUserRole = "R1",
            messages = listOf(msg("1"), other),
            selectedMessageIds = setOf("1", "2"),
        )
        val synced = syncSelections(state)
        assertEquals(setOf("1"), synced.selectedMessageIds)
    }

    @Test
    fun syncSelections_keepsAdminSelectionForOthersMessages() {
        val globalRoom = ChatAllianceIds.GLOBAL
        val other = msg("2").copy(senderId = "other", allianceId = globalRoom)
        val state = ChatState(
            currentUserId = "u1",
            currentUserRole = "ADMIN",
            isAppAdmin = true,
            messages = listOf(msg("1").copy(allianceId = globalRoom), other),
            selectedMessageIds = setOf("1", "2"),
        )
        val synced = syncSelections(state)
        assertEquals(setOf("1", "2"), synced.selectedMessageIds)
    }

    @Test
    fun syncSelections_clearsOrphanBulkConfirm() {
        val state = ChatState(
            messages = listOf(msg("1")),
            selectedMessageIds = emptySet(),
            confirmBulkDelete = true,
        )
        val synced = syncSelections(state)
        assertFalse(synced.confirmBulkDelete)
    }

    @Test
    fun outgoingPayloadMatches_treatsEmptyAttachmentsOnOptimisticAsInFlight() {
        val pending = msg("pending-1", "").copy(
            attachments = emptyList(),
            clientMessageId = "cid-img",
        )
        val confirmed = msg("server-1", "").copy(
            attachments = listOf(
                ChatAttachment(kind = "image", url = "/chat/attachments/abc"),
            ),
            clientMessageId = "cid-img",
        )
        assertTrue(outgoingPayloadMatches(pending, confirmed))
    }

    @Test
    fun sanitizeMessagesAfterRealtimeApply_stripsRacingEchoWithoutOutboxPendingHint() {
        val now = "2026-06-07T12:00:00.000Z"
        val pending = msg("pending-1", "").copy(
            clientMessageId = "cid-1",
            createdAt = now,
        )
        val echo = msg("507f1f77bcf86cd799439099", "").copy(
            attachments = listOf(
                ChatAttachment(kind = "image", url = "/chat/attachments/abc"),
            ),
            createdAt = now,
        )
        val out = sanitizeMessagesAfterRealtimeApply(
            messages = listOf(echo, pending),
            currentUserId = "u1",
            activeOutgoingPendingId = null,
        )
        assertEquals(listOf("pending-1"), out.map { it._id })
    }

    @Test
    fun attachPendingClientMessageIdsToOwnConfirmed_linksNonAdjacentWhenClientMessageIdSet() {
        val now = "2026-06-07T12:00:00.000Z"
        val pending = msg("pending-1", "").copy(
            clientMessageId = "cid-1",
            createdAt = now,
        )
        val peer = msg("507f1f77bcf86cd799439011", "peer").copy(senderId = "peer")
        val echo = msg("507f1f77bcf86cd799439099", "").copy(
            attachments = listOf(
                ChatAttachment(kind = "image", url = "/chat/attachments/abc"),
            ),
            createdAt = now,
        )
        val linked = attachPendingClientMessageIdsToOwnConfirmed(
            messages = listOf(echo, peer, pending),
            currentUserId = "u1",
        )
        assertEquals("cid-1", linked[0].clientMessageId)
        val stripped = stripRedundantOwnOutgoingByClientMessageId(linked, "u1")
        assertEquals(listOf("507f1f77bcf86cd799439099", "507f1f77bcf86cd799439011"), stripped.map { it._id })
    }
}
