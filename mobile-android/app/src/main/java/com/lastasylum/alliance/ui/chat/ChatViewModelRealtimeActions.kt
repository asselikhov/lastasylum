package com.lastasylum.alliance.ui.chat

import android.util.Log
import com.lastasylum.alliance.BuildConfig
import com.lastasylum.alliance.data.chat.ChatMessage
import com.lastasylum.alliance.data.chat.ChatMessageDeletedEvent
import com.lastasylum.alliance.data.chat.ChatRoomPinChangedEvent
import com.lastasylum.alliance.data.chat.ChatRoomReadEvent
import com.lastasylum.alliance.data.chat.ChatRaidRoomSync
import com.lastasylum.alliance.data.chat.ChatSessionCache
import com.lastasylum.alliance.data.chat.ChatTypingEvent
import com.lastasylum.alliance.data.chat.mergeIncomingChatUpdate
import com.lastasylum.alliance.data.chat.sync.ChatRoomMessageCache
import com.lastasylum.alliance.di.AppContainer
import com.lastasylum.alliance.ui.util.toUserMessageRu
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException


internal fun ChatViewModel.shouldSuppressOwnOutgoingRealtimeEchoImpl(message: ChatMessage): Boolean =
        shouldBlockOwnOutgoingRealtime(message)

internal fun ChatViewModel.shouldBlockOwnOutgoingRealtimeImpl(message: ChatMessage): Boolean =
        synchronized(chatMutationLock) {
            shouldBlockOwnOutgoingRealtimeUnlocked(message)
        }

private fun ChatViewModel.shouldBlockOwnOutgoingRealtimeUnlocked(message: ChatMessage): Boolean {
        val selfId = currentUserId.trim()
        if (selfId.isEmpty() || message.senderId.trim() != selfId) return false
        if (message.clientMessageId.isNullOrBlank() && activeOutgoingClientMessageIds.isNotEmpty()) {
            return true
        }
        message.clientMessageId?.trim()?.takeIf { it.isNotEmpty() }?.let { cid ->
            if (cid in confirmedOutgoingClientMessageIds) return true
            if (cid in activeOutgoingClientMessageIds) return true
        }
        val snapshot = outboxRoomSnapshot
        message.clientMessageId?.trim()?.takeIf { it.isNotEmpty() }?.let { cid ->
            if (cid in snapshot.activeClientMessageIds) return true
        }
        if (hasMatchingPendingOutgoing(vmState.value.messages, message, currentUserId)) return true
        val roomId = message.roomId.trim()
        val serverId = message._id?.trim().orEmpty()
        if (roomId.isNotEmpty() && serverId.isNotEmpty() && !messageIdIndex.containsKey(serverId)) {
            val hasPendingInRoom = vmState.value.messages.any { msg ->
                val pendingRowId = msg._id?.trim().orEmpty()
                isOptimisticOutgoingMessageId(pendingRowId) &&
                    msg.senderId.trim() == selfId &&
                    msg.roomId.trim() == roomId
            }
            if (hasPendingInRoom) return true
        }
        val pendingId = message._id?.trim().orEmpty()
        if (pendingId.isNotEmpty() && snapshot.pendingToClientId.containsKey(pendingId)) return true
        return isDuplicateOwnOutgoingDelivery(
            vmState.value.messages,
            message,
            messageIdIndex,
        )
    }

internal fun ChatViewModel.onIncomingMessageImpl(message: ChatMessage) {
        val mid = message._id?.trim().orEmpty()
        val rid = message.roomId.trim()
        if (rid.isNotEmpty()) {
            vmRepository.ensureRoomJoined(rid)
        }
        val selfId = currentUserId.trim()
        val isOwn = selfId.isNotEmpty() && message.senderId.trim() == selfId
        if (isOwn) {
            val cid = resolveOutgoingClientMessageId(message, activeOutgoingClientMessageIds)
            if (cid != null) {
                val normalized = message.withOutgoingClientMessageId(cid)
                confirmOutgoingByClientMessageId(cid, normalized)
                vmScope.launch(Dispatchers.IO) {
                    chatSyncEngine.onSocketMessageConfirmed(
                        currentUserId,
                        message.roomId,
                        normalized,
                        cid,
                    )
                }
                return
            }
        }
        if (shouldBlockOwnOutgoingRealtime(message)) return
        if (isOwn) {
            val serverId = message._id?.trim().orEmpty()
            if (serverId.isNotEmpty() && messageIdIndex.containsKey(serverId)) return
        }
        if (!isIncomingMessageVisible(message)) return
        if (mid.isNotEmpty() && rid.isNotEmpty()) {
            if (!com.lastasylum.alliance.data.chat.ChatSocketIngress.claimForChatList(rid, mid)) {
                return
            }
        }
        if (!isOwn && mid.isNotEmpty()) {
            com.lastasylum.alliance.data.chat.OverlaySocketMessageStash.stash(message)
        }
        if (isChatRealtimeViewActive()) {
            if (shouldBlockOwnOutgoingRealtime(message) || !isIncomingMessageVisible(message)) return
            dispatchIncomingBatch(listOf(message))
            return
        }
        if (!incomingMessages.trySend(message).isSuccess) {
            vmScope.launch(Dispatchers.Main) {
                if (shouldBlockOwnOutgoingRealtime(message) || !isIncomingMessageVisible(message)) return@launch
                dispatchIncomingBatch(listOf(message))
            }
        }
    }

internal fun ChatViewModel.onRoomReadEventImpl(event: ChatRoomReadEvent) {
        if (event.userId.isBlank() || event.messageId.isBlank()) return
        if (event.userId == currentUserId) {
            if (event.roomId.isNotBlank()) {
                mergeReadCursor(event.roomId, event.messageId)
                vmState.update { st ->
                    st.copy(rooms = clearUnreadForRoom(st.rooms, event.roomId))
                }
            }
            return
        }
        if (BuildConfig.DEBUG) {
            android.util.Log.d(
                "ChatReadReceipt",
                "room:read peer=${event.userId} room=${event.roomId} upto=${event.messageId} " +
                    "selected=${vmState.value.selectedRoomId}",
            )
        }
        val publishUpto = PeerReadCursorLogic.mergePeerReadEvent(
            otherReadUptoByRoom = otherReadUptoByRoom,
            selectedRoomId = vmState.value.selectedRoomId,
            event = event,
            currentUserId = currentUserId,
        )
        if (publishUpto != null) {
            _otherReadUptoMessageId.value = publishUpto
            deliveryLatencyTracker.endSpanByCorrelation(
                com.lastasylum.alliance.data.telemetry.LatencySpanType.ChatReadReceipt,
                event.messageId,
                "ok",
            )
        }
    }

internal fun ChatViewModel.onTypingFromPeerImpl(event: ChatTypingEvent) {
        vmScope.launch {
            if (event.userId.isBlank() || event.userId == currentUserId) return@launch
            val roomId = vmState.value.selectedRoomId ?: return@launch
            if (event.roomId.isNotBlank() && event.roomId != roomId) return@launch
            val username = event.username.ifBlank { "…" }
            synchronized(typingPeerJobsLock) {
                typingPeerJobs[event.userId]?.cancel()
            }
            val job = launch {
                try {
                    _typingPeers.update { current ->
                        current.toMutableMap().apply { put(event.userId, username) }
                    }
                    delay(3200)
                    _typingPeers.update { current ->
                        current.toMutableMap().apply { remove(event.userId) }
                    }
                } catch (_: CancellationException) {
                    // superseded by a newer typing event for the same user
                }
            }
            synchronized(typingPeerJobsLock) {
                typingPeerJobs[event.userId] = job
            }
            job.invokeOnCompletion {
                synchronized(typingPeerJobsLock) {
                    if (typingPeerJobs[event.userId] === job) {
                        typingPeerJobs.remove(event.userId)
                    }
                }
            }
        }
    }

internal fun ChatViewModel.onDeletedMessageImpl(event: ChatMessageDeletedEvent) {
        vmScope.launch {
            val removedId = event.messageId.trim()
            if (removedId.isEmpty()) return@launch
            val eventRoomId = event.roomId.trim()
            val selected = vmState.value.selectedRoomId
            if (eventRoomId.isBlank() || eventRoomId == selected) {
                val scrubbed = scrubRemovedMessage(vmState.value, removedId)
                vmState.value = syncSelections(
                    scrubbed.copy(
                        deletingMessageId = if (scrubbed.deletingMessageId == removedId) {
                            null
                        } else {
                            scrubbed.deletingMessageId
                        },
                    ),
                )
            } else if (eventRoomId.isNotEmpty()) {
                vmState.update { st ->
                    clearRoomPinAfterMessageRemoved(st, removedId, eventRoomId)
                }
            }
            persistMessageRemoved(removedId, eventRoomId)
        }
    }

internal fun ChatViewModel.persistMessageRemovedImpl(removedId: String, roomId: String) {
        val id = removedId.trim()
        if (id.isEmpty()) return
        markMessageRemovedLocally(id)
        val rid = roomId.trim().ifBlank { vmState.value.selectedRoomId?.trim().orEmpty() }
        if (rid.isEmpty()) {
            schedulePersistChatSnapshot()
            return
        }
        val cached = roomMessageCache[rid]
        val nextMessages = when {
            cached != null -> {
                val cacheKnown = cached.messages.mapNotNull { it._id }.toMutableSet()
                scrubMessagesAfterRemove(cached.messages, id, cacheKnown)
            }
            vmState.value.selectedRoomId == rid -> vmState.value.messages
            else -> null
        }
        if (nextMessages != null) {
            val capped = capMessagesForMemory(nextMessages)
            roomMessageCache[rid] = ChatRoomMessageCache(
                messages = capped,
                hasMoreOlder = cached?.hasMoreOlder ?: vmState.value.hasMoreOlder,
            )
            ChatSessionCache.updateMessages(rid, capped)
        }
        flushRoomMessagesToDiskNow(rid)
        schedulePersistChatSnapshot()
    }

internal fun ChatViewModel.markMessageRemovedLocallyImpl(messageId: String) {
        val id = messageId.trim()
        if (id.isEmpty()) return
        locallyRemovedMessageIds.add(id)
        while (locallyRemovedMessageIds.size > 512) {
            locallyRemovedMessageIds.remove(locallyRemovedMessageIds.first())
        }
        if (currentUserId.isNotBlank()) {
            vmScope.launch(Dispatchers.IO) {
                launchDiskCache.saveRemovedMessageIds(currentUserId, locallyRemovedMessageIds)
            }
        }
    }

internal fun ChatViewModel.messagesWithoutLocallyRemovedImpl(messages: List<ChatMessage>): List<ChatMessage> {
        if (locallyRemovedMessageIds.isEmpty()) return messages
        var out = messages
        val known = out.mapNotNull { it._id }.toMutableSet()
        for (removedId in locallyRemovedMessageIds) {
            if (removedId.isBlank()) continue
            out = scrubMessagesAfterRemove(out, removedId, known)
        }
        return out
    }

internal fun ChatViewModel.applyLocallyRemovedFilterToLoadedCachesImpl() {
        if (locallyRemovedMessageIds.isEmpty()) return
        for ((rid, entry) in roomMessageCache.toList()) {
            val filtered = messagesWithoutLocallyRemoved(entry.messages)
            if (filtered.size == entry.messages.size) continue
            val capped = capMessagesForMemory(filtered)
            roomMessageCache[rid] = entry.copy(messages = capped)
            ChatSessionCache.updateMessages(rid, capped)
        }
        val roomId = vmState.value.selectedRoomId?.trim().orEmpty()
        if (roomId.isEmpty()) return
        val current = messagesWithoutLocallyRemoved(vmState.value.messages)
        if (current.size == vmState.value.messages.size) return
        knownMessageIds.clear()
        messageIdIndex.clear()
        knownMessageIds.addAll(current.mapNotNull { it._id })
        rebuildMessageIdIndex(current, messageIdIndex)
        vmState.update { st -> syncSelections(st.copy(messages = current)) }
        publishMessagesDerived(current)
    }

internal fun ChatViewModel.flushRoomMessagesToDiskNowImpl(roomId: String) {
        if (currentUserId.isBlank()) return
        val rid = roomId.trim()
        if (rid.isEmpty()) return
        schedulePersistChatSnapshot()
    }

internal fun ChatViewModel.scrubRemovedMessageImpl(state: ChatState, removedId: String): ChatState {
        val nextMessages = scrubMessagesAfterRemove(state.messages, removedId, knownMessageIds)
        rebuildMessageIdIndex(nextMessages, messageIdIndex)
        publishMessagesDerived(nextMessages)
        return clearRoomPinAfterMessageRemoved(
            state = state.copy(messages = nextMessages),
            removedId = removedId,
            roomId = state.selectedRoomId.orEmpty(),
        )
    }

internal fun ChatViewModel.clearRoomPinAfterMessageRemovedImpl(
        state: ChatState,
        removedId: String,
        roomId: String,
    ): ChatState {
        val rid = roomId.trim()
        val removed = removedId.trim()
        if (rid.isEmpty() || removed.isEmpty()) return state
        pinHistoryByRoomInternal[rid] = removePinFromHistory(
            pinHistoryByRoomInternal[rid].orEmpty(),
            removed,
        )
        persistPinHistory(rid)
        val room = state.rooms.find { it.id == rid } ?: return state
        if (room.pinnedMessageId?.trim() != removed) return state
        val cleared = room.withOptimisticUnpin()
        return applyPinBarUiImpl(state.copy(rooms = applyRoomPinToRoomsImpl(state.rooms, cleared)))
    }

internal fun ChatViewModel.applyRoomPinChangedEventImpl(event: ChatRoomPinChangedEvent) {
        vmState.update { st ->
            val withRooms = st.copy(rooms = applyRoomPinEvent(st.rooms, event))
            if (st.selectedRoomId == event.roomId) applyPinBarUiImpl(withRooms) else withRooms
        }
        ChatSessionCache.update(vmState.value.rooms)
    }

internal fun ChatViewModel.applyMessageReplaceSynchronouslyImpl(updated: ChatMessage) {
        val id = updated._id?.trim().orEmpty()
        if (id.isEmpty()) return
        synchronized(chatMutationLock) {
            val idx = messageIdIndex[id] ?: vmState.value.messages.indexOfFirst { it._id?.trim() == id }
            if (idx < 0) return
            val messages = vmState.value.messages.toMutableList()
            if (idx !in messages.indices) return
            messages[idx] = updated.mergeIncomingChatUpdate(messages[idx])
            rebuildMessageIdIndex(messages, messageIdIndex)
            publishMessagesDerivedAfterPatch(messages, idx)
            val roomId = vmState.value.selectedRoomId?.trim().orEmpty()
            vmState.value = syncSelections(vmState.value.copy(messages = messages))
            if (roomId.isNotEmpty()) {
                val cached = roomMessageCache[roomId]
                roomMessageCache[roomId] = ChatRoomMessageCache(
                    messages = messages,
                    hasMoreOlder = cached?.hasMoreOlder ?: vmState.value.hasMoreOlder,
                )
                ChatSessionCache.updateMessages(roomId, messages)
            }
            val room = vmState.value.rooms.find { it.id == roomId }
            if (room != null && room.pinnedMessageId?.trim() == id) {
                updated.toPinnedPreview()?.let { preview ->
                    val pinnedRoom = room.withOptimisticPin(id, preview, room.pinnedByUserId.orEmpty())
                    publishRoomPinImpl(pinnedRoom)
                }
            }
        }
        // Edits/reactions/socket-replaces mutate text/editedAt/reactions in place. Flush the
        // durable snapshot so cold start reflects the change (other mutation paths already do this).
        schedulePersistChatSnapshot()
    }

internal fun ChatViewModel.messagesForRoomMergeImpl(roomId: String): List<ChatMessage> {
        val rid = roomId.trim()
        if (rid.isEmpty()) return emptyList()
        val visible = if (vmState.value.selectedRoomId == rid) {
            filterMessagesForRoom(vmState.value.messages, rid)
        } else {
            emptyList()
        }
        val cached = messagesWithoutLocallyRemoved(
            filterMessagesForRoom(roomMessageCache[rid]?.messages.orEmpty(), rid),
        )
        return mergeVisibleMessagesWithRoomCache(
            visible = visible,
            cached = cached,
            roomId = rid,
            maxMessages = messageMemoryCap,
            excludedMessageIds = locallyRemovedMessageIds,
            hiddenBeforeMessageId = hiddenBeforeForRoom(rid),
            currentUserId = currentUserId,
        )
    }

internal fun ChatViewModel.mergeSessionCacheForSelectedRoomImpl() {
        val rid = vmState.value.selectedRoomId?.trim().orEmpty()
        if (rid.isEmpty()) return
        val session = com.lastasylum.alliance.data.chat.ChatSessionCache.getFreshMessages(rid)
            ?: return
        if (session.isEmpty()) return
        val cached = roomMessageCache[rid]?.messages.orEmpty()
        val merged = mergeVisibleMessagesWithRoomCache(
            visible = cached,
            cached = session,
            roomId = rid,
            maxMessages = messageMemoryCap,
            excludedMessageIds = locallyRemovedMessageIds,
            hiddenBeforeMessageId = hiddenBeforeForRoom(rid),
            currentUserId = currentUserId,
        )
        if (merged.isEmpty()) return
        session.forEach { message ->
            message._id?.trim()?.takeIf { it.isNotEmpty() }?.let {
                trackRecentSocketMessageId(rid, it)
            }
        }
        roomMessageCache[rid] = ChatRoomMessageCache(
            messages = capMessagesForMemory(merged),
            hasMoreOlder = roomMessageCache[rid]?.hasMoreOlder ?: true,
        )
    }

internal fun ChatViewModel.stashIncomingMessageForRoomImpl(message: ChatMessage) {
        val roomId = message.roomId.trim()
        if (roomId.isBlank()) return
        val selfId = currentUserId.trim()
        if (selfId.isNotEmpty() && message.senderId.trim() == selfId) {
            val cid = resolveOutgoingClientMessageId(message, activeOutgoingClientMessageIds)
            if (cid != null) {
                confirmOutgoingByClientMessageId(cid, message.withOutgoingClientMessageId(cid))
                return
            }
            if (hasOptimisticOutgoingPending(vmState.value.messages, currentUserId) ||
                hasMatchingPendingOutgoing(vmState.value.messages, message, currentUserId)
            ) {
                ChatDeliveryMetrics.logDrop(roomId, message._id, "own_outgoing_inflight")
                return
            }
        }
        if (!isIncomingMessageVisible(message)) {
            ChatDeliveryMetrics.logDrop(roomId, message._id, "hidden")
            return
        }
        val messageId = message._id?.trim().orEmpty()
        if (messageId.isEmpty()) {
            val fp = incomingMessageFingerprint(
                message.senderId,
                message.text,
                message.createdAt,
            )
            pendingIdlessStash[fp] = message
            ChatDeliveryMetrics.logStash(roomId, null, "no_id")
            return
        }
        trackRecentSocketMessageId(roomId, messageId)
        pendingIdlessStash.keys.removeIf { key ->
            val pending = pendingIdlessStash[key] ?: return@removeIf false
            pending.senderId == message.senderId &&
                pending.text == message.text &&
                pending.createdAt == message.createdAt
        }
        val cached = roomMessageCache[roomId]
        val existing = cached?.messages ?: emptyList()
        val localKnown = existing.mapNotNull { it._id }.toMutableSet()
        val update = upsertMessage(existing, message, localKnown, idIndex = null)
        val sanitized = capMessagesForMemory(
            sanitizeMessagesForUiList(
                messages = dedupeMessagesByIdNewestFirst(update.messages),
                currentUserId = currentUserId,
                activeOutgoingPendingId = outboxRoomSnapshot.newestPendingId,
            ),
        )
        roomMessageCache[roomId] = ChatRoomMessageCache(
            messages = sanitized,
            hasMoreOlder = cached?.hasMoreOlder ?: true,
        )
        ChatSessionCache.updateMessages(roomId, roomMessageCache[roomId]!!.messages)
        ChatDeliveryMetrics.logStash(roomId, messageId)
        scheduleRehydrateSelectedRoomFromStash(roomId)
    }

internal fun ChatViewModel.scheduleRehydrateSelectedRoomFromStashImpl(roomId: String) {
        val rid = roomId.trim()
        if (rid.isEmpty() || vmState.value.selectedRoomId != rid) return
        stashRehydrateJob?.cancel()
        if (isOverlayChatRealtimeViewActive()) {
            rehydrateRoomMessagesFromCache(rid)
            return
        }
        if (isChatRealtimeViewActive()) {
            rehydrateRoomMessagesFromCache(rid)
            return
        }
        stashRehydrateJob = vmScope.launch {
            if (vmState.value.selectedRoomId != rid) return@launch
            rehydrateRoomMessagesFromCache(rid)
        }
    }

internal fun ChatViewModel.isAllianceRaidRoomImpl(roomId: String): Boolean {
        val rid = roomId.trim()
        if (rid.isEmpty()) return false
        chatRoomPreferences.getRaidRoomId()?.trim()?.takeIf { it.isNotEmpty() }?.let {
            if (it == rid) return true
        }
        val room = vmState.value.rooms.find { it.id == rid } ?: return false
        return ChatRaidRoomSync.isAllianceRaidRoom(room)
    }

