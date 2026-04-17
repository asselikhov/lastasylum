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
        bootstrap()
    }

    private fun bootstrap() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isRoomsLoading = true, error = null)
            repository.listRooms()
                .onSuccess { rooms ->
                    if (rooms.isEmpty()) {
                        _state.value = ChatState(
                            isRoomsLoading = false,
                            currentUserId = currentUserId,
                            error = getApplication<Application>().getString(
                            com.lastasylum.alliance.R.string.chat_no_rooms,
                        ),
                        )
                        return@launch
                    }
                    val stored = chatRoomPreferences.getSelectedRoomId()
                    val selected = rooms.find { it.id == stored }?.id ?: rooms.minByOrNull { it.sortOrder }?.id
                        ?: rooms.first().id
                    chatRoomPreferences.setSelectedRoomId(selected)
                    openRoom(selected, rooms)
                }
                .onFailure { e ->
                    _state.value = ChatState(
                        isRoomsLoading = false,
                        error = e.toUserMessageRu(res),
                        currentUserId = currentUserId,
                    )
                }
        }
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
        )
        repository.loadRecentMessages(roomId)
            .onSuccess { loaded ->
                _state.value = _state.value.copy(
                    isLoading = false,
                    messages = loaded,
                    selectedRoomId = roomId,
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
