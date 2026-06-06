package com.lastasylum.alliance.ui.chat

import android.app.Application
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.chat.ChatAllianceIds
import com.lastasylum.alliance.data.chat.ChatMessage
import com.lastasylum.alliance.data.chat.ChatRoomDto
import com.lastasylum.alliance.data.chat.ChatRoomKind
import com.lastasylum.alliance.data.chat.ChatRoomKindResolver
import com.lastasylum.alliance.data.chat.ChatRoomUnreadEvent
import com.lastasylum.alliance.data.chat.ChatRoomsSessionCache
import com.lastasylum.alliance.data.chat.ChatSessionCache
import com.lastasylum.alliance.data.chat.ChatTeamRoomsMembership
import com.lastasylum.alliance.data.chat.store.ChatArchitectureFlags
import com.lastasylum.alliance.data.chat.sync.CHAT_INITIAL_PAGE_SIZE
import com.lastasylum.alliance.data.chat.sync.CHAT_PAGE_SIZE
import com.lastasylum.alliance.data.chat.sync.CHAT_ROOMS_SYNC_ON_RESUME_TTL_MS
import com.lastasylum.alliance.data.chat.sync.CHAT_UNREAD_SYNC_DEBOUNCE_MS
import com.lastasylum.alliance.data.chat.sync.ChatRoomMessageCache
import com.lastasylum.alliance.data.chat.sync.LaunchDiskPrimePayload
import com.lastasylum.alliance.data.displayedUnreadCount
import com.lastasylum.alliance.data.effectiveUnreadCount
import com.lastasylum.alliance.data.isObjectIdNewer
import com.lastasylum.alliance.di.AppContainer
import com.lastasylum.alliance.overlay.CombatOverlayService
import com.lastasylum.alliance.ui.OVERLAY_PANEL_LOAD_MAX_MS
import com.lastasylum.alliance.ui.chat.usecase.ChatRoomsUseCase
import com.lastasylum.alliance.ui.chat.usecase.ChatUnreadUseCase
import com.lastasylum.alliance.ui.util.toUserMessageRu
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
internal fun ChatViewModel.publishMessagesDerivedImpl(messages: List<ChatMessage>) {
        deriveJob?.cancel()
        deriveDebounceJob?.cancel()
        if (messages.isEmpty()) {
            _listDerived.value = ChatMessagesListDerived.Empty
            return
        }
        if (listDeriveDefer.deferFullDerive(messages)) return
        publishMessagesDerivedNow(messages)
    }

internal fun ChatViewModel.publishMessagesDerivedNowImpl(messages: List<ChatMessage>) {
        deriveJob?.cancel()
        deriveDebounceJob?.cancel()
        if (messages.isEmpty()) {
            _listDerived.value = ChatMessagesListDerived.Empty
            return
        }
        val expected = messages
        deriveDebounceJob = vmScope.launch {
            delay(CHAT_LIST_DERIVE_DEBOUNCE_MS)
            deriveJob = launch(Dispatchers.Default) {
                val derived = buildChatMessagesListDerived(expected)
                if (chatMessagesListContentEqual(expected, vmState.value.messages)) {
                    _listDerived.value = derived
                    withContext(Dispatchers.Main) {
                        maybeRefreshPinBarUi()
                    }
                }
            }
        }
    }

internal fun ChatViewModel.maybeRefreshPinBarUiImpl() {
        val st = vmState.value
        val roomId = st.selectedRoomId?.trim().orEmpty()
        if (roomId.isEmpty()) return
        val room = st.rooms.find { it.id == roomId } ?: return
        val pinId = room.pinnedMessageId?.trim().orEmpty()
        if (pinId.isEmpty()) return
        val edited = st.messages.find { it._id?.trim() == pinId }?.toPinnedPreview()
        if (edited != null) {
            val pinnedRoom = room.withOptimisticPin(
                pinId,
                edited,
                room.pinnedByUserId.orEmpty(),
            ).copy(
                pinnedMessages = room.pinnedMessagesOrEmpty().map { entry ->
                    if (entry.id.trim() == pinId) edited else entry
                }.ifEmpty { listOf(edited) },
            )
            publishRoomPin(pinnedRoom)
            return
        }
        updatePinBarUi()
    }

    /** Reactions / single-row edits: keep timeline shape, patch one row off the UI thread. */
internal fun ChatViewModel.publishMessagesDerivedAfterPatchImpl(messages: List<ChatMessage>, messageIndex: Int) {
        deriveJob?.cancel()
        deriveDebounceJob?.cancel()
        if (messages.isEmpty()) {
            _listDerived.value = ChatMessagesListDerived.Empty
            return
        }
        val previousDerived = _listDerived.value
        if (messages.size <= CHAT_LIST_DERIVE_SYNC_MAX) {
            _listDerived.value = buildChatMessagesListDerivedAfterPatchMessage(
                previousDerived = previousDerived,
                messages = messages,
                messageIndex = messageIndex,
            )
            return
        }
        val expected = messages
        val idx = messageIndex
        deriveJob = vmScope.launch(Dispatchers.Default) {
            val derived = buildChatMessagesListDerivedAfterPatchMessage(
                previousDerived = previousDerived,
                messages = expected,
                messageIndex = idx,
            )
            if (chatMessagesListContentEqual(expected, vmState.value.messages)) {
                _listDerived.value = derived
            }
        }
    }

    /** Мгновенная лента при переключении комнаты (кэш уже в памяти). */
internal fun ChatViewModel.publishMessagesDerivedImmediateImpl(messages: List<ChatMessage>) {
        deriveJob?.cancel()
        if (messages.isEmpty()) {
            _listDerived.value = ChatMessagesListDerived.Empty
            return
        }
        if (messages.size <= CHAT_LIST_DERIVE_SYNC_MAX) {
            _listDerived.value = buildChatMessagesListDerived(messages)
            return
        }
        val expected = messages
        deriveJob = vmScope.launch(Dispatchers.Default) {
            val derived = buildChatMessagesListDerived(expected)
            if (vmState.value.selectedRoomId == null) return@launch
            if (!chatMessagesListContentEqual(expected, vmState.value.messages)) return@launch
            _listDerived.value = derived
        }
    }

internal suspend fun ChatViewModel.buildDerivedAfterUpsertImpl(
        messages: List<ChatMessage>,
        previousMessages: List<ChatMessage>,
        previousDerived: ChatMessagesListDerived,
    ): ChatMessagesListDerived {
        if (messages.isEmpty()) return ChatMessagesListDerived.Empty
        val canPrepend = previousMessages.isNotEmpty() &&
            messages.size == previousMessages.size + 1 &&
            messages.drop(1) == previousMessages
        if (canPrepend) {
            return buildChatMessagesListDerivedAfterPrepend(
                previousDerived = previousDerived,
                previousMessages = previousMessages,
                messages = messages,
            )
        }
        if (previousMessages.isNotEmpty() &&
            messages.size == previousMessages.size &&
            messages.drop(1) == previousMessages.drop(1) &&
            messages[0] != previousMessages[0]
        ) {
            return buildChatMessagesListDerivedAfterReplaceNewest(
                previousDerived = previousDerived,
                previousMessages = previousMessages,
                messages = messages,
            )
        }
        return withContext(Dispatchers.Default) {
            buildChatMessagesListDerived(messages)
        }
    }

