package com.lastasylum.alliance.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lastasylum.alliance.data.chat.ChatMessage
import com.lastasylum.alliance.data.chat.ChatMessageDeletedEvent
import com.lastasylum.alliance.data.chat.ChatRepository
import com.lastasylum.alliance.data.chat.ChatTypingEvent
import com.lastasylum.alliance.data.chat.ChatRoomDto
import com.lastasylum.alliance.data.chat.ChatRoomPreferences
import com.lastasylum.alliance.ui.util.toUserMessageRu
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val PAGE_SIZE = 30

class ChatViewModel(
    application: Application,
    private val repository: ChatRepository,
    private val chatRoomPreferences: ChatRoomPreferences,
    private val currentUserId: String,
    private val currentUserRole: String,
) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(
        ChatState(
            currentUserId = currentUserId,
            currentUserRole = currentUserRole,
        ),
    )
    val state: StateFlow<ChatState> = _state.asStateFlow()

    /** Isolated from [state] so each keystroke does not recompose the whole chat list. */
    private val _draftMessage = MutableStateFlow("")
    val draftMessage: StateFlow<String> = _draftMessage.asStateFlow()
    private val knownMessageIds = LinkedHashSet<String>()
    private val typingPeerJobs = mutableMapOf<String, Job>()
    private var typingEmitJob: Job? = null

    private val res get() = getApplication<Application>().resources

    fun refreshChat() {
        viewModelScope.launch { bootstrap() }
    }

    private suspend fun bootstrap() {
        _state.value = _state.value.copy(isRoomsLoading = true, error = null)
        val rooms = repository.listRooms().getOrElse { e ->
            _draftMessage.value = ""
            _state.value = ChatState(
                isRoomsLoading = false,
                error = e.toUserMessageRu(res),
                currentUserId = currentUserId,
                currentUserRole = currentUserRole,
            )
            return
        }
        if (rooms.isEmpty()) {
            _draftMessage.value = ""
            _state.value = ChatState(
                isRoomsLoading = false,
                currentUserId = currentUserId,
                currentUserRole = currentUserRole,
                error = getApplication<Application>().getString(
                    com.lastasylum.alliance.R.string.chat_no_rooms,
                ),
            )
            return
        }
        val stored = chatRoomPreferences.getSelectedRoomId()
        val selected = rooms.find { it.id == stored }?.id
            ?: rooms.minByOrNull { it.sortOrder }?.id
            ?: rooms.first().id
        chatRoomPreferences.setSelectedRoomId(selected)
        openRoom(selected, rooms)
    }

    fun selectRoom(roomId: String) {
        if (roomId == _state.value.selectedRoomId) return
        viewModelScope.launch {
            chatRoomPreferences.setSelectedRoomId(roomId)
            repository.disconnectRealtime()
            openRoom(roomId, _state.value.rooms)
        }
    }

    private suspend fun openRoom(roomId: String, rooms: List<ChatRoomDto>) {
        typingEmitJob?.cancel()
        typingEmitJob = null
        typingPeerJobs.values.forEach { it.cancel() }
        typingPeerJobs.clear()
        knownMessageIds.clear()
        _draftMessage.value = ""
        _state.value = _state.value.copy(
            isLoading = true,
            isRoomsLoading = false,
            rooms = rooms,
            selectedRoomId = roomId,
            error = null,
            messages = emptyList(),
            currentUserId = currentUserId,
            currentUserRole = currentUserRole,
            hasMoreOlder = true,
            isLoadingOlder = false,
            isSending = false,
            replyToMessage = null,
            activeActionMessageId = null,
            confirmDeleteMessageId = null,
            deletingMessageId = null,
            newestMessageKey = null,
            scrollToLatestNonce = 0L,
            sendFailure = null,
            typingPeers = emptyMap(),
        )
        repository.loadRecentMessages(roomId, beforeMessageId = null, limit = PAGE_SIZE)
            .onSuccess { loaded ->
                knownMessageIds.clear()
                knownMessageIds.addAll(loaded.mapNotNull { it._id })
                _state.value = _state.value.copy(
                    isLoading = false,
                    messages = loaded,
                    selectedRoomId = roomId,
                    hasMoreOlder = loaded.size >= PAGE_SIZE,
                )
                repository.connectRealtime(
                    roomId = roomId,
                    onMessage = ::onIncomingMessage,
                    onDeleteMessage = ::onDeletedMessage,
                    onTyping = ::onTypingFromPeer,
                )
            }
            .onFailure { e ->
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.toUserMessageRu(res),
                )
            }
    }

    fun loadOlderMessages() {
        val roomId = _state.value.selectedRoomId ?: return
        val oldestId = _state.value.messages.lastOrNull()?._id ?: return
        if (!_state.value.hasMoreOlder || _state.value.isLoadingOlder || _state.value.isLoading) {
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoadingOlder = true)
            repository.loadRecentMessages(
                roomId = roomId,
                beforeMessageId = oldestId,
                limit = PAGE_SIZE,
            )
                .onSuccess { older ->
                    val merged = _state.value.messages + older.filter { msg ->
                        val id = msg._id
                        id == null || knownMessageIds.add(id)
                    }
                    _state.value = _state.value.copy(
                        messages = merged,
                        isLoadingOlder = false,
                        hasMoreOlder = older.size >= PAGE_SIZE,
                    )
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        isLoadingOlder = false,
                        error = e.toUserMessageRu(res),
                    )
                }
        }
    }

    fun sendMessage(text: String, replyOverride: String? = null) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return
        val roomId = _state.value.selectedRoomId ?: return
        val replyToMessageId = replyOverride ?: _state.value.replyToMessage?._id
        viewModelScope.launch {
            _state.value = _state.value.copy(
                isSending = true,
                error = null,
                sendFailure = null,
            )
            repository.sendMessageWithRetries(trimmed, roomId, replyToMessageId)
                .onSuccess { sent ->
                    applyIncomingMessage(sent, clearComposer = true)
                }
                .onFailure { throwable ->
                    _state.value = _state.value.copy(
                        isSending = false,
                        sendFailure = ChatSendFailure(
                            messageText = trimmed,
                            replyToMessageId = replyToMessageId,
                            errorMessage = throwable.toUserMessageRu(res),
                        ),
                    )
                }
        }
    }

    fun sendDraftMessage() {
        sendMessage(_draftMessage.value.trim())
    }

    fun retrySendFailure() {
        val failure = _state.value.sendFailure ?: return
        sendMessage(failure.messageText, replyOverride = failure.replyToMessageId)
    }

    fun dismissSendFailure() {
        if (_state.value.sendFailure == null) return
        _state.value = _state.value.copy(sendFailure = null)
    }

    fun setDraftMessage(value: String) {
        if (_draftMessage.value == value) return
        _draftMessage.value = value
        scheduleTypingEmit()
    }

    private fun scheduleTypingEmit() {
        typingEmitJob?.cancel()
        val roomId = _state.value.selectedRoomId ?: return
        if (_draftMessage.value.isBlank()) return
        typingEmitJob = viewModelScope.launch {
            try {
                delay(500)
                repository.emitTypingPing(roomId)
            } catch (_: CancellationException) {
                // cancelled by newer keystroke or room switch
            }
        }
    }

    fun beginReplyToMessage(messageId: String) {
        val target = _state.value.messages.find { it._id == messageId } ?: return
        if (target.deletedAt != null) return
        _state.value = _state.value.copy(
            replyToMessage = target,
            activeActionMessageId = null,
        )
    }

    fun clearReplyToMessage() {
        if (_state.value.replyToMessage == null) return
        _state.value = _state.value.copy(replyToMessage = null)
    }

    fun openMessageActions(messageId: String) {
        _state.value = _state.value.copy(activeActionMessageId = messageId)
    }

    fun dismissMessageActions() {
        if (_state.value.activeActionMessageId == null) return
        _state.value = _state.value.copy(activeActionMessageId = null)
    }

    fun requestDeleteMessage(messageId: String) {
        _state.value = _state.value.copy(
            activeActionMessageId = null,
            confirmDeleteMessageId = messageId,
        )
    }

    fun dismissDeleteMessage() {
        if (_state.value.confirmDeleteMessageId == null) return
        _state.value = _state.value.copy(confirmDeleteMessageId = null)
    }

    fun confirmDeleteMessage() {
        val messageId = _state.value.confirmDeleteMessageId ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(
                confirmDeleteMessageId = null,
                deletingMessageId = messageId,
                error = null,
            )
            repository.deleteMessage(messageId)
                .onSuccess { result ->
                    _state.value = syncSelections(
                        scrubRemovedMessage(_state.value, result.messageId).copy(
                            deletingMessageId = null,
                            error = null,
                        ),
                    )
                }
                .onFailure { throwable ->
                    _state.value = _state.value.copy(
                        deletingMessageId = null,
                        error = throwable.toUserMessageRu(res),
                    )
                }
        }
    }

    private fun onIncomingMessage(message: ChatMessage) {
        // Socket.IO вызывает слушателей с рабочего потока движка — обновление UI-состояния только через Main.
        viewModelScope.launch {
            val roomId = _state.value.selectedRoomId ?: return@launch
            if (message.roomId.isNotBlank() && message.roomId != roomId) return@launch
            applyIncomingMessage(message)
        }
    }

    private fun onTypingFromPeer(event: ChatTypingEvent) {
        viewModelScope.launch {
            if (event.userId.isBlank() || event.userId == currentUserId) return@launch
            val roomId = _state.value.selectedRoomId ?: return@launch
            if (event.roomId.isNotBlank() && event.roomId != roomId) return@launch
            val username = event.username.ifBlank { "…" }
            typingPeerJobs[event.userId]?.cancel()
            typingPeerJobs[event.userId] = launch {
                try {
                    val m = _state.value.typingPeers.toMutableMap()
                    m[event.userId] = username
                    _state.value = _state.value.copy(typingPeers = m)
                    delay(3200)
                    val m2 = _state.value.typingPeers.toMutableMap()
                    m2.remove(event.userId)
                    _state.value = _state.value.copy(typingPeers = m2)
                } catch (_: CancellationException) {
                    // superseded by a newer typing event for the same user
                } finally {
                    typingPeerJobs.remove(event.userId)
                }
            }
        }
    }

    private fun onDeletedMessage(event: ChatMessageDeletedEvent) {
        viewModelScope.launch {
            val roomId = _state.value.selectedRoomId ?: return@launch
            if (event.roomId.isNotBlank() && event.roomId != roomId) return@launch
            val scrubbed = scrubRemovedMessage(_state.value, event.messageId)
            _state.value = syncSelections(
                scrubbed.copy(
                    deletingMessageId = if (scrubbed.deletingMessageId == event.messageId) {
                        null
                    } else {
                        scrubbed.deletingMessageId
                    },
                ),
            )
        }
    }

    private fun scrubRemovedMessage(state: ChatState, removedId: String): ChatState {
        knownMessageIds.remove(removedId)
        val nextMessages = state.messages
            .filterNot { it._id == removedId }
            .map { message ->
                if (message.replyTo?._id == removedId) {
                    message.copy(replyTo = null)
                } else {
                    message
                }
            }
        return state.copy(messages = nextMessages)
    }

    private fun applyIncomingMessage(
        message: ChatMessage,
        clearComposer: Boolean = false,
    ) {
        val update = upsertMessage(_state.value.messages, message)
        var nextState = _state.value.copy(
            messages = update.messages,
            newestMessageKey = update.newestMessageKey ?: _state.value.newestMessageKey,
            isSending = false,
            deletingMessageId = if (_state.value.deletingMessageId == message._id) null
            else _state.value.deletingMessageId,
            error = null,
        )
        if (clearComposer) {
            _draftMessage.value = ""
            nextState = nextState.copy(
                replyToMessage = null,
                scrollToLatestNonce = nextState.scrollToLatestNonce + 1L,
                sendFailure = null,
            )
        }
        _state.value = syncSelections(nextState)
    }

    private fun upsertMessage(
        current: List<ChatMessage>,
        incoming: ChatMessage,
    ): MessageUpsertResult {
        val id = incoming._id
        if (id != null) {
            val existingIndex = current.indexOfFirst { it._id == id }
            if (existingIndex >= 0) {
                val updated = current.toMutableList()
                updated[existingIndex] = incoming
                return MessageUpsertResult(
                    messages = updated,
                    newestMessageKey = null,
                )
            }
            knownMessageIds.add(id)
            return MessageUpsertResult(
                messages = listOf(incoming) + current,
                newestMessageKey = id,
            )
        }
        val exists = current.any {
            it._id == null &&
                it.senderId == incoming.senderId &&
                it.createdAt == incoming.createdAt &&
                it.text == incoming.text
        }
        if (exists) {
            return MessageUpsertResult(current, null)
        }
        return MessageUpsertResult(
            messages = listOf(incoming) + current,
            newestMessageKey = fallbackMessageKey(incoming),
        )
    }

    private fun syncSelections(state: ChatState): ChatState {
        val replyId = state.replyToMessage?._id
        val syncedReply = replyId?.let { id ->
            state.messages.find { it._id == id }
        }?.takeIf { it.deletedAt == null }
        val activeActionExists = state.activeActionMessageId?.let { id ->
            state.messages.any { it._id == id }
        } == true
        val deleteTargetExists = state.confirmDeleteMessageId?.let { id ->
            state.messages.any { it._id == id }
        } == true
        return state.copy(
            replyToMessage = syncedReply,
            activeActionMessageId = if (activeActionExists) state.activeActionMessageId else null,
            confirmDeleteMessageId = if (deleteTargetExists) {
                state.confirmDeleteMessageId
            } else {
                null
            },
        )
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    override fun onCleared() {
        typingEmitJob?.cancel()
        typingPeerJobs.values.forEach { it.cancel() }
        typingPeerJobs.clear()
        repository.disconnectRealtime()
        super.onCleared()
    }
}

private data class MessageUpsertResult(
    val messages: List<ChatMessage>,
    val newestMessageKey: String?,
)

private fun fallbackMessageKey(message: ChatMessage): String {
    return message._id
        ?: "${message.senderId}_${message.createdAt}_${message.text.hashCode()}"
}
