package com.lastasylum.alliance.ui.chat

import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.chat.ChatMessage
import com.lastasylum.alliance.data.chat.ChatRoomDto
import com.lastasylum.alliance.data.chat.ChatSessionCache
import com.lastasylum.alliance.ui.chat.rebuildMessageIdIndex
import com.lastasylum.alliance.ui.util.toUserMessageRu
import kotlinx.coroutines.flow.update

internal fun ChatViewModel.clearTypingForRoomOpen() {
    typingEmitJob?.cancel()
    typingEmitJob = null
    synchronized(typingPeerJobsLock) {
        typingPeerJobs.values.forEach { it.cancel() }
        typingPeerJobs.clear()
    }
}

internal suspend fun ChatViewModel.loadTeamProfileGateForOpen(
    isGlobalRoom: Boolean,
    hadCachedMessages: Boolean,
    messagesAlreadyInState: Boolean,
): Boolean = when {
    isGlobalRoom -> loadTeamProfileGate()
    hadCachedMessages && cachedTeamProfileGate != null -> cachedTeamProfileGate == true
    messagesAlreadyInState -> vmState.value.hasTeamProfileForGlobalChat
    else -> loadTeamProfileGate()
}

internal fun ChatViewModel.applyOpenRoomLoadingState(
    roomId: String,
    rooms: List<ChatRoomDto>,
    hasTeam: Boolean,
) {
    val rid = roomId.trim()
    vmState.value = vmState.value.copy(
        isLoading = true,
        isRoomsLoading = false,
        rooms = clearUnreadForRoomIfViewing(rooms, rid, treatAsViewing = true),
        selectedRoomId = rid,
        hasTeamProfileForGlobalChat = hasTeam,
        error = null,
        messages = emptyList(),
        currentUserId = currentUserIdInternal,
        currentUserRole = currentUserRoleInternal,
        hasMoreOlder = true,
        isLoadingOlder = false,
        isSending = false,
        replyToMessage = null,
        activeActionMessageId = null,
        confirmDeleteMessageId = null,
        selectedMessageIds = emptySet(),
        confirmBulkDelete = false,
        isDeletingSelection = false,
        deletingMessageId = null,
        newestMessageKey = null,
        scrollToLatestNonce = 0L,
        scrollToMessageId = null,
        highlightMessageId = null,
        transientNotice = null,
        sendFailure = null,
    )
    _otherReadUptoMessageId.value = otherReadUptoByRoom[rid]
    _listDerived.value = ChatMessagesListDerived.Empty
}

internal fun ChatViewModel.applyOpenRoomCachedState(
    roomId: String,
    rooms: List<ChatRoomDto>,
    hasTeam: Boolean,
    filteredCache: List<ChatMessage>,
    hasMoreOlder: Boolean,
    messagesAlreadyInState: Boolean,
) {
    val rid = roomId.trim()
    _otherReadUptoMessageId.value = otherReadUptoByRoom[rid]
    if (!messagesAlreadyInState) {
        knownMessageIds.clear()
        messageIdIndex.clear()
        knownMessageIds.addAll(filteredCache.mapNotNull { it._id })
        rebuildMessageIdIndex(filteredCache, messageIdIndex)
        vmState.value = vmState.value.copy(
            isLoading = false,
            messages = filteredCache,
            selectedRoomId = rid,
            hasMoreOlder = hasMoreOlder,
            rooms = clearUnreadForRoomIfViewing(vmState.value.rooms, rid, treatAsViewing = true),
        )
        publishMessagesDerivedImmediate(filteredCache)
    } else {
        vmState.update {
            it.copy(
                isLoading = false,
                selectedRoomId = rid,
                hasMoreOlder = hasMoreOlder,
                rooms = clearUnreadForRoomIfViewing(it.rooms, rid, treatAsViewing = true),
            )
        }
    }
    vmState.update {
        it.copy(
            isRoomsLoading = false,
            rooms = clearUnreadForRoomIfViewing(rooms, rid, treatAsViewing = true),
            selectedRoomId = rid,
            hasTeamProfileForGlobalChat = hasTeam,
        )
    }
}

internal fun ChatViewModel.fallbackRoomsOnBootstrapError(error: Throwable): List<ChatRoomDto>? {
    val fallback = ChatSessionCache.getFreshRooms()
        ?: launchDiskCacheInternal.loadChatRooms(currentUserIdInternal)
        ?: vmState.value.rooms.takeIf { it.isNotEmpty() }
    if (!fallback.isNullOrEmpty()) return fallback
    applyBootstrapError(error.toUserMessageRu(res))
    return null
}

internal fun ChatViewModel.applyBootstrapError(message: String) {
    _draftMessage.value = ""
    _typingPeers.value = emptyMap()
    vmState.value = ChatState(
        isRoomsLoading = false,
        error = message,
        currentUserId = currentUserIdInternal,
        currentUserRole = currentUserRoleInternal,
        isAppAdmin = isAppAdmin(currentUserRoleInternal),
        hasTeamProfileForGlobalChat = false,
        enabledStickerPackKeys = emptySet(),
    )
}

internal fun ChatViewModel.applyBootstrapEmptyRoomsError(message: String) {
    _draftMessage.value = ""
    _typingPeers.value = emptyMap()
    chatRoomPreferencesInternal.clearRaidRoomId()
    vmState.value = ChatState(
        isRoomsLoading = false,
        currentUserId = currentUserIdInternal,
        currentUserRole = currentUserRoleInternal,
        isAppAdmin = isAppAdmin(currentUserRoleInternal),
        hasTeamProfileForGlobalChat = false,
        error = res.getString(R.string.chat_no_rooms),
        enabledStickerPackKeys = emptySet(),
    )
}
