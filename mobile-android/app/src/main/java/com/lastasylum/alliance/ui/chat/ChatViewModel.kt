package com.lastasylum.alliance.ui.chat

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lastasylum.alliance.data.chat.ChatAllianceIds
import com.lastasylum.alliance.data.chat.ChatMessage
import com.lastasylum.alliance.data.chat.ChatMessageDeletedEvent
import com.lastasylum.alliance.data.chat.ChatRepository
import com.lastasylum.alliance.data.chat.ChatTypingEvent
import com.lastasylum.alliance.data.chat.ChatRoomDto
import com.lastasylum.alliance.data.chat.ChatRoomPreferences
import com.lastasylum.alliance.data.users.UsersRepository
import com.lastasylum.alliance.ui.util.toUserMessageRu
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.widget.Toast
import java.io.File
import java.io.InputStream
import java.util.Locale
import java.util.UUID

private const val PAGE_SIZE = 30

class ChatViewModel(
    application: Application,
    private val repository: ChatRepository,
    private val chatRoomPreferences: ChatRoomPreferences,
    private val usersRepository: UsersRepository,
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

    /** Picked images for composer; uploaded then referenced as attachment ids on send. */
    private val _pickedImageUris = MutableStateFlow<List<Uri>>(emptyList())
    val pickedImageUris: StateFlow<List<Uri>> = _pickedImageUris.asStateFlow()

    /** Isolated from [state] so typing socket churn does not recompose the message list. */
    private val _typingPeers = MutableStateFlow<Map<String, String>>(emptyMap())
    val typingPeers: StateFlow<Map<String, String>> = _typingPeers.asStateFlow()

    private val knownMessageIds = LinkedHashSet<String>()
    private val typingPeerJobs = mutableMapOf<String, Job>()
    private val typingPeerJobsLock = Any()
    private var typingEmitJob: Job? = null

    private val incomingMessages = Channel<ChatMessage>(capacity = Channel.UNLIMITED)

    private val mainHandler = Handler(Looper.getMainLooper())
    private var chatVoiceRecognizer: ChatVoiceRecognizer? = null

    private val res get() = getApplication<Application>().resources

    init {
        viewModelScope.launch {
            for (message in incomingMessages) {
                val roomId = _state.value.selectedRoomId ?: continue
                if (message.roomId.isNotBlank() && message.roomId != roomId) continue
                applyIncomingMessage(message)
            }
        }
    }

    fun refreshChat() {
        viewModelScope.launch { bootstrap() }
    }

    /** Refresh profile gate when returning from profile or opening chat. */
    fun refreshTeamProfileGate() {
        viewModelScope.launch {
            val hasTeam = loadTeamProfileGate()
            val roomsResult = repository.listRooms()
            _state.value = if (roomsResult.isSuccess) {
                _state.value.copy(
                    hasTeamProfileForGlobalChat = hasTeam,
                    rooms = roomsResult.getOrElse { _state.value.rooms },
                )
            } else {
                _state.value.copy(hasTeamProfileForGlobalChat = hasTeam)
            }
        }
    }

    private suspend fun loadTeamProfileGate(): Boolean {
        return usersRepository.getMyProfile().getOrNull()?.let { p ->
            !p.teamDisplayName.isNullOrBlank() && !p.teamTag.isNullOrBlank()
        } ?: false
    }

    private suspend fun bootstrap() {
        _state.value = _state.value.copy(isRoomsLoading = true, error = null)
        val rooms = repository.listRooms().getOrElse { e ->
            _draftMessage.value = ""
            _typingPeers.value = emptyMap()
            _state.value = ChatState(
                isRoomsLoading = false,
                error = e.toUserMessageRu(res),
                currentUserId = currentUserId,
                currentUserRole = currentUserRole,
                hasTeamProfileForGlobalChat = false,
            )
            return
        }
        if (rooms.isEmpty()) {
            _draftMessage.value = ""
            _typingPeers.value = emptyMap()
            _state.value = ChatState(
                isRoomsLoading = false,
                currentUserId = currentUserId,
                currentUserRole = currentUserRole,
                hasTeamProfileForGlobalChat = false,
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
        synchronized(typingPeerJobsLock) {
            typingPeerJobs.values.forEach { it.cancel() }
            typingPeerJobs.clear()
        }
        knownMessageIds.clear()
        _draftMessage.value = ""
        _typingPeers.value = emptyMap()
        val hasTeam = loadTeamProfileGate()
        _state.value = _state.value.copy(
            isLoading = true,
            isRoomsLoading = false,
            rooms = rooms,
            selectedRoomId = roomId,
            hasTeamProfileForGlobalChat = hasTeam,
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
            selectedMessageIds = emptySet(),
            confirmBulkDelete = false,
            isDeletingSelection = false,
            deletingMessageId = null,
            newestMessageKey = null,
            scrollToLatestNonce = 0L,
            sendFailure = null,
        )
        repository.loadRecentMessages(roomId, beforeMessageId = null, limit = PAGE_SIZE)
            .onSuccess { loaded ->
                knownMessageIds.clear()
                knownMessageIds.addAll(loaded.mapNotNull { it._id })
                val capped = capNewestFirst(loaded, CHAT_MAX_MESSAGES_IN_MEMORY)
                _state.value = _state.value.copy(
                    isLoading = false,
                    messages = capped,
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
                    val merged = mergeOlderPage(_state.value.messages, older, knownMessageIds)
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
        val room = _state.value.rooms.find { it.id == roomId }
        if (room?.allianceId == ChatAllianceIds.GLOBAL &&
            !_state.value.hasTeamProfileForGlobalChat
        ) {
            _state.value = _state.value.copy(
                sendFailure = ChatSendFailure(
                    messageText = trimmed,
                    replyToMessageId = replyToMessageId,
                    errorMessage = res.getString(com.lastasylum.alliance.R.string.chat_global_team_required),
                ),
            )
            return
        }
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
        val text = _draftMessage.value.trim()
        if (text.isBlank() && _pickedImageUris.value.isEmpty()) return
        val roomId = _state.value.selectedRoomId ?: return
        val replyToMessageId = _state.value.replyToMessage?._id
        val uris = _pickedImageUris.value

        viewModelScope.launch {
            _state.value = _state.value.copy(isSending = true, error = null, sendFailure = null)
            val uploadedIds = ArrayList<String>(uris.size)
            try {
                for (uri in uris) {
                    val uploadedId = uploadOneImage(roomId, uri).getOrElse { t ->
                        _state.value = _state.value.copy(
                            isSending = false,
                            sendFailure = ChatSendFailure(
                                messageText = text,
                                replyToMessageId = replyToMessageId,
                                errorMessage = t.toUserMessageRu(res),
                            ),
                        )
                        return@launch
                    }
                    uploadedIds.add(uploadedId)
                }
                repository.sendMessageWithRetries(
                    text = text.ifBlank { " " },
                    roomId = roomId,
                    replyToMessageId = replyToMessageId,
                    attachments = uploadedIds.takeIf { it.isNotEmpty() },
                )
                    .onSuccess { sent ->
                        applyIncomingMessage(sent, clearComposer = true)
                    }
                    .onFailure { throwable ->
                        _state.value = _state.value.copy(
                            isSending = false,
                            sendFailure = ChatSendFailure(
                                messageText = text,
                                replyToMessageId = replyToMessageId,
                                errorMessage = throwable.toUserMessageRu(res),
                            ),
                        )
                    }
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    isSending = false,
                    sendFailure = ChatSendFailure(
                        messageText = text,
                        replyToMessageId = replyToMessageId,
                        errorMessage = t.toUserMessageRu(res),
                    ),
                )
            }
        }
    }

    private suspend fun uploadOneImage(roomId: String, uri: Uri): Result<String> =
        withContext(Dispatchers.IO) {
            val ctx = getApplication<Application>()
            val cr = ctx.contentResolver
            val tmp = File.createTempFile("chat_upload_${UUID.randomUUID()}", ".part", ctx.cacheDir)
            try {
                val input = openUriInputStream(cr, uri)
                    ?: return@withContext Result.failure(
                        IllegalStateException(
                            ctx.getString(com.lastasylum.alliance.R.string.chat_attachment_read_failed),
                        ),
                    )
                input.use { inp ->
                    tmp.outputStream().use { out -> inp.copyTo(out) }
                }
                if (tmp.length() == 0L) {
                    return@withContext Result.failure(
                        IllegalStateException(
                            ctx.getString(com.lastasylum.alliance.R.string.chat_attachment_prepare_failed),
                        ),
                    )
                }
                val header = ByteArray(32)
                tmp.inputStream().use { it.read(header) }
                val sniffed = sniffImageMimeFromHeader(header)
                val declared = cr.getType(uri)?.trim().orEmpty()
                val mime = resolveUploadImageMime(declared, sniffed)
                    ?: return@withContext Result.failure(
                        IllegalStateException(
                            ctx.getString(com.lastasylum.alliance.R.string.chat_attachment_unsupported),
                        ),
                    )
                repository.uploadImageFile(roomId, tmp, mime).map { it.fileId }
            } finally {
                runCatching { tmp.delete() }
            }
        }

    private fun openUriInputStream(cr: ContentResolver, uri: Uri): InputStream? {
        runCatching { cr.openInputStream(uri) }.getOrNull()?.let { return it }
        val pfd = runCatching { cr.openFileDescriptor(uri, "r") }.getOrNull()
        if (pfd != null) {
            runCatching { return ParcelFileDescriptor.AutoCloseInputStream(pfd) }
            runCatching { pfd.close() }
        }
        val afd = runCatching { cr.openAssetFileDescriptor(uri, "r") }.getOrNull()
        if (afd != null) {
            runCatching { return afd.createInputStream() }
            runCatching { afd.close() }
        }
        return null
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

    fun onImagesPicked(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val current = _pickedImageUris.value
        val merged = (current + uris).distinct()
        _pickedImageUris.value = merged.take(12)
    }

    fun removePickedImage(uri: Uri) {
        val next = _pickedImageUris.value.filterNot { it == uri }
        if (next.size == _pickedImageUris.value.size) return
        _pickedImageUris.value = next
    }

    fun clearPickedImages() {
        if (_pickedImageUris.value.isEmpty()) return
        _pickedImageUris.value = emptyList()
    }

    private fun scheduleTypingEmit() {
        typingEmitJob?.cancel()
        val roomId = _state.value.selectedRoomId ?: return
        val room = _state.value.rooms.find { it.id == roomId }
        if (room?.allianceId == ChatAllianceIds.GLOBAL &&
            !_state.value.hasTeamProfileForGlobalChat
        ) {
            return
        }
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
            selectedMessageIds = emptySet(),
            confirmBulkDelete = false,
        )
    }

    fun clearReplyToMessage() {
        if (_state.value.replyToMessage == null) return
        _state.value = _state.value.copy(replyToMessage = null)
    }

    fun openMessageActions(messageId: String) {
        _state.value = _state.value.copy(
            activeActionMessageId = messageId,
            selectedMessageIds = emptySet(),
            confirmBulkDelete = false,
        )
    }

    fun dismissMessageActions() {
        if (_state.value.activeActionMessageId == null) return
        _state.value = _state.value.copy(activeActionMessageId = null)
    }

    fun requestDeleteMessage(messageId: String) {
        _state.value = _state.value.copy(
            activeActionMessageId = null,
            confirmDeleteMessageId = messageId,
            selectedMessageIds = emptySet(),
            confirmBulkDelete = false,
        )
    }

    fun toggleReaction(messageId: String, emoji: String) {
        if (messageId.isBlank() || emoji.isBlank()) return
        viewModelScope.launch {
            repository.toggleReaction(messageId, emoji)
                .onSuccess { updated ->
                    applyIncomingMessage(updated)
                }
        }
    }

    fun editMessage(messageId: String, newText: String) {
        if (messageId.isBlank()) return
        val trimmed = newText.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            repository.editMessage(messageId, trimmed)
                .onSuccess { updated ->
                    applyIncomingMessage(updated)
                }
        }
    }

    fun forwardMessage(messageId: String) {
        if (messageId.isBlank()) return
        val roomId = _state.value.selectedRoomId ?: return
        viewModelScope.launch {
            repository.forwardMessage(messageId, roomId)
                .onSuccess { forwarded ->
                    applyIncomingMessage(forwarded)
                }
        }
    }

    fun beginMessageSelection(messageId: String) {
        val target = _state.value.messages.find { it._id == messageId } ?: return
        if (target.deletedAt != null) return
        if (!canDeleteChatMessage(target, currentUserId, currentUserRole)) return
        _state.value = _state.value.copy(
            selectedMessageIds = setOf(messageId),
            activeActionMessageId = null,
            confirmDeleteMessageId = null,
            confirmBulkDelete = false,
        )
    }

    fun toggleMessageSelection(messageId: String) {
        val target = _state.value.messages.find { it._id == messageId } ?: return
        if (target.deletedAt != null) return
        if (!canDeleteChatMessage(target, currentUserId, currentUserRole)) return
        val cur = _state.value.selectedMessageIds
        if (cur.isEmpty()) return
        val next = if (messageId in cur) cur - messageId else cur + messageId
        _state.value = _state.value.copy(selectedMessageIds = next)
    }

    fun clearMessageSelection() {
        if (_state.value.selectedMessageIds.isEmpty() &&
            !_state.value.confirmBulkDelete &&
            !_state.value.isDeletingSelection
        ) {
            return
        }
        _state.value = _state.value.copy(
            selectedMessageIds = emptySet(),
            confirmBulkDelete = false,
        )
    }

    fun requestBulkDelete() {
        if (_state.value.selectedMessageIds.isEmpty()) return
        _state.value = _state.value.copy(confirmBulkDelete = true)
    }

    fun dismissBulkDeleteConfirm() {
        if (!_state.value.confirmBulkDelete) return
        _state.value = _state.value.copy(confirmBulkDelete = false)
    }

    fun confirmDeleteSelectedMessages() {
        val ids = _state.value.selectedMessageIds.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            _state.value = _state.value.copy(
                confirmBulkDelete = false,
                isDeletingSelection = true,
                error = null,
            )
            var lastFailure: Throwable? = null
            for (id in ids) {
                repository.deleteMessage(id)
                    .onSuccess { result ->
                        val messageId = result.messageId
                        _state.value = syncSelections(
                            scrubRemovedMessage(_state.value, messageId).copy(
                                isDeletingSelection = true,
                            ),
                        )
                    }
                    .onFailure { t ->
                        lastFailure = t
                    }
                if (lastFailure != null) break
            }
            _state.value = _state.value.copy(
                isDeletingSelection = false,
                error = lastFailure?.toUserMessageRu(res),
            )
        }
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
        incomingMessages.trySend(message).isSuccess
    }

    private fun onTypingFromPeer(event: ChatTypingEvent) {
        viewModelScope.launch {
            if (event.userId.isBlank() || event.userId == currentUserId) return@launch
            val roomId = _state.value.selectedRoomId ?: return@launch
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
        val nextMessages = scrubMessagesAfterRemove(state.messages, removedId, knownMessageIds)
        return state.copy(messages = nextMessages)
    }

    private fun applyIncomingMessage(
        message: ChatMessage,
        clearComposer: Boolean = false,
    ) {
        val update = upsertMessage(_state.value.messages, message, knownMessageIds)
        val cappedMessages = capNewestFirst(update.messages, CHAT_MAX_MESSAGES_IN_MEMORY)
        var nextState = _state.value.copy(
            messages = cappedMessages,
            newestMessageKey = update.newestMessageKey ?: _state.value.newestMessageKey,
            isSending = false,
            deletingMessageId = if (_state.value.deletingMessageId == message._id) null
            else _state.value.deletingMessageId,
            error = null,
        )
        if (clearComposer) {
            _draftMessage.value = ""
            _pickedImageUris.value = emptyList()
            nextState = nextState.copy(
                replyToMessage = null,
                scrollToLatestNonce = nextState.scrollToLatestNonce + 1L,
                sendFailure = null,
            )
        }
        _state.value = syncSelections(nextState)
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    private fun ensureChatVoiceRecognizer(): ChatVoiceRecognizer {
        chatVoiceRecognizer?.let { return it }
        val app = getApplication<Application>()
        val r = ChatVoiceRecognizer(
            context = app,
            mainHandler = mainHandler,
            scope = viewModelScope,
            setPhase = { phase -> _state.update { it.copy(chatVoicePhase = phase) } },
            onRecognizedText = { text -> sendMessage(text) },
            onNotify = { msg ->
                mainHandler.post {
                    Toast.makeText(app, msg, Toast.LENGTH_SHORT).show()
                }
            },
        )
        r.initIfAvailable()
        chatVoiceRecognizer = r
        return r
    }

    fun startChatVoiceInput() {
        ensureChatVoiceRecognizer().startRecording()
    }

    fun stopChatVoiceInput() {
        chatVoiceRecognizer?.stopRecording()
    }

    override fun onCleared() {
        chatVoiceRecognizer?.destroy()
        chatVoiceRecognizer = null
        typingEmitJob?.cancel()
        synchronized(typingPeerJobsLock) {
            typingPeerJobs.values.forEach { it.cancel() }
            typingPeerJobs.clear()
        }
        incomingMessages.close()
        repository.disconnectRealtime()
        super.onCleared()
    }
}

private fun ByteArray.hasPrefix(prefix: ByteArray): Boolean {
    if (size < prefix.size) return false
    for (i in prefix.indices) {
        if (this[i] != prefix[i]) return false
    }
    return true
}

private fun sniffImageMimeFromHeader(header: ByteArray): String? {
    if (header.size < 12) return null
    val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
    if (header.hasPrefix(jpeg)) return "image/jpeg"
    val png = byteArrayOf(
        0x89.toByte(),
        0x50,
        0x4E,
        0x47,
        0x0D,
        0x0A,
        0x1A,
        0x0A,
    )
    if (header.hasPrefix(png)) return "image/png"
    if (header.size >= 6) {
        val gif = String(header, 0, 6, Charsets.US_ASCII)
        if (gif == "GIF87a" || gif == "GIF89a") return "image/gif"
    }
    val riff = String(header, 0, 4, Charsets.US_ASCII)
    val webp = String(header, 8, 4, Charsets.US_ASCII)
    if (riff == "RIFF" && webp == "WEBP") return "image/webp"
    if (header.size >= 2 && header[0] == 0x42.toByte() && header[1] == 0x4D.toByte()) return "image/bmp"
    if (header.size >= 12 &&
        header[4] == 'f'.code.toByte() &&
        header[5] == 't'.code.toByte() &&
        header[6] == 'y'.code.toByte() &&
        header[7] == 'p'.code.toByte()
    ) {
        val brand = String(header, 8, 4, Charsets.US_ASCII)
        if (brand.equals("heic", ignoreCase = true) ||
            brand.equals("heix", ignoreCase = true) ||
            brand.equals("mif1", ignoreCase = true) ||
            brand.equals("msf1", ignoreCase = true)
        ) {
            return "image/heic"
        }
    }
    return null
}

private fun resolveUploadImageMime(declared: String, sniffed: String?): String? {
    val d = declared.trim()
    val dl = d.lowercase(Locale.ROOT)
    return when {
        dl.startsWith("image/") && dl != "image/*" -> d
        dl == "image/*" -> sniffed ?: "image/jpeg"
        sniffed != null -> sniffed
        else -> null
    }
}
