package com.lastasylum.alliance.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lastasylum.alliance.data.chat.ChatMessage
import com.lastasylum.alliance.data.chat.ChatRepository
import com.lastasylum.alliance.data.chat.ChatRoomDto
import com.lastasylum.alliance.data.chat.ChatRoomPreferences
import com.lastasylum.alliance.ui.util.toUserMessageRu
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
) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(ChatState(currentUserId = currentUserId))
    val state: StateFlow<ChatState> = _state.asStateFlow()

    private val res get() = getApplication<Application>().resources

    init {
        viewModelScope.launch {
            repository.realtimeConnectionState.collect { s ->
                _state.value = _state.value.copy(connectionState = s)
            }
        }
    }

    fun refreshChat() {
        viewModelScope.launch { bootstrap() }
    }

    private suspend fun bootstrap() {
        _state.value = _state.value.copy(isRoomsLoading = true, error = null)
        val rooms = repository.listRooms().getOrElse { e ->
            _state.value = ChatState(
                isRoomsLoading = false,
                error = e.toUserMessageRu(res),
                currentUserId = currentUserId,
            )
            return
        }
        if (rooms.isEmpty()) {
            _state.value = ChatState(
                isRoomsLoading = false,
                currentUserId = currentUserId,
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
        _state.value = _state.value.copy(
            isLoading = true,
            isRoomsLoading = false,
            rooms = rooms,
            selectedRoomId = roomId,
            error = null,
            messages = emptyList(),
            currentUserId = currentUserId,
            hasMoreOlder = true,
            isLoadingOlder = false,
        )
        repository.loadRecentMessages(roomId, beforeMessageId = null, limit = PAGE_SIZE)
            .onSuccess { loaded ->
                _state.value = _state.value.copy(
                    isLoading = false,
                    messages = loaded,
                    selectedRoomId = roomId,
                    hasMoreOlder = loaded.size >= PAGE_SIZE,
                )
                repository.connectRealtime(roomId, ::onIncomingMessage)
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
                    val existingIds = _state.value.messages.mapNotNull { it._id }.toSet()
                    val merged = _state.value.messages + older.filter { msg ->
                        msg._id == null || msg._id !in existingIds
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

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        val roomId = _state.value.selectedRoomId ?: return
        viewModelScope.launch {
            repository.sendMessage(text, roomId)
                .onSuccess { sent ->
                    _state.value = _state.value.copy(
                        messages = prependIfMissing(_state.value.messages, sent),
                        error = null,
                    )
                }
                .onFailure { throwable ->
                    _state.value = _state.value.copy(
                        error = throwable.toUserMessageRu(res),
                    )
                }
        }
    }

    private fun onIncomingMessage(message: ChatMessage) {
        val roomId = _state.value.selectedRoomId ?: return
        if (message.roomId.isNotBlank() && message.roomId != roomId) return
        _state.value = _state.value.copy(
            messages = prependIfMissing(_state.value.messages, message),
        )
    }

    private fun prependIfMissing(
        current: List<ChatMessage>,
        incoming: ChatMessage,
    ): List<ChatMessage> {
        if (incoming._id != null && current.any { it._id == incoming._id }) {
            return current
        }
        return listOf(incoming) + current
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    override fun onCleared() {
        repository.disconnectRealtime()
        super.onCleared()
    }
}
