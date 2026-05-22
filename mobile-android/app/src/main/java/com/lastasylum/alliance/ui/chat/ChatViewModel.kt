package com.lastasylum.alliance.ui.chat

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lastasylum.alliance.data.effectiveUnreadCount
import com.lastasylum.alliance.data.isObjectIdNewer
import com.lastasylum.alliance.data.chat.ChatAllianceIds
import com.lastasylum.alliance.data.chat.ChatMessage
import com.lastasylum.alliance.data.chat.ChatMessageDeletedEvent
import com.lastasylum.alliance.data.chat.ChatReaction
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.chat.ChatRepository
import com.lastasylum.alliance.data.chat.ChatSessionCache
import com.lastasylum.alliance.data.chat.ChatRoomReadEvent
import com.lastasylum.alliance.data.chat.ChatTypingEvent
import com.lastasylum.alliance.data.chat.ChatRoomDto
import com.lastasylum.alliance.data.chat.ChatRaidRoomSync
import com.lastasylum.alliance.data.chat.ChatRoomPreferences
import com.lastasylum.alliance.data.users.UsersRepository
import com.lastasylum.alliance.ui.util.toUserMessageRu
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
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
private const val INCOMING_SOCKET_DEBOUNCE_MS = 75L

private data class RoomMessageCache(
    val messages: List<ChatMessage>,
    val hasMoreOlder: Boolean,
)

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
            isAppAdmin = isAppAdmin(currentUserRole),
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

    /** Isolated from [state] so mic animation does not recompose the message list. */
    private val _chatVoicePhase = MutableStateFlow(ChatVoicePhase.Idle)
    val chatVoicePhase: StateFlow<ChatVoicePhase> = _chatVoicePhase.asStateFlow()

    /** Isolated from [state] so read receipts do not recompose the whole chat screen. */
    private val _otherReadUptoMessageId = MutableStateFlow<String?>(null)
    val otherReadUptoMessageId: StateFlow<String?> = _otherReadUptoMessageId.asStateFlow()

    private val knownMessageIds = LinkedHashSet<String>()
  /** messageId → index in [ChatState.messages] (newest-first); cleared on room switch. */
    private val messageIdIndex = HashMap<String, Int>()
    private val typingPeerJobs = mutableMapOf<String, Job>()
    private val typingPeerJobsLock = Any()
    private var typingEmitJob: Job? = null

    private val incomingMessages = Channel<ChatMessage>(
        capacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val roomMessageCache = mutableMapOf<String, RoomMessageCache>()
    /** Latest message id we successfully marked read per room (avoids regress + duplicate bumps). */
    private val lastMarkedReadByRoom = mutableMapOf<String, String>()
    private var unreadSyncJob: Job? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private var chatVoiceRecognizer: ChatVoiceRecognizer? = null

    private val res get() = getApplication<Application>().resources

    init {
        hydrateReadCursorsFromPreferences()
        viewModelScope.launch(Dispatchers.Default) {
            val pending = ArrayList<ChatMessage>(16)
            var flushJob: Job? = null
            suspend fun flushPending() {
                if (pending.isEmpty()) return
                val batch = pending.toList()
                pending.clear()
                withContext(Dispatchers.Main) {
                    dispatchIncomingBatch(batch)
                }
            }
            for (message in incomingMessages) {
                pending.add(message)
                flushJob?.cancel()
                flushJob = launch {
                    delay(INCOMING_SOCKET_DEBOUNCE_MS)
                    flushPending()
                }
            }
        }
    }

    private fun dispatchIncomingBatch(batch: List<ChatMessage>) {
        for (message in batch) {
            val roomId = message.roomId
            if (roomId.isBlank()) continue
            if (_state.value.rooms.none { it.id == roomId }) continue
            val selected = _state.value.selectedRoomId
            if (roomId == selected) {
                applyIncomingMessage(message)
            } else if (message.senderId != currentUserId) {
                val mid = message._id ?: continue
                if (!shouldTrackUnreadForMessage(roomId, mid)) continue
                bumpRoomUnreadLocally(roomId)
                scheduleUnreadSyncFromServer()
            }
        }
    }

    private fun realtimeSubscriptionRoomIds(rooms: List<ChatRoomDto>): List<String> {
        val raid = chatRoomPreferences.getRaidRoomId()
        val selected = _state.value.selectedRoomId
        val hub = rooms.firstOrNull { isAllianceHubRoom(it) }?.id
        return buildList {
            if (!raid.isNullOrBlank()) add(raid)
            if (!hub.isNullOrBlank() && hub !in this) add(hub)
            if (!selected.isNullOrBlank() && selected !in this) add(selected)
            if (rooms.isNotEmpty() && isEmpty()) {
                rooms.firstOrNull { it.id == selected }?.id?.let { add(it) }
            }
        }
    }

    fun refreshChat() {
        viewModelScope.launch { bootstrap() }
    }

    /** Оверлей-чат: всегда открывать комнату альянса (sortOrder 1), не последнюю из приложения. */
    fun refreshChatForOverlay() {
        viewModelScope.launch { bootstrap(preferAllianceHubRoom = true) }
    }

    /** Refresh profile gate when returning from profile or opening chat. */
    fun refreshTeamProfileGate() {
        viewModelScope.launch {
            val hasTeam = loadTeamProfileGate()
            val roomsResult = repository.listRooms()
            _state.value = if (roomsResult.isSuccess) {
                val nextRooms = applyRoomsFromServer(
                    roomsResult.getOrElse { _state.value.rooms },
                )
                syncRaidRoomPreference(nextRooms)
                _state.value.copy(
                    hasTeamProfileForGlobalChat = hasTeam,
                    rooms = nextRooms,
                )
            } else {
                _state.value.copy(hasTeamProfileForGlobalChat = hasTeam)
            }
        }
    }

    /** Sync tab badges from API (e.g. after overlay chat or app resume). */
    fun syncRoomsFromServer() {
        viewModelScope.launch {
            repository.listRooms()
                .onSuccess { raw ->
                    val next = applyRoomsFromServer(raw)
                    syncRaidRoomPreference(next)
                    _state.update { it.copy(rooms = next) }
                    reconcileStaleServerUnread(next)
                    reconfirmReadForVisibleRoom()
                }
        }
    }

    /** Returning to the Chat tab: re-mark visible room and refresh badges without stale server counts. */
    fun onChatTabResumed() {
        viewModelScope.launch {
            val roomId = _state.value.selectedRoomId
            val newestId = _state.value.messages.firstOrNull()?._id
            if (!roomId.isNullOrBlank() && !newestId.isNullOrBlank()) {
                markRoomReadUpTo(roomId, newestId)
            }
            syncRoomsFromServer()
        }
    }

    /** Overlay fullscreen chat closed — reclaim socket + refresh server unread counts. */
    fun onOverlayChatPanelClosed() {
        viewModelScope.launch {
            syncRoomsFromServer()
            reconnectRealtimeIfNeeded()
        }
    }

    private fun isAllianceRaidRoom(room: ChatRoomDto): Boolean =
        room.sortOrder == 2 &&
            !room.allianceId.isNullOrBlank() &&
            room.allianceId.startsWith("pt:")

    private fun isAllianceHubRoom(room: ChatRoomDto): Boolean =
        room.sortOrder == 1 &&
            !room.allianceId.isNullOrBlank() &&
            room.allianceId != ChatAllianceIds.GLOBAL &&
            !ChatAllianceIds.isServerScope(room.allianceId)

    private fun sortChatRoomsForDisplay(rooms: List<ChatRoomDto>): List<ChatRoomDto> =
        rooms.sortedWith(
            compareBy<ChatRoomDto> { room ->
                when {
                    room.allianceId == ChatAllianceIds.GLOBAL -> 0
                    ChatAllianceIds.isServerScope(room.allianceId) -> 1
                    isAllianceHubRoom(room) -> 2
                    isAllianceRaidRoom(room) -> 3
                    else -> 4
                }
            }.thenBy { it.sortOrder }.thenBy { it.title },
        )

    private fun syncRaidRoomPreference(rooms: List<ChatRoomDto>) {
        repository.applyOverlayRoomsFromRooms(rooms)
    }

    /**
     * Только профиль/стикеры/гейт глобального чата — без [repository.listRooms].
     * Используется при входе на вкладку чата, чтобы не дублировать сетевую нагрузку с [bootstrap].
     */
    fun refreshTeamProfileGateLight() {
        viewModelScope.launch {
            val hasTeam = loadTeamProfileGate()
            _state.value = _state.value.copy(hasTeamProfileForGlobalChat = hasTeam)
        }
    }

    private suspend fun loadTeamProfileGate(): Boolean {
        val p = usersRepository.getMyProfile().getOrNull()
        val keys = p?.enabledStickerPacks?.toSet() ?: emptySet()
        _state.value = _state.value.copy(
            enabledStickerPackKeys = keys,
            playerTeamSquadRole = p?.playerTeamSquadRole,
        )
        return p?.let {
            !it.teamDisplayName.isNullOrBlank() && !it.teamTag.isNullOrBlank()
        } ?: false
    }

    private fun canModerateChat(message: ChatMessage): Boolean =
        canDeleteChatMessage(
            message = message,
            currentUserId = currentUserId,
            isAppAdmin = _state.value.isAppAdmin,
            playerTeamSquadRole = _state.value.playerTeamSquadRole,
        )

    private fun allianceHubRoomId(rooms: List<ChatRoomDto>): String? =
        rooms.firstOrNull { isAllianceHubRoom(it) }?.id

    private fun globalSendBlocked(
        roomId: String,
        messageText: String,
        replyToMessageId: String?,
    ): Boolean {
        val room = _state.value.rooms.find { it.id == roomId } ?: return false
        if (room.allianceId != ChatAllianceIds.GLOBAL ||
            _state.value.hasTeamProfileForGlobalChat
        ) {
            return false
        }
        _state.value = _state.value.copy(
            sendFailure = ChatSendFailure(
                messageText = messageText,
                replyToMessageId = replyToMessageId,
                errorMessage = res.getString(com.lastasylum.alliance.R.string.chat_global_team_required),
            ),
        )
        return true
    }

    private suspend fun bootstrap(preferAllianceHubRoom: Boolean = false) {
        _state.value = _state.value.copy(isRoomsLoading = true, error = null)
        val cachedRooms = if (preferAllianceHubRoom) ChatSessionCache.getFreshRooms() else null
        val roomsRaw = cachedRooms ?: repository.listRooms().getOrElse { e ->
            _draftMessage.value = ""
            _typingPeers.value = emptyMap()
            _state.value = ChatState(
                isRoomsLoading = false,
                error = e.toUserMessageRu(res),
                currentUserId = currentUserId,
                currentUserRole = currentUserRole,
                isAppAdmin = isAppAdmin(currentUserRole),
                hasTeamProfileForGlobalChat = false,
                enabledStickerPackKeys = emptySet(),
            )
            return
        }
        if (cachedRooms == null) {
            ChatSessionCache.update(roomsRaw)
        }
        val rooms = applyRoomsFromServer(roomsRaw)
        if (rooms.isEmpty()) {
            _draftMessage.value = ""
            _typingPeers.value = emptyMap()
            chatRoomPreferences.clearRaidRoomId()
            _state.value = ChatState(
                isRoomsLoading = false,
                currentUserId = currentUserId,
                currentUserRole = currentUserRole,
                isAppAdmin = isAppAdmin(currentUserRole),
                hasTeamProfileForGlobalChat = false,
                error = getApplication<Application>().getString(
                    com.lastasylum.alliance.R.string.chat_no_rooms,
                ),
                enabledStickerPackKeys = emptySet(),
            )
            return
        }
        syncRaidRoomPreference(rooms)
        val hubId = allianceHubRoomId(rooms)
        val stored = chatRoomPreferences.getSelectedRoomId()
        val selected = when {
            preferAllianceHubRoom && hubId != null -> hubId
            else ->
                rooms.find { it.id == stored }?.id
                    ?: hubId
                    ?: rooms.minByOrNull { it.sortOrder }?.id
                    ?: rooms.first().id
        }
        chatRoomPreferences.setSelectedRoomId(selected)
        viewModelScope.launch { reconcileStaleServerUnread(rooms) }
        val cachedOverlayMessages = if (preferAllianceHubRoom) {
            ChatSessionCache.getFreshMessages(selected)
        } else {
            null
        }
        if (!cachedOverlayMessages.isNullOrEmpty()) {
            val capped = capNewestFirst(cachedOverlayMessages, PAGE_SIZE)
            roomMessageCache[selected] = RoomMessageCache(
                messages = capped,
                hasMoreOlder = cachedOverlayMessages.size >= PAGE_SIZE,
            )
            openRoom(selected, rooms, hadCachedMessages = true)
        } else {
            openRoom(selected, rooms)
        }
    }

    fun selectRoom(roomId: String) {
        if (roomId == _state.value.selectedRoomId) return
        val previousRoomId = _state.value.selectedRoomId
        if (previousRoomId != null && _state.value.messages.isNotEmpty()) {
            roomMessageCache[previousRoomId] = RoomMessageCache(
                messages = _state.value.messages,
                hasMoreOlder = _state.value.hasMoreOlder,
            )
        }
        val previousNewestId = previousRoomId?.let { rid ->
            roomMessageCache[rid]?.messages?.firstOrNull()?._id
        }
        val cached = roomMessageCache[roomId]
        knownMessageIds.clear()
        messageIdIndex.clear()
        _draftMessage.value = ""
        _typingPeers.value = emptyMap()
        synchronized(typingPeerJobsLock) {
            typingPeerJobs.values.forEach { it.cancel() }
            typingPeerJobs.clear()
        }
        _state.value = _state.value.copy(
            selectedRoomId = roomId,
            messages = cached?.messages ?: emptyList(),
            isLoading = cached == null,
            hasMoreOlder = cached?.hasMoreOlder ?: true,
            isLoadingOlder = false,
            error = null,
            replyToMessage = null,
            rooms = clearUnreadForRoom(_state.value.rooms, roomId),
        )
        cached?.messages?.mapNotNull { it._id }?.let { knownMessageIds.addAll(it) }
        viewModelScope.launch {
            chatRoomPreferences.setSelectedRoomId(roomId)
            if (previousRoomId != null && !previousNewestId.isNullOrBlank()) {
                markRoomReadUpTo(previousRoomId, previousNewestId)
            }
            openRoom(roomId, _state.value.rooms, hadCachedMessages = cached != null)
        }
    }

    private fun clearUnreadForRoom(
        rooms: List<ChatRoomDto>,
        roomId: String,
    ): List<ChatRoomDto> =
        rooms.map { if (it.id == roomId) it.copy(unreadCount = 0) else it }

    private fun hydrateReadCursorsFromPreferences() {
        chatRoomPreferences.loadAllLastReadMessageIds().forEach { (roomId, messageId) ->
            mergeReadCursor(roomId, messageId)
        }
    }

    private fun hydrateReadCursorsFromRooms(rooms: List<ChatRoomDto>) {
        rooms.forEach { room ->
            val serverLast = room.lastReadMessageId?.trim().orEmpty()
            if (serverLast.isNotBlank()) {
                mergeReadCursor(room.id, serverLast)
            }
        }
    }

    private fun mergeReadCursor(roomId: String, messageId: String) {
        if (roomId.isBlank() || messageId.isBlank()) return
        val current = lastMarkedReadByRoom[roomId]
        if (current == null || isObjectIdNewer(messageId, current)) {
            lastMarkedReadByRoom[roomId] = messageId
            chatRoomPreferences.setLastReadMessageId(roomId, messageId)
        }
    }

    private fun applyRoomsFromServer(serverRooms: List<ChatRoomDto>): List<ChatRoomDto> {
        ChatSessionCache.update(serverRooms)
        val sorted = sortChatRoomsForDisplay(serverRooms)
        hydrateReadCursorsFromRooms(sorted)
        return mergeRoomsUnreadFromServer(sorted)
    }

    private fun mergeRoomsUnreadFromServer(serverRooms: List<ChatRoomDto>): List<ChatRoomDto> {
        val selected = _state.value.selectedRoomId
        val viewingSelected =
            !selected.isNullOrBlank() && _state.value.messages.isNotEmpty()
        return serverRooms.map { room ->
            val unread = when {
                viewingSelected && room.id == selected -> 0
                else -> effectiveUnreadForRoom(room)
            }
            room.copy(unreadCount = unread)
        }
    }

    private fun resolvedLastReadMessageId(room: ChatRoomDto): String? {
        val local = lastMarkedReadByRoom[room.id]?.trim().orEmpty()
        val server = room.lastReadMessageId?.trim().orEmpty()
        return when {
            local.isBlank() -> server.takeIf { it.isNotBlank() }
            server.isBlank() -> local
            isObjectIdNewer(local, server) -> local
            else -> server
        }
    }

    private fun effectiveUnreadForRoom(room: ChatRoomDto): Int =
        effectiveUnreadCount(
            serverUnread = room.unreadCount,
            lastReadMessageId = room.lastReadMessageId,
            localLastReadMessageId = resolvedLastReadMessageId(room),
        )

    private fun shouldTrackUnreadForMessage(roomId: String, messageId: String): Boolean {
        val lastRead = lastMarkedReadByRoom[roomId]
        if (lastRead != null && !isObjectIdNewer(messageId, lastRead)) {
            return false
        }
        return true
    }

    private fun bumpRoomUnreadLocally(roomId: String) {
        _state.update { st ->
            st.copy(
                rooms = st.rooms.map { room ->
                    if (room.id != roomId) room
                    else room.copy(
                        unreadCount = (effectiveUnreadForRoom(room) + 1).coerceAtMost(999),
                    )
                },
            )
        }
    }

    private fun scheduleUnreadSyncFromServer() {
        unreadSyncJob?.cancel()
        unreadSyncJob = viewModelScope.launch {
            delay(120)
            syncRoomsFromServer()
        }
    }

    private suspend fun reconfirmReadForVisibleRoom() {
        val roomId = _state.value.selectedRoomId ?: return
        val newestId = _state.value.messages.firstOrNull()?._id ?: return
        markRoomReadUpTo(roomId, newestId)
    }

    /** Server unread can lag; re-push persisted read cursor for rooms that still show a badge. */
    private suspend fun reconcileStaleServerUnread(rooms: List<ChatRoomDto>) {
        for (room in rooms) {
            if (effectiveUnreadForRoom(room) > 0) continue
            if (room.unreadCount <= 0) continue
            val localLast = lastMarkedReadByRoom[room.id]
                ?: room.lastReadMessageId?.trim().orEmpty().takeIf { it.isNotBlank() }
                ?: continue
            markRoomReadUpTo(room.id, localLast, forceSync = true)
        }
    }

    private suspend fun markRoomReadUpTo(
        roomId: String,
        messageId: String,
        forceSync: Boolean = false,
    ) {
        if (roomId.isBlank() || messageId.isBlank()) return
        val prev = lastMarkedReadByRoom[roomId]
        if (!forceSync && prev != null && !isObjectIdNewer(messageId, prev)) return
        mergeReadCursor(roomId, messageId)
        _state.update { st ->
            st.copy(rooms = clearUnreadForRoom(st.rooms, roomId))
        }
        repository.markRoomRead(roomId, messageId)
            .onSuccess {
                mergeReadCursor(roomId, messageId)
                _state.update { st ->
                    st.copy(rooms = clearUnreadForRoom(st.rooms, roomId))
                }
            }
    }

    private fun reconnectRealtimeIfNeeded() {
        val rooms = _state.value.rooms
        val roomIds = realtimeSubscriptionRoomIds(rooms)
        if (roomIds.isEmpty()) return
        repository.connectRealtimeRooms(
            roomIds = roomIds,
            onMessage = ::onIncomingMessage,
            onDeleteMessage = ::onDeletedMessage,
            onTyping = ::onTypingFromPeer,
            onRead = ::onRoomReadEvent,
        )
    }

    private suspend fun openRoom(
        roomId: String,
        rooms: List<ChatRoomDto>,
        hadCachedMessages: Boolean = false,
    ) {
        typingEmitJob?.cancel()
        typingEmitJob = null
        synchronized(typingPeerJobsLock) {
            typingPeerJobs.values.forEach { it.cancel() }
            typingPeerJobs.clear()
        }
        knownMessageIds.clear()
        messageIdIndex.clear()
        _draftMessage.value = ""
        _typingPeers.value = emptyMap()
        val hasTeam = loadTeamProfileGate()
        _state.update { st ->
            st.copy(rooms = clearUnreadForRoom(st.rooms.ifEmpty { rooms }, roomId))
        }
        if (!hadCachedMessages) {
            _state.value = _state.value.copy(
                isLoading = true,
                isRoomsLoading = false,
                rooms = clearUnreadForRoom(rooms, roomId),
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
            _otherReadUptoMessageId.value = null
        } else {
            _otherReadUptoMessageId.value = null
            _state.value = _state.value.copy(
                isRoomsLoading = false,
                rooms = clearUnreadForRoom(rooms, roomId),
                selectedRoomId = roomId,
                hasTeamProfileForGlobalChat = hasTeam,
            )
        }
        repository.connectRealtimeRooms(
            roomIds = realtimeSubscriptionRoomIds(rooms),
            onMessage = ::onIncomingMessage,
            onDeleteMessage = ::onDeletedMessage,
            onTyping = ::onTypingFromPeer,
            onRead = ::onRoomReadEvent,
        )
        repository.loadRecentMessages(roomId, beforeMessageId = null, limit = PAGE_SIZE)
            .onSuccess { loaded ->
                ChatSessionCache.updateMessages(roomId, loaded)
                knownMessageIds.clear()
                messageIdIndex.clear()
                knownMessageIds.addAll(loaded.mapNotNull { it._id })
                val capped = capNewestFirst(loaded, CHAT_MAX_MESSAGES_IN_MEMORY)
                rebuildMessageIdIndex(capped, messageIdIndex)
                roomMessageCache[roomId] = RoomMessageCache(
                    messages = capped,
                    hasMoreOlder = loaded.size >= PAGE_SIZE,
                )
                _state.value = _state.value.copy(
                    isLoading = false,
                    messages = capped,
                    selectedRoomId = roomId,
                    hasMoreOlder = loaded.size >= PAGE_SIZE,
                    rooms = clearUnreadForRoom(_state.value.rooms, roomId),
                )
                capped.firstOrNull()?._id?.let { newestId ->
                    markRoomReadUpTo(roomId, newestId)
                }
            }
            .onFailure { e ->
                if (!hadCachedMessages) {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = e.toUserMessageRu(res),
                    )
                }
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
        if (globalSendBlocked(roomId, trimmed, replyToMessageId)) return
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
        if (globalSendBlocked(roomId, text, replyToMessageId)) return

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
                    text = text.trim(),
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

    /**
     * @param append false — заменить текущий выбор (галерея / системный пикер);
     * true — добавить к уже прикреплённым (повторный «+» в композере).
     */
    fun onImagesPicked(uris: List<Uri>, append: Boolean = false) {
        if (uris.isEmpty()) return
        val distinct = uris.distinctBy { it.toString() }
        val next = if (append) {
            (_pickedImageUris.value + distinct).distinctBy { it.toString() }
        } else {
            distinct
        }
        _pickedImageUris.value = next.take(12)
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
        val previousMessages = _state.value.messages
        val optimistic = applyOptimisticReactionToggle(
            messages = previousMessages,
            messageId = messageId,
            emoji = emoji,
        )
        if (optimistic !== previousMessages) {
            _state.value = _state.value.copy(messages = optimistic)
        }
        viewModelScope.launch {
            repository.toggleReaction(messageId, emoji)
                .onSuccess { updated ->
                    applyIncomingMessage(updated)
                    if (_state.value.activeActionMessageId == messageId) {
                        _state.value = _state.value.copy(activeActionMessageId = null)
                    }
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        messages = previousMessages,
                        error = e.toUserMessageRu(res),
                    )
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
                .onFailure { e ->
                    _state.value = _state.value.copy(error = e.toUserMessageRu(res))
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
                .onFailure { e ->
                    _state.value = _state.value.copy(error = e.toUserMessageRu(res))
                }
        }
    }

    fun beginMessageSelection(messageId: String) {
        val target = _state.value.messages.find { it._id == messageId } ?: return
        if (target.deletedAt != null) return
        if (!canModerateChat(target)) return
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
        if (!canModerateChat(target)) return
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

    private fun onRoomReadEvent(event: ChatRoomReadEvent) {
        if (event.userId.isBlank() || event.messageId.isBlank()) return
        if (event.userId == currentUserId) {
            if (event.roomId.isNotBlank()) {
                mergeReadCursor(event.roomId, event.messageId)
                _state.update { st ->
                    st.copy(rooms = clearUnreadForRoom(st.rooms, event.roomId))
                }
            }
            return
        }
        val roomId = _state.value.selectedRoomId
        if (!roomId.isNullOrBlank() && event.roomId.isNotBlank() && event.roomId != roomId) return
        _otherReadUptoMessageId.update { cur ->
            if (cur == null || cur < event.messageId) event.messageId else cur
        }
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
        val update = upsertMessage(
            _state.value.messages,
            message,
            knownMessageIds,
            messageIdIndex,
        )
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
        val rid = _state.value.selectedRoomId
        if (!rid.isNullOrBlank()) {
            nextState.messages.firstOrNull()?._id?.let { newestId ->
                viewModelScope.launch { markRoomReadUpTo(rid, newestId) }
            }
        }
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
            setPhase = { phase -> _chatVoicePhase.value = phase },
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

/** Telegram-like instant feedback before REST round-trip. */
private fun applyOptimisticReactionToggle(
    messages: List<ChatMessage>,
    messageId: String,
    emoji: String,
): List<ChatMessage> {
    val index = messages.indexOfFirst { it._id == messageId }
    if (index < 0) return messages
    val message = messages[index]
    val reactions = message.reactions.toMutableList()
    val at = reactions.indexOfFirst { it.emoji == emoji }
    if (at >= 0) {
        val row = reactions[at]
        if (row.reactedByMe) {
            val nextCount = row.count - 1
            if (nextCount <= 0) {
                reactions.removeAt(at)
            } else {
                reactions[at] = row.copy(count = nextCount, reactedByMe = false)
            }
        } else {
            reactions[at] = row.copy(count = row.count + 1, reactedByMe = true)
        }
    } else {
        reactions.add(
            ChatReaction(
                emoji = emoji,
                count = 1,
                reactedByMe = true,
            ),
        )
    }
    if (reactions == message.reactions) return messages
    val updated = messages.toMutableList()
    updated[index] = message.copy(reactions = reactions)
    return updated
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
