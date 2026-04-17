package com.lastasylum.alliance.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lastasylum.alliance.data.chat.ChatMessage
import com.lastasylum.alliance.data.chat.ChatRepository
import com.lastasylum.alliance.ui.util.toUserMessageRu
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatViewModel(
    application: Application,
    private val repository: ChatRepository,
) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(ChatState(isLoading = true))
    val state: StateFlow<ChatState> = _state.asStateFlow()

    private val res get() = getApplication<Application>().resources

    init {
        loadInitial()
        repository.connectRealtime(::onIncomingMessage)
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            repository.sendMessage(text)
                .onSuccess { sent ->
                    _state.value = _state.value.copy(
                        messages = prependIfMissing(_state.value.messages, sent),
                    )
                }
                .onFailure { throwable ->
                    _state.value = _state.value.copy(
                        error = throwable.toUserMessageRu(res),
                    )
                }
        }
    }

    private fun loadInitial() {
        viewModelScope.launch {
            repository.loadRecentMessages()
                .onSuccess { loaded ->
                    _state.value = ChatState(
                        isLoading = false,
                        messages = loaded,
                    )
                }
                .onFailure { throwable ->
                    _state.value = ChatState(
                        isLoading = false,
                        error = throwable.toUserMessageRu(res),
                    )
                }
        }
    }

    private fun onIncomingMessage(message: ChatMessage) {
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

    override fun onCleared() {
        repository.disconnectRealtime()
        super.onCleared()
    }
}
