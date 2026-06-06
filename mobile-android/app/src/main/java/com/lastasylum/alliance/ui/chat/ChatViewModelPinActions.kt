package com.lastasylum.alliance.ui.chat

import android.util.Log
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.chat.ChatMessage
import com.lastasylum.alliance.data.chat.ChatRoomDto
import com.lastasylum.alliance.data.chat.ChatRoomPinChangedEvent
import com.lastasylum.alliance.data.chat.ChatRoomsSessionCache
import com.lastasylum.alliance.data.chat.ChatSessionCache
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonEncodingException
import com.lastasylum.alliance.ui.util.toUserMessageRu
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException

private const val PIN_DIAG_TAG = "PinDiag"


internal fun ChatViewModel.chatPinCoordinatorImpl(roomId: String): PinScopeCoordinator =
        roomPinCoordinatorsInternal.getOrPut(roomId) {
            PinScopeCoordinator(
                pinHistoryPreferencesInternal,
                pinHistoryPreferencesInternal.chatScopeKey(roomId),
            )
        }

internal fun ChatViewModel.syncChatPinCoordinatorImpl(roomId: String, room: ChatRoomDto) {
        val coordinator = chatPinCoordinatorImpl(roomId)
        coordinator.pinnedMessageId = room.pinnedMessageId
        coordinator.pinnedMessage = room.pinnedMessage
        coordinator.pinnedAt = room.pinnedAt
        coordinator.pinnedByUserId = room.pinnedByUserId
        coordinator.replacePinHistory(pinHistoryByRoomInternal[roomId].orEmpty())
        coordinator.barIndex = pinBarIndexByRoomInternal.getOrDefault(roomId, 0)
    }

internal fun ChatViewModel.applyRoomPinToRoomsImpl(rooms: List<ChatRoomDto>, room: ChatRoomDto): List<ChatRoomDto> =
        rooms.map { if (it.id == room.id) room else it }

internal fun ChatViewModel.publishRoomsImpl(next: List<ChatRoomDto>) {
        vmState.update { applyPinBarUiImpl(it.copy(rooms = next)) }
    }

internal fun ChatViewModel.persistPinHistoryImpl(roomId: String) {
        val history = pinHistoryByRoomInternal[roomId].orEmpty()
        pinHistoryPreferencesInternal.save(pinHistoryPreferencesInternal.chatScopeKey(roomId), history)
    }

internal fun ChatViewModel.syncPinHistoryForRoomImpl(
        roomId: String,
        room: ChatRoomDto,
        messages: List<ChatMessage>,
    ) {
        val pinId = room.pinnedMessageId?.trim().orEmpty()
        if (pinId.isEmpty()) {
            pinHistoryByRoomInternal.remove(roomId)
            return
        }
        val serverHistory = serverPinHistoryFromRoom(room)
        val localHistory = pinHistoryByRoomInternal[roomId].orEmpty()
        val merged = mergePinHistory(serverHistory, localHistory)
        val refreshed = refreshPinHistoryPreviews(
            if (merged.isNotEmpty()) merged else localHistory,
            messages,
        )
        pinHistoryByRoomInternal[roomId] = refreshed
        val prevActivePin = lastSyncedActivePinIdByRoomInternal[roomId]
        if (prevActivePin != pinId) {
            lastSyncedActivePinIdByRoomInternal[roomId] = pinId
            pinBarIndexByRoomInternal[roomId] = 0
        }
    }

internal fun ChatViewModel.applyPinBarUiImpl(state: ChatState): ChatState {
        val roomId = state.selectedRoomId?.trim().orEmpty()
        if (roomId.isEmpty()) {
            return state.copy(
                pinBarPreview = null,
                pinHistoryCount = 0,
                isPinBarDismissed = false,
            )
        }
        val room = state.rooms.find { it.id == roomId }
            ?: return state.copy(
                pinBarPreview = null,
                pinHistoryCount = 0,
                isPinBarDismissed = false,
            )
        val pinId = room.pinnedMessageId?.trim().orEmpty()
        if (pinId.isEmpty()) {
            return state.copy(
                pinBarPreview = null,
                pinHistoryCount = 0,
                pinnedMessages = emptyList(),
                isPinBarDismissed = false,
            )
        }
        syncPinHistoryForRoomImpl(roomId, room, state.messages)
        syncChatPinCoordinatorImpl(roomId, room)
        val coordinator = chatPinCoordinatorImpl(roomId)
        val dismissed = coordinator.isPinBarDismissed()
        val serverPreview = resolveChatPinnedPreview(
            room.pinnedMessageId,
            room.pinnedMessage,
            state.messages,
        )
        val result = coordinator.applyPinBarUiChat(state.messages, serverPreview)
        pinHistoryByRoomInternal[roomId] = result.history
        pinBarIndexByRoomInternal[roomId] = result.barIndex
        return state.copy(
            pinBarPreview = result.preview,
            pinHistoryCount = result.historyCount,
            pinnedMessages = result.history,
            isPinBarDismissed = dismissed,
        )
    }

internal fun ChatViewModel.unpinOnePinnedMessageImpl(messageId: String) {
        val roomId = vmState.value.selectedRoomId?.trim().orEmpty()
        val trimmedId = messageId.trim()
        if (roomId.isEmpty() || trimmedId.isEmpty() || vmState.value.pinInFlight) return
        val roomsSnapshot = vmState.value.rooms
        val pinHistorySnapshot = pinHistoryByRoomInternal[roomId]?.toList().orEmpty()
        val localHistory = pinHistorySnapshot.ifEmpty {
            roomsSnapshot.find { it.id == roomId }?.pinnedMessagesOrEmpty().orEmpty()
        }
        pinHistoryByRoomInternal[roomId] = removePinFromHistory(localHistory, trimmedId)
        val optimisticRoom = roomsSnapshot.find { it.id == roomId }
            ?.withOptimisticUnpinOne(trimmedId, localHistory)
        if (optimisticRoom != null) {
            markPinStateAuthoritativeImpl(roomId)
            publishRoomPinImpl(optimisticRoom)
        }
        vmState.update { it.copy(pinInFlight = true) }
        vmScope.launch {
            vmRepository.unpinOneRoomMessage(roomId, trimmedId)
                .onSuccess { room ->
                    ChatRoomsSessionCache.invalidate()
                    pinHistoryByRoomInternal[roomId] = serverPinHistoryFromRoom(room)
                    publishRoomPinImpl(room)
                    vmState.update {
                        it.copy(
                            pinInFlight = false,
                            transientNotice = res.getString(R.string.chat_pinned_toast_unpinned),
                        )
                    }
                }
                .onFailure { e ->
                    handlePinApiFailureImpl(
                        roomId = roomId,
                        pinHistorySnapshot = pinHistorySnapshot,
                        roomsSnapshot = roomsSnapshot,
                        error = e,
                        successNotice = res.getString(R.string.chat_pinned_toast_unpinned),
                    )
                }
        }
    }

internal fun ChatViewModel.dismissPinBarForRoomImpl() {
        val roomId = vmState.value.selectedRoomId?.trim().orEmpty()
        val pinId = vmState.value.rooms.find { it.id == roomId }?.pinnedMessageId?.trim().orEmpty()
        if (roomId.isEmpty() || pinId.isEmpty()) return
        chatPinCoordinatorImpl(roomId).dismissPinBar()
        vmState.update { applyPinBarUiImpl(it) }
    }

internal fun ChatViewModel.restorePinBarForRoomImpl() {
        val roomId = vmState.value.selectedRoomId?.trim().orEmpty()
        if (roomId.isEmpty()) return
        chatPinCoordinatorImpl(roomId).restorePinBar()
        vmState.update { applyPinBarUiImpl(it) }
    }

internal fun ChatViewModel.isPinBarDismissedForRoomImpl(roomId: String, activePinId: String): Boolean =
        pinHistoryPreferencesInternal.isPinBarDismissed(
            pinHistoryPreferencesInternal.chatScopeKey(roomId),
            activePinId,
        )

internal fun ChatViewModel.updatePinBarUiImpl() {
        vmState.update { applyPinBarUiImpl(it) }
    }

    /**
     * Reload pin history and emit pin bar into [chromePaneState].
     * Needed when overlay/tab UI resubscribes after [SharingStarted.WhileSubscribed] idle.
     */
internal fun ChatViewModel.refreshPinBarForSelectedRoomImpl(resetBarIndex: Boolean = false) {
        val roomId = vmState.value.selectedRoomId?.trim().orEmpty()
        if (roomId.isEmpty()) return
        val room = vmState.value.rooms.find { it.id == roomId } ?: return
        val serverHistory = serverPinHistoryFromRoom(room)
        val localHistory =
            pinHistoryPreferencesInternal.load(pinHistoryPreferencesInternal.chatScopeKey(roomId)).orEmpty()
        pinHistoryByRoomInternal[roomId] = mergePinHistory(serverHistory, localHistory)
        if (resetBarIndex) {
            pinBarIndexByRoomInternal[roomId] = 0
        }
        updatePinBarUiImpl()
    }

internal fun ChatViewModel.onJumpToPinnedMessageImpl(messageId: String) {
        val targetId = messageId.trim()
        if (targetId.isEmpty()) return
        vmState.update {
            it.copy(
                scrollToMessageId = targetId,
                highlightMessageId = targetId,
                transientNotice = null,
            )
        }
        vmScope.launch {
            val found = jumpToChatPinnedMessage(
                messageId = targetId,
                messageIdsNewestFirst = {
                    vmState.value.messages.mapNotNull { it._id }
                },
                hasMoreOlder = { vmState.value.hasMoreOlder },
                isLoadingOlder = { vmState.value.isLoadingOlder },
                loadOlder = { loadOlderMessagesAwait(ignoreHiddenWatermark = true) },
                timelineIndexForMessageId = { jumpId ->
                    chatLazyIndexForMessageId(
                        vmState.value.messages,
                        _listDerived.value,
                        jumpId,
                    )
                },
                onJumpToMessage = { id ->
                    vmState.update {
                        it.copy(
                            scrollToMessageId = id,
                            highlightMessageId = id,
                            transientNotice = null,
                        )
                    }
                },
            )
            if (!found) {
                vmState.update {
                    it.copy(
                        transientNotice = res.getString(R.string.chat_jump_quote_not_found),
                        scrollToMessageId = null,
                        highlightMessageId = null,
                    )
                }
            }
        }
    }

internal fun ChatViewModel.onPinnedBarTapImpl() {
        val st = vmState.value
        val roomId = st.selectedRoomId?.trim().orEmpty()
        if (roomId.isEmpty()) return
        val room = st.rooms.find { it.id == roomId } ?: return
        val activePinId = room.pinnedMessageId?.trim().orEmpty()
        if (activePinId.isEmpty()) return
        val targetId = st.pinBarPreview?.id?.trim().orEmpty().ifEmpty { activePinId }
        val history = pinHistoryByRoomInternal[roomId].orEmpty()
        vmState.update {
            it.copy(
                scrollToMessageId = targetId,
                highlightMessageId = targetId,
                transientNotice = null,
            )
        }
        vmScope.launch {
            val found = jumpToChatPinnedMessage(
                messageId = targetId,
                messageIdsNewestFirst = {
                    vmState.value.messages.mapNotNull { it._id }
                },
                hasMoreOlder = { vmState.value.hasMoreOlder },
                isLoadingOlder = { vmState.value.isLoadingOlder },
                loadOlder = { loadOlderMessagesAwait(ignoreHiddenWatermark = true) },
                timelineIndexForMessageId = { jumpId ->
                    chatLazyIndexForMessageId(
                        vmState.value.messages,
                        _listDerived.value,
                        jumpId,
                    )
                },
                onJumpToMessage = { id ->
                    vmState.update {
                        it.copy(
                            scrollToMessageId = id,
                            highlightMessageId = id,
                            transientNotice = null,
                        )
                    }
                },
            )
            if (!found) {
                vmState.update {
                    it.copy(
                        transientNotice = res.getString(R.string.chat_jump_quote_not_found),
                        scrollToMessageId = null,
                        highlightMessageId = null,
                    )
                }
                return@launch
            }
            if (history.size > 1) {
                val barIndex = pinBarIndexByRoomInternal.getOrDefault(roomId, 0)
                pinBarIndexByRoomInternal[roomId] = advancePinBarIndex(history, barIndex)
                updatePinBarUiImpl()
            }
        }
    }

internal fun ChatViewModel.applyRoomPinEventImpl(rooms: List<ChatRoomDto>, event: ChatRoomPinChangedEvent): List<ChatRoomDto> =
        rooms.map { room ->
            if (room.id != event.roomId) room
            else room.mergePinFromEvent(event)
        }

internal fun ChatViewModel.markPinStateAuthoritativeImpl(roomId: String) {
        val id = roomId.trim()
        if (id.isNotEmpty()) pinStateAuthoritativeRoomIdsInternal.add(id)
    }

internal fun ChatViewModel.publishRoomPinImpl(room: ChatRoomDto) {
        vmState.update { st ->
            val withRooms = st.copy(rooms = applyRoomPinToRoomsImpl(st.rooms, room))
            if (st.selectedRoomId == room.id) applyPinBarUiImpl(withRooms) else withRooms
        }
        ChatSessionCache.update(vmState.value.rooms)
    }

internal fun ChatViewModel.onRoomPinChangedImpl(event: ChatRoomPinChangedEvent) {
        if (event.roomId in pinStateAuthoritativeRoomIdsInternal) {
            val localPinId = vmState.value.rooms
                .find { it.id == event.roomId }
                ?.pinnedMessageId
                ?.trim()
                .orEmpty()
            val eventPinId = event.pinnedMessageId?.trim().orEmpty()
            if (localPinId != eventPinId) {
                Log.d(
                    PIN_DIAG_TAG,
                    "ignore stale room pin-changed roomId=${event.roomId} local=$localPinId event=$eventPinId",
                )
                return
            }
            pinStateAuthoritativeRoomIdsInternal.remove(event.roomId)
        }
        val serverHistory = event.pinnedMessages.ifEmpty {
            event.pinnedMessage?.let { listOf(it) } ?: emptyList()
        }
        if (event.pinnedMessageId.isNullOrBlank()) {
            pinHistoryByRoomInternal.remove(event.roomId)
        } else {
            val localHistory = pinHistoryByRoomInternal[event.roomId].orEmpty()
            pinHistoryByRoomInternal[event.roomId] = mergePinHistory(serverHistory, localHistory)
        }
        val history = pinHistoryByRoomInternal[event.roomId].orEmpty()
        val scopeKey = pinHistoryPreferencesInternal.chatScopeKey(event.roomId)
        val newPinId = event.pinnedMessageId?.trim().orEmpty()
        if (newPinId.isNotEmpty()) {
            pinHistoryPreferencesInternal.clearDismissedPinBar(scopeKey)
        }
        Log.d(
            PIN_DIAG_TAG,
            "room pin-changed roomId=${event.roomId} history=${history.size} active=$newPinId",
        )
        vmState.update { st ->
            val withRooms = st.copy(rooms = applyRoomPinEventImpl(st.rooms, event))
            if (st.selectedRoomId == event.roomId) applyPinBarUiImpl(withRooms) else withRooms
        }
        ChatSessionCache.update(vmState.value.rooms)
    }

internal fun ChatViewModel.isPinResponseParseErrorImpl(error: Throwable): Boolean =
        error is JsonDataException || error is JsonEncodingException

    /**
     * Pin/unpin REST may succeed while Moshi fails on the room payload; keep optimistic/socket
     * state instead of rolling back to the pre-request snapshot.
     */
internal fun ChatViewModel.handlePinApiFailureImpl(
        roomId: String,
        pinHistorySnapshot: List<com.lastasylum.alliance.data.chat.PinnedMessagePreviewDto>,
        roomsSnapshot: List<ChatRoomDto>,
        error: Throwable,
        successNotice: String,
    ) {
        val parseOnly = isPinResponseParseErrorImpl(error)
        if (!parseOnly) {
            pinHistoryByRoomInternal[roomId] = pinHistorySnapshot
        }
        vmState.update { st ->
            applyPinBarUiImpl(
                st.copy(
                    rooms = if (parseOnly) st.rooms else roomsSnapshot,
                    pinInFlight = false,
                    transientNotice = if (parseOnly) successNotice else error.toUserMessageRu(res),
                ),
            )
        }
        if (parseOnly) {
            vmScope.launch {
                ChatRoomsSessionCache.invalidate()
                vmRepository.listRooms()
                    .onSuccess { rooms ->
                        val selectedId = vmState.value.selectedRoomId?.trim().orEmpty()
                        val currentRooms = vmState.value.rooms
                        val merged = if (selectedId.isEmpty()) {
                            rooms
                        } else {
                            rooms.map { serverRoom ->
                                if (serverRoom.id != selectedId) serverRoom
                                else currentRooms.find { it.id == selectedId } ?: serverRoom
                            }
                        }
                        vmState.update { st ->
                            applyPinBarUiImpl(st.copy(rooms = applyRoomsFromServer(merged)))
                        }
                    }
            }
        }
    }

internal fun ChatViewModel.pinMessageImpl(messageId: String, previewSource: ChatMessage? = null) {
        val roomId = vmState.value.selectedRoomId?.trim().orEmpty()
        val trimmedId = messageId.trim()
        if (roomId.isEmpty() || trimmedId.isEmpty() || vmState.value.pinInFlight) return
        val roomsSnapshot = vmState.value.rooms
        val pinHistorySnapshot = pinHistoryByRoomInternal[roomId]?.toList().orEmpty()
        val roomBefore = roomsSnapshot.find { it.id == roomId }
        val message = previewSource?.takeIf { it._id == trimmedId }
            ?: vmState.value.messages.find { it._id == trimmedId }
        val preview = message?.toPinnedPreview(
            actorUsername = message.takeIf {
                chatMessageIsOwn(it, vmState.value.currentUserId)
            }?.senderUsername?.trim()?.takeIf { it.isNotEmpty() },
        )
        val optimisticRoom = roomBefore?.let { room ->
            preview?.let { room.withOptimisticPin(trimmedId, it, vmState.value.currentUserId) }
                ?: room.copy(
                    pinnedMessageId = trimmedId,
                    pinnedAt = java.time.Instant.now().toString(),
                    pinnedByUserId = vmState.value.currentUserId,
                )
        }
        if (optimisticRoom != null) {
            markPinStateAuthoritativeImpl(roomId)
            publishRoomPinImpl(optimisticRoom)
        }
        vmState.update { it.copy(pinInFlight = true) }
        vmScope.launch {
            vmRepository.pinRoomMessage(roomId, trimmedId)
                .onSuccess { room ->
                    ChatRoomsSessionCache.invalidate()
                    val merged = ensureRoomPinPreview(
                        room = room,
                        preview = preview,
                        pinnedByUserId = vmState.value.currentUserId,
                    )
                    pinHistoryByRoomInternal[roomId] = serverPinHistoryFromRoom(merged)
                    pinBarIndexByRoomInternal[roomId] = 0
                    publishRoomPinImpl(merged)
                    vmState.update {
                        it.copy(
                            pinInFlight = false,
                            transientNotice = res.getString(R.string.chat_pinned_toast_pinned),
                        )
                    }
                }
                .onFailure { e ->
                    handlePinApiFailureImpl(
                        roomId = roomId,
                        pinHistorySnapshot = pinHistorySnapshot,
                        roomsSnapshot = roomsSnapshot,
                        error = e,
                        successNotice = res.getString(R.string.chat_pinned_toast_pinned),
                    )
                }
        }
    }

internal fun ChatViewModel.unpinSelectedRoomImpl() {
        val roomId = vmState.value.selectedRoomId?.trim().orEmpty()
        if (roomId.isEmpty() || vmState.value.pinInFlight) return
        val roomsSnapshot = vmState.value.rooms
        val pinHistorySnapshot = pinHistoryByRoomInternal[roomId]?.toList().orEmpty()
        pinHistoryByRoomInternal.remove(roomId)
        val optimisticRoom = roomsSnapshot.find { it.id == roomId }?.withOptimisticUnpin()
        if (optimisticRoom != null) {
            markPinStateAuthoritativeImpl(roomId)
            publishRoomPinImpl(optimisticRoom)
        }
        vmState.update { it.copy(pinInFlight = true) }
        vmScope.launch {
            vmRepository.pinRoomMessage(roomId, null)
                .onSuccess { room ->
                    ChatRoomsSessionCache.invalidate()
                    pinHistoryByRoomInternal.remove(roomId)
                    pinBarIndexByRoomInternal.remove(roomId)
                    publishRoomPinImpl(room)
                    vmState.update {
                        it.copy(
                            pinInFlight = false,
                            transientNotice = res.getString(R.string.chat_pinned_toast_unpinned),
                        )
                    }
                }
                .onFailure { e ->
                    handlePinApiFailureImpl(
                        roomId = roomId,
                        pinHistorySnapshot = pinHistorySnapshot,
                        roomsSnapshot = roomsSnapshot,
                        error = e,
                        successNotice = res.getString(R.string.chat_pinned_toast_unpinned),
                    )
                }
        }
    }

internal fun ChatViewModel.editMessageImpl(messageId: String, newText: String) {
        if (messageId.isBlank()) return
        val trimmed = newText.trim()
        if (trimmed.isBlank()) return
        val id = messageId.trim()
        val previous = vmState.value.messages.find { it._id?.trim() == id }
        previous?.let { row ->
            applyMessageReplaceSynchronously(
                row.copy(
                    text = trimmed,
                    editedAt = java.time.Instant.now().toString(),
                ).normalizeEditedAtForDisplay(),
            )
        }
        vmScope.launch {
            vmRepository.editMessage(id, trimmed)
                .onSuccess { updated ->
                    applyMessageReplaceSynchronously(updated.normalizeEditedAtForDisplay())
                }
                .onFailure { e ->
                    previous?.let { applyMessageReplaceSynchronously(it) }
                    vmState.value = vmState.value.copy(error = e.toUserMessageRu(res))
                }
        }
    }

internal fun ChatViewModel.forwardMessageImpl(messageId: String) {
        if (messageId.isBlank()) return
        val roomId = vmState.value.selectedRoomId ?: return
        vmScope.launch {
            vmRepository.forwardMessage(messageId, roomId)
                .onSuccess { forwarded ->
                    applyIncomingMessage(forwarded)
                }
                .onFailure { e ->
                    vmState.value = vmState.value.copy(error = e.toUserMessageRu(res))
                }
        }
    }

internal fun ChatViewModel.beginMessageSelectionImpl(messageId: String) {
        val target = vmState.value.messages.find { it._id == messageId } ?: return
        if (target.deletedAt != null) return
        if (!canModerateChat(target)) return
        vmState.value = vmState.value.copy(
            selectedMessageIds = setOf(messageId),
            activeActionMessageId = null,
            confirmDeleteMessageId = null,
            confirmBulkDelete = false,
        )
    }

internal fun ChatViewModel.toggleMessageSelectionImpl(messageId: String) {
        val target = vmState.value.messages.find { it._id == messageId } ?: return
        if (target.deletedAt != null) return
        if (!canModerateChat(target)) return
        val cur = vmState.value.selectedMessageIds
        if (cur.isEmpty()) return
        val next = if (messageId in cur) cur - messageId else cur + messageId
        vmState.value = vmState.value.copy(selectedMessageIds = next)
    }

internal fun ChatViewModel.clearMessageSelectionImpl() {
        if (vmState.value.selectedMessageIds.isEmpty() &&
            !vmState.value.confirmBulkDelete &&
            !vmState.value.isDeletingSelection
        ) {
            return
        }
        vmState.value = vmState.value.copy(
            selectedMessageIds = emptySet(),
            confirmBulkDelete = false,
        )
    }

internal fun ChatViewModel.requestBulkDeleteImpl() {
        if (vmState.value.selectedMessageIds.isEmpty()) return
        vmState.value = vmState.value.copy(confirmBulkDelete = true)
    }

internal fun ChatViewModel.dismissBulkDeleteConfirmImpl() {
        if (!vmState.value.confirmBulkDelete) return
        vmState.value = vmState.value.copy(confirmBulkDelete = false)
    }

internal fun ChatViewModel.confirmDeleteSelectedMessagesImpl() {
        val ids = vmState.value.selectedMessageIds.toList()
        if (ids.isEmpty()) return
        vmScope.launch {
            vmState.value = vmState.value.copy(
                confirmBulkDelete = false,
                isDeletingSelection = true,
                error = null,
            )
            var lastFailure: Throwable? = null
            for (id in ids) {
                vmRepository.deleteMessage(id)
                    .onSuccess { result ->
                        val messageId = result.messageId
                        vmState.value = syncSelections(
                            scrubRemovedMessage(vmState.value, messageId).copy(
                                isDeletingSelection = true,
                            ),
                        )
                        result.pinChanged?.let { applyRoomPinChangedEvent(it) }
                        persistMessageRemoved(messageId, result.roomId)
                    }
                    .onFailure { t ->
                        if (t.isChatMessageAlreadyGoneOnServer()) {
                            notifyOverlayStripMessageRemoved(id)
                            vmState.value = syncSelections(
                                scrubRemovedMessage(vmState.value, id).copy(
                                    isDeletingSelection = true,
                                ),
                            )
                            persistMessageRemoved(id, vmState.value.selectedRoomId.orEmpty())
                        } else {
                            lastFailure = t
                        }
                    }
                if (lastFailure != null) break
            }
            vmState.value = vmState.value.copy(
                isDeletingSelection = false,
                error = lastFailure?.toUserMessageRu(res),
            )
        }
    }

internal fun ChatViewModel.dismissDeleteMessageImpl() {
        if (vmState.value.confirmDeleteMessageId == null) return
        vmState.value = vmState.value.copy(confirmDeleteMessageId = null)
    }

internal fun ChatViewModel.confirmDeleteMessageImpl() {
        val messageId = vmState.value.confirmDeleteMessageId ?: return
        vmScope.launch {
            vmState.value = vmState.value.copy(
                confirmDeleteMessageId = null,
                deletingMessageId = messageId,
                error = null,
            )
            vmRepository.deleteMessage(messageId)
                .onSuccess { result ->
                    vmState.value = syncSelections(
                        scrubRemovedMessage(vmState.value, result.messageId).copy(
                            deletingMessageId = null,
                            error = null,
                        ),
                    )
                    result.pinChanged?.let { applyRoomPinChangedEvent(it) }
                    persistMessageRemoved(result.messageId, result.roomId)
                }
                .onFailure { throwable ->
                    if (throwable.isChatMessageAlreadyGoneOnServer()) {
                        notifyOverlayStripMessageRemoved(messageId)
                        vmState.value = syncSelections(
                            scrubRemovedMessage(vmState.value, messageId).copy(
                                deletingMessageId = null,
                                error = null,
                            ),
                        )
                        persistMessageRemoved(messageId, vmState.value.selectedRoomId.orEmpty())
                    } else {
                        vmState.value = vmState.value.copy(
                            deletingMessageId = null,
                            error = throwable.toUserMessageRu(res),
                        )
                    }
                }
        }
    }

    /** Server hard-deleted the row (or never had it); drop from local feed anyway. */
internal fun Throwable.isChatMessageAlreadyGoneOnServer(): Boolean =
    this is HttpException && code() == 404

internal fun ChatViewModel.notifyOverlayStripMessageRemovedImpl(messageId: String) {
        val id = messageId.trim()
        if (id.isEmpty()) return
        vmRepository.notifyOverlayMessageDeleted(id, vmState.value.selectedRoomId.orEmpty())
    }

    /** Drop own socket/HTTP echo before the debounced channel — prevents a visible duplicate row. */
