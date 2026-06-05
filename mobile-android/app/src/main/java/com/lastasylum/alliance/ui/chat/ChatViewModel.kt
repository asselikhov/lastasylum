package com.lastasylum.alliance.ui.chat

import android.app.ActivityManager
import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.util.Log
import com.lastasylum.alliance.BuildConfig
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lastasylum.alliance.data.displayedUnreadCount
import com.lastasylum.alliance.data.effectiveUnreadCount
import com.lastasylum.alliance.data.isObjectIdNewer
import com.lastasylum.alliance.data.chat.ChatHistoryWipe
import com.lastasylum.alliance.data.chat.ChatAllianceIds
import com.lastasylum.alliance.data.chat.ChatMessage
import com.lastasylum.alliance.data.chat.ChatMessageDeletedEvent
import com.lastasylum.alliance.data.chat.ChatReaction
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.cache.LaunchDiskCache
import com.lastasylum.alliance.data.chat.ChatConnectionState
import com.lastasylum.alliance.data.chat.ChatRepository
import com.lastasylum.alliance.data.chat.ChatSessionCache
import com.lastasylum.alliance.data.chat.ChatRoomReadEvent
import com.lastasylum.alliance.data.chat.ChatRoomPinChangedEvent
import com.lastasylum.alliance.data.chat.ChatRoomUnreadEvent
import com.lastasylum.alliance.data.chat.ChatTypingEvent
import com.lastasylum.alliance.data.chat.ChatRoomDto
import com.lastasylum.alliance.data.chat.ChatRaidRoomSync
import com.lastasylum.alliance.data.chat.ChatRoomKind
import com.lastasylum.alliance.data.chat.ChatRoomKindResolver
import com.lastasylum.alliance.data.chat.stickers.StickerPacks
import com.lastasylum.alliance.overlay.CombatOverlayService
import com.lastasylum.alliance.overlay.OverlayRaidChatForwardPolicy
import com.lastasylum.alliance.data.chat.ChatUnreadCounts
import com.lastasylum.alliance.data.chat.ChatRoomPreferences
import com.lastasylum.alliance.data.chat.PinHistoryPreferences
import com.lastasylum.alliance.data.chat.isCompactReactionSocketUpdate
import com.lastasylum.alliance.data.chat.mergeIncomingChatUpdate
import com.lastasylum.alliance.data.chat.mergePreservingAttachments
import com.lastasylum.alliance.data.users.UsersRepository
import com.lastasylum.alliance.ui.util.toUserMessageRu
import retrofit2.HttpException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import com.lastasylum.alliance.ui.OVERLAY_PANEL_LOAD_MAX_MS
import android.widget.Toast
import java.io.File
import java.io.InputStream
import java.util.Locale
import java.util.UUID

private const val INITIAL_PAGE_SIZE = 20
private const val PAGE_SIZE = 30
private const val INCOMING_SOCKET_DEBOUNCE_MS = 24L
private const val CHAT_DERIVE_DEBOUNCE_MS = 24L
private const val PROFILE_GATE_TTL_MS = 5 * 60_000L
/** Отложить reconcile с сервером, если сообщения уже в кэше после splash/bootstrap. */
private const val BACKGROUND_MESSAGE_REFRESH_DEFER_MS = 400L
/** Coalesce listRooms после socket unread — не дергать сеть на каждый bump. */
private const val UNREAD_SYNC_DEBOUNCE_MS = 450L
/** Min interval between full listRooms() on chat tab resume. */
private const val ROOMS_SYNC_ON_RESUME_TTL_MS = 45_000L

private data class RoomMessageCache(
    val messages: List<ChatMessage>,
    val hasMoreOlder: Boolean,
)

class ChatViewModel(
    application: Application,
    private val repository: ChatRepository,
    private val chatRoomPreferences: ChatRoomPreferences,
    private val pinHistoryPreferences: PinHistoryPreferences,
    private val usersRepository: UsersRepository,
    private val launchDiskCache: LaunchDiskCache,
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

    /** List subtree — updates on message/scroll/selection changes, not on draft/typing. */
    val listPaneState: StateFlow<ChatListPaneState> = _state
        .map { it.toListPane() }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatListPaneState())

    /** Rooms bar, errors, action sheets — not on every new message. */
    val chromePaneState: StateFlow<ChatChromePaneState> = _state
        .map { it.toChromePane() }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatChromePaneState())

    /** Composer subtree — reply, send failure, stickers. */
    val composerPaneState: StateFlow<ChatComposerPaneState> = _state
        .map { it.toComposerPane() }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatComposerPaneState())

    /** Precomputed timeline for [ChatScreen] — avoids derive on every Compose frame. */
    private val _listDerived = MutableStateFlow(ChatMessagesListDerived.Empty)
    val listDerived: StateFlow<ChatMessagesListDerived> = _listDerived.asStateFlow()
    private var deriveJob: Job? = null
    private var deriveDebounceJob: Job? = null
    private val listDeriveDefer = ChatListDeriveDefer()
    private val incomingApplyMutex = Mutex()
    private val chatMutationLock = Any()
    /** HTTP in-flight sends — socket echo must not insert a second row before [confirmPendingOutgoingMessage]. */
    private val inFlightOutgoingFingerprints =
        java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<String, Boolean>())
    /**
     * Stable LazyColumn keys: pending id → server id swap must not change Compose item identity.
     */
    private val lazyColumnKeyByMessageId = mutableMapOf<String, String>()
    /** While set, only HTTP confirm may replace the optimistic row — no socket second row. */
    @Volatile
    private var activeOutgoingPendingId: String? = null

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
    /** Per-room peer read cursor — survives room switches (overlay + main app). */
    private val otherReadUptoByRoom = mutableMapOf<String, String>()
    /** Client-side pin history per room (Telegram-style bar cycling). */
    private val pinHistoryByRoom = mutableMapOf<String, List<com.lastasylum.alliance.data.chat.PinnedMessagePreviewDto>>()
    private val pinBarIndexByRoom = mutableMapOf<String, Int>()
    /** Detect active pin change to reset bar cycle index. */
    private val lastSyncedActivePinIdByRoom = mutableMapOf<String, String>()

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
    /** Hard-deleted ids — mergeLoadedPageWithExisting must not resurrect them from disk/socket cache. */
    private val locallyRemovedMessageIds = LinkedHashSet<String>()
    /** Latest message id we successfully marked read per room (avoids regress + duplicate bumps). */
    private val lastMarkedReadByRoom = mutableMapOf<String, String>()
    /** Socket can deliver the same message via new + reaction/edited; count unread once per id. */
    private val unreadBumpedMessageIds = LinkedHashSet<String>()
    /** Realtime before listRooms — applied after [applyRoomsFromServer]. */
    private val pendingUnreadBumps = ArrayDeque<Pair<String, String>>()
    /** Socket bump not yet reflected in listRooms / rooms:unread — do not zero tab badge. */
    private val optimisticUnreadFloorByRoom = mutableMapOf<String, Int>()
    private var lastRoomsSyncedAtMs: Long = 0L
    private val markReadInFlight = CopyOnWriteArrayList<Job>()
    private var unreadSyncJob: Job? = null
    /** False when user left the Chat tab — must not auto mark-read or zero selected-room badge. */
    private var isChatTabActive = false

    /** Main activity in foreground — false when user is in-game with app in background. */
    @Volatile
    private var appInForeground = true

    /** Fullscreen overlay chat/team panel is open — separate from bottom-nav Chat tab. */
    @Volatile
    private var overlayChatPanelVisible = false
    private var overlayAutoMarkReadJob: kotlinx.coroutines.Job? = null
    private val bootstrapMutex = Mutex()
    private var bootstrapJob: Job? = null
    private var persistSnapshotJob: Job? = null

    @Volatile
    private var launchWarmupNeedsMessages = false

    @Volatile
    private var launchWarmupNeedsBootstrap = false

    @Volatile
    private var cachedTeamProfileGate: Boolean? = null

    @Volatile
    private var profileGateLoadedAtMs: Long = 0L

    private val mainHandler = Handler(Looper.getMainLooper())
    private var chatVoiceRecognizer: ChatVoiceRecognizer? = null

    private val res get() = getApplication<Application>().resources

    private val messageMemoryCap: Int by lazy {
        val am = getApplication<Application>().getSystemService(ActivityManager::class.java)
        if (am?.isLowRamDevice == true) 400 else CHAT_MAX_MESSAGES_IN_MEMORY
    }

    private fun capMessagesForMemory(messages: List<ChatMessage>): List<ChatMessage> =
        capNewestFirst(messages, messageMemoryCap)

    init {
        hydrateReadCursorsFromPreferences()
        if (currentUserId.isNotBlank()) {
            locallyRemovedMessageIds.addAll(launchDiskCache.loadRemovedMessageIds(currentUserId))
        }
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
        viewModelScope.launch {
            repository.chatConnectionState()
                .filter { it == ChatConnectionState.Connected }
                .collect { onChatSocketConnected() }
        }
    }

    /** Admin wiped all chat history (socket or overlay forward). */
    fun applyChatHistoryClearedFromServer() {
        ChatHistoryWipe.wipeCaches(currentUserId, launchDiskCache, chatRoomPreferences)
        roomMessageCache.clear()
        knownMessageIds.clear()
        messageIdIndex.clear()
        lazyColumnKeyByMessageId.clear()
        locallyRemovedMessageIds.clear()
        unreadBumpedMessageIds.clear()
        pendingUnreadBumps.clear()
        optimisticUnreadFloorByRoom.clear()
        otherReadUptoByRoom.clear()
        lastMarkedReadByRoom.clear()
        pinHistoryByRoom.clear()
        pinBarIndexByRoom.clear()

        val selected = _state.value.selectedRoomId
        _state.update { st ->
            st.copy(
                messages = emptyList(),
                newestMessageKey = null,
                hasMoreOlder = false,
                isLoadingOlder = false,
                isSending = false,
                deletingMessageId = null,
                confirmDeleteMessageId = null,
                selectedMessageIds = emptySet(),
                isDeletingSelection = false,
                activeActionMessageId = null,
                rooms = st.rooms.map { it.copy(unreadCount = 0, lastReadMessageId = null) },
                pinBarPreview = null,
                pinHistoryCount = 0,
            )
        }
        updatePinBarUi()
        _otherReadUptoMessageId.value = null
        _listDerived.value = ChatMessagesListDerived.Empty
        publishMessagesDerived(emptyList())

        viewModelScope.launch {
            syncRoomsFromServer(reconfirmVisibleRoom = !selected.isNullOrBlank())
            selected?.let { refreshMessagesInBackground(it, force = true) }
        }
        schedulePersistChatSnapshot()
    }

    private fun onChatHistoryClearedFromServer() {
        applyChatHistoryClearedFromServer()
    }

    private fun dispatchIncomingBatch(batch: List<ChatMessage>) {
        if (batch.isEmpty()) return
        val selected = _state.value.selectedRoomId
        val applyQueue = ArrayList<ChatMessage>(batch.size)
        for (message in batch) {
            val roomId = message.roomId.trim()
            if (roomId.isBlank()) continue
            if (roomId == selected && isRoomActivelyViewed(roomId)) {
                if (!shouldDeferOwnOutgoingSocketEcho(message)) {
                    applyQueue.add(message)
                }
            } else {
                processRealtimeMessageForUnread(message)
            }
        }
        if (applyQueue.isNotEmpty()) {
            applyIncomingBatch(applyQueue)
        }
    }

    /** Called from overlay hub socket when activity [ChatViewModel] is bound. */
    fun recordRealtimeUnreadHint(message: ChatMessage) {
        processRealtimeMessageForUnread(message)
    }

    /** Overlay FGS: apply server room unread without replacing primary socket listener. */
    fun applyRoomUnreadFromServer(event: ChatRoomUnreadEvent) {
        onRoomUnreadFromServer(event)
    }

    /**
     * Сохранить realtime в кэш комнаты (оверлей FGS), даже если полноэкранная панель закрыта.
     * Иначе при открытии «Рейд» остаётся устаревшая короткая выборка из диска/RAM.
     */
    fun stashOverlayRealtimeMessage(message: ChatMessage) {
        if (message.isCompactReactionSocketUpdate()) {
            applyKnownChatMessageUpdate(message)
            return
        }
        val roomId = message.roomId.trim()
        if (roomId.isBlank() || message._id.isNullOrBlank()) return
        if (isKnownChatMessageId(message._id)) {
            applyKnownChatMessageUpdate(message)
            return
        }
        stashIncomingMessageForRoom(message)
    }

    /**
     * Overlay quick-command HTTP: cache + visible list when «Рейд» (or matching room) is selected.
     * Unlike socket forward, does not skip apply when activity holds primary subscription.
     */
    fun applyOverlayRaidHttpMessage(message: ChatMessage) {
        if (shouldSuppressOwnOutgoingRealtimeEcho(message)) return
        stashOverlayRealtimeMessage(message)
        if (!OverlayRaidChatForwardPolicy.shouldApplyToVisibleChat(_state.value.selectedRoomId, message.roomId)) {
            return
        }
        applyOverlayChatMessageFromSocket(message)
    }

    /** Overlay socket while in-game chat panel is open (primary listener may be absent). */
    fun applyOverlayChatMessageFromSocket(message: ChatMessage) {
        if (message.isCompactReactionSocketUpdate()) {
            applyKnownChatMessageUpdate(message)
            return
        }
        val roomId = message.roomId.trim()
        if (roomId.isBlank()) return
        if (isKnownChatMessageId(message._id)) {
            applyKnownChatMessageUpdate(message)
            return
        }
        onIncomingMessage(message)
    }

    fun applyOverlayChatDeletedFromSocket(event: ChatMessageDeletedEvent) {
        onDeletedMessage(event)
    }

    fun applyOverlayChatTypingFromSocket(event: ChatTypingEvent) {
        onTypingFromPeer(event)
    }

    fun applyOverlayChatReadFromSocket(event: ChatRoomReadEvent) {
        onRoomReadEvent(event)
    }

    private fun isKnownChatMessageId(messageId: String?): Boolean {
        val id = messageId?.trim().orEmpty()
        return id.isNotEmpty() && knownMessageIds.contains(id)
    }

    private fun processRealtimeMessageForUnread(message: ChatMessage) {
        val roomId = message.roomId
        if (roomId.isBlank()) return
        if (isKnownChatMessageId(message._id)) {
            applyKnownChatMessageUpdate(message)
            return
        }
        val selected = _state.value.selectedRoomId
        if (roomId == selected) {
            if (isRoomActivelyViewed(roomId)) {
                if (!shouldDeferOwnOutgoingSocketEcho(message)) {
                    applyIncomingMessage(message)
                    if (message.senderId.trim() != currentUserId.trim()) {
                        scheduleMarkReadForVisibleIncoming(message)
                    }
                }
            } else {
                stashIncomingMessageForRoom(message)
            }
            if (!isRoomActivelyViewed(roomId) && message.senderId != currentUserId) {
                val mid = message._id ?: return
                if (shouldTrackUnreadForMessage(roomId, mid)) {
                    bumpRoomUnreadLocally(roomId, mid)
                    scheduleUnreadSyncFromServer()
                }
            }
            return
        }
        if (message._id != null) {
            stashIncomingMessageForRoom(message)
        }
        if (message.senderId == currentUserId) return
        val mid = message._id ?: return
        if (!shouldTrackUnreadForMessage(roomId, mid)) return
        if (_state.value.rooms.none { it.id == roomId }) {
            queuePendingUnreadBump(roomId, mid)
            notifyOverlayHubIfPending(roomId, mid)
            scheduleUnreadSyncFromServer()
            return
        }
        bumpRoomUnreadLocally(roomId, mid)
        scheduleUnreadSyncFromServer()
    }

    private fun queuePendingUnreadBump(roomId: String, messageId: String) {
        pendingUnreadBumps.addLast(roomId to messageId)
        while (pendingUnreadBumps.size > 64) {
            pendingUnreadBumps.removeFirst()
        }
    }

    private fun flushPendingUnreadBumps() {
        if (pendingUnreadBumps.isEmpty()) return
        val pending = pendingUnreadBumps.toList()
        pendingUnreadBumps.clear()
        val selected = _state.value.selectedRoomId
        for ((roomId, messageId) in pending) {
            if (_state.value.rooms.none { it.id == roomId }) continue
            if (roomId == selected && isRoomActivelyViewed(roomId)) continue
            if (!shouldTrackUnreadForMessage(roomId, messageId)) continue
            bumpRoomUnreadLocally(roomId, messageId)
        }
        scheduleUnreadSyncFromServer()
    }

    private fun notifyOverlayHubIfPending(roomId: String, messageId: String) {
        val hubId = chatRoomPreferences.getHubRoomId()?.trim().orEmpty()
        if (hubId.isBlank() || roomId != hubId) return
        CombatOverlayService.bumpAllianceHubUnreadFromRealtime(messageId)
    }

    private fun syncOverlayAllianceHubBadge(rooms: List<ChatRoomDto> = _state.value.rooms) {
        val localRead = chatRoomPreferences.loadAllLastReadMessageIds()
        val displayed = ChatUnreadCounts.overlayAllianceHubBadge(
            rooms = rooms,
            localReadByRoom = localRead,
            optimisticFloor = 0,
            previouslyDisplayed = 0,
        )
        CombatOverlayService.syncHubBadgeFromSharedReadState(displayed)
    }

  /**
     * Socket `room:join` targets. When chat UI is inactive, rely on `rooms:unread` on `user:{id}`
     * (backend fanout) and only join raid/hub/selected plus rooms that still show a local badge.
     */
    private fun realtimeSubscriptionRoomIds(rooms: List<ChatRoomDto>): List<String> =
        orderRealtimeSubscriptionRoomIds(
            rooms = rooms,
            selectedRoomId = _state.value.selectedRoomId,
            raidRoomId = chatRoomPreferences.getRaidRoomId(),
            hubRoomId = ChatRoomKindResolver.allianceHubRoom(rooms)?.id,
            subscribeAllRooms = isChatTabActive || overlayChatPanelVisible,
        )

    fun refreshChat() {
        scheduleBootstrap(preferAllianceHubRoom = true, force = true)
    }

    /** Stable LazyColumn key — survives pending → server id confirm without remove/insert flicker. */
    fun messageListCompositionKey(message: ChatMessage): String {
        val id = message._id?.trim().orEmpty()
        if (id.isNotEmpty()) {
            lazyColumnKeyByMessageId[id]?.let { return it }
            return id
        }
        return chatMessageKey(message)
    }

    private fun registerOutgoingLazyColumnKey(messageId: String) {
        val id = messageId.trim()
        if (id.isEmpty()) return
        lazyColumnKeyByMessageId[id] = "out-${UUID.randomUUID()}"
    }

    private fun transferOutgoingLazyColumnKey(fromMessageId: String, toMessageId: String) {
        val from = fromMessageId.trim()
        val to = toMessageId.trim()
        if (from.isEmpty() || to.isEmpty() || from == to) return
        val stable = lazyColumnKeyByMessageId.remove(from) ?: return
        lazyColumnKeyByMessageId[to] = stable
    }

    private fun dropOutgoingLazyColumnKey(messageId: String?) {
        val id = messageId?.trim().orEmpty()
        if (id.isEmpty()) return
        lazyColumnKeyByMessageId.remove(id)
    }

    /** Splash (критический путь): комнаты с диска или один listRooms; без openRoom / сети сообщений. */
    suspend fun warmUpForLaunchLight() {
        val primed = primeFromLaunchDisk()
        if (_state.value.rooms.isEmpty()) {
            val roomsResult = withContext(Dispatchers.IO) { repository.listRooms() }
            roomsResult
                .onSuccess { raw ->
                    val rooms = applyRoomsFromServer(raw)
                    syncRaidRoomPreference(rooms)
                    val selected = resolveOverlayPreferredRoomId(
                        rooms = rooms,
                        preferOverlayRaidRoom = false,
                    ) ?: rooms.firstOrNull()?.id
                    selected?.let { chatRoomPreferences.setSelectedRoomId(it) }
                    reconcileStaleServerUnread(rooms, raw)
                    _state.update {
                        applyPinBarUi(
                            it.copy(
                                rooms = rooms,
                                isRoomsLoading = false,
                                selectedRoomId = selected ?: it.selectedRoomId,
                            ),
                        )
                    }
                    schedulePersistChatSnapshot()
                }
                .onFailure {
                    _state.update { it.copy(isRoomsLoading = false) }
                }
        } else {
            recomputeRoomUnreadBadges()
            _state.update { it.copy(isRoomsLoading = false) }
        }
        launchWarmupNeedsBootstrap = true
        if (
            (primed || _state.value.rooms.isNotEmpty()) &&
            _state.value.messages.isEmpty() &&
            !_state.value.selectedRoomId.isNullOrBlank()
        ) {
            launchWarmupNeedsMessages = true
        }
    }

    /** Splash: комнаты сразу; сообщения — из диска или фоном после UI. */
    suspend fun warmUpForLaunch() {
        val primed = primeFromLaunchDisk()
        if (primed) {
            bootstrap(preferAllianceHubRoom = true, force = false)
            if (_state.value.messages.isEmpty() &&
                !_state.value.selectedRoomId.isNullOrBlank()
            ) {
                launchWarmupNeedsMessages = true
            }
        } else {
            bootstrap(
                preferAllianceHubRoom = true,
                force = true,
                deferNetworkMessages = true,
            )
        }
    }

    /** После splash: openRoom / bootstrap и догрузка ленты. */
    fun continueLaunchWarmup() {
        viewModelScope.launch {
            if (launchWarmupNeedsBootstrap) {
                launchWarmupNeedsBootstrap = false
                bootstrap(preferAllianceHubRoom = true, force = false, deferNetworkMessages = false)
            }
            if (!launchWarmupNeedsMessages) {
                if (_state.value.rooms.isNotEmpty()) schedulePersistChatSnapshot()
                return@launch
            }
            val roomId = _state.value.selectedRoomId?.trim().orEmpty()
            if (roomId.isEmpty()) {
                launchWarmupNeedsMessages = false
                return@launch
            }
            launchWarmupNeedsMessages = false
            val result = withContext(Dispatchers.IO) {
                repository.loadRecentMessages(
                    roomId,
                    beforeMessageId = null,
                    limit = INITIAL_PAGE_SIZE,
                )
            }
            result
                .onSuccess { loaded ->
                    applyLoadedMessagePage(
                        roomId = roomId,
                        loaded = loaded,
                        pageSizeForHasMore = INITIAL_PAGE_SIZE,
                    )
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = e.toUserMessageRu(res),
                        )
                    }
                }
        }
    }

    /**
     * Подставить комнаты/сообщения с диска до сети (offline-first после прошлого входа).
     * @return true если список комнат восстановлен
     */
    fun primeFromLaunchDisk(): Boolean {
        val payload = readLaunchDiskPrimePayload() ?: return false
        return applyLaunchDiskPrimePayload(payload)
    }

    /** Disk reads on [Dispatchers.IO] — не блокировать main при открытии оверлей-чата. */
    suspend fun primeFromLaunchDiskForOverlay(): Boolean {
        val payload = withContext(Dispatchers.IO) { readLaunchDiskPrimePayload() } ?: return false
        return withContext(Dispatchers.Main.immediate) { applyLaunchDiskPrimePayload(payload) }
    }

    private data class LaunchDiskPrimePayload(
        val roomsRaw: List<ChatRoomDto>,
        val selectedRoomId: String,
        val roomCaches: Map<String, RoomMessageCache>,
    )

    private fun readLaunchDiskPrimePayload(): LaunchDiskPrimePayload? {
        if (currentUserId.isBlank()) return null
        val roomsRaw = launchDiskCache.loadChatRooms(currentUserId) ?: return null
        val rooms = applyRoomsFromServer(roomsRaw)
        if (rooms.isEmpty()) return null
        val selected = resolveOverlayPreferredRoomId(
            rooms = rooms,
            preferOverlayRaidRoom = false,
        ) ?: chatRoomPreferences.getSelectedRoomId()?.takeIf { id ->
            rooms.any { it.id == id }
        } ?: rooms.first().id
        val roomIdsToPrime = linkedSetOf(selected)
        allianceHubRoomId(rooms)?.let { roomIdsToPrime.add(it) }
        allianceRaidRoomId(rooms)?.let { roomIdsToPrime.add(it) }
        val roomCaches = linkedMapOf<String, RoomMessageCache>()
        for (rid in roomIdsToPrime) {
            launchDiskCache.loadRoomMessages(currentUserId, rid)?.let { disk ->
                val scrubbed = filterMessagesForRoom(
                    messagesWithoutLocallyRemoved(disk.messages),
                    rid,
                )
                val capped = capNewestFirst(scrubbed, PAGE_SIZE)
                roomCaches[rid] = RoomMessageCache(
                    messages = capped,
                    hasMoreOlder = disk.hasMoreOlder,
                )
            }
        }
        return LaunchDiskPrimePayload(
            roomsRaw = roomsRaw,
            selectedRoomId = selected,
            roomCaches = roomCaches,
        )
    }

    private fun applyLaunchDiskPrimePayload(payload: LaunchDiskPrimePayload): Boolean {
        val rooms = applyRoomsFromServer(payload.roomsRaw)
        if (rooms.isEmpty()) return false
        ChatSessionCache.update(rooms)
        val selected = payload.selectedRoomId
        payload.roomCaches.forEach { (rid, cache) ->
            roomMessageCache[rid] = cache
            if (cache.messages.isNotEmpty()) {
                ChatSessionCache.updateMessages(rid, cache.messages)
            }
        }
        val cached = roomMessageCache[selected]
        if (cached != null && cached.messages.isNotEmpty()) {
            knownMessageIds.clear()
            messageIdIndex.clear()
            knownMessageIds.addAll(cached.messages.mapNotNull { it._id })
            rebuildMessageIdIndex(cached.messages, messageIdIndex)
            _state.value = applyPinBarUi(
                _state.value.copy(
                    isLoading = false,
                    isRoomsLoading = false,
                    rooms = rooms,
                    selectedRoomId = selected,
                    messages = cached.messages,
                    hasMoreOlder = cached.hasMoreOlder,
                    error = null,
                    scrollToLatestNonce = _state.value.scrollToLatestNonce + 1L,
                ),
            )
            publishMessagesDerived(cached.messages)
        } else {
            _state.value = applyPinBarUi(
                _state.value.copy(
                    isLoading = true,
                    isRoomsLoading = false,
                    rooms = rooms,
                    selectedRoomId = selected,
                    messages = emptyList(),
                    hasMoreOlder = true,
                    error = null,
                ),
            )
            _listDerived.value = ChatMessagesListDerived.Empty
        }
        return true
    }

    private fun schedulePersistChatSnapshot() {
        if (currentUserId.isBlank()) return
        persistSnapshotJob?.cancel()
        persistSnapshotJob = viewModelScope.launch(Dispatchers.IO) {
            delay(300L)
            persistChatSnapshot()
        }
    }

    private fun persistChatSnapshot() {
        if (currentUserId.isBlank()) return
        val rooms = _state.value.rooms
        if (rooms.isNotEmpty()) {
            launchDiskCache.saveChatRooms(currentUserId, rooms)
        }
        val selected = _state.value.selectedRoomId?.trim().orEmpty()
        if (selected.isNotEmpty()) {
            val entry = roomMessageCache[selected]
            val raw = entry?.messages?.takeIf { it.isNotEmpty() }
                ?: _state.value.messages.takeIf { it.isNotEmpty() }
            val messages = raw
                ?.let { messagesWithoutLocallyRemoved(it) }
                ?.let { filterMessagesForRoom(it, selected) }
            if (!messages.isNullOrEmpty()) {
                launchDiskCache.saveRoomMessages(
                    userId = currentUserId,
                    roomId = selected,
                    messages = messages,
                    hasMoreOlder = entry?.hasMoreOlder ?: _state.value.hasMoreOlder,
                )
            }
        }
        val hubId = allianceHubRoomId(rooms)
        if (!hubId.isNullOrBlank() && hubId != selected) {
            roomMessageCache[hubId]?.let { entry ->
                val messages = messagesWithoutLocallyRemoved(entry.messages)
                if (messages.isNotEmpty()) {
                    launchDiskCache.saveRoomMessages(
                        currentUserId,
                        hubId,
                        messages,
                        entry.hasMoreOlder,
                    )
                }
            }
        }
        val raidId = allianceRaidRoomId(rooms)
        if (!raidId.isNullOrBlank() && raidId != selected && raidId != hubId) {
            roomMessageCache[raidId]?.let { entry ->
                val messages = messagesWithoutLocallyRemoved(entry.messages)
                if (messages.isNotEmpty()) {
                    launchDiskCache.saveRoomMessages(
                        currentUserId,
                        raidId,
                        messages,
                        entry.hasMoreOlder,
                    )
                }
            }
        }
    }

    private fun diskChatRoomsOrNull(): List<ChatRoomDto>? {
        if (currentUserId.isBlank()) return null
        return launchDiskCache.loadChatRooms(currentUserId)
    }

    private fun primeRoomMessagesFromDisk(roomId: String): RoomMessageCache? {
        if (currentUserId.isBlank() || roomId.isBlank()) return null
        val disk = launchDiskCache.loadRoomMessages(currentUserId, roomId) ?: return null
        if (disk.messages.isEmpty()) return null
        val scrubbed = filterMessagesForRoom(
            messagesWithoutLocallyRemoved(disk.messages),
            roomId,
        )
        val capped = capNewestFirst(scrubbed, PAGE_SIZE)
        return RoomMessageCache(
            messages = capped,
            hasMoreOlder = disk.hasMoreOlder,
        ).also { entry ->
            roomMessageCache[roomId] = entry
            ChatSessionCache.updateMessages(roomId, capped)
        }
    }

    /**
     * Синхронно подставить комнаты/ленту из RAM, [ChatSessionCache] или [LaunchDiskCache]
     * до первого кадра оверлея (без «Пока нет сообщений…» и ожидания bootstrap).
     */
    fun primeOverlayChatFromCache(
        preferAllianceHubRoom: Boolean = true,
        preferOverlayRaidRoom: Boolean = false,
    ) {
        if (preferOverlayRaidRoom && overlayRaidAlreadyReady(_state.value.rooms)) {
            allianceRaidRoomId(_state.value.rooms)?.let { rehydrateRoomMessagesFromCache(it) }
            _state.update { it.copy(isLoading = false, isRoomsLoading = false) }
            return
        }
        if (preferAllianceHubRoom && !preferOverlayRaidRoom) {
            allianceHubRoomId(_state.value.rooms)?.let { rehydrateRoomMessagesFromCache(it) }
            if (overlayHubAlreadyReady(_state.value.rooms)) {
                _state.update { it.copy(isLoading = false, isRoomsLoading = false) }
                return
            }
        }
        val roomsRaw = ChatSessionCache.getFreshRooms()
            ?: _state.value.rooms.takeIf { it.isNotEmpty() }
            ?: diskChatRoomsOrNull()
            ?: return
        val rooms = applyRoomsFromServer(roomsRaw)
        if (ChatSessionCache.getFreshRooms() == null) {
            ChatSessionCache.update(rooms)
        }
        if (rooms.isEmpty()) return
        val roomId = resolveOverlayPreferredRoomId(
            rooms = rooms,
            preferOverlayRaidRoom = preferOverlayRaidRoom,
        ) ?: return
        if (_state.value.selectedRoomId == roomId &&
            _state.value.messages.isNotEmpty() &&
            messagesBelongToRoom(_state.value.messages, roomId)
        ) {
            rehydrateRoomMessagesFromCache(roomId)
            _state.update { it.copy(isLoading = false, isRoomsLoading = false) }
            return
        }
        val cached = roomMessageCache[roomId]
            ?: ChatSessionCache.getFreshMessages(roomId)?.let { sessionMessages ->
                val scrubbed = filterMessagesForRoom(
                    messagesWithoutLocallyRemoved(sessionMessages),
                    roomId,
                )
                val capped = capNewestFirst(scrubbed, PAGE_SIZE)
                RoomMessageCache(
                    messages = capped,
                    hasMoreOlder = scrubbed.size >= PAGE_SIZE,
                ).also { roomMessageCache[roomId] = it }
            }
            ?: primeRoomMessagesFromDisk(roomId)
        if (cached == null || cached.messages.isEmpty()) {
            val switchingRoom = _state.value.selectedRoomId != roomId ||
                !messagesBelongToRoom(_state.value.messages, roomId)
            if (switchingRoom) {
                knownMessageIds.clear()
                messageIdIndex.clear()
                _listDerived.value = ChatMessagesListDerived.Empty
            }
            _state.update { st ->
                st.copy(
                    isLoading = true,
                    isRoomsLoading = false,
                    selectedRoomId = roomId,
                    messages = if (switchingRoom) emptyList() else st.messages,
                    rooms = if (st.rooms.isEmpty()) rooms else st.rooms,
                )
            }
            return
        }
        knownMessageIds.clear()
        messageIdIndex.clear()
        knownMessageIds.addAll(cached.messages.mapNotNull { it._id })
        rebuildMessageIdIndex(cached.messages, messageIdIndex)
        _state.value = _state.value.copy(
            isLoading = false,
            isRoomsLoading = false,
            rooms = rooms,
            selectedRoomId = roomId,
            messages = cached.messages,
            hasMoreOlder = cached.hasMoreOlder,
            error = null,
            scrollToLatestNonce = _state.value.scrollToLatestNonce + 1L,
        )
        publishMessagesDerived(cached.messages)
    }

    /** Оверлей-чат: по умолчанию комната «Альянс» (hub), как вкладка чата в приложении. */
    fun refreshChatForOverlay() {
        syncReadStateFromPreferences()
        primeOverlayChatFromCache(preferAllianceHubRoom = true)
        if (!overlayHubAlreadyReady(_state.value.rooms)) {
            primeFromLaunchDisk()
            primeOverlayChatFromCache(preferAllianceHubRoom = true)
        }
        if (!overlayHubAlreadyReady(_state.value.rooms)) {
            scheduleBootstrap(preferAllianceHubRoom = true, force = false)
        }
        syncOverlayRoomsQuietly()
    }

    /** Список комнат в фоне — без сброса уже показанной ленты hub. */
    private fun syncOverlayRoomsQuietly() {
        viewModelScope.launch {
            repository.listRooms()
                .onSuccess { raw ->
                    val next = applyRoomsFromServer(raw)
                    syncRaidRoomPreference(next)
                    ChatSessionCache.update(raw)
                    _state.update { applyPinBarUi(it.copy(rooms = next)) }
                    syncTabUnreadBadge(next)
                    syncOverlayAllianceHubBadge(next)
                    if (overlayChatPanelVisible) {
                        recomputeRoomUnreadBadges()
                    }
                    reconnectRealtimeIfNeeded()
                    schedulePersistChatSnapshot()
                }
        }
    }

    fun setAppInForeground(inForeground: Boolean) {
        if (appInForeground == inForeground) return
        appInForeground = inForeground
        if (inForeground) {
            syncReadStateFromPreferences()
            applyLocallyRemovedFilterToLoadedCaches()
            if (isChatTabActive || overlayChatPanelVisible) {
                rehydrateSelectedRoomMessagesFromCache()
            }
        } else {
            persistSnapshotJob?.cancel()
            viewModelScope.launch(Dispatchers.IO) { persistChatSnapshot() }
        }
        recomputeRoomUnreadBadges()
    }

    fun setOverlayChatPanelVisible(visible: Boolean) {
        if (overlayChatPanelVisible == visible) return
        overlayChatPanelVisible = visible
        if (visible) {
            refreshStickerPackAccess()
            refreshTeamProfileGateLight()
            syncReadStateFromPreferences()
            val hubReady = overlayHubReadyForPanel()
            if (!hubReady) {
                primeOverlayChatFromCache(preferAllianceHubRoom = true)
            } else {
                _state.update { it.copy(isLoading = false, isRoomsLoading = false) }
            }
            rehydrateSelectedRoomMessagesFromCache()
            reconnectRealtimeIfNeeded()
            viewModelScope.launch {
                if (!overlayChatPanelVisible) return@launch
                delay(32)
                if (!overlayChatPanelVisible) return@launch
                rehydrateSelectedRoomMessagesFromCache()
                if (_state.value.selectedRoomId.isNullOrBlank()) {
                    ensureAllianceHubRoomSelected()
                }
                val activeRoomId = _state.value.selectedRoomId?.trim().orEmpty()
                val newestId = _state.value.messages.firstOrNull()?._id
                if (
                    activeRoomId.isNotEmpty() &&
                    isValidMarkReadMessageId(newestId) &&
                    shouldAutoMarkReadSelectedRoom()
                ) {
                    markRoomReadUpTo(activeRoomId, newestId!!)
                } else {
                    recomputeRoomUnreadBadges()
                }
                val roomId = _state.value.selectedRoomId
                if (!roomId.isNullOrBlank()) {
                    refreshMessagesInBackground(roomId, force = !hubReady)
                }
                if (!hubReady && !overlayHubReadyForPanel()) {
                    scheduleBootstrap(preferAllianceHubRoom = true, force = false)
                } else {
                    syncOverlayRoomsQuietly()
                }
            }
            startOverlayAutoMarkReadCollector()
            return
        }
        overlayAutoMarkReadJob?.cancel()
        overlayAutoMarkReadJob = null
        snapshotSelectedRoomToMessageCache()
        schedulePersistChatSnapshot()
        viewModelScope.launch {
            awaitPendingMarkRead()
            recomputeRoomUnreadBadges()
            syncRoomsFromServer(reconfirmVisibleRoom = false)
        }
    }

    private fun hiddenBeforeForRoom(roomId: String): String? =
        chatRoomPreferences.getHiddenBeforeMessageId(roomId)

    private fun filterMessagesForRoom(
        messages: List<ChatMessage>,
        roomId: String,
    ): List<ChatMessage> =
        com.lastasylum.alliance.ui.chat.filterMessagesForRoom(
            messages,
            roomId,
            hiddenBeforeForRoom(roomId),
        )

    /** Reaction/edit/delete socket rows — update list/cache, never bump unread. */
    private fun applyKnownChatMessageUpdate(message: ChatMessage) {
        val roomId = message.roomId.trim()
        if (roomId.isBlank()) return
        if (!com.lastasylum.alliance.data.chat.ChatMessageVisibilityPolicy.isMessageVisible(
                message,
                hiddenBeforeForRoom(roomId),
            )
        ) {
            return
        }
        if (shouldBlockOwnOutgoingRealtime(message)) return
        val selected = _state.value.selectedRoomId
        when {
            roomId == selected ->
                applyIncomingMessage(message)
            roomMessageCache.containsKey(roomId) ->
                stashIncomingMessageForRoom(message)
        }
    }

    private fun isRoomActivelyViewed(roomId: String): Boolean {
        if (_state.value.selectedRoomId != roomId) return false
        if (overlayChatPanelVisible) {
            if (!CombatOverlayService.isOverlayChatTabActive()) return false
            return appInForeground ||
                com.lastasylum.alliance.overlay.OverlayChatInteractionHold.isFullscreenChatTeamPanelVisible ||
                CombatOverlayService.isOverlayChatPanelOpenInGame()
        }
        if (!appInForeground) return false
        return isChatTabActive
    }

    private fun shouldAutoMarkReadSelectedRoom(): Boolean {
        val roomId = _state.value.selectedRoomId ?: return false
        // Overlay HUD: read cursor advances when the panel closes, not while browsing.
        if (overlayChatPanelVisible) return false
        return isRoomActivelyViewed(roomId)
    }

    /** Mark visible overlay messages read (viewport); advances cursor only forward. */
    fun markOverlayVisibleMessagesAsRead(messageIds: List<String>) {
        if (!overlayChatPanelVisible) return
        val roomId = _state.value.selectedRoomId?.trim().orEmpty()
        if (roomId.isEmpty()) return
        val room = _state.value.rooms.find { it.id == roomId } ?: return
        val lastRead = resolvedLastReadMessageId(room)?.trim().orEmpty()
        val self = currentUserId.trim()
        var watermark: String? = null
        for (raw in messageIds) {
            val id = raw.trim()
            if (!isValidMarkReadMessageId(id)) continue
            if (lastRead.isNotEmpty() && !isObjectIdNewer(id, lastRead)) continue
            val senderId = _state.value.messages.find { it._id?.trim() == id }?.senderId?.trim().orEmpty()
            if (self.isNotBlank() && senderId == self) continue
            watermark = when (val prev = watermark) {
                null -> id
                else -> if (isObjectIdNewer(id, prev)) id else prev
            }
        }
        val markId = watermark ?: return
        viewModelScope.launch { markRoomReadUpTo(roomId, markId) }
    }

    /** Oldest unread incoming in the open room (reverse list: last matching row). */
    fun jumpToFirstUnreadInSelectedRoom() {
        val roomId = _state.value.selectedRoomId?.trim().orEmpty()
        if (roomId.isEmpty()) return
        val room = _state.value.rooms.find { it.id == roomId } ?: return
        val lastRead = resolvedLastReadMessageId(room)?.trim().orEmpty()
        if (lastRead.isEmpty()) return
        val self = currentUserId.trim()
        val targetId = _state.value.messages.lastOrNull { message ->
            val id = message._id?.trim().orEmpty()
            if (!isValidMarkReadMessageId(id)) return@lastOrNull false
            if (self.isNotBlank() && message.senderId.trim() == self) return@lastOrNull false
            isObjectIdNewer(id, lastRead)
        }?._id ?: return
        jumpToQuotedMessage(targetId)
    }

    private fun isValidMarkReadMessageId(messageId: String?): Boolean {
        val id = messageId?.trim().orEmpty()
        return id.isNotEmpty() && !id.startsWith("pending-")
    }

    /** Peer opened the thread — advance local read cursor so sender gets ✓✓ via room:read. */
    private fun scheduleMarkReadForVisibleIncoming(message: ChatMessage) {
        val roomId = _state.value.selectedRoomId?.trim().orEmpty()
        val messageId = message._id?.trim().orEmpty()
        if (roomId.isEmpty() || !isValidMarkReadMessageId(messageId)) return
        if (!shouldAutoMarkReadSelectedRoom()) return
        if (message.senderId.trim() == currentUserId.trim()) return
        viewModelScope.launch {
            markRoomReadUpTo(roomId, messageId)
        }
    }

    private fun scheduleMarkReadAfterIncomingBatch(
        roomId: String?,
        scopedBatch: List<ChatMessage>,
        cappedMessages: List<ChatMessage>,
    ) {
        val rid = roomId?.trim().orEmpty()
        if (rid.isEmpty() || !shouldAutoMarkReadSelectedRoom()) return
        val markId = scopedBatch
            .mapNotNull { it._id?.trim() }
            .filter(::isValidMarkReadMessageId)
            .maxOrNull()
            ?: cappedMessages
                .mapNotNull { it._id?.trim() }
                .firstOrNull { isValidMarkReadMessageId(it) }
        if (markId == null) return
        viewModelScope.launch {
            kotlinx.coroutines.yield()
            markRoomReadUpTo(rid, markId)
        }
    }

    /** While the user is in this room, never show a local unread badge on the tab/chip. */
    private fun clearUnreadWhileActivelyViewing(roomId: String) {
        val rid = roomId.trim()
        if (rid.isEmpty() || !isRoomActivelyViewed(rid)) return
        clearOptimisticUnreadFloor(rid)
        _state.update { st ->
            st.copy(rooms = clearUnreadForRoom(st.rooms, rid))
        }
        ChatSessionCache.update(_state.value.rooms)
        syncTabUnreadBadge()
        syncOverlayAllianceHubBadge()
    }

    /** After HTTP confirm — advance read cursor so peer gets room:read and ✓✓ on sender side. */
    private fun acknowledgeOwnOutgoingInActiveRoom(roomId: String, serverMessageId: String?) {
        clearUnreadWhileActivelyViewing(roomId)
        val id = serverMessageId?.trim().orEmpty()
        if (!isValidMarkReadMessageId(id)) return
        mergeReadCursor(roomId, id)
        viewModelScope.launch { markRoomReadUpTo(roomId, id) }
    }

    private fun recomputeRoomUnreadBadges() {
        val rooms = _state.value.rooms
        if (rooms.isEmpty()) return
        val adjusted = mergeRoomsUnreadFromServer(rooms)
        _state.update { it.copy(rooms = adjusted) }
        syncTabUnreadBadge(adjusted)
        ChatSessionCache.update(adjusted)
        syncOverlayAllianceHubBadge(adjusted)
    }

    /**
     * Reload per-room read cursors from SharedPreferences (e.g. user read in the main app)
     * and recompute tab badges before overlay or tab resume uses cached room lists.
     */
    fun syncReadStateFromPreferences() {
        hydrateReadCursorsFromPreferences()
        val rooms = _state.value.rooms
        if (rooms.isEmpty()) return
        val adjusted = mergeRoomsUnreadFromServer(rooms)
        syncTabUnreadBadge(adjusted)
        _state.update { it.copy(rooms = adjusted) }
        ChatSessionCache.update(adjusted)
        syncOverlayAllianceHubBadge(adjusted)
    }

    /** Overlay panel closed — wait for in-flight mark-read before releasing shared VM state. */
    suspend fun awaitPendingMarkRead() {
        markReadInFlight.toList().joinAll()
    }

    /** Mark every room with unread up to the latest known message (overlay DoneAll). */
    suspend fun markAllRoomsReadUpToLatest() {
        val rooms = _state.value.rooms
        if (rooms.isEmpty()) return
        val selectedId = _state.value.selectedRoomId?.trim().orEmpty()
        for (room in rooms) {
            val hasUnread = effectiveUnreadForRoom(room) > 0 || room.unreadCount > 0
            if (!hasUnread) continue
            val messageId = when (room.id.trim()) {
                selectedId -> _state.value.messages.firstOrNull()?._id?.trim().orEmpty()
                else -> ""
            }.takeIf { isValidMarkReadMessageId(it) }
                ?: repository.loadRecentMessages(room.id, beforeMessageId = null, limit = 1)
                    .getOrNull()
                    ?.firstOrNull()
                    ?._id
                    ?.trim()
                    .orEmpty()
                    .takeIf { isValidMarkReadMessageId(it) }
                ?: resolvedLastReadMessageId(room)?.trim().orEmpty().takeIf { it.isNotEmpty() }
            if (messageId.isNullOrBlank()) continue
            markRoomReadUpTo(room.id, messageId, forceSync = true)
        }
        awaitPendingMarkRead()
        recomputeRoomUnreadBadges()
        syncOverlayAllianceHubBadge()
        CombatOverlayService.clearHubUnreadState()
    }

    private fun startOverlayAutoMarkReadCollector() {
        overlayAutoMarkReadJob?.cancel()
        overlayAutoMarkReadJob = viewModelScope.launch {
            _state
                .map { st ->
                    Triple(
                        overlayChatPanelVisible,
                        st.selectedRoomId,
                        st.messages.firstOrNull()?._id,
                    )
                }
                .distinctUntilChanged()
                .collect { (visible, roomId, newestId) ->
                    if (!visible) return@collect
                    val rid = roomId?.trim().orEmpty()
                    if (rid.isEmpty() || !isValidMarkReadMessageId(newestId)) return@collect
                    if (!shouldAutoMarkReadSelectedRoom()) return@collect
                    markRoomReadUpTo(rid, newestId!!)
                }
        }
    }

    private fun scheduleBootstrap(
        preferAllianceHubRoom: Boolean = false,
        preferOverlayRaidRoom: Boolean = false,
        force: Boolean = false,
    ) {
        bootstrapJob?.cancel()
        bootstrapJob = viewModelScope.launch {
            val useOverlayTimeout = overlayChatPanelVisible ||
                preferAllianceHubRoom ||
                preferOverlayRaidRoom
            if (useOverlayTimeout) {
                val completed = withTimeoutOrNull(OVERLAY_PANEL_LOAD_MAX_MS) {
                    bootstrap(
                        preferAllianceHubRoom = preferAllianceHubRoom,
                        preferOverlayRaidRoom = preferOverlayRaidRoom,
                        force = force,
                    )
                }
                if (completed == null && overlayChatPanelVisible) {
                    _state.update {
                        if (it.isLoading || it.isRoomsLoading) {
                            it.copy(
                                isLoading = false,
                                isRoomsLoading = false,
                                error = it.error ?: res.getString(R.string.overlay_panel_load_timeout),
                            )
                        } else {
                            it
                        }
                    }
                }
            } else {
                bootstrap(
                    preferAllianceHubRoom = preferAllianceHubRoom,
                    preferOverlayRaidRoom = preferOverlayRaidRoom,
                    force = force,
                )
            }
        }
    }

    private fun overlayHubAlreadyReady(rooms: List<ChatRoomDto>): Boolean {
        val hubId = allianceHubRoomId(rooms) ?: return false
        return !_state.value.isRoomsLoading &&
            !_state.value.isLoading &&
            _state.value.error.isNullOrBlank() &&
            _state.value.selectedRoomId == hubId &&
            _state.value.messages.isNotEmpty() &&
            messagesBelongToRoom(_state.value.messages, hubId)
    }

    private fun overlayHubRoomsReady(rooms: List<ChatRoomDto>): Boolean {
        if (rooms.isEmpty()) return false
        val hubId = allianceHubRoomId(rooms) ?: return false
        return !_state.value.isRoomsLoading &&
            _state.value.error.isNullOrBlank() &&
            (_state.value.selectedRoomId == hubId || _state.value.selectedRoomId.isNullOrBlank())
    }

    /** Overlay panel: hub room selected with rooms list (messages may still load in background). */
    fun overlayHubRoomsReadyForPanel(): Boolean = overlayHubRoomsReady(_state.value.rooms)

    /** Overlay panel: hub room has messages to show without waiting on network. */
    fun overlayHubReadyForPanel(): Boolean =
        overlayHubAlreadyReady(_state.value.rooms) ||
            overlayHubRoomsReady(_state.value.rooms)

    private fun overlayRaidAlreadyReady(rooms: List<ChatRoomDto>): Boolean {
        val raidId = allianceRaidRoomId(rooms) ?: return false
        return !_state.value.isRoomsLoading &&
            !_state.value.isLoading &&
            _state.value.error.isNullOrBlank() &&
            _state.value.selectedRoomId == raidId &&
            _state.value.messages.isNotEmpty() &&
            messagesBelongToRoom(_state.value.messages, raidId)
    }

    private suspend fun resolveRoomsForBootstrap(
        preferAllianceHubRoom: Boolean,
        preferOverlayRaidRoom: Boolean = false,
    ): Result<List<ChatRoomDto>> {
        ChatSessionCache.getFreshRooms()?.let { return Result.success(it) }
        if ((preferAllianceHubRoom || preferOverlayRaidRoom) && _state.value.rooms.isNotEmpty()) {
            return Result.success(_state.value.rooms)
        }
        return repository.listRooms()
    }

    /** Refresh profile gate when returning from profile or opening chat. */
    fun refreshTeamProfileGate() {
        viewModelScope.launch {
            val hasTeam = loadTeamProfileGate()
            val cached = ChatSessionCache.getFreshRooms()
            val roomsResult = if (cached != null) {
                Result.success(cached)
            } else {
                repository.listRooms()
            }
            _state.value = if (roomsResult.isSuccess) {
                val nextRooms = applyRoomsFromServer(
                    roomsResult.getOrElse { _state.value.rooms },
                )
                syncRaidRoomPreference(nextRooms)
                applyPinBarUi(
                    _state.value.copy(
                        hasTeamProfileForGlobalChat = hasTeam,
                        rooms = nextRooms,
                    ),
                )
            } else {
                _state.value.copy(hasTeamProfileForGlobalChat = hasTeam)
            }
        }
    }

    /** Sync tab badges from API (e.g. after overlay chat or app resume). */
    fun syncRoomsFromServer(reconfirmVisibleRoom: Boolean = true) {
        if (!isChatTabActive && !overlayChatPanelVisible) return
        viewModelScope.launch {
            if (!isChatTabActive && !overlayChatPanelVisible) return@launch
            repository.listRooms()
                .onSuccess { raw ->
                    val next = applyRoomsFromServer(raw)
                    syncRaidRoomPreference(next)
                    publishRooms(next)
                    syncTabUnreadBadge(next)
                    reconcileStaleServerUnread(next, raw)
                    if (reconfirmVisibleRoom) {
                        reconfirmReadForVisibleRoom()
                    }
                    syncOverlayAllianceHubBadge(next)
                    lastRoomsSyncedAtMs = System.currentTimeMillis()
                    reconnectRealtimeIfNeeded()
                }
        }
    }

    /** При входе на вкладку «Чат» — комната «Альянс», если доступна. */
    fun ensureAllianceHubRoomSelected() {
        val hubId = allianceHubRoomId(_state.value.rooms) ?: return
        if (_state.value.selectedRoomId == hubId) return
        if (_state.value.rooms.isEmpty()) return
        selectRoom(hubId)
    }

    /** User left the Chat tab — stop treating selected room as actively viewed. */
    fun onChatTabPaused() {
        isChatTabActive = false
        snapshotSelectedRoomToMessageCache()
    }

    /** Returning to the Chat tab: refresh badges; read cursor only when room is visible. */
    fun onChatTabResumed() {
        isChatTabActive = true
        reconnectRealtimeIfNeeded()
        syncReadStateFromPreferences()
        viewModelScope.launch {
            // Let the tab compose and draw once before room sync / cache hydration.
            delay(32)
            if (!isChatTabActive) return@launch
            refreshStickerPackAccess()
            val cachedRooms = ChatSessionCache.getFreshRooms()
            val roomsStale = System.currentTimeMillis() - lastRoomsSyncedAtMs > ROOMS_SYNC_ON_RESUME_TTL_MS
            when {
                cachedRooms != null && _state.value.rooms.isEmpty() -> {
                    val next = applyRoomsFromServer(cachedRooms)
                    publishRooms(next)
                    lastRoomsSyncedAtMs = System.currentTimeMillis()
                }
                _state.value.rooms.isNotEmpty() && !roomsStale -> {
                    recomputeRoomUnreadBadges()
                }
                else -> {
                    syncRoomsFromServer(reconfirmVisibleRoom = false)
                }
            }
            val roomId = _state.value.selectedRoomId?.trim().orEmpty()
            if (roomId.isEmpty()) return@launch
            rehydrateSelectedRoomMessagesFromCache()
            refreshMessagesInBackground(roomId, force = true)
            if (_state.value.selectedRoomId.isNullOrBlank()) {
                ensureAllianceHubRoomSelected()
            }
            val activeRoomId = _state.value.selectedRoomId
            val newestId = _state.value.messages.firstOrNull()?._id
            if (!activeRoomId.isNullOrBlank() && !newestId.isNullOrBlank()) {
                markRoomReadUpTo(activeRoomId, newestId)
            }
        }
    }

    /** Overlay fullscreen chat closed — reclaim socket + refresh server unread counts. */
    fun onOverlayChatPanelClosed() {
        syncReadStateFromPreferences()
        viewModelScope.launch {
            syncRoomsFromServer()
            reconnectRealtimeIfNeeded()
        }
    }

    private fun sortChatRoomsForDisplay(rooms: List<ChatRoomDto>): List<ChatRoomDto> =
        rooms.sortedWith(
            compareBy<ChatRoomDto> { room ->
                when (ChatRoomKindResolver.kindOf(room)) {
                    ChatRoomKind.GlobalUnion -> 0
                    ChatRoomKind.Server -> 1
                    ChatRoomKind.AllianceHub -> 2
                    ChatRoomKind.Raid -> 3
                    ChatRoomKind.Other -> 4
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

    fun refreshStickerPackAccess() {
        viewModelScope.launch {
            val keys = usersRepository.getMyProfile().getOrNull()
                ?.enabledStickerPacks
                ?.toSet()
                ?: emptySet()
            _state.update { it.copy(enabledStickerPackKeys = keys) }
        }
    }

    private suspend fun loadTeamProfileGate(forceRefresh: Boolean = false): Boolean {
        val now = System.currentTimeMillis()
        if (!forceRefresh) {
            cachedTeamProfileGate?.let { cached ->
                if (now - profileGateLoadedAtMs < PROFILE_GATE_TTL_MS) {
                    return cached
                }
            }
        }
        val p = usersRepository.peekMyProfile()
            ?: usersRepository.getMyProfile().getOrNull()
        val keys = p?.enabledStickerPacks?.toSet() ?: emptySet()
        _state.value = _state.value.copy(
            enabledStickerPackKeys = keys,
            playerTeamSquadRole = p?.playerTeamSquadRole,
        )
        val hasTeam = p?.let {
            !it.teamDisplayName.isNullOrBlank() && !it.teamTag.isNullOrBlank()
        } ?: false
        cachedTeamProfileGate = hasTeam
        profileGateLoadedAtMs = now
        return hasTeam
    }

    private fun canModerateChat(message: ChatMessage): Boolean =
        canDeleteChatMessage(
            message = message,
            currentUserId = currentUserId,
            isAppAdmin = _state.value.isAppAdmin,
            playerTeamSquadRole = _state.value.playerTeamSquadRole,
        )

    private fun allianceHubRoomId(rooms: List<ChatRoomDto>): String? =
        ChatRoomKindResolver.allianceHubRoom(rooms)?.id

    private fun allianceRaidRoomId(rooms: List<ChatRoomDto>): String? =
        ChatRoomKindResolver.allianceRaidRoom(rooms)?.id
            ?: chatRoomPreferences.getRaidRoomId()?.trim()?.takeIf { it.isNotEmpty() }

    private fun messagesBelongToRoom(messages: List<ChatMessage>, roomId: String): Boolean {
        if (messages.isEmpty()) return true
        val rid = roomId.trim()
        if (rid.isEmpty()) return true
        return messages.all { it.roomId.trim() == rid }
    }

    private fun resolveOverlayPreferredRoomId(
        rooms: List<ChatRoomDto>,
        preferOverlayRaidRoom: Boolean,
    ): String? {
        val hubId = allianceHubRoomId(rooms)
        val raidId = allianceRaidRoomId(rooms)
        return when {
            preferOverlayRaidRoom && raidId != null -> raidId
            hubId != null -> hubId
            else -> rooms.minByOrNull { it.sortOrder }?.id ?: rooms.firstOrNull()?.id
        }
    }

    private fun isGlobalChatRoom(
        roomId: String,
        rooms: List<ChatRoomDto> = _state.value.rooms,
    ): Boolean =
        rooms.find { it.id == roomId }?.allianceId == ChatAllianceIds.GLOBAL

    private fun isRaidChatRoom(
        roomId: String,
        rooms: List<ChatRoomDto> = _state.value.rooms,
    ): Boolean {
        val room = rooms.find { it.id == roomId } ?: return false
        return ChatRoomKindResolver.kindOf(room) == ChatRoomKind.Raid
    }

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

    private suspend fun bootstrap(
        preferAllianceHubRoom: Boolean = false,
        preferOverlayRaidRoom: Boolean = false,
        force: Boolean = false,
        deferNetworkMessages: Boolean = false,
    ) {
        bootstrapMutex.withLock {
            if (!force) {
                if (preferOverlayRaidRoom && overlayRaidAlreadyReady(_state.value.rooms)) {
                    recomputeRoomUnreadBadges()
                    viewModelScope.launch { syncRoomsFromServer(reconfirmVisibleRoom = false) }
                    _state.value.selectedRoomId?.let { rid ->
                        if (_state.value.messages.size < PAGE_SIZE) {
                            refreshMessagesInBackground(rid, force = true)
                        }
                    }
                    return
                }
                if (preferAllianceHubRoom && overlayHubAlreadyReady(_state.value.rooms)) {
                    recomputeRoomUnreadBadges()
                    viewModelScope.launch { syncRoomsFromServer(reconfirmVisibleRoom = false) }
                    return
                }
            }
        }
        _state.update { st ->
            st.copy(
                isRoomsLoading = st.rooms.isEmpty() && st.messages.isEmpty(),
                error = null,
            )
        }
        val roomsResult = resolveRoomsForBootstrap(
            preferAllianceHubRoom = preferAllianceHubRoom,
            preferOverlayRaidRoom = preferOverlayRaidRoom,
        )
        val roomsRaw = roomsResult.getOrElse { e ->
            val fallback = ChatSessionCache.getFreshRooms()
                ?: launchDiskCache.loadChatRooms(currentUserId)
                ?: _state.value.rooms.takeIf { it.isNotEmpty() }
            if (!fallback.isNullOrEmpty()) {
                fallback
            } else {
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
        }
        val rooms = applyRoomsFromServer(roomsRaw)
        if (roomsResult.isSuccess) {
            ChatSessionCache.update(rooms)
            schedulePersistChatSnapshot()
        }
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
        syncOverlayAllianceHubBadge(rooms)
        val selected = resolveOverlayPreferredRoomId(
            rooms = rooms,
            preferOverlayRaidRoom = preferOverlayRaidRoom,
        ) ?: rooms.first().id
        chatRoomPreferences.setSelectedRoomId(selected)
        reconcileStaleServerUnread(rooms, roomsRaw)
        val cachedOverlayMessages = ChatSessionCache.getFreshMessages(selected)
            ?: roomMessageCache[selected]?.messages
        if (!cachedOverlayMessages.isNullOrEmpty()) {
            val capped = capNewestFirst(cachedOverlayMessages, PAGE_SIZE)
            roomMessageCache[selected] = RoomMessageCache(
                messages = capped,
                hasMoreOlder = cachedOverlayMessages.size >= PAGE_SIZE,
            )
            openRoom(
                selected,
                rooms,
                hadCachedMessages = true,
                deferNetworkMessages = deferNetworkMessages,
            )
        } else {
            openRoom(
                selected,
                rooms,
                deferNetworkMessages = deferNetworkMessages,
            )
        }
    }

    fun clearHistoryForSelectedRoom() {
        val roomId = _state.value.selectedRoomId?.trim().orEmpty()
        if (roomId.isEmpty() || _state.value.isLoading) return
        viewModelScope.launch {
            repository.clearRoomHistoryForUser(roomId)
                .onSuccess { response ->
                    response.hiddenBeforeMessageId?.trim()?.takeIf { it.isNotEmpty() }?.let {
                        chatRoomPreferences.setHiddenBeforeMessageId(roomId, it)
                    }
                    response.lastReadMessageId?.trim()?.takeIf { it.isNotEmpty() }?.let {
                        chatRoomPreferences.setLastReadMessageId(roomId, it)
                        lastMarkedReadByRoom[roomId] = it
                    }
                    applyClearedRoomHistoryLocal(roomId, response.unreadCount)
                    CombatOverlayService.notifyRoomHistoryCleared(roomId)
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        transientNotice = e.toUserMessageRu(getApplication<Application>().resources),
                    )
                }
        }
    }

    private fun applyClearedRoomHistoryLocal(roomId: String, unreadCount: Int) {
        synchronized(chatMutationLock) {
            roomMessageCache[roomId] = RoomMessageCache(
                messages = emptyList(),
                hasMoreOlder = false,
            )
            knownMessageIds.clear()
            messageIdIndex.clear()
            lazyColumnKeyByMessageId.clear()
        }
        _draftMessage.value = ""
        _pickedImageUris.value = emptyList()
        _listDerived.value = ChatMessagesListDerived.Empty
        ChatSessionCache.updateMessages(roomId, emptyList())
        val updatedRooms = _state.value.rooms.map {
            if (it.id == roomId) it.copy(unreadCount = unreadCount.coerceAtLeast(0)) else it
        }
        _state.value = _state.value.copy(
            messages = emptyList(),
            hasMoreOlder = false,
            isLoading = false,
            isLoadingOlder = false,
            error = null,
            replyToMessage = null,
            scrollToMessageId = null,
            highlightMessageId = null,
            transientNotice = null,
            activeActionMessageId = null,
            confirmDeleteMessageId = null,
            selectedMessageIds = emptySet(),
            confirmBulkDelete = false,
            isDeletingSelection = false,
            deletingMessageId = null,
            rooms = updatedRooms,
        )
        clearOptimisticUnreadFloor(roomId)
        ChatSessionCache.update(updatedRooms)
        syncTabUnreadBadge(updatedRooms)
        syncOverlayAllianceHubBadge(updatedRooms)
        schedulePersistChatSnapshot()
    }

    fun selectRoom(roomId: String) {
        if (roomId == _state.value.selectedRoomId) return
        if (isGlobalChatRoom(roomId)) {
            refreshTeamProfileGateLight()
        }
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
            ?: ChatSessionCache.getFreshMessages(roomId)?.let { sessionMessages ->
                val capped = capMessagesForMemory(sessionMessages)
                RoomMessageCache(
                    messages = capped,
                    hasMoreOlder = sessionMessages.size >= INITIAL_PAGE_SIZE,
                ).also { roomMessageCache[roomId] = it }
            }
        knownMessageIds.clear()
        messageIdIndex.clear()
        lazyColumnKeyByMessageId.clear()
        _draftMessage.value = ""
        _pickedImageUris.value = emptyList()
        _typingPeers.value = emptyMap()
        synchronized(typingPeerJobsLock) {
            typingPeerJobs.values.forEach { it.cancel() }
            typingPeerJobs.clear()
        }
        val cachedMessages = run {
            val fromCache = messagesWithoutLocallyRemoved(
                filterMessagesForRoom(cached?.messages.orEmpty(), roomId),
            )
            val visible = messagesWithoutLocallyRemoved(
                filterMessagesForRoom(_state.value.messages, roomId),
            )
            mergeVisibleMessagesWithRoomCache(
                visible = visible,
                cached = fromCache,
                roomId = roomId,
                maxMessages = messageMemoryCap,
                excludedMessageIds = locallyRemovedMessageIds,
                hiddenBeforeMessageId = hiddenBeforeForRoom(roomId),
            )
        }
        val hasCachedMessages = cachedMessages.isNotEmpty()
        _state.value = _state.value.copy(
            selectedRoomId = roomId,
            messages = cachedMessages,
            isLoading = !hasCachedMessages,
            hasMoreOlder = cached?.hasMoreOlder ?: true,
            isLoadingOlder = false,
            error = null,
            replyToMessage = null,
            editingMessage = null,
            scrollToMessageId = null,
            highlightMessageId = null,
            transientNotice = null,
            rooms = clearUnreadForRoomIfViewing(
                _state.value.rooms,
                roomId,
                treatAsViewing = isChatTabActive ||
                    (overlayChatPanelVisible && CombatOverlayService.isOverlayChatTabActive()),
            ),
        )
        pinHistoryByRoom[roomId] = pinHistoryPreferences.load(pinHistoryPreferences.chatScopeKey(roomId))
        pinBarIndexByRoom[roomId] = 0
        updatePinBarUi()
        if (hasCachedMessages) {
            knownMessageIds.addAll(cachedMessages.mapNotNull { it._id })
            rebuildMessageIdIndex(cachedMessages, messageIdIndex)
            publishMessagesDerivedImmediate(cachedMessages)
        } else {
            _listDerived.value = ChatMessagesListDerived.Empty
        }
        repository.ensureRoomJoined(roomId)
        viewModelScope.launch {
            chatRoomPreferences.setSelectedRoomId(roomId)
            if (previousRoomId != null && !previousNewestId.isNullOrBlank()) {
                launch(Dispatchers.IO) {
                    markRoomReadUpTo(previousRoomId, previousNewestId)
                }
            }
            openRoom(
                roomId = roomId,
                rooms = _state.value.rooms,
                hadCachedMessages = cached != null,
                messagesAlreadyInState = hasCachedMessages,
            )
        }
    }

    private fun clearUnreadForRoom(
        rooms: List<ChatRoomDto>,
        roomId: String,
    ): List<ChatRoomDto> = run {
        // The UI cleared unread, so optimistic unread floor must be cleared too
        // otherwise badges can keep accumulating after leaving and returning.
        clearOptimisticUnreadFloor(roomId)
        rooms.map { if (it.id == roomId) it.copy(unreadCount = 0) else it }
    }

    /** Avoid zeroing server unread in UI when the room is not actively viewed (background / other tab). */
    private fun clearUnreadForRoomIfViewing(
        rooms: List<ChatRoomDto>,
        roomId: String,
        treatAsViewing: Boolean = false,
    ): List<ChatRoomDto> {
        val viewing = treatAsViewing ||
            (_state.value.selectedRoomId == roomId && isRoomActivelyViewed(roomId))
        return if (viewing) clearUnreadForRoom(rooms, roomId) else rooms
    }

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
        hydrateReadCursorsFromPreferences()
        val sorted = sortChatRoomsForDisplay(serverRooms)
        val merged = mergeRoomsUnreadFromServer(sorted)
        hydrateReadCursorsFromRooms(sorted)
        ChatSessionCache.update(merged)
        flushPendingUnreadBumps()
        syncOverlayAllianceHubBadge(merged)
        syncTabUnreadBadge(merged)
        return merged
    }

    private fun mergeRoomsUnreadFromServer(serverRooms: List<ChatRoomDto>): List<ChatRoomDto> {
        val previousById = _state.value.rooms.associateBy { it.id }
        val pinInFlight = _state.value.pinInFlight
        val selectedRoomId = _state.value.selectedRoomId?.trim().orEmpty()
        return serverRooms.map { room ->
            val previousUnread = previousById[room.id]?.unreadCount ?: 0
            val serverUnread = when {
                isRoomActivelyViewed(room.id) -> 0
                else -> effectiveUnreadForRoom(room)
            }
            if (serverUnread == 0) {
                clearOptimisticUnreadFloor(room.id)
            }
            val floor = optimisticUnreadFloorByRoom[room.id] ?: 0
            val unread = displayedUnreadCount(
                effectiveUnread = serverUnread,
                previouslyDisplayed = previousUnread,
                rawServerUnread = room.unreadCount,
                optimisticFloor = floor,
            )
            if (unread == 0) {
                clearOptimisticUnreadFloor(room.id)
            }
            val resolvedLast = resolvedLastReadMessageId(room)
            room.copy(
                unreadCount = unread,
                lastReadMessageId = resolvedLast ?: room.lastReadMessageId,
            ).mergePinFromPrevious(
                previous = previousById[room.id],
                pinOperationInFlight = pinInFlight && room.id == selectedRoomId,
            )
        }
    }

    private fun clearOptimisticUnreadFloor(roomId: String) {
        if (roomId.isBlank()) return
        optimisticUnreadFloorByRoom.remove(roomId)
    }

    private fun syncTabUnreadBadge(rooms: List<ChatRoomDto> = _state.value.rooms) {
        val badge = ChatUnreadCounts.tabBadgeTotal(rooms)
        if (_state.value.tabUnreadBadge != badge) {
            _state.update { it.copy(tabUnreadBadge = badge) }
        }
    }

    private fun deviceLastReadMessageId(roomId: String): String? {
        val fromMemory = lastMarkedReadByRoom[roomId]?.trim().orEmpty()
        val fromPrefs = chatRoomPreferences.getLastReadMessageId(roomId)?.trim().orEmpty()
        return listOf(fromMemory, fromPrefs)
            .filter { it.isNotBlank() }
            .reduceOrNull { acc, next ->
                if (isObjectIdNewer(next, acc)) next else acc
            }
    }

    private fun deviceLastReadMessageId(room: ChatRoomDto): String? =
        deviceLastReadMessageId(room.id)

    private fun resolvedLastReadMessageId(room: ChatRoomDto): String? {
        val fromMemory = lastMarkedReadByRoom[room.id]?.trim().orEmpty()
        val fromPrefs = chatRoomPreferences.getLastReadMessageId(room.id)?.trim().orEmpty()
        val server = room.lastReadMessageId?.trim().orEmpty()
        return listOf(fromMemory, fromPrefs, server)
            .filter { it.isNotBlank() }
            .reduceOrNull { acc, next ->
                if (isObjectIdNewer(next, acc)) next else acc
            }
    }

    private fun effectiveUnreadForRoom(room: ChatRoomDto): Int =
        effectiveUnreadCount(
            serverUnread = room.unreadCount,
            lastReadMessageId = room.lastReadMessageId,
            localLastReadMessageId = deviceLastReadMessageId(room),
        )

    private fun shouldTrackUnreadForMessage(roomId: String, messageId: String): Boolean {
        val room = _state.value.rooms.find { it.id == roomId }
        val lastRead = room?.let { resolvedLastReadMessageId(it) }
            ?: chatRoomPreferences.getLastReadMessageId(roomId)
        if (lastRead != null && !isObjectIdNewer(messageId, lastRead)) {
            return false
        }
        return true
    }

    private fun bumpRoomUnreadLocally(roomId: String, messageId: String) {
        if (!unreadBumpedMessageIds.add(messageId)) return
        while (unreadBumpedMessageIds.size > 512) {
            val oldest = unreadBumpedMessageIds.first()
            unreadBumpedMessageIds.remove(oldest)
        }
        val room = _state.value.rooms.find { it.id == roomId }
        val effectiveBase = room?.let { effectiveUnreadForRoom(it) } ?: 0
        val prevDisplayed = room?.unreadCount ?: 0
        val nextFloor = ((optimisticUnreadFloorByRoom[roomId] ?: 0) + 1).coerceAtMost(999)
        optimisticUnreadFloorByRoom[roomId] = nextFloor
        val displayed = displayedUnreadCount(
            effectiveUnread = effectiveBase + 1,
            previouslyDisplayed = prevDisplayed,
            rawServerUnread = room?.unreadCount ?: 0,
            optimisticFloor = nextFloor,
        )
        _state.update { st ->
            st.copy(
                rooms = st.rooms.map { r ->
                    if (r.id != roomId) r else r.copy(unreadCount = displayed)
                },
            )
        }
        ChatSessionCache.update(_state.value.rooms)
        syncTabUnreadBadge()
        if (room != null && ChatRoomKindResolver.isAllianceHubRoom(room)) {
            syncOverlayAllianceHubBadge(_state.value.rooms)
        }
    }

    private fun scheduleUnreadSyncFromServer() {
        unreadSyncJob?.cancel()
        unreadSyncJob = viewModelScope.launch {
            delay(UNREAD_SYNC_DEBOUNCE_MS)
            syncRoomsFromServer(reconfirmVisibleRoom = false)
        }
    }

    private fun shouldSkipBackgroundMessageRefreshForRoom(roomId: String): Boolean {
        val rid = roomId.trim()
        if (rid.isEmpty()) return false
        val visible = filterMessagesForRoom(_state.value.messages, rid)
        val sessionCache = ChatSessionCache.getFreshMessages(rid)
        val roomCache = roomMessageCache[rid]?.messages?.let {
            messagesWithoutLocallyRemoved(filterMessagesForRoom(it, rid))
        }
        return shouldSkipBackgroundMessageRefresh(
            visible = visible,
            sessionCache = sessionCache,
            roomCache = roomCache,
            pageSize = PAGE_SIZE,
        )
    }

    /** Pull peer rows from [roomMessageCache] into visible UI after tab/foreground resume. */
    private fun rehydrateSelectedRoomMessagesFromCache(): Boolean {
        val roomId = _state.value.selectedRoomId?.trim().orEmpty()
        if (roomId.isEmpty()) return false
        return rehydrateRoomMessagesFromCache(roomId)
    }

    /** Merge socket stash in [roomMessageCache] into the visible list for [roomId]. */
    private fun rehydrateRoomMessagesFromCache(roomId: String): Boolean {
        val rid = roomId.trim()
        if (rid.isEmpty()) return false
        val cachedEntry = roomMessageCache[rid]
        val visible = messagesWithoutLocallyRemoved(
            filterMessagesForRoom(_state.value.messages, rid),
        )
        if (cachedEntry == null) {
            if (visible.isEmpty()) return false
            roomMessageCache[rid] = RoomMessageCache(
                messages = capMessagesForMemory(visible),
                hasMoreOlder = _state.value.hasMoreOlder,
            )
            ChatSessionCache.updateMessages(rid, roomMessageCache[rid]!!.messages)
            return false
        }
        val cached = messagesWithoutLocallyRemoved(
            filterMessagesForRoom(cachedEntry.messages, rid),
        )
        val merged = mergeVisibleMessagesWithRoomCache(
            visible = visible,
            cached = cached,
            roomId = rid,
            maxMessages = messageMemoryCap,
            excludedMessageIds = locallyRemovedMessageIds,
            hiddenBeforeMessageId = hiddenBeforeForRoom(rid),
        )
        if (chatMessagesListContentEqual(visible, merged)) return false
        knownMessageIds.clear()
        messageIdIndex.clear()
        knownMessageIds.addAll(merged.mapNotNull { it._id })
        rebuildMessageIdIndex(merged, messageIdIndex)
        val hasMoreOlder = cachedEntry.hasMoreOlder
        roomMessageCache[rid] = RoomMessageCache(
            messages = merged,
            hasMoreOlder = hasMoreOlder,
        )
        ChatSessionCache.updateMessages(rid, merged)
        _state.update { st ->
            st.copy(
                messages = merged,
                selectedRoomId = rid,
                hasMoreOlder = hasMoreOlder,
                isLoading = false,
                newestMessageKey = merged.firstOrNull()?._id ?: st.newestMessageKey,
            )
        }
        publishMessagesDerived(merged)
        return true
    }

    /** Keep RAM cache aligned with visible feed when overlay/tab closes. */
    private fun snapshotSelectedRoomToMessageCache() {
        val roomId = _state.value.selectedRoomId?.trim().orEmpty()
        if (roomId.isEmpty()) return
        val visible = messagesWithoutLocallyRemoved(
            filterMessagesForRoom(_state.value.messages, roomId),
        )
        if (visible.isEmpty()) return
        val capped = capMessagesForMemory(visible)
        val prev = roomMessageCache[roomId]
        roomMessageCache[roomId] = RoomMessageCache(
            messages = capped,
            hasMoreOlder = prev?.hasMoreOlder ?: _state.value.hasMoreOlder,
        )
        ChatSessionCache.updateMessages(roomId, capped)
    }

    private suspend fun reconfirmReadForVisibleRoom() {
        val roomId = _state.value.selectedRoomId ?: return
        val newestId = _state.value.messages.firstOrNull()?._id ?: return
        markRoomReadUpTo(roomId, newestId)
    }

    /**
     * Server unread can lag; re-push read cursor when API still reports unread
     * but local cursor proves the room was read (including legacy prefs or missing server state).
     * Skips optimistic socket bumps (displayed > raw API count).
     */
    private suspend fun reconcileStaleServerUnread(
        mergedRooms: List<ChatRoomDto>,
        rawServerRooms: List<ChatRoomDto>,
    ) {
        val rawById = rawServerRooms.associateBy { it.id }
        for (room in mergedRooms) {
            val raw = rawById[room.id] ?: continue
            if ((optimisticUnreadFloorByRoom[room.id] ?: 0) > 0) continue
            if (raw.unreadCount <= 0) continue
            val effectiveRaw = effectiveUnreadCount(
                serverUnread = raw.unreadCount,
                lastReadMessageId = raw.lastReadMessageId,
                localLastReadMessageId = deviceLastReadMessageId(room),
            )
            if (effectiveRaw > 0 && room.unreadCount > raw.unreadCount) continue
            val localLast = deviceLastReadMessageId(room) ?: continue
            val serverLast = raw.lastReadMessageId?.trim().orEmpty()
            val localAhead =
                serverLast.isBlank() || isObjectIdNewer(localLast, serverLast)
            if (!localAhead) continue
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
        clearOptimisticUnreadFloor(roomId)
        val job = viewModelScope.launch {
            mergeReadCursor(roomId, messageId)
            ChatSessionCache.patchRoomRead(roomId, messageId)
            _state.update { st ->
                st.copy(rooms = clearUnreadForRoom(st.rooms, roomId))
            }
            syncTabUnreadBadge()
            ChatSessionCache.update(_state.value.rooms)
            if (ChatRoomKindResolver.allianceHubRoom(_state.value.rooms)?.id == roomId) {
                CombatOverlayService.notifyAllianceHubUnread(0)
            }
            repository.markRoomRead(roomId, messageId)
                .onSuccess { response ->
                    mergeReadCursor(roomId, messageId)
                    ChatSessionCache.patchRoomRead(roomId, messageId)
                    if (response.unreadCount <= 0) {
                        _state.update { st ->
                            st.copy(rooms = clearUnreadForRoom(st.rooms, roomId))
                        }
                        syncTabUnreadBadge()
                        ChatSessionCache.update(_state.value.rooms)
                        if (ChatRoomKindResolver.allianceHubRoom(_state.value.rooms)?.id == roomId) {
                            CombatOverlayService.notifyAllianceHubUnread(0)
                        }
                    } else {
                        scheduleUnreadSyncFromServer()
                    }
                }
        }
        markReadInFlight.add(job)
        job.invokeOnCompletion { markReadInFlight.remove(job) }
    }

    private fun onRoomUnreadFromServer(event: ChatRoomUnreadEvent) {
        val roomId = event.roomId.trim()
        if (roomId.isBlank()) return
        if (roomId == _state.value.selectedRoomId && isRoomActivelyViewed(roomId)) {
            clearUnreadWhileActivelyViewing(roomId)
            _state.value.messages.firstOrNull()?._id?.let { newestId ->
                if (isValidMarkReadMessageId(newestId)) {
                    viewModelScope.launch { markRoomReadUpTo(roomId, newestId) }
                }
            }
            return
        }
        if (_state.value.rooms.none { it.id == roomId }) {
            scheduleUnreadSyncFromServer()
            return
        }
        val serverLast = event.lastReadMessageId?.trim().orEmpty()
        val serverUnread = event.unreadCount.coerceAtLeast(0)
        val roomDto = _state.value.rooms.find { it.id == roomId }
        val localLast = roomDto?.let { deviceLastReadMessageId(it) }
            ?: chatRoomPreferences.getLastReadMessageId(roomId)
        if (serverUnread > 0 && !localLast.isNullOrBlank()) {
            val suppressed = effectiveUnreadCount(
                serverUnread = serverUnread,
                lastReadMessageId = serverLast.takeIf { it.isNotEmpty() },
                localLastReadMessageId = localLast,
            ) == 0
            if (suppressed) {
                clearOptimisticUnreadFloor(roomId)
                _state.update { st ->
                    val rooms = st.rooms.map { room ->
                        if (room.id != roomId) room
                        else room.copy(
                            unreadCount = 0,
                            lastReadMessageId = localLast,
                        )
                    }
                    st.copy(rooms = rooms)
                }
                ChatSessionCache.update(_state.value.rooms)
                syncTabUnreadBadge()
                syncOverlayAllianceHubBadge()
                viewModelScope.launch { markRoomReadUpTo(roomId, localLast, forceSync = true) }
                return
            }
        }
        val selectedId = _state.value.selectedRoomId
        if (serverLast.isNotBlank() &&
            (serverUnread > 0 || (selectedId == roomId && isChatTabActive))
        ) {
            mergeReadCursor(roomId, serverLast)
        }
        _state.update { st ->
            val rooms = st.rooms.map { room ->
                if (room.id != roomId) room
                else {
                    val floor = optimisticUnreadFloorByRoom[roomId] ?: 0
                    val prevDisplayed = room.unreadCount
                    val merged = room.copy(
                        unreadCount = serverUnread,
                        lastReadMessageId = serverLast.takeIf { s -> s.isNotBlank() }
                            ?: room.lastReadMessageId,
                    )
                    val effective = effectiveUnreadForRoom(merged)
                    val displayed = displayedUnreadCount(
                        effectiveUnread = effective,
                        previouslyDisplayed = prevDisplayed,
                        rawServerUnread = serverUnread,
                        optimisticFloor = floor,
                    )
                    val next = merged.copy(unreadCount = displayed)
                    if (displayed == 0) clearOptimisticUnreadFloor(roomId)
                    next
                }
            }
            st.copy(rooms = rooms)
        }
        ChatSessionCache.update(_state.value.rooms)
        syncTabUnreadBadge()
        syncOverlayAllianceHubBadge()
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
            onRoomUnread = ::onRoomUnreadFromServer,
            onRoomPinChanged = ::onRoomPinChanged,
            onHistoryCleared = ::onChatHistoryClearedFromServer,
        )
    }

    /** Gap-fill after socket reconnect: merge stash + REST for the visible room. */
    private fun onChatSocketConnected() {
        rehydrateSelectedRoomMessagesFromCache()
        if (!isChatTabActive && !overlayChatPanelVisible) return
        reconnectRealtimeIfNeeded()
        val roomId = _state.value.selectedRoomId?.trim().orEmpty()
        if (roomId.isNotEmpty()) {
            refreshMessagesInBackground(roomId, force = true)
        }
    }

    private suspend fun openRoom(
        roomId: String,
        rooms: List<ChatRoomDto>,
        hadCachedMessages: Boolean = false,
        messagesAlreadyInState: Boolean = false,
        deferNetworkMessages: Boolean = false,
    ) {
        typingEmitJob?.cancel()
        typingEmitJob = null
        synchronized(typingPeerJobsLock) {
            typingPeerJobs.values.forEach { it.cancel() }
            typingPeerJobs.clear()
        }
        if (!messagesAlreadyInState) {
            knownMessageIds.clear()
            messageIdIndex.clear()
            _draftMessage.value = ""
            _typingPeers.value = emptyMap()
        }
        _state.update { st ->
            st.copy(rooms = clearUnreadForRoomIfViewing(st.rooms.ifEmpty { rooms }, roomId, treatAsViewing = true))
        }
        val isGlobalRoom = isGlobalChatRoom(roomId, rooms)
        val hasTeam = when {
            isGlobalRoom -> loadTeamProfileGate()
            hadCachedMessages && cachedTeamProfileGate != null -> cachedTeamProfileGate == true
            messagesAlreadyInState -> _state.value.hasTeamProfileForGlobalChat
            else -> loadTeamProfileGate()
        }
        if (!hadCachedMessages) {
            _state.value = _state.value.copy(
                isLoading = true,
                isRoomsLoading = false,
                rooms = clearUnreadForRoomIfViewing(rooms, roomId, treatAsViewing = true),
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
                scrollToMessageId = null,
                highlightMessageId = null,
                transientNotice = null,
                sendFailure = null,
            )
            _otherReadUptoMessageId.value = otherReadUptoByRoom[roomId]
            _listDerived.value = ChatMessagesListDerived.Empty
        } else {
            _otherReadUptoMessageId.value = otherReadUptoByRoom[roomId]
            _state.value = _state.value.copy(
                isRoomsLoading = false,
                rooms = clearUnreadForRoomIfViewing(rooms, roomId, treatAsViewing = true),
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
            onRoomUnread = ::onRoomUnreadFromServer,
            onRoomPinChanged = ::onRoomPinChanged,
            onHistoryCleared = ::onChatHistoryClearedFromServer,
        )
        val cached = roomMessageCache[roomId]
        if (hadCachedMessages && cached != null && cached.messages.isNotEmpty()) {
            rehydrateRoomMessagesFromCache(roomId)
            val filteredCache = messagesWithoutLocallyRemoved(
                filterMessagesForRoom(roomMessageCache[roomId]?.messages.orEmpty(), roomId),
            )
            if (roomMessageCache[roomId]?.messages?.size != filteredCache.size) {
                roomMessageCache[roomId] = cached.copy(messages = capMessagesForMemory(filteredCache))
                ChatSessionCache.updateMessages(roomId, roomMessageCache[roomId]!!.messages)
            }
            if (!messagesAlreadyInState) {
                knownMessageIds.clear()
                messageIdIndex.clear()
                knownMessageIds.addAll(filteredCache.mapNotNull { it._id })
                rebuildMessageIdIndex(filteredCache, messageIdIndex)
                _state.value = _state.value.copy(
                    isLoading = false,
                    messages = filteredCache,
                    selectedRoomId = roomId,
                    hasMoreOlder = cached.hasMoreOlder,
                    rooms = clearUnreadForRoomIfViewing(_state.value.rooms, roomId, treatAsViewing = true),
                )
                publishMessagesDerived(filteredCache)
            } else {
                _state.update {
                    it.copy(
                        isLoading = false,
                        selectedRoomId = roomId,
                        hasMoreOlder = cached.hasMoreOlder,
                        rooms = clearUnreadForRoomIfViewing(it.rooms, roomId, treatAsViewing = true),
                    )
                }
            }
            if (shouldAutoMarkReadSelectedRoom()) {
                _state.value.messages.firstOrNull()?._id?.let { newestId ->
                    markRoomReadUpTo(roomId, newestId)
                }
            }
            if (!shouldSkipBackgroundMessageRefreshForRoom(roomId)) {
                refreshMessagesInBackground(roomId)
            } else if (filteredCache.size < PAGE_SIZE) {
                refreshMessagesInBackground(roomId, force = true)
            }
            schedulePersistChatSnapshot()
            return
        }
        if (deferNetworkMessages) {
            launchWarmupNeedsMessages = true
            schedulePersistChatSnapshot()
            return
        }
        repository.loadRecentMessages(roomId, beforeMessageId = null, limit = INITIAL_PAGE_SIZE)
            .onSuccess { loaded ->
                applyLoadedMessagePage(
                    roomId = roomId,
                    loaded = loaded,
                    pageSizeForHasMore = INITIAL_PAGE_SIZE,
                )
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

    private fun refreshMessagesInBackground(roomId: String, force: Boolean = false) {
        if (!force && shouldSkipBackgroundMessageRefreshForRoom(roomId)) return
        val deferMs = if (ChatSessionCache.getFreshMessages(roomId) != null) {
            BACKGROUND_MESSAGE_REFRESH_DEFER_MS
        } else {
            0L
        }
        viewModelScope.launch {
            if (deferMs > 0L) delay(deferMs)
            if (_state.value.selectedRoomId != roomId) return@launch
            val overlayEmptyLoad = overlayChatPanelVisible && _state.value.messages.isEmpty()
            val loadResult = if (overlayEmptyLoad) {
                withTimeoutOrNull(OVERLAY_PANEL_LOAD_MAX_MS) {
                    repository.loadRecentMessages(roomId, beforeMessageId = null, limit = PAGE_SIZE)
                }
            } else {
                repository.loadRecentMessages(roomId, beforeMessageId = null, limit = PAGE_SIZE)
            }
            if (loadResult == null) {
                if (overlayEmptyLoad && _state.value.selectedRoomId == roomId) {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = it.error ?: res.getString(R.string.overlay_panel_load_timeout),
                        )
                    }
                }
                return@launch
            }
            loadResult
                .onSuccess { loaded ->
                    if (_state.value.selectedRoomId != roomId) return@onSuccess
                    val hasMoreOlder = loaded.size >= PAGE_SIZE
                    val current = _state.value.messages
                    val merged = withContext(Dispatchers.Default) {
                        mergeLoadedPageWithExisting(
                            existing = current,
                            loaded = loaded,
                            maxMessages = messageMemoryCap,
                            excludedMessageIds = locallyRemovedMessageIds,
                            roomId = roomId,
                        )
                    }
                    val unchanged = withContext(Dispatchers.Default) {
                        chatMessagesListContentEqual(current, merged)
                    }
                    if (unchanged) {
                        roomMessageCache[roomId] = RoomMessageCache(
                            messages = merged,
                            hasMoreOlder = hasMoreOlder,
                        )
                        ChatSessionCache.updateMessages(roomId, merged)
                        if (_state.value.hasMoreOlder != hasMoreOlder) {
                            _state.update { it.copy(hasMoreOlder = hasMoreOlder) }
                        }
                        return@onSuccess
                    }
                    knownMessageIds.clear()
                    messageIdIndex.clear()
                    knownMessageIds.addAll(merged.mapNotNull { it._id })
                    rebuildMessageIdIndex(merged, messageIdIndex)
                    roomMessageCache[roomId] = RoomMessageCache(
                        messages = merged,
                        hasMoreOlder = hasMoreOlder,
                    )
                    ChatSessionCache.updateMessages(roomId, merged)
                    _state.update {
                        it.copy(
                            messages = merged,
                            hasMoreOlder = hasMoreOlder,
                            newestMessageKey = merged.firstOrNull()?._id,
                        )
                    }
                    publishMessagesDerived(merged)
                }
                .onFailure { e ->
                    if (
                        overlayChatPanelVisible &&
                        _state.value.selectedRoomId == roomId &&
                        _state.value.messages.isEmpty()
                    ) {
                        _state.update {
                            it.copy(
                                isLoading = false,
                                error = e.toUserMessageRu(res),
                            )
                        }
                    }
                }
        }
    }

    private fun applyLoadedMessagePage(
        roomId: String,
        loaded: List<ChatMessage>,
        pageSizeForHasMore: Int,
    ) {
        val current = if (_state.value.selectedRoomId == roomId) {
            filterMessagesForRoom(_state.value.messages, roomId)
        } else {
            emptyList()
        }
        val merged = mergeLoadedPageWithExisting(
            existing = current,
            loaded = loaded,
            maxMessages = messageMemoryCap,
            excludedMessageIds = locallyRemovedMessageIds,
            roomId = roomId,
        )
        ChatSessionCache.updateMessages(roomId, merged)
        knownMessageIds.clear()
        messageIdIndex.clear()
        knownMessageIds.addAll(merged.mapNotNull { it._id })
        val capped = capMessagesForMemory(merged)
        rebuildMessageIdIndex(capped, messageIdIndex)
        val hasMoreOlder = loaded.size >= pageSizeForHasMore
        roomMessageCache[roomId] = RoomMessageCache(
            messages = capped,
            hasMoreOlder = hasMoreOlder,
        )
        _state.value = _state.value.copy(
            isLoading = false,
            messages = capped,
            selectedRoomId = roomId,
            hasMoreOlder = hasMoreOlder,
            rooms = clearUnreadForRoomIfViewing(_state.value.rooms, roomId, treatAsViewing = true),
        )
        publishMessagesDerived(capped)
        if (shouldAutoMarkReadSelectedRoom()) {
            capped.firstOrNull()?._id?.let { newestId ->
                viewModelScope.launch { markRoomReadUpTo(roomId, newestId) }
            }
        }
        schedulePersistChatSnapshot()
    }

    fun setMessageListScrollInProgress(inProgress: Boolean) {
        val flush = listDeriveDefer.setScrollInProgress(inProgress)
        if (flush != null) {
            publishMessagesDerivedNow(flush)
        }
    }

    private fun publishMessagesDerived(messages: List<ChatMessage>) {
        deriveJob?.cancel()
        deriveDebounceJob?.cancel()
        if (messages.isEmpty()) {
            _listDerived.value = ChatMessagesListDerived.Empty
            return
        }
        if (listDeriveDefer.deferFullDerive(messages)) return
        publishMessagesDerivedNow(messages)
    }

    private fun publishMessagesDerivedNow(messages: List<ChatMessage>) {
        deriveJob?.cancel()
        deriveDebounceJob?.cancel()
        if (messages.isEmpty()) {
            _listDerived.value = ChatMessagesListDerived.Empty
            return
        }
        val expected = messages
        deriveDebounceJob = viewModelScope.launch {
            delay(CHAT_DERIVE_DEBOUNCE_MS)
            deriveJob = launch(Dispatchers.Default) {
                val derived = buildChatMessagesListDerived(expected)
                if (chatMessagesListContentEqual(expected, _state.value.messages)) {
                    _listDerived.value = derived
                    withContext(Dispatchers.Main) {
                        maybeRefreshPinBarUi()
                    }
                }
            }
        }
    }

    private fun maybeRefreshPinBarUi() {
        val st = _state.value
        val roomId = st.selectedRoomId?.trim().orEmpty()
        if (roomId.isEmpty()) return
        val room = st.rooms.find { it.id == roomId } ?: return
        if (room.pinnedMessageId.isNullOrBlank()) return
        updatePinBarUi()
    }

    /** Reactions / single-row edits: keep timeline shape, patch one row off the UI thread. */
    private fun publishMessagesDerivedAfterPatch(messages: List<ChatMessage>, messageIndex: Int) {
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
        deriveJob = viewModelScope.launch(Dispatchers.Default) {
            val derived = buildChatMessagesListDerivedAfterPatchMessage(
                previousDerived = previousDerived,
                messages = expected,
                messageIndex = idx,
            )
            if (chatMessagesListContentEqual(expected, _state.value.messages)) {
                _listDerived.value = derived
            }
        }
    }

    /** Мгновенная лента при переключении комнаты (кэш уже в памяти). */
    private fun publishMessagesDerivedImmediate(messages: List<ChatMessage>) {
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
        deriveJob = viewModelScope.launch(Dispatchers.Default) {
            val derived = buildChatMessagesListDerived(expected)
            if (_state.value.selectedRoomId == null) return@launch
            if (!chatMessagesListContentEqual(expected, _state.value.messages)) return@launch
            _listDerived.value = derived
        }
    }

    private suspend fun buildDerivedAfterUpsert(
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

    fun loadOlderMessages() {
        if (!_state.value.hasMoreOlder || _state.value.isLoadingOlder || _state.value.isLoading) {
            return
        }
        viewModelScope.launch { loadOlderMessagesAwait() }
    }

    /** Loads one older page; returns whether any messages were appended. */
    private suspend fun loadOlderMessagesAwait(): Boolean {
        val roomId = _state.value.selectedRoomId ?: return false
        val oldestId = _state.value.messages.lastOrNull()?._id ?: return false
        if (!_state.value.hasMoreOlder || _state.value.isLoadingOlder || _state.value.isLoading) {
            return false
        }
        _state.value = _state.value.copy(isLoadingOlder = true)
        val result = repository.loadRecentMessages(
            roomId = roomId,
            beforeMessageId = oldestId,
            limit = PAGE_SIZE,
        )
        result
            .onSuccess { older ->
                if (older.isEmpty()) {
                    _state.value = _state.value.copy(
                        isLoadingOlder = false,
                        hasMoreOlder = false,
                    )
                } else {
                    val merged = mergeOlderPage(_state.value.messages, older, knownMessageIds)
                    rebuildMessageIdIndex(merged, messageIdIndex)
                    _state.value = _state.value.copy(
                        messages = merged,
                        isLoadingOlder = false,
                        hasMoreOlder = older.size >= PAGE_SIZE,
                    )
                    publishMessagesDerived(merged)
                }
            }
            .onFailure { e ->
                _state.value = _state.value.copy(
                    isLoadingOlder = false,
                    error = e.toUserMessageRu(res),
                )
            }
        return result.isSuccess && result.getOrNull()?.isNotEmpty() == true
    }

    fun jumpToQuotedMessage(messageId: String) {
        val id = messageId.trim()
        if (id.isEmpty()) return
        viewModelScope.launch {
            val found = jumpToChatPinnedMessage(
                messageId = id,
                messageIdsNewestFirst = _state.value.messages.mapNotNull { it._id },
                hasMoreOlder = { _state.value.hasMoreOlder },
                isLoadingOlder = { _state.value.isLoadingOlder },
                loadOlder = { loadOlderMessagesAwait() },
                timelineIndexForMessageId = { targetId ->
                    chatTimelineIndexForMessageId(
                        _listDerived.value.timeline,
                        _state.value.messages,
                        targetId,
                    )
                },
                onJumpToMessage = { targetId ->
                    _state.update {
                        it.copy(
                            scrollToMessageId = targetId,
                            highlightMessageId = targetId,
                            transientNotice = null,
                        )
                    }
                },
            )
            if (!found) {
                _state.update {
                    it.copy(
                        transientNotice = res.getString(R.string.chat_jump_quote_not_found),
                        scrollToMessageId = null,
                        highlightMessageId = null,
                    )
                }
            }
        }
    }

    fun consumeScrollToMessage() {
        _state.update { it.copy(scrollToMessageId = null) }
    }

    fun clearHighlightMessage() {
        _state.update { it.copy(highlightMessageId = null) }
    }

    fun consumeTransientNotice() {
        _state.update { it.copy(transientNotice = null) }
    }

    fun sendMessage(text: String, replyOverride: String? = null) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return
        val roomId = _state.value.selectedRoomId ?: return
        if (isRaidChatRoom(roomId) && StickerPacks.stemForMessage(trimmed) != null) return
        val replyToMessageId = replyOverride ?: _state.value.replyToMessage?._id
        if (globalSendBlocked(roomId, trimmed, replyToMessageId)) return
        val optimistic = buildOptimisticOutgoingMessage(roomId, trimmed, replyToMessageId)
        val pendingId = optimistic._id
        _state.value = _state.value.copy(isSending = false, error = null, sendFailure = null)
        registerInFlightOutgoing(roomId, trimmed, replyToMessageId)
        activeOutgoingPendingId = pendingId
        deriveJob?.cancel()
        deriveDebounceJob?.cancel()
        insertOptimisticOutgoingSynchronously(optimistic, clearComposer = true)
        viewModelScope.launch {
            repository.sendMessageWithRetriesForChatUi(trimmed, roomId, replyToMessageId)
                .onSuccess { sent ->
                    withContext(Dispatchers.Main.immediate) {
                        confirmPendingOutgoingMessage(pendingId, sent)
                    }
                }
                .onFailure { throwable ->
                    activeOutgoingPendingId = null
                    clearInFlightOutgoing(roomId, trimmed, replyToMessageId)
                    removePendingOutgoingMessage(pendingId)
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
        val editing = _state.value.editingMessage
        if (editing != null) {
            val id = editing._id?.trim().orEmpty()
            val trimmed = _draftMessage.value.trim()
            if (id.isEmpty() || trimmed.isBlank()) return
            editMessage(id, trimmed)
            cancelEditMessage()
            return
        }
        val text = _draftMessage.value.trim()
        if (text.isBlank() && _pickedImageUris.value.isEmpty()) return
        val roomId = _state.value.selectedRoomId ?: return
        val replyToMessageId = _state.value.replyToMessage?._id
        val uris = _pickedImageUris.value
        if (globalSendBlocked(roomId, text, replyToMessageId)) return

        if (uris.isEmpty() && text.isNotBlank()) {
            val optimistic = buildOptimisticOutgoingMessage(roomId, text, replyToMessageId)
            val pendingId = optimistic._id
            _state.value = _state.value.copy(isSending = false, error = null, sendFailure = null)
            registerInFlightOutgoing(roomId, text, replyToMessageId)
            activeOutgoingPendingId = pendingId
            deriveJob?.cancel()
            deriveDebounceJob?.cancel()
            insertOptimisticOutgoingSynchronously(optimistic, clearComposer = true)
            viewModelScope.launch {
                repository.sendMessageWithRetriesForChatUi(
                    text = text.trim(),
                    roomId = roomId,
                    replyToMessageId = replyToMessageId,
                )
                    .onSuccess { sent ->
                        withContext(Dispatchers.Main.immediate) {
                            confirmPendingOutgoingMessage(pendingId, sent)
                        }
                    }
                    .onFailure { throwable ->
                        activeOutgoingPendingId = null
                        clearInFlightOutgoing(roomId, text, replyToMessageId)
                        removePendingOutgoingMessage(pendingId)
                        _state.value = _state.value.copy(
                            isSending = false,
                            sendFailure = ChatSendFailure(
                                messageText = text,
                                replyToMessageId = replyToMessageId,
                                errorMessage = throwable.toUserMessageRu(res),
                            ),
                        )
                    }
            }
            return
        }
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
                repository.sendMessageWithRetriesForChatUi(
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
        val roomId = _state.value.selectedRoomId ?: return
        if (isRaidChatRoom(roomId)) return
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
                delay(280)
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
            editingMessage = null,
            activeActionMessageId = null,
            selectedMessageIds = emptySet(),
            confirmBulkDelete = false,
        )
    }

    fun clearReplyToMessage() {
        if (_state.value.replyToMessage == null) return
        _state.value = _state.value.copy(replyToMessage = null)
    }

    fun beginEditMessage(messageId: String) {
        val target = _state.value.messages.find { it._id == messageId } ?: return
        if (target.deletedAt != null || target.text.isBlank()) return
        _draftMessage.value = target.text
        _state.value = _state.value.copy(
            editingMessage = target,
            replyToMessage = null,
            activeActionMessageId = null,
            selectedMessageIds = emptySet(),
            confirmBulkDelete = false,
        )
    }

    fun cancelEditMessage() {
        if (_state.value.editingMessage == null) return
        _draftMessage.value = ""
        _state.value = _state.value.copy(editingMessage = null)
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
        val patchIndex = previousMessages.indexOfFirst { it._id == messageId }
        val optimistic = applyOptimisticReactionToggle(
            messages = previousMessages,
            messageId = messageId,
            emoji = emoji,
        )
        if (optimistic !== previousMessages) {
            _state.value = _state.value.copy(messages = optimistic)
            if (patchIndex >= 0) {
                publishMessagesDerivedAfterPatch(optimistic, patchIndex)
            } else {
                publishMessagesDerived(optimistic)
            }
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
                    val rollbackIndex = previousMessages.indexOfFirst { it._id == messageId }
                    _state.value = _state.value.copy(
                        messages = previousMessages,
                        error = e.toUserMessageRu(res),
                    )
                    if (rollbackIndex >= 0) {
                        publishMessagesDerivedAfterPatch(previousMessages, rollbackIndex)
                    } else {
                        publishMessagesDerived(previousMessages)
                    }
                }
        }
    }

    private fun applyRoomPinToRooms(rooms: List<ChatRoomDto>, room: ChatRoomDto): List<ChatRoomDto> =
        rooms.map { if (it.id == room.id) room else it }

    private fun publishRooms(next: List<ChatRoomDto>) {
        _state.update { applyPinBarUi(it.copy(rooms = next)) }
    }

    private fun persistPinHistory(roomId: String) {
        val history = pinHistoryByRoom[roomId].orEmpty()
        pinHistoryPreferences.save(pinHistoryPreferences.chatScopeKey(roomId), history)
    }

    private fun syncPinHistoryForRoom(
        roomId: String,
        room: ChatRoomDto,
        messages: List<ChatMessage>,
    ) {
        val pinId = room.pinnedMessageId?.trim().orEmpty()
        if (pinId.isEmpty()) return
        val serverPreview = resolveChatPinnedPreview(
            room.pinnedMessageId,
            room.pinnedMessage,
            messages,
        )
        val existing = refreshPinHistoryPreviews(
            pinHistoryByRoom[roomId].orEmpty(),
            messages,
        )
        val (updated, resetIndex) = syncRoomPinHistory(
            existing,
            serverPreview,
            room.pinnedMessageId,
        )
        pinHistoryByRoom[roomId] = updated
        persistPinHistory(roomId)
        val prevActivePin = lastSyncedActivePinIdByRoom[roomId]
        if (prevActivePin != pinId) {
            lastSyncedActivePinIdByRoom[roomId] = pinId
            pinBarIndexByRoom[roomId] = 0
        } else if (resetIndex || !pinBarIndexByRoom.containsKey(roomId)) {
            pinBarIndexByRoom[roomId] = 0
        }
    }

    private fun applyPinBarUi(state: ChatState): ChatState {
        val roomId = state.selectedRoomId?.trim().orEmpty()
        if (roomId.isEmpty()) return state.copy(pinBarPreview = null, pinHistoryCount = 0)
        val room = state.rooms.find { it.id == roomId }
            ?: return state.copy(pinBarPreview = null, pinHistoryCount = 0)
        val pinId = room.pinnedMessageId?.trim().orEmpty()
        if (pinId.isEmpty()) return state.copy(pinBarPreview = null, pinHistoryCount = 0)
        syncPinHistoryForRoom(roomId, room, state.messages)
        val history = pinHistoryByRoom[roomId].orEmpty()
        val barIndex = pinBarIndexByRoom.getOrDefault(roomId, 0)
            .coerceIn(0, (history.size - 1).coerceAtLeast(0))
        pinBarIndexByRoom[roomId] = barIndex
        val serverPreview = resolveChatPinnedPreview(
            room.pinnedMessageId,
            room.pinnedMessage,
            state.messages,
        )
        val preview = pinBarPreviewAtIndex(history, barIndex, serverPreview, room.pinnedMessageId)
        return state.copy(
            pinBarPreview = preview,
            pinHistoryCount = pinHistoryDisplayCount(history),
        )
    }

    private fun updatePinBarUi() {
        _state.update { applyPinBarUi(it) }
    }

    fun onPinnedBarTap() {
        val st = _state.value
        val roomId = st.selectedRoomId?.trim().orEmpty()
        if (roomId.isEmpty()) return
        val room = st.rooms.find { it.id == roomId } ?: return
        val activePinId = room.pinnedMessageId?.trim().orEmpty()
        if (activePinId.isEmpty()) return
        val targetId = st.pinBarPreview?.id?.trim().orEmpty().ifEmpty { activePinId }
        val history = pinHistoryByRoom[roomId].orEmpty()
        viewModelScope.launch {
            val found = jumpToChatPinnedMessage(
                messageId = targetId,
                messageIdsNewestFirst = _state.value.messages.mapNotNull { it._id },
                hasMoreOlder = { _state.value.hasMoreOlder },
                isLoadingOlder = { _state.value.isLoadingOlder },
                loadOlder = { loadOlderMessagesAwait() },
                timelineIndexForMessageId = { id ->
                    chatTimelineIndexForMessageId(
                        _listDerived.value.timeline,
                        _state.value.messages,
                        id,
                    )
                },
                onJumpToMessage = { id ->
                    _state.update {
                        it.copy(
                            scrollToMessageId = id,
                            highlightMessageId = id,
                            transientNotice = null,
                        )
                    }
                },
            )
            if (!found) {
                _state.update {
                    it.copy(
                        transientNotice = res.getString(R.string.chat_jump_quote_not_found),
                        scrollToMessageId = null,
                        highlightMessageId = null,
                    )
                }
                return@launch
            }
            if (history.size > 1) {
                val barIndex = pinBarIndexByRoom.getOrDefault(roomId, 0)
                pinBarIndexByRoom[roomId] = advancePinBarIndex(history, barIndex)
                updatePinBarUi()
            }
        }
    }

    private fun applyRoomPinEvent(rooms: List<ChatRoomDto>, event: ChatRoomPinChangedEvent): List<ChatRoomDto> =
        rooms.map { room ->
            if (room.id != event.roomId) room
            else room.mergePinFromEvent(event)
        }

    private fun publishRoomPin(room: ChatRoomDto) {
        _state.update { st ->
            val withRooms = st.copy(rooms = applyRoomPinToRooms(st.rooms, room))
            if (st.selectedRoomId == room.id) applyPinBarUi(withRooms) else withRooms
        }
        ChatSessionCache.update(_state.value.rooms)
    }

    fun onRoomPinChanged(event: ChatRoomPinChangedEvent) {
        _state.update { st ->
            val withRooms = st.copy(rooms = applyRoomPinEvent(st.rooms, event))
            if (st.selectedRoomId == event.roomId) applyPinBarUi(withRooms) else withRooms
        }
        ChatSessionCache.update(_state.value.rooms)
    }

    fun pinMessage(messageId: String, previewSource: ChatMessage? = null) {
        val roomId = _state.value.selectedRoomId?.trim().orEmpty()
        val trimmedId = messageId.trim()
        if (roomId.isEmpty() || trimmedId.isEmpty() || _state.value.pinInFlight) return
        val roomsSnapshot = _state.value.rooms
        val roomBefore = roomsSnapshot.find { it.id == roomId }
        val message = previewSource?.takeIf { it._id == trimmedId }
            ?: _state.value.messages.find { it._id == trimmedId }
        val preview = message?.toPinnedPreview()
        preview?.let { p ->
            pinHistoryByRoom[roomId] = pushPinHistory(pinHistoryByRoom[roomId].orEmpty(), p)
            pinBarIndexByRoom[roomId] = 0
            persistPinHistory(roomId)
        }
        val optimisticRoom = roomBefore?.let { room ->
            preview?.let { room.withOptimisticPin(trimmedId, it, _state.value.currentUserId) }
                ?: room.copy(
                    pinnedMessageId = trimmedId,
                    pinnedAt = java.time.Instant.now().toString(),
                    pinnedByUserId = _state.value.currentUserId,
                )
        }
        if (optimisticRoom != null) {
            publishRoomPin(optimisticRoom)
        }
        _state.update { it.copy(pinInFlight = true) }
        viewModelScope.launch {
            repository.pinRoomMessage(roomId, trimmedId)
                .onSuccess { room ->
                    val merged = ensureRoomPinPreview(
                        room = room,
                        preview = preview,
                        pinnedByUserId = _state.value.currentUserId,
                    )
                    publishRoomPin(merged)
                    _state.update {
                        it.copy(
                            pinInFlight = false,
                            transientNotice = res.getString(R.string.chat_pinned_toast_pinned),
                        )
                    }
                }
                .onFailure { e ->
                    _state.update {
                        applyPinBarUi(
                            it.copy(
                                rooms = roomsSnapshot,
                                pinInFlight = false,
                                transientNotice = e.toUserMessageRu(res),
                            ),
                        )
                    }
                }
        }
    }

    fun unpinSelectedRoom() {
        val roomId = _state.value.selectedRoomId?.trim().orEmpty()
        if (roomId.isEmpty() || _state.value.pinInFlight) return
        val roomsSnapshot = _state.value.rooms
        val optimisticRoom = roomsSnapshot.find { it.id == roomId }?.withOptimisticUnpin()
        if (optimisticRoom != null) {
            publishRoomPin(optimisticRoom)
        }
        _state.update { it.copy(pinInFlight = true) }
        viewModelScope.launch {
            repository.pinRoomMessage(roomId, null)
                .onSuccess { room ->
                    publishRoomPin(room)
                    _state.update {
                        it.copy(
                            pinInFlight = false,
                            transientNotice = res.getString(R.string.chat_pinned_toast_unpinned),
                        )
                    }
                }
                .onFailure { e ->
                    _state.update {
                        applyPinBarUi(
                            it.copy(
                                rooms = roomsSnapshot,
                                pinInFlight = false,
                                transientNotice = e.toUserMessageRu(res),
                            ),
                        )
                    }
                }
        }
    }

    fun editMessage(messageId: String, newText: String) {
        if (messageId.isBlank()) return
        val trimmed = newText.trim()
        if (trimmed.isBlank()) return
        val id = messageId.trim()
        val previous = _state.value.messages.find { it._id?.trim() == id }
        previous?.let { row ->
            applyMessageReplaceSynchronously(
                row.copy(
                    text = trimmed,
                    editedAt = java.time.Instant.now().toString(),
                ).normalizeEditedAtForDisplay(),
            )
        }
        viewModelScope.launch {
            repository.editMessage(id, trimmed)
                .onSuccess { updated ->
                    applyMessageReplaceSynchronously(updated.normalizeEditedAtForDisplay())
                }
                .onFailure { e ->
                    previous?.let { applyMessageReplaceSynchronously(it) }
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
                        result.pinChanged?.let { applyRoomPinChangedEvent(it) }
                        persistMessageRemoved(messageId, result.roomId)
                    }
                    .onFailure { t ->
                        if (t.isChatMessageAlreadyGoneOnServer()) {
                            notifyOverlayStripMessageRemoved(id)
                            _state.value = syncSelections(
                                scrubRemovedMessage(_state.value, id).copy(
                                    isDeletingSelection = true,
                                ),
                            )
                            persistMessageRemoved(id, _state.value.selectedRoomId.orEmpty())
                        } else {
                            lastFailure = t
                        }
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
                    result.pinChanged?.let { applyRoomPinChangedEvent(it) }
                    persistMessageRemoved(result.messageId, result.roomId)
                }
                .onFailure { throwable ->
                    if (throwable.isChatMessageAlreadyGoneOnServer()) {
                        notifyOverlayStripMessageRemoved(messageId)
                        _state.value = syncSelections(
                            scrubRemovedMessage(_state.value, messageId).copy(
                                deletingMessageId = null,
                                error = null,
                            ),
                        )
                        persistMessageRemoved(messageId, _state.value.selectedRoomId.orEmpty())
                    } else {
                        _state.value = _state.value.copy(
                            deletingMessageId = null,
                            error = throwable.toUserMessageRu(res),
                        )
                    }
                }
        }
    }

    /** Server hard-deleted the row (or never had it); drop from local feed anyway. */
    private fun Throwable.isChatMessageAlreadyGoneOnServer(): Boolean =
        this is HttpException && code() == 404

    private fun notifyOverlayStripMessageRemoved(messageId: String) {
        val id = messageId.trim()
        if (id.isEmpty()) return
        repository.notifyOverlayMessageDeleted(id, _state.value.selectedRoomId.orEmpty())
    }

    /** Drop own socket/HTTP echo before the debounced channel — prevents a visible duplicate row. */
    fun shouldSuppressOwnOutgoingRealtimeEcho(message: ChatMessage): Boolean =
        shouldBlockOwnOutgoingRealtime(message)

    private fun shouldBlockOwnOutgoingRealtime(message: ChatMessage): Boolean {
        val selfId = currentUserId.trim()
        if (selfId.isEmpty() || message.senderId.trim() != selfId) return false
        if (!activeOutgoingPendingId.isNullOrBlank()) return true
        if (hasMatchingPendingOutgoing(_state.value.messages, message, currentUserId)) return true
        val fingerprint = outgoingMessageFingerprint(
            message.roomId,
            message.text,
            message.replyToMessageId,
        )
        if (inFlightOutgoingFingerprints.contains(fingerprint)) return true
        return isDuplicateOwnOutgoingDelivery(
            _state.value.messages,
            message,
            messageIdIndex,
        )
    }

    private fun onIncomingMessage(message: ChatMessage) {
        if (shouldBlockOwnOutgoingRealtime(message)) return
        if (!incomingMessages.trySend(message).isSuccess) {
            if (BuildConfig.DEBUG) {
                Log.w("ChatViewModel", "incomingMessages channel overflow; flushing on main")
            }
            viewModelScope.launch(Dispatchers.Main) {
                if (shouldBlockOwnOutgoingRealtime(message)) return@launch
                dispatchIncomingBatch(listOf(message))
            }
        }
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
        if (BuildConfig.DEBUG) {
            android.util.Log.d(
                "ChatReadReceipt",
                "room:read peer=${event.userId} room=${event.roomId} upto=${event.messageId} " +
                    "selected=${_state.value.selectedRoomId}",
            )
        }
        val publishUpto = PeerReadCursorLogic.mergePeerReadEvent(
            otherReadUptoByRoom = otherReadUptoByRoom,
            selectedRoomId = _state.value.selectedRoomId,
            event = event,
            currentUserId = currentUserId,
        )
        if (publishUpto != null) {
            _otherReadUptoMessageId.value = publishUpto
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
            val removedId = event.messageId.trim()
            if (removedId.isEmpty()) return@launch
            val eventRoomId = event.roomId.trim()
            val selected = _state.value.selectedRoomId
            if (eventRoomId.isBlank() || eventRoomId == selected) {
                val scrubbed = scrubRemovedMessage(_state.value, removedId)
                _state.value = syncSelections(
                    scrubbed.copy(
                        deletingMessageId = if (scrubbed.deletingMessageId == removedId) {
                            null
                        } else {
                            scrubbed.deletingMessageId
                        },
                    ),
                )
            } else if (eventRoomId.isNotEmpty()) {
                _state.update { st ->
                    clearRoomPinAfterMessageRemoved(st, removedId, eventRoomId)
                }
            }
            persistMessageRemoved(removedId, eventRoomId)
        }
    }

    private fun persistMessageRemoved(removedId: String, roomId: String) {
        val id = removedId.trim()
        if (id.isEmpty()) return
        markMessageRemovedLocally(id)
        val rid = roomId.trim().ifBlank { _state.value.selectedRoomId?.trim().orEmpty() }
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
            _state.value.selectedRoomId == rid -> _state.value.messages
            else -> null
        }
        if (nextMessages != null) {
            val capped = capMessagesForMemory(nextMessages)
            roomMessageCache[rid] = RoomMessageCache(
                messages = capped,
                hasMoreOlder = cached?.hasMoreOlder ?: _state.value.hasMoreOlder,
            )
            ChatSessionCache.updateMessages(rid, capped)
        }
        flushRoomMessagesToDiskNow(rid)
        schedulePersistChatSnapshot()
    }

    private fun markMessageRemovedLocally(messageId: String) {
        val id = messageId.trim()
        if (id.isEmpty()) return
        locallyRemovedMessageIds.add(id)
        while (locallyRemovedMessageIds.size > 512) {
            val eldest = locallyRemovedMessageIds.first()
            locallyRemovedMessageIds.remove(eldest)
        }
        if (currentUserId.isNotBlank()) {
            viewModelScope.launch(Dispatchers.IO) {
                launchDiskCache.addRemovedMessageId(currentUserId, id)
            }
        }
    }

    private fun messagesWithoutLocallyRemoved(messages: List<ChatMessage>): List<ChatMessage> {
        if (locallyRemovedMessageIds.isEmpty()) return messages
        var out = messages
        val known = out.mapNotNull { it._id }.toMutableSet()
        for (removedId in locallyRemovedMessageIds) {
            if (removedId.isBlank()) continue
            out = scrubMessagesAfterRemove(out, removedId, known)
        }
        return out
    }

    private fun applyLocallyRemovedFilterToLoadedCaches() {
        if (locallyRemovedMessageIds.isEmpty()) return
        for ((rid, entry) in roomMessageCache.toList()) {
            val filtered = messagesWithoutLocallyRemoved(entry.messages)
            if (filtered.size == entry.messages.size) continue
            val capped = capMessagesForMemory(filtered)
            roomMessageCache[rid] = entry.copy(messages = capped)
            ChatSessionCache.updateMessages(rid, capped)
        }
        val roomId = _state.value.selectedRoomId?.trim().orEmpty()
        if (roomId.isEmpty()) return
        val current = messagesWithoutLocallyRemoved(_state.value.messages)
        if (current.size == _state.value.messages.size) return
        knownMessageIds.clear()
        messageIdIndex.clear()
        knownMessageIds.addAll(current.mapNotNull { it._id })
        rebuildMessageIdIndex(current, messageIdIndex)
        _state.update { st -> syncSelections(st.copy(messages = current)) }
        publishMessagesDerived(current)
    }

    private fun flushRoomMessagesToDiskNow(roomId: String) {
        if (currentUserId.isBlank()) return
        val rid = roomId.trim()
        if (rid.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val snapshot = withContext(Dispatchers.Main) {
                val entry = roomMessageCache[rid]
                val raw = entry?.messages?.takeIf { it.isNotEmpty() }
                    ?: if (_state.value.selectedRoomId == rid) {
                        _state.value.messages
                    } else {
                        null
                    }
                if (raw == null) return@withContext null
                Triple(
                    messagesWithoutLocallyRemoved(raw),
                    entry?.hasMoreOlder ?: _state.value.hasMoreOlder,
                    raw,
                )
            } ?: return@launch
            val (messages, hasMoreOlder, _) = snapshot
            if (messages.isEmpty()) return@launch
            launchDiskCache.saveRoomMessages(currentUserId, rid, messages, hasMoreOlder)
        }
    }

    private fun scrubRemovedMessage(state: ChatState, removedId: String): ChatState {
        val nextMessages = scrubMessagesAfterRemove(state.messages, removedId, knownMessageIds)
        rebuildMessageIdIndex(nextMessages, messageIdIndex)
        publishMessagesDerived(nextMessages)
        return clearRoomPinAfterMessageRemoved(
            state = state.copy(messages = nextMessages),
            removedId = removedId,
            roomId = state.selectedRoomId.orEmpty(),
        )
    }

    private fun clearRoomPinAfterMessageRemoved(
        state: ChatState,
        removedId: String,
        roomId: String,
    ): ChatState {
        val rid = roomId.trim()
        val removed = removedId.trim()
        if (rid.isEmpty() || removed.isEmpty()) return state
        pinHistoryByRoom[rid] = removePinFromHistory(
            pinHistoryByRoom[rid].orEmpty(),
            removed,
        )
        persistPinHistory(rid)
        val room = state.rooms.find { it.id == rid } ?: return state
        if (room.pinnedMessageId?.trim() != removed) return state
        val cleared = room.withOptimisticUnpin()
        return applyPinBarUi(state.copy(rooms = applyRoomPinToRooms(state.rooms, cleared)))
    }

    private fun applyRoomPinChangedEvent(event: ChatRoomPinChangedEvent) {
        _state.update { st ->
            val withRooms = st.copy(rooms = applyRoomPinEvent(st.rooms, event))
            if (st.selectedRoomId == event.roomId) applyPinBarUi(withRooms) else withRooms
        }
        ChatSessionCache.update(_state.value.rooms)
    }

    private fun applyMessageReplaceSynchronously(updated: ChatMessage) {
        val id = updated._id?.trim().orEmpty()
        if (id.isEmpty()) return
        synchronized(chatMutationLock) {
            val idx = messageIdIndex[id] ?: _state.value.messages.indexOfFirst { it._id?.trim() == id }
            if (idx < 0) return
            val messages = _state.value.messages.toMutableList()
            if (idx !in messages.indices) return
            messages[idx] = updated.mergeIncomingChatUpdate(messages[idx])
            rebuildMessageIdIndex(messages, messageIdIndex)
            publishMessagesDerived(messages)
            val roomId = _state.value.selectedRoomId?.trim().orEmpty()
            _state.value = syncSelections(_state.value.copy(messages = messages))
            if (roomId.isNotEmpty()) {
                val cached = roomMessageCache[roomId]
                roomMessageCache[roomId] = RoomMessageCache(
                    messages = messages,
                    hasMoreOlder = cached?.hasMoreOlder ?: _state.value.hasMoreOlder,
                )
                ChatSessionCache.updateMessages(roomId, messages)
            }
            val room = _state.value.rooms.find { it.id == roomId }
            if (room != null && room.pinnedMessageId?.trim() == id) {
                updated.toPinnedPreview()?.let { preview ->
                    val pinnedRoom = room.withOptimisticPin(id, preview, room.pinnedByUserId.orEmpty())
                    publishRoomPin(pinnedRoom)
                }
            }
        }
    }

    private fun stashIncomingMessageForRoom(message: ChatMessage) {
        val roomId = message.roomId.trim()
        if (roomId.isBlank()) return
        if (message._id == null) return
        val cached = roomMessageCache[roomId]
        val existing = cached?.messages ?: emptyList()
        val localKnown = existing.mapNotNull { it._id }.toMutableSet()
        val update = upsertMessage(existing, message, localKnown, idIndex = null)
        roomMessageCache[roomId] = RoomMessageCache(
            messages = capMessagesForMemory(update.messages),
            hasMoreOlder = cached?.hasMoreOlder ?: true,
        )
        ChatSessionCache.updateMessages(roomId, roomMessageCache[roomId]!!.messages)
    }

    private fun buildOptimisticOutgoingMessage(
        roomId: String,
        text: String,
        replyToMessageId: String?,
    ): ChatMessage =
        ChatMessage(
            _id = "pending-${UUID.randomUUID()}",
            allianceId = "",
            roomId = roomId,
            senderId = currentUserId,
            senderUsername = "",
            senderRole = currentUserRole,
            text = text,
            replyToMessageId = replyToMessageId,
            createdAt = java.time.Instant.now().toString(),
        )

    private fun publishRaidMessageToOverlayStrip(message: ChatMessage) {
        val roomId = message.roomId.trim().ifBlank { _state.value.selectedRoomId?.trim().orEmpty() }
        if (roomId.isEmpty()) return
        val prefsRaid = chatRoomPreferences.getRaidRoomId()?.trim()
        val isRaid = when {
            !prefsRaid.isNullOrEmpty() && prefsRaid == roomId -> true
            else -> {
                val room = _state.value.rooms.find { it.id == roomId } ?: return
                ChatRaidRoomSync.isAllianceRaidRoom(room)
            }
        }
        if (!isRaid) return
        runCatching { CombatOverlayService.publishRaidMessageToStripFromApp(message) }
        runCatching { CombatOverlayService.extendInGameOverlayUiHold() }
    }

    /** Pending row must exist before HTTP/socket echo to avoid a brief duplicate at the list head. */
    private fun insertOptimisticOutgoingSynchronously(
        message: ChatMessage,
        clearComposer: Boolean,
    ) {
        if (clearComposer && overlayChatPanelVisible) {
            runCatching { CombatOverlayService.extendInGameOverlayUiHold() }
        }
        val work = synchronized(chatMutationLock) {
            val snapshot = _state.value
            val update = upsertMessage(
                current = snapshot.messages,
                incoming = message,
                knownMessageIds = knownMessageIds,
                idIndex = messageIdIndex,
            )
            val capped = capMessagesForMemory(update.messages)
            rebuildMessageIdIndex(capped, messageIdIndex)
            Triple(snapshot, capped, update.newestMessageKey ?: message._id?.trim().orEmpty())
        }
        val (snapshot, capped, newestKey) = work
        activeOutgoingPendingId = message._id
        message._id?.let { registerOutgoingLazyColumnKey(it) }
        var nextState = snapshot.copy(
            messages = capped,
            newestMessageKey = newestKey.ifEmpty { snapshot.newestMessageKey },
            isSending = false,
            error = null,
        )
        if (clearComposer) {
            _draftMessage.value = ""
            _pickedImageUris.value = emptyList()
            nextState = nextState.copy(
                replyToMessage = null,
                sendFailure = null,
                scrollToLatestNonce = snapshot.scrollToLatestNonce + 1L,
            )
            snapshot.selectedRoomId?.trim()?.takeIf { it.isNotEmpty() }?.let {
                clearUnreadWhileActivelyViewing(it)
            }
        }
        _state.value = syncSelections(nextState)
        publishMessagesDerivedImmediate(capped)
        val rid = _state.value.selectedRoomId
        if (!rid.isNullOrBlank()) {
            roomMessageCache[rid] = RoomMessageCache(
                messages = capped,
                hasMoreOlder = _state.value.hasMoreOlder,
            )
            ChatSessionCache.updateMessages(rid, capped)
        }
        publishRaidMessageToOverlayStrip(message)
    }

    private fun removePendingOutgoingMessage(pendingId: String?) {
        val id = pendingId?.trim().orEmpty()
        if (id.isEmpty()) return
        if (activeOutgoingPendingId == id) activeOutgoingPendingId = null
        dropOutgoingLazyColumnKey(id)
        knownMessageIds.remove(id)
        messageIdIndex.remove(id)
        val filtered = _state.value.messages.filter { it._id != id }
        _state.update { st -> st.copy(messages = filtered) }
        publishMessagesDerived(filtered)
        val rid = _state.value.selectedRoomId ?: return
        roomMessageCache[rid] = RoomMessageCache(
            messages = _state.value.messages,
            hasMoreOlder = _state.value.hasMoreOlder,
        )
        ChatSessionCache.updateMessages(rid, _state.value.messages)
    }

    /**
     * Пока в ленте есть optimistic `pending-*` с тем же текстом, не вставляем свой socket-echo —
     * иначе кратко видны две строки до [confirmPendingOutgoingMessage].
     */
    private fun registerInFlightOutgoing(roomId: String, text: String, replyToMessageId: String?) {
        inFlightOutgoingFingerprints.add(
            outgoingMessageFingerprint(roomId, text, replyToMessageId),
        )
    }

    private fun clearInFlightOutgoing(roomId: String, text: String, replyToMessageId: String?) {
        inFlightOutgoingFingerprints.remove(
            outgoingMessageFingerprint(roomId, text, replyToMessageId),
        )
    }

    private fun shouldDeferOwnOutgoingSocketEcho(message: ChatMessage): Boolean =
        shouldBlockOwnOutgoingRealtime(message)

    private fun partitionOwnOutgoingEchoes(
        batch: List<ChatMessage>,
    ): Pair<List<ChatMessage>, List<ChatMessage>> {
        val selfId = currentUserId.trim()
        if (selfId.isEmpty()) return emptyList<ChatMessage>() to batch
        val echoes = ArrayList<ChatMessage>(batch.size)
        val fresh = ArrayList<ChatMessage>(batch.size)
        for (message in batch) {
            val id = message._id?.trim().orEmpty()
            if (id.isNotEmpty() &&
                message.senderId.trim() == selfId &&
                messageIdIndex.containsKey(id)
            ) {
                echoes.add(message)
            } else {
                fresh.add(message)
            }
        }
        return echoes to fresh
    }

    private fun mergeOwnOutgoingEchoesInPlace(
        echoes: List<ChatMessage>,
        messages: List<ChatMessage>,
        derived: ChatMessagesListDerived,
    ): Pair<List<ChatMessage>, ChatMessagesListDerived> {
        var nextMessages = messages
        var nextDerived = derived
        for (echo in echoes) {
            val id = echo._id?.trim().orEmpty()
            val idx = messageIdIndex[id] ?: continue
            if (idx !in nextMessages.indices) continue
            val before = nextMessages
            val existing = nextMessages[idx]
            val merged = if (existing._id?.trim().orEmpty().startsWith("pending-") &&
                echo.senderId.trim() == currentUserId.trim()
            ) {
                mergeOutgoingConfirmation(existing, echo)
            } else {
                echo.mergeIncomingChatUpdate(existing)
            }
            if (merged == existing) continue
            val updated = nextMessages.toMutableList()
            updated[idx] = merged
            nextMessages = updated
            nextDerived = buildChatMessagesListDerivedAfterReplaceNewest(
                previousDerived = nextDerived,
                previousMessages = before,
                messages = nextMessages,
            )
        }
        return nextMessages to nextDerived
    }

    /** Replace optimistic row in-place (no remove+insert, no extra scroll). */
    private fun confirmPendingOutgoingMessage(pendingId: String?, sent: ChatMessage) {
        val pending = pendingId?.trim().orEmpty()
        val serverId = sent._id?.trim().orEmpty()
        if (pending.isEmpty() || serverId.isEmpty()) {
            clearInFlightOutgoing(sent.roomId, sent.text, sent.replyToMessageId)
            applyIncomingMessage(sent, clearComposer = false)
            return
        }
        val roomId = _state.value.selectedRoomId
        val work = synchronized(chatMutationLock) {
            val snapshot = _state.value
            val previousMessages = snapshot.messages
            val previousDerived = _listDerived.value
            val withoutSocketDupes = previousMessages.filterNot { msg ->
                val id = msg._id?.trim().orEmpty()
                id == serverId && id != pending
            }
            val replacement = replaceMatchingPendingOutgoing(
                current = withoutSocketDupes,
                incoming = sent,
                currentUserId = currentUserId,
            )
            val updated = if (replacement != null) {
                transferOutgoingLazyColumnKey(replacement.pendingId, replacement.serverId)
                knownMessageIds.remove(replacement.pendingId)
                knownMessageIds.add(replacement.serverId)
                messageIdIndex.remove(replacement.pendingId)
                messageIdIndex[replacement.serverId] = replacement.replacedIndex
                replacement.messages
            } else {
                var list = withoutSocketDupes.filterNot { it._id?.trim() == pending }
                val serverIdx = list.indexOfFirst { it._id?.trim() == serverId }
                list = if (serverIdx >= 0) {
                    list.toMutableList().apply {
                        this[serverIdx] = mergeOutgoingConfirmation(this[serverIdx], sent)
                    }
                } else {
                    listOf(sent.copy(editedAt = null)) + list
                }
                transferOutgoingLazyColumnKey(pending, serverId)
                knownMessageIds.remove(pending)
                knownMessageIds.add(serverId)
                messageIdIndex.remove(pending)
                list
            }
            val capped = capMessagesForMemory(
                dedupeMessagesByIdNewestFirst(
                    stripRedundantPendingOutgoing(updated, currentUserId),
                ),
            )
            rebuildMessageIdIndex(capped, messageIdIndex)
            IncomingBatchWork(
                previousMessages = previousMessages,
                cappedMessages = capped,
                newestMessageKey = serverId,
                previousDerived = previousDerived,
            )
        }
        if (roomId != null && _state.value.selectedRoomId != roomId) {
            activeOutgoingPendingId = null
            clearInFlightOutgoing(sent.roomId, sent.text, sent.replyToMessageId)
            return
        }
        val isInPlaceConfirm = work.cappedMessages.size == work.previousMessages.size &&
            work.cappedMessages.isNotEmpty() &&
            work.previousMessages.isNotEmpty() &&
            work.cappedMessages.drop(1) == work.previousMessages.drop(1) &&
            work.cappedMessages[0] != work.previousMessages[0]
        val derived = if (isInPlaceConfirm) {
            buildChatMessagesListDerivedAfterReplaceNewest(
                previousDerived = work.previousDerived,
                previousMessages = work.previousMessages,
                messages = work.cappedMessages,
            )
        } else {
            buildChatMessagesListDerived(work.cappedMessages)
        }
        deriveJob?.cancel()
        deriveDebounceJob?.cancel()
        val snapshot = _state.value
        _state.value = syncSelections(
            snapshot.copy(
                messages = work.cappedMessages,
                newestMessageKey = serverId,
                isSending = false,
                error = null,
                sendFailure = null,
                scrollToLatestNonce = snapshot.scrollToLatestNonce + 1L,
            ),
        )
        _listDerived.value = derived
        activeOutgoingPendingId = null
        clearInFlightOutgoing(sent.roomId, sent.text, sent.replyToMessageId)
        val rid = _state.value.selectedRoomId
        if (!rid.isNullOrBlank()) {
            acknowledgeOwnOutgoingInActiveRoom(rid, serverId)
        }
        if (!rid.isNullOrBlank()) {
            roomMessageCache[rid] = RoomMessageCache(
                messages = work.cappedMessages,
                hasMoreOlder = _state.value.hasMoreOlder,
            )
            ChatSessionCache.updateMessages(rid, work.cappedMessages)
            schedulePersistChatSnapshot()
        }
        publishRaidMessageToOverlayStrip(sent)
    }

    private fun applyIncomingMessage(
        message: ChatMessage,
        clearComposer: Boolean = false,
    ) {
        if (shouldBlockOwnOutgoingRealtime(message)) return
        if (clearComposer && overlayChatPanelVisible) {
            runCatching { CombatOverlayService.extendInGameOverlayUiHold() }
        }
        applyIncomingBatch(listOf(message), clearComposer = clearComposer)
    }

    private fun applyIncomingBatch(
        batch: List<ChatMessage>,
        clearComposer: Boolean = false,
    ) {
        if (batch.isEmpty()) return
        val roomId = _state.value.selectedRoomId
        val selectedRoom = roomId?.trim().orEmpty()
        val scopedBatch = if (selectedRoom.isEmpty()) {
            batch
        } else {
            batch.filter { it.roomId.trim() == selectedRoom }
        }.filterNot { shouldBlockOwnOutgoingRealtime(it) }
        if (scopedBatch.isEmpty()) return
        viewModelScope.launch(Dispatchers.Default) {
            incomingApplyMutex.withLock {
            val work = synchronized(chatMutationLock) {
                val snapshot = _state.value
                val afterDrop = dropMatchingPendingOutgoing(
                    current = filterMessagesForRoom(snapshot.messages, selectedRoom),
                    incoming = scopedBatch,
                    currentUserId = currentUserId,
                )
                val (echoes, fresh) = partitionOwnOutgoingEchoes(scopedBatch)
                var messages = afterDrop
                var listDerived = _listDerived.value
                if (echoes.isNotEmpty()) {
                    val merged = mergeOwnOutgoingEchoesInPlace(echoes, messages, listDerived)
                    messages = merged.first
                    listDerived = merged.second
                }
                var newestFromPendingReplace: String? = null
                val stillFresh = ArrayList<ChatMessage>(fresh.size)
                for (message in fresh) {
                    if (shouldDeferOwnOutgoingSocketEcho(message)) continue
                    val replacement = replaceMatchingPendingOutgoing(
                        current = messages,
                        incoming = message,
                        currentUserId = currentUserId,
                    )
                    if (replacement != null) {
                        transferOutgoingLazyColumnKey(replacement.pendingId, replacement.serverId)
                        messages = replacement.messages
                        knownMessageIds.remove(replacement.pendingId)
                        knownMessageIds.add(replacement.serverId)
                        messageIdIndex.remove(replacement.pendingId)
                        messageIdIndex[replacement.serverId] = replacement.replacedIndex
                        newestFromPendingReplace = replacement.serverId
                        listDerived = buildChatMessagesListDerivedAfterReplaceNewest(
                            previousDerived = listDerived,
                            previousMessages = snapshot.messages,
                            messages = messages,
                        )
                    } else if (
                        !hasMatchingPendingOutgoing(messages, message, currentUserId)
                    ) {
                        stillFresh.add(message)
                    }
                }
                messages = stripRedundantPendingOutgoing(messages, currentUserId)
                messages = dedupeMessagesByIdNewestFirst(messages)
                rebuildMessageIdIndex(messages, messageIdIndex)
                if (stillFresh.isEmpty()) {
                    return@synchronized IncomingBatchWork(
                        previousMessages = snapshot.messages,
                        cappedMessages = messages,
                        newestMessageKey = newestFromPendingReplace,
                        previousDerived = _listDerived.value,
                        precomputedDerived = listDerived,
                        echoesOnly = newestFromPendingReplace == null,
                    )
                }
                val update = upsertMessagesBatch(
                    current = messages,
                    incoming = stillFresh,
                    knownMessageIds = knownMessageIds,
                    idIndex = messageIdIndex,
                    maxMessages = messageMemoryCap,
                )
                val cappedAfterUpsert = stripRedundantPendingOutgoing(
                    update.messages,
                    currentUserId,
                ).let { dedupeMessagesByIdNewestFirst(it) }
                rebuildMessageIdIndex(cappedAfterUpsert, messageIdIndex)
                IncomingBatchWork(
                    previousMessages = messages,
                    cappedMessages = cappedAfterUpsert,
                    newestMessageKey = update.newestMessageKey ?: newestFromPendingReplace,
                    previousDerived = listDerived,
                    precomputedDerived = null,
                    echoesOnly = false,
                )
            }
            if (roomId != null && _state.value.selectedRoomId != roomId) return@withLock
            withContext(Dispatchers.Main) {
                if (roomId != null && _state.value.selectedRoomId != roomId) return@withContext
                val cappedMessages = sanitizeMessagesAfterRealtimeApply(
                    work.cappedMessages,
                    currentUserId,
                    activeOutgoingPendingId,
                )
                val snapshot = _state.value
                if (work.echoesOnly &&
                    !clearComposer &&
                    chatMessagesListContentEqual(snapshot.messages, cappedMessages)
                ) {
                    return@withContext
                }
                val derived = if (
                    work.precomputedDerived != null &&
                    chatMessagesListContentEqual(cappedMessages, work.cappedMessages)
                ) {
                    work.precomputedDerived
                } else {
                    buildChatMessagesListDerived(cappedMessages)
                }
                var nextState = snapshot.copy(
                    messages = cappedMessages,
                    newestMessageKey = work.newestMessageKey ?: snapshot.newestMessageKey,
                    isSending = false,
                    error = null,
                )
                val clearedDeletingId = scopedBatch.mapNotNull { it._id }.toSet()
                if (nextState.deletingMessageId in clearedDeletingId) {
                    nextState = nextState.copy(deletingMessageId = null)
                }
                val selfId = currentUserId.trim()
                val ownOutgoing = selfId.isNotEmpty() &&
                    scopedBatch.any { it.senderId.trim() == selfId }
                if (clearComposer) {
                    _draftMessage.value = ""
                    _pickedImageUris.value = emptyList()
                    nextState = nextState.copy(
                        replyToMessage = null,
                        sendFailure = null,
                    )
                }
                if (ownOutgoing && clearComposer) {
                    nextState = nextState.copy(
                        scrollToLatestNonce = nextState.scrollToLatestNonce + 1L,
                    )
                }
                _state.value = syncSelections(nextState)
                _listDerived.value = derived
                val rid = _state.value.selectedRoomId
                if (!rid.isNullOrBlank()) {
                    roomMessageCache[rid] = RoomMessageCache(
                        messages = cappedMessages,
                        hasMoreOlder = _state.value.hasMoreOlder,
                    )
                    ChatSessionCache.updateMessages(rid, cappedMessages)
                }
                scheduleMarkReadAfterIncomingBatch(rid, scopedBatch, cappedMessages)
            }
            }
        }
    }

    private data class IncomingBatchWork(
        val previousMessages: List<ChatMessage>,
        val cappedMessages: List<ChatMessage>,
        val newestMessageKey: String?,
        val previousDerived: ChatMessagesListDerived,
        val precomputedDerived: ChatMessagesListDerived? = null,
        val echoesOnly: Boolean = false,
    )

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    /** Scroll chat to newest messages and sync read cursor (FAB / return from history). */
    fun scrollToLatestMessages() {
        val roomId = _state.value.selectedRoomId ?: return
        val newestId = _state.value.messages.firstOrNull()?._id
        _state.value = _state.value.copy(
            scrollToLatestNonce = _state.value.scrollToLatestNonce + 1L,
        )
        if (!newestId.isNullOrBlank()) {
            viewModelScope.launch { markRoomReadUpTo(roomId, newestId) }
        }
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
