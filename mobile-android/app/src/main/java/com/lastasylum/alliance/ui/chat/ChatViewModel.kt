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
import com.lastasylum.alliance.data.chat.ChatRoomsSessionCache
import com.lastasylum.alliance.data.chat.ChatTeamRoomsMembership
import com.lastasylum.alliance.data.chat.stickers.StickerPacks
import com.lastasylum.alliance.data.teams.TeamMembershipNotifier
import com.lastasylum.alliance.data.chat.store.ChatRoomStoreBindings
import com.lastasylum.alliance.data.chat.store.MessageStore
import com.lastasylum.alliance.data.chat.sync.CHAT_ACTIVE_ROOM_RECONCILE_INTERVAL_MS
import com.lastasylum.alliance.data.chat.sync.CHAT_BACKGROUND_MESSAGE_REFRESH_DEFER_MS
import com.lastasylum.alliance.data.chat.sync.CHAT_INCOMING_SOCKET_DEBOUNCE_BURST_MS
import com.lastasylum.alliance.data.chat.sync.CHAT_INCOMING_SOCKET_DEBOUNCE_MS
import com.lastasylum.alliance.data.chat.sync.CHAT_INITIAL_PAGE_SIZE
import com.lastasylum.alliance.data.chat.sync.CHAT_PAGE_SIZE
import com.lastasylum.alliance.data.chat.sync.CHAT_ROOMS_SYNC_ON_RESUME_TTL_MS
import com.lastasylum.alliance.data.chat.sync.CHAT_UNREAD_SYNC_DEBOUNCE_MS
import com.lastasylum.alliance.data.chat.sync.ChatBootstrapSync
import com.lastasylum.alliance.data.chat.sync.ChatIncomingSync
import com.lastasylum.alliance.data.chat.sync.ChatOverlaySync
import com.lastasylum.alliance.data.chat.sync.ChatRehydrateSync
import com.lastasylum.alliance.data.chat.sync.ChatRoomMessageCache
import com.lastasylum.alliance.data.chat.sync.ChatRoomOpenSync
import com.lastasylum.alliance.data.chat.sync.ChatRoomPagingSync
import com.lastasylum.alliance.data.chat.sync.ChatRoomsListSync
import com.lastasylum.alliance.data.chat.sync.LaunchDiskPrimePayload
import com.lastasylum.alliance.data.chat.outbox.OutboxResumeScheduler
import com.lastasylum.alliance.data.chat.outbox.OutboxSendSource
import com.lastasylum.alliance.data.chat.sync.IncomingBatchWork
import com.lastasylum.alliance.di.AppContainer
import com.lastasylum.alliance.overlay.CombatOverlayService
import com.lastasylum.alliance.overlay.OverlayHudBadgeEvent
import com.lastasylum.alliance.overlay.OverlayRaidChatForwardPolicy
import com.lastasylum.alliance.data.chat.ChatUnreadCounts
import com.lastasylum.alliance.data.chat.ChatRoomPreferences
import com.lastasylum.alliance.data.chat.PinHistoryPreferences
import com.lastasylum.alliance.data.chat.isCompactReactionSocketUpdate
import com.lastasylum.alliance.data.chat.mergeIncomingChatUpdate
import com.lastasylum.alliance.data.chat.mergePreservingAttachments
import com.lastasylum.alliance.data.users.UsersRepository
import com.lastasylum.alliance.ui.chat.usecase.ChatRoomsUseCase
import com.lastasylum.alliance.ui.chat.usecase.ChatUnreadUseCase
import com.lastasylum.alliance.ui.util.toUserMessageRu
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonEncodingException
import retrofit2.HttpException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.CopyOnWriteArrayList
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

private const val PEER_READ_CURSOR_POLL_DELAY_MS = 2_000L
internal const val CHAT_LIST_DERIVE_DEBOUNCE_MS = 24L
private const val CHAT_DERIVE_DEBOUNCE_MS = CHAT_LIST_DERIVE_DEBOUNCE_MS
internal const val CHAT_PROFILE_GATE_TTL_MS = 5 * 60_000L
private const val PROFILE_GATE_TTL_MS = CHAT_PROFILE_GATE_TTL_MS

class ChatViewModel(
    application: Application,
    internal val repository: ChatRepository,
    internal val chatRoomPreferences: ChatRoomPreferences,
    internal val pinHistoryPreferences: PinHistoryPreferences,
    internal val usersRepository: UsersRepository,
    internal val launchDiskCache: LaunchDiskCache,
    internal val currentUserId: String,
    internal val currentUserRole: String,
) : AndroidViewModel(application) {
    internal val _state = MutableStateFlow(
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
    internal val _listDerived = MutableStateFlow(ChatMessagesListDerived.Empty)
    val listDerived: StateFlow<ChatMessagesListDerived> = _listDerived.asStateFlow()
    internal var deriveJob: Job? = null
    internal var deriveDebounceJob: Job? = null
    internal val listDeriveDefer = ChatListDeriveDefer()
    internal val incomingApplyMutex = Mutex()
    internal val chatMutationLock = Any()
    /**
     * Stable LazyColumn keys: pending id → server id swap must not change Compose item identity.
     */
    internal val lazyColumnKeyByMessageId = mutableMapOf<String, String>()
    /** Flow-driven snapshot of active outbox sends for the selected room. */
    @Volatile
    internal var outboxRoomSnapshot = OutboxRoomSnapshot()
    /** In-flight sends keyed by [ChatMessage.clientMessageId] — blocks socket echo before Room Flow updates. */
    internal val activeOutgoingClientMessageIds =
        java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<String, Boolean>())
    /** Optimistic overlay quick commands waiting for [sendOverlayRaidQuickCommandImpl]. */
    internal val overlayQuickCommandPrepared =
        java.util.concurrent.ConcurrentHashMap<String, com.lastasylum.alliance.data.chat.outbox.OutboxEnqueueResult>()
    /** While true, Room observer and merge must not resurrect stale local rows after admin wipe. */
    @Volatile
    internal var postHistoryWipeAuthoritativeEmpty = false
    /** Per-room soft-clear: block paging merge until next filtered fetch completes. */
    internal val clearedRoomAuthoritativeEmptyIds =
        java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<String, Boolean>())
    internal var outboxObserverJob: Job? = null

    /** Isolated from [state] so each keystroke does not recompose the whole chat list. */
    internal val _draftMessage = MutableStateFlow("")
    val draftMessage: StateFlow<String> = _draftMessage.asStateFlow()

    /** Picked images for composer; uploaded then referenced as attachment ids on send. */
    internal val _pickedImageUris = MutableStateFlow<List<Uri>>(emptyList())
    val pickedImageUris: StateFlow<List<Uri>> = _pickedImageUris.asStateFlow()

    /** Isolated from [state] so typing socket churn does not recompose the message list. */
    internal val _typingPeers = MutableStateFlow<Map<String, String>>(emptyMap())
    val typingPeers: StateFlow<Map<String, String>> = _typingPeers.asStateFlow()

    /** Isolated from [state] so mic animation does not recompose the message list. */
    internal val _chatVoicePhase = MutableStateFlow(ChatVoicePhase.Idle)
    val chatVoicePhase: StateFlow<ChatVoicePhase> = _chatVoicePhase.asStateFlow()

    /** Isolated from [state] so read receipts do not recompose the whole chat screen. */
    internal val _otherReadUptoMessageId = MutableStateFlow<String?>(null)
    val otherReadUptoMessageId: StateFlow<String?> = _otherReadUptoMessageId.asStateFlow()
    /** Per-room peer read cursor — survives room switches (overlay + main app). */
    internal val otherReadUptoByRoom = mutableMapOf<String, String>()
    /** Client-side pin history per room (Telegram-style bar cycling). */
    internal val pinHistoryByRoom = mutableMapOf<String, List<com.lastasylum.alliance.data.chat.PinnedMessagePreviewDto>>()
    internal val roomPinCoordinators = mutableMapOf<String, PinScopeCoordinator>()
    internal val pinBarIndexByRoom = mutableMapOf<String, Int>()
    /** Detect active pin change to reset bar cycle index. */
    internal val lastSyncedActivePinIdByRoom = mutableMapOf<String, String>()
    /**
     * Rooms where a local pin/unpin mutation must win over stale listRooms or socket payloads
     * until the server confirms the same active pin id.
     */
    internal val pinStateAuthoritativeRoomIds = mutableSetOf<String>()

    internal val knownMessageIds = LinkedHashSet<String>()
  /** messageId → index in [ChatState.messages] (newest-first); cleared on room switch. */
    internal val messageIdIndex = HashMap<String, Int>()
    internal val typingPeerJobs = mutableMapOf<String, Job>()
    internal val typingPeerJobsLock = Any()
    internal var typingEmitJob: Job? = null

    internal val incomingMessages = Channel<ChatMessage>(Channel.UNLIMITED)
    internal val roomMessageCache = mutableMapOf<String, ChatRoomMessageCache>()
    /** Hard-deleted ids — mergeLoadedPageWithExisting must not resurrect them from disk/socket cache. */
    internal val locallyRemovedMessageIds = LinkedHashSet<String>()
    /** Latest message id we successfully marked read per room (avoids regress + duplicate bumps). */
    internal val lastMarkedReadByRoom = mutableMapOf<String, String>()
    /** Socket can deliver the same message via new + reaction/edited; count unread once per id. */
    internal val unreadBumpedMessageIds = LinkedHashSet<String>()
    /** Realtime before listRooms — applied after [applyRoomsFromServer]. */
    internal val pendingUnreadBumps = ArrayDeque<Pair<String, String>>()
    /** Socket bump not yet reflected in listRooms / rooms:unread — do not zero tab badge. */
    internal val optimisticUnreadFloorByRoom = mutableMapOf<String, Int>()
    internal var lastRoomsSyncedAtMs: Long = 0L
    internal val markReadInFlight = CopyOnWriteArrayList<Job>()
    internal val peerReadPollJobs = mutableMapOf<String, Job>()
    internal var unreadSyncJob: Job? = null
    internal var stashRehydrateJob: Job? = null
    /** False when user left the Chat tab — must not auto mark-read or zero selected-room badge. */
    internal var isChatTabActive = false

    /** Main activity in foreground — false when user is in-game with app in background. */
    @Volatile
    internal var appInForeground = true

    /** Fullscreen overlay chat/team panel is open — separate from bottom-nav Chat tab. */
    @Volatile
    internal var overlayChatPanelVisible = false
    internal var overlayAutoMarkReadJob: kotlinx.coroutines.Job? = null
    internal val bootstrapMutex = Mutex()
    internal var bootstrapJob: Job? = null
    internal var openRoomJob: Job? = null
    internal var persistSnapshotJob: Job? = null

    internal fun isActiveSelectedRoom(roomId: String): Boolean =
        _state.value.selectedRoomId?.trim() == roomId.trim()

    /** Update room cache without touching visible feed when another room is selected. */
    internal fun persistRoomMessagesToCache(
        roomId: String,
        messages: List<ChatMessage>,
        hasMoreOlder: Boolean,
    ) {
        val rid = roomId.trim()
        if (rid.isEmpty()) return
        if (messages.size > messageMemoryCap && currentUserId.isNotBlank()) {
            ChatDeliveryMetrics.logCapTrim(rid, messages.size, messageMemoryCap)
            schedulePersistChatSnapshot()
        }
        val capped = capMessagesForMemory(messages)
        roomMessageCache[rid] = ChatRoomMessageCache(
            messages = capped,
            hasMoreOlder = hasMoreOlder,
        )
        ChatSessionCache.updateMessages(rid, capped)
    }

    internal val onTeamMembershipChanged: () -> Unit = {
        viewModelScope.launch {
            if (!isChatTabActive && !overlayChatPanelVisible) return@launch
            bootstrap(preferAllianceHubRoom = true, force = true)
        }
    }

    @Volatile
    internal var launchWarmupNeedsMessages = false

    @Volatile
    internal var launchWarmupNeedsBootstrap = false

    @Volatile
    internal var cachedTeamProfileGate: Boolean? = null

    @Volatile
    internal var profileGateLoadedAtMs: Long = 0L

    internal val pendingIdlessStash = java.util.concurrent.ConcurrentHashMap<String, ChatMessage>()
    internal val recentSocketMessageIds = LinkedHashSet<String>()
    internal val lastBackgroundRefreshAtMs = java.util.concurrent.ConcurrentHashMap<String, Long>()
    @Volatile
    internal var forceBackgroundRefreshAfterReconnect = false
    internal var periodicReconcileJob: Job? = null

    internal fun trackRecentSocketMessageId(id: String?) {
        val trimmed = id?.trim().orEmpty()
        if (trimmed.isEmpty() || isOptimisticOutgoingMessageId(trimmed)) return
        synchronized(recentSocketMessageIds) {
            recentSocketMessageIds.add(trimmed)
            while (recentSocketMessageIds.size > 256) {
                val oldest = recentSocketMessageIds.first()
                recentSocketMessageIds.remove(oldest)
            }
        }
    }

    internal fun protectedSocketMessageIds(): Set<String> =
        synchronized(recentSocketMessageIds) { recentSocketMessageIds.toSet() }

    internal fun mergeAnchorDropLogger(roomId: String): (String) -> Unit = { id ->
        ChatDeliveryMetrics.logMergeAnchorDrop(roomId, id)
    }

    internal val mainHandler = Handler(Looper.getMainLooper())
    internal var chatVoiceRecognizer: ChatVoiceRecognizer? = null

    internal val res get() = getApplication<Application>().resources

    internal val messageMemoryCap: Int by lazy {
        val am = getApplication<Application>().getSystemService(ActivityManager::class.java)
        if (am?.isLowRamDevice == true) 400 else CHAT_MAX_MESSAGES_IN_MEMORY
    }

    internal fun capMessagesForMemory(messages: List<ChatMessage>): List<ChatMessage> =
        capNewestFirst(messages, messageMemoryCap)

    internal val chatSyncEngine = AppContainer.from(application).chatSyncEngine
    internal val chatOutbox = AppContainer.from(application).chatOutbox
    internal val messageStore: MessageStore = AppContainer.from(application).messageStore
    internal val roomStoreBindings = ChatRoomStoreBindings(
        messageStore = messageStore,
        syncEngine = chatSyncEngine,
        scope = viewModelScope,
        userId = currentUserId,
    )
    internal val chatIncomingSync by lazy {
        ChatIncomingSync(
            scope = viewModelScope,
            incomingApplyMutex = incomingApplyMutex,
            chatMutationLock = chatMutationLock,
            host = incomingSyncHost,
        )
    }
    internal val incomingSyncHost = object : ChatIncomingSync.Host {
        override val currentUserId: String
            get() = this@ChatViewModel.currentUserId
        override val messageMemoryCap: Int
            get() = this@ChatViewModel.messageMemoryCap

        override fun selectedRoomId(): String? = _state.value.selectedRoomId
        override fun stateSnapshot(): ChatState = _state.value
        override fun listDerived(): ChatMessagesListDerived = _listDerived.value
        override fun activeOutgoingPendingId(): String? = outboxRoomSnapshot.newestPendingId

        override fun knownMessageIds(): MutableSet<String> = this@ChatViewModel.knownMessageIds
        override fun messageIdIndex(): MutableMap<String, Int> = this@ChatViewModel.messageIdIndex

        override fun isRoomActivelyViewed(roomId: String, message: ChatMessage?): Boolean =
            this@ChatViewModel.isRoomActivelyViewed(roomId, message)

        override fun shouldDeferOwnOutgoingSocketEcho(message: ChatMessage): Boolean =
            this@ChatViewModel.shouldDeferOwnOutgoingSocketEcho(message)

        override fun shouldBlockOwnOutgoingRealtime(message: ChatMessage): Boolean =
            this@ChatViewModel.shouldBlockOwnOutgoingRealtime(message)

        override fun stashIncomingMessageForRoom(message: ChatMessage) =
            this@ChatViewModel.stashIncomingMessageForRoom(message)

        override fun processRealtimeMessageForUnread(message: ChatMessage) =
            this@ChatViewModel.processRealtimeMessageForUnread(message)

        override fun trackRecentSocketMessageId(id: String?) =
            this@ChatViewModel.trackRecentSocketMessageId(id)

        override fun transferOutgoingLazyColumnKey(pendingId: String, serverId: String) =
            this@ChatViewModel.transferOutgoingLazyColumnKey(pendingId, serverId)

        override fun overlayChatPanelVisible(): Boolean = overlayChatPanelVisible

        override fun filterMessagesForRoom(messages: List<ChatMessage>, roomId: String): List<ChatMessage> =
            this@ChatViewModel.filterMessagesForRoom(messages, roomId)

        override fun commitIncomingBatchUi(
            roomId: String?,
            scopedBatch: List<ChatMessage>,
            cappedMessages: List<ChatMessage>,
            work: IncomingBatchWork,
            derived: ChatMessagesListDerived,
            clearComposer: Boolean,
        ) {
            val snapshot = _state.value
            val safeMessages = sanitizeMessagesForUiList(
                messages = cappedMessages,
                currentUserId = currentUserId,
                activeOutgoingPendingId = outboxRoomSnapshot.newestPendingId,
            )
            val pendingInSnapshot = snapshot.messages.any {
                isOptimisticOutgoingMessageId(it._id?.trim().orEmpty())
            }
            val pendingInBatch = safeMessages.any {
                isOptimisticOutgoingMessageId(it._id?.trim().orEmpty())
            }
            if (!pendingInSnapshot && pendingInBatch && work.previousMessages !== snapshot.messages) {
                return
            }
            if (hasDuplicateMessageIds(safeMessages) && !hasDuplicateMessageIds(snapshot.messages)) {
                return
            }
            val safeDerived = reconcileDerivedWithMessages(
                derived = if (safeMessages == cappedMessages) {
                    derived
                } else {
                    buildChatMessagesListDerived(safeMessages)
                },
                messages = safeMessages,
            )
            var nextState = snapshot.copy(
                messages = safeMessages,
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
            _listDerived.value = safeDerived
        }

        override fun onIncomingBatchSideEffects(
            roomId: String,
            scopedBatch: List<ChatMessage>,
            cappedMessages: List<ChatMessage>,
            work: IncomingBatchWork,
            clearComposer: Boolean,
        ) {
            ChatDeliveryMetrics.logApply(roomId, scopedBatch.size)
            roomMessageCache[roomId] = ChatRoomMessageCache(
                messages = cappedMessages,
                hasMoreOlder = _state.value.hasMoreOlder,
            )
            ChatSessionCache.updateMessages(roomId, cappedMessages)
            val prevHead = work.previousMessages.firstOrNull()?._id
            val incomingHead = scopedBatch.firstOrNull()?._id
            val gapThreshold = if (isAllianceRaidRoom(roomId)) {
                RAID_GAP_RECONCILE_THRESHOLD_MS
            } else {
                CHAT_GAP_RECONCILE_THRESHOLD_MS
            }
            if (shouldTriggerGapReconcile(
                    prevHead,
                    incomingHead,
                    knownMessageIds,
                    gapThreshold,
                )
            ) {
                ChatDeliveryMetrics.logGapReconcile(roomId, "socket_jump")
                refreshMessagesInBackground(roomId, force = true)
            }
            scheduleMarkReadAfterIncomingBatch(roomId, scopedBatch, cappedMessages)
            if (currentUserId.isNotBlank()) {
                val mirrorBatch = scopedBatch.toList()
                viewModelScope.launch(Dispatchers.IO) {
                    mirrorBatch.forEach { msg ->
                        chatSyncEngine.onIncomingSocketMessage(currentUserId, msg)
                    }
                }
            }
        }
    }

    internal val vmState get() = _state

    internal val syncBundle by lazy {
        ChatViewModelSyncBundle(
            vm = this,
            scope = viewModelScope,
            repository = repository,
            bootstrapMutex = bootstrapMutex,
        )
    }

    internal val currentUserIdInternal get() = currentUserId
    internal val currentUserRoleInternal get() = currentUserRole
    internal val launchDiskCacheInternal get() = launchDiskCache
    internal val chatRoomPreferencesInternal get() = chatRoomPreferences
    internal val usersRepositoryInternal get() = usersRepository
    internal val vmScope get() = viewModelScope
    internal val vmRepository get() = repository
    internal val vmApplication get() = getApplication<Application>()
    internal val deliveryLatencyTracker by lazy { AppContainer.from(getApplication()).deliveryLatencyTracker }
    internal val pinHistoryPreferencesInternal get() = pinHistoryPreferences
    internal val pinHistoryByRoomInternal get() = pinHistoryByRoom
    internal val pinBarIndexByRoomInternal get() = pinBarIndexByRoom
    internal val roomPinCoordinatorsInternal get() = roomPinCoordinators
    internal val pinStateAuthoritativeRoomIdsInternal get() = pinStateAuthoritativeRoomIds
    internal val lastSyncedActivePinIdByRoomInternal get() = lastSyncedActivePinIdByRoom

    init {
        TeamMembershipNotifier.addListener(onTeamMembershipChanged)
        hydrateReadCursorsFromPreferences()
        if (currentUserId.isNotBlank()) {
            locallyRemovedMessageIds.addAll(launchDiskCache.loadRemovedMessageIds(currentUserId))
        }
        viewModelScope.launch {
            chatSyncEngine.bindUser(currentUserId)
            AppContainer.from(getApplication()).launchDiskCacheImporter.importIfNeeded(currentUserId)
            chatSyncEngine.resumePendingOutbox(viewModelScope, currentUserId)
            if (currentUserId.isNotBlank()) {
                bindRoomStoreObservers()
            }
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
                    val debounceMs = when {
                        pending.size >= 4 -> CHAT_INCOMING_SOCKET_DEBOUNCE_BURST_MS
                        pending.size >= 2 -> CHAT_INCOMING_SOCKET_DEBOUNCE_MS / 2
                        else -> CHAT_INCOMING_SOCKET_DEBOUNCE_MS
                    }
                    delay(debounceMs)
                    flushPending()
                }
            }
        }
        viewModelScope.launch {
            while (isActive) {
                delay(CHAT_ACTIVE_ROOM_RECONCILE_INTERVAL_MS)
                if (!isChatTabActive && !overlayChatPanelVisible) continue
                val roomId = _state.value.selectedRoomId?.trim().orEmpty()
                if (roomId.isEmpty()) continue
                refreshMessagesInBackground(roomId, force = false)
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
        val selected = _state.value.selectedRoomId
        viewModelScope.launch {
            postHistoryWipeAuthoritativeEmpty = true
            runCatching {
                ChatHistoryWipe.wipeAllLocalChatData(
                    userId = currentUserId,
                    messageStore = messageStore,
                    chatOutbox = chatOutbox,
                    launchDiskCache = launchDiskCacheInternal,
                    chatRoomPreferences = chatRoomPreferences,
                )
            }
            resetChatStateAfterHistoryWipe()
            syncRoomsFromServer(reconfirmVisibleRoom = !selected.isNullOrBlank())
            selected?.let { refreshMessagesInBackground(it, force = true) }
            schedulePersistChatSnapshot()
        }
    }

    internal fun clearPostHistoryWipeAuthoritativeEmpty() {
        postHistoryWipeAuthoritativeEmpty = false
    }

    internal fun markRoomClearedAuthoritativeEmpty(roomId: String) {
        val rid = roomId.trim()
        if (rid.isNotEmpty()) clearedRoomAuthoritativeEmptyIds.add(rid)
    }

    internal fun isRoomAuthoritativeEmpty(roomId: String): Boolean {
        if (postHistoryWipeAuthoritativeEmpty) return true
        return clearedRoomAuthoritativeEmptyIds.contains(roomId.trim())
    }

    internal fun clearRoomAuthoritativeEmpty(roomId: String) {
        clearedRoomAuthoritativeEmptyIds.remove(roomId.trim())
    }

    private fun resetChatStateAfterHistoryWipe() {
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
        pinStateAuthoritativeRoomIds.clear()
        activeOutgoingClientMessageIds.clear()
        overlayQuickCommandPrepared.clear()

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
    }

    internal fun onChatHistoryClearedFromServer() {
        applyChatHistoryClearedFromServer()
    }

    internal fun dispatchIncomingBatch(batch: List<ChatMessage>) {
        chatIncomingSync.dispatchIncomingBatch(batch)
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
        stashOverlayRealtimeMessage(message)
        if (shouldSuppressOwnOutgoingRealtimeEcho(message)) return
        val isOwnQuickCommand = message.senderId.trim() == currentUserId.trim()
        if (!OverlayRaidChatForwardPolicy.shouldApplyToVisibleChat(
                selectedRoomId = _state.value.selectedRoomId,
                messageRoomId = message.roomId,
                overlayPanelVisible = overlayChatPanelVisible,
                isOwnQuickCommandResponse = isOwnQuickCommand,
                isPeerMessage = !isOwnQuickCommand,
            )
        ) {
            return
        }
        applyOverlayChatMessageFromSocket(message)
    }

    /** Optimistic row for overlay quick commands before HTTP confirm. */
    fun prepareOverlayRaidQuickCommandOutgoing(
        pendingId: String,
        roomId: String,
        text: String,
        gameEventAlert: String? = null,
    ) = prepareOverlayRaidQuickCommandOutgoingImpl(pendingId, roomId, text, gameEventAlert)

    suspend fun sendOverlayRaidQuickCommand(
        pendingId: String,
        roomId: String,
        text: String,
        gameEventAlert: String? = null,
    ): Result<ChatMessage> = sendOverlayRaidQuickCommandImpl(pendingId, roomId, text, gameEventAlert)

    /** HTTP confirm for overlay quick command — replaces optimistic row when present. */
    fun confirmOverlayRaidQuickCommandHttp(pendingId: String?, sent: ChatMessage) {
        val pending = pendingId?.trim().orEmpty()
        if (pending.startsWith("overlay-pending-")) {
            confirmPendingOutgoingMessage(pending, sent)
            return
        }
        applyOverlayRaidHttpMessage(sent)
    }

    fun cancelOverlayRaidQuickCommandOutgoing(pendingId: String?, roomId: String, text: String) {
        val pending = pendingId?.trim().orEmpty()
        if (pending.isNotEmpty()) {
            removePendingOutgoingMessage(pending)
        }
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

    internal fun isKnownChatMessageId(messageId: String?): Boolean {
        val id = messageId?.trim().orEmpty()
        return id.isNotEmpty() && knownMessageIds.contains(id)
    }

    internal fun processRealtimeMessageForUnread(message: ChatMessage) {
        val roomId = message.roomId
        if (roomId.isBlank()) return
        if (!isIncomingMessageVisible(message)) return
        if (isKnownChatMessageId(message._id)) {
            applyKnownChatMessageUpdate(message)
            return
        }
        val selected = _state.value.selectedRoomId
        if (roomId == selected) {
            if (isRoomActivelyViewed(roomId, message)) {
                if (!shouldDeferOwnOutgoingSocketEcho(message)) {
                    applyIncomingMessage(message)
                    if (message.senderId.trim() != currentUserId.trim()) {
                        scheduleMarkReadForVisibleIncoming(message)
                    }
                } else {
                    stashIncomingMessageForRoom(message)
                }
            } else {
                stashIncomingMessageForRoom(message)
            }
            if (!isRoomActivelyViewed(roomId, message) && message.senderId != currentUserId) {
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

    internal fun queuePendingUnreadBump(roomId: String, messageId: String) {
        pendingUnreadBumps.addLast(roomId to messageId)
        while (pendingUnreadBumps.size > 64) {
            pendingUnreadBumps.removeFirst()
        }
    }

    internal fun flushPendingUnreadBumps() {
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

    internal fun notifyOverlayHubIfPending(roomId: String, messageId: String) {
        val hubId = chatRoomPreferences.getHubRoomId()?.trim().orEmpty()
        if (hubId.isBlank() || roomId != hubId) return
        CombatOverlayService.bumpAllianceHubUnreadFromRealtime(messageId)
    }

    internal fun syncOverlayAllianceHubBadge(rooms: List<ChatRoomDto> = _state.value.rooms) {
        val localRead = chatRoomPreferences.loadAllLastReadMessageIds()
        if (ChatUnreadCounts.isAllianceHubLocallyReadSuppressed(rooms, localRead)) {
            CombatOverlayService.syncHubBadgeFromSharedReadState(null)
            return
        }
        val displayed = ChatUnreadCounts.overlayAllianceHubBadge(
            rooms = rooms,
            localReadByRoom = localRead,
            optimisticFloor = CombatOverlayService.currentHubUnreadOptimisticFloor(),
            previouslyDisplayed = CombatOverlayService.currentAllianceHubBadgeDisplayed(),
        )
        CombatOverlayService.syncHubBadgeFromSharedReadState(displayed)
    }

    /** Called from overlay service when activity VM owns unread (socket hub path). */
    fun syncOverlayAllianceHubBadgeFromExternal() {
        syncOverlayAllianceHubBadge()
    }

    /** Raid/hub strip visible in-game — mark newest strip message read. */
    fun markOverlayStripVisibleAsRead() {
        val (roomId, messageId) = CombatOverlayService.overlayStripMarkReadTarget() ?: return
        if (!isValidMarkReadMessageId(messageId)) return
        viewModelScope.launch { markRoomReadUpTo(roomId, messageId) }
    }

    internal fun schedulePeerReadCursorPoll(roomId: String, sentMessageId: String) {
        val rid = roomId.trim()
        val sentId = sentMessageId.trim()
        if (rid.isEmpty() || !isValidMarkReadMessageId(sentId)) return
        peerReadPollJobs[rid]?.cancel()
        val baseline = otherReadUptoByRoom[rid]
        peerReadPollJobs[rid] = viewModelScope.launch {
            kotlinx.coroutines.delay(PEER_READ_CURSOR_POLL_DELAY_MS)
            val current = otherReadUptoByRoom[rid]
            if (current != null && isObjectIdNewer(current, baseline)) return@launch
            if (current != null && isObjectIdNewer(current, sentId)) return@launch
            repository.getPeerReadCursor(rid).getOrNull()?.let { peerUpto ->
                val publish = PeerReadCursorLogic.hydratePeerRead(
                    otherReadUptoByRoom = otherReadUptoByRoom,
                    selectedRoomId = _state.value.selectedRoomId,
                    roomId = rid,
                    peerUptoMessageId = peerUpto,
                )
                if (publish != null) {
                    _otherReadUptoMessageId.value = publish
                }
            }
        }
    }

  /**
     * Socket `room:join` targets. When chat UI is inactive, rely on `rooms:unread` on `user:{id}`
     * (backend fanout) and only join raid/hub/selected plus rooms that still show a local badge.
     */
    internal fun realtimeSubscriptionRoomIds(rooms: List<ChatRoomDto>): List<String> =
        orderRealtimeSubscriptionRoomIds(
            rooms = rooms,
            selectedRoomId = _state.value.selectedRoomId,
            raidRoomId = chatRoomPreferences.getRaidRoomId(),
            hubRoomId = ChatRoomKindResolver.allianceHubRoom(rooms)?.id,
            subscribeAllRooms = true,
        )

    fun refreshChat() {
        scheduleBootstrap(preferAllianceHubRoom = true, force = true)
    }

    /** Stable LazyColumn key — survives pending → server id confirm without remove/insert flicker. */
    fun messageListCompositionKey(message: ChatMessage): String {
        val id = message._id?.trim().orEmpty()
        if (id.isNotEmpty()) {
            lazyColumnKeyByMessageId[id]?.let { return it }
            val index = messageIdIndex[id]
            if (index != null &&
                vmState.value.messages.count { it._id?.trim() == id } > 1
            ) {
                return "$id@$index"
            }
            return id
        }
        return chatMessageKey(message)
    }

    internal fun registerOutgoingLazyColumnKey(messageId: String) {
        val id = messageId.trim()
        if (id.isEmpty()) return
        lazyColumnKeyByMessageId[id] = "out-${UUID.randomUUID()}"
    }

    internal fun transferOutgoingLazyColumnKey(fromMessageId: String, toMessageId: String) {
        val from = fromMessageId.trim()
        val to = toMessageId.trim()
        if (from.isEmpty() || to.isEmpty() || from == to) return
        val stable = lazyColumnKeyByMessageId.remove(from) ?: return
        lazyColumnKeyByMessageId[to] = stable
    }

    internal fun dropOutgoingLazyColumnKey(messageId: String?) {
        val id = messageId?.trim().orEmpty()
        if (id.isEmpty()) return
        lazyColumnKeyByMessageId.remove(id)
    }
    // --- delegated to ui/chat extension files ---
    internal suspend fun warmUpForLaunchLight() = warmUpForLaunchLightImpl()
    internal suspend fun warmUpForLaunch() = warmUpForLaunchImpl()
    fun continueLaunchWarmup() = continueLaunchWarmupImpl()
    fun primeFromLaunchDisk() = primeFromLaunchDiskImpl()
    suspend fun primeFromLaunchDiskForOverlay() = primeFromLaunchDiskForOverlayImpl()
    internal fun readLaunchDiskPrimePayload() = readLaunchDiskPrimePayloadImpl()
    internal fun applyLaunchDiskPrimePayload(payload: LaunchDiskPrimePayload) = applyLaunchDiskPrimePayloadImpl(payload)
    internal fun bindRoomStoreObservers() = bindRoomStoreObserversImpl()
    internal fun bindOutboxObservers(roomId: String?) = bindOutboxObserversImpl(roomId)
    internal fun schedulePersistChatSnapshot() = schedulePersistChatSnapshotImpl()
    internal fun persistChatSnapshot() = persistChatSnapshotImpl()
    internal fun shouldAutoMarkReadSelectedRoom() = shouldAutoMarkReadSelectedRoomImpl()
    internal fun shouldOverlayAutoMarkReadSelectedRoom() = shouldOverlayAutoMarkReadSelectedRoomImpl()
    internal fun markOverlayPanelReadToNewestIncoming() = markOverlayPanelReadToNewestIncomingImpl()
    internal suspend fun hydratePeerReadCursor(roomId: String) = hydratePeerReadCursorImpl(roomId)
    fun markOverlayVisibleMessagesAsRead(messageIds: List<String>) = markOverlayVisibleMessagesAsReadImpl(messageIds)
    fun jumpToFirstUnreadInSelectedRoom() = jumpToFirstUnreadInSelectedRoomImpl()
    internal fun isValidMarkReadMessageId(messageId: String?) = isValidMarkReadMessageIdImpl(messageId)
    internal fun scheduleMarkReadForVisibleIncoming(message: ChatMessage) = scheduleMarkReadForVisibleIncomingImpl(message)
    internal fun scheduleMarkReadAfterIncomingBatch(
        roomId: String?,
        scopedBatch: List<ChatMessage>,
        cappedMessages: List<ChatMessage>,
    ) = scheduleMarkReadAfterIncomingBatchImpl(roomId, scopedBatch, cappedMessages)
    internal fun clearUnreadWhileActivelyViewing(roomId: String) = clearUnreadWhileActivelyViewingImpl(roomId)
    internal fun acknowledgeOwnOutgoingInActiveRoom(roomId: String, serverMessageId: String?) =
        acknowledgeOwnOutgoingInActiveRoomImpl(roomId, serverMessageId)
    internal fun recomputeRoomUnreadBadges() = recomputeRoomUnreadBadgesImpl()
    fun syncReadStateFromPreferences() = syncReadStateFromPreferencesImpl()
    suspend fun awaitPendingMarkRead() = awaitPendingMarkReadImpl()
    suspend fun markAllRoomsReadUpToLatest() = markAllRoomsReadUpToLatestImpl()
    internal fun startOverlayAutoMarkReadCollector() = startOverlayAutoMarkReadCollectorImpl()
    internal fun scheduleBootstrap(
        preferAllianceHubRoom: Boolean = false,
        preferOverlayRaidRoom: Boolean = false,
        force: Boolean = false,
    ) = scheduleBootstrapImpl(preferAllianceHubRoom, preferOverlayRaidRoom, force)
    internal fun overlayHubAlreadyReady(rooms: List<ChatRoomDto>) = overlayHubAlreadyReadyImpl(rooms)
    internal fun overlayHubRoomsReady(rooms: List<ChatRoomDto>) = overlayHubRoomsReadyImpl(rooms)
    fun overlayHubRoomsReadyForPanel() = overlayHubRoomsReadyForPanelImpl()
    fun overlayHubReadyForPanel() = overlayHubReadyForPanelImpl()
    internal fun overlayRaidAlreadyReady(rooms: List<ChatRoomDto>) = overlayRaidAlreadyReadyImpl(rooms)
    internal suspend fun resolveRoomsForBootstrap(
        preferAllianceHubRoom: Boolean,
        preferOverlayRaidRoom: Boolean = false,
    ) = resolveRoomsForBootstrapImpl(preferAllianceHubRoom, preferOverlayRaidRoom)
    fun refreshTeamProfileGate() = refreshTeamProfileGateImpl()
    fun syncRoomsFromServer(reconfirmVisibleRoom: Boolean = true) = syncRoomsFromServerImpl(reconfirmVisibleRoom)
    fun ensureAllianceHubRoomSelected() = ensureAllianceHubRoomSelectedImpl()
    fun onChatTabPaused() = onChatTabPausedImpl()
    fun onChatTabResumed() = onChatTabResumedImpl()
    fun onOverlayChatPanelClosed() = onOverlayChatPanelClosedImpl()
    internal fun sortChatRoomsForDisplay(rooms: List<ChatRoomDto>) = sortChatRoomsForDisplayImpl(rooms)
    internal fun syncRaidRoomPreference(rooms: List<ChatRoomDto>) = syncRaidRoomPreferenceImpl(rooms)
    fun refreshTeamProfileGateLight() = refreshTeamProfileGateLightImpl()
    fun refreshStickerPackAccess() = refreshStickerPackAccessImpl()
    internal suspend fun loadTeamProfileGate(forceRefresh: Boolean = false) = loadTeamProfileGateImpl(forceRefresh)
    internal fun canModerateChat(message: ChatMessage) = canModerateChatImpl(message)
    internal fun allianceHubRoomId(rooms: List<ChatRoomDto>) = allianceHubRoomIdImpl(rooms)
    internal fun allianceRaidRoomId(rooms: List<ChatRoomDto>) = allianceRaidRoomIdImpl(rooms)
    internal fun messagesBelongToRoom(messages: List<ChatMessage>, roomId: String) =
        messagesBelongToRoomImpl(messages, roomId)
    internal fun resolveOverlayPreferredRoomId(rooms: List<ChatRoomDto>, preferOverlayRaidRoom: Boolean) =
        resolveOverlayPreferredRoomIdImpl(rooms, preferOverlayRaidRoom)
    internal fun resolveStartupRoomId(rooms: List<ChatRoomDto>, preferOverlayRaidRoom: Boolean = false) =
        resolveStartupRoomIdImpl(rooms, preferOverlayRaidRoom)
    internal fun isGlobalChatRoom(roomId: String, rooms: List<ChatRoomDto> = _state.value.rooms) =
        isGlobalChatRoomImpl(roomId, rooms)
    internal fun isRaidChatRoom(roomId: String, rooms: List<ChatRoomDto> = _state.value.rooms) =
        isRaidChatRoomImpl(roomId, rooms)
    internal fun globalSendBlocked(roomId: String, messageText: String, replyToMessageId: String?) =
        globalSendBlockedImpl(roomId, messageText, replyToMessageId)
    internal suspend fun bootstrap(
        preferAllianceHubRoom: Boolean = false,
        preferOverlayRaidRoom: Boolean = false,
        force: Boolean = false,
        deferNetworkMessages: Boolean = false,
    ) = bootstrapImpl(preferAllianceHubRoom, preferOverlayRaidRoom, force, deferNetworkMessages)
    fun clearHistoryForSelectedRoom() = clearHistoryForSelectedRoomImpl()
    internal fun applyClearedRoomHistoryLocal(roomId: String, unreadCount: Int) =
        applyClearedRoomHistoryLocalImpl(roomId, unreadCount)
    fun selectRoom(roomId: String) = selectRoomImpl(roomId)
    internal fun clearUnreadForRoom(rooms: List<ChatRoomDto>, roomId: String) = clearUnreadForRoomImpl(rooms, roomId)
    internal fun clearUnreadForRoomIfViewing(
        rooms: List<ChatRoomDto>,
        roomId: String,
        treatAsViewing: Boolean = false,
    ) = clearUnreadForRoomIfViewingImpl(rooms, roomId, treatAsViewing)
    internal fun hydrateReadCursorsFromPreferences() = hydrateReadCursorsFromPreferencesImpl()
    internal fun hydrateReadCursorsFromRooms(rooms: List<ChatRoomDto>) = hydrateReadCursorsFromRoomsImpl(rooms)
    internal fun mergeReadCursor(roomId: String, messageId: String) = mergeReadCursorImpl(roomId, messageId)
    internal fun applyRoomsFromServer(serverRooms: List<ChatRoomDto>) = applyRoomsFromServerImpl(serverRooms)
    internal fun mergeRoomsUnreadFromServer(serverRooms: List<ChatRoomDto>) = mergeRoomsUnreadFromServerImpl(serverRooms)
    internal fun clearOptimisticUnreadFloor(roomId: String) = clearOptimisticUnreadFloorImpl(roomId)
    internal fun syncTabUnreadBadge(rooms: List<ChatRoomDto> = _state.value.rooms) = syncTabUnreadBadgeImpl(rooms)
    internal fun deviceLastReadMessageId(roomId: String) = deviceLastReadMessageIdImpl(roomId)
    internal fun deviceLastReadMessageId(room: ChatRoomDto) = deviceLastReadMessageIdImpl(room)
    internal fun resolvedLastReadMessageId(room: ChatRoomDto) = resolvedLastReadMessageIdImpl(room)
    internal fun effectiveUnreadForRoom(room: ChatRoomDto) = effectiveUnreadForRoomImpl(room)
    internal fun shouldTrackUnreadForMessage(roomId: String, messageId: String) =
        shouldTrackUnreadForMessageImpl(roomId, messageId)
    internal fun bumpRoomUnreadLocally(roomId: String, messageId: String) = bumpRoomUnreadLocallyImpl(roomId, messageId)
    internal fun scheduleUnreadSyncFromServer() = scheduleUnreadSyncFromServerImpl()
    internal fun shouldSkipBackgroundMessageRefreshForRoom(roomId: String) =
        shouldSkipBackgroundMessageRefreshForRoomImpl(roomId)
    internal fun rehydrateSelectedRoomMessagesFromCache() = rehydrateSelectedRoomMessagesFromCacheImpl()
    internal fun rehydrateRoomMessagesFromCache(roomId: String) = rehydrateRoomMessagesFromCacheImpl(roomId)
    internal fun snapshotSelectedRoomToMessageCache() = snapshotSelectedRoomToMessageCacheImpl()
    internal suspend fun reconfirmReadForVisibleRoom() = reconfirmReadForVisibleRoomImpl()
    internal suspend fun reconcileStaleServerUnread(
        mergedRooms: List<ChatRoomDto>,
        rawServerRooms: List<ChatRoomDto>,
    ) = reconcileStaleServerUnreadImpl(mergedRooms, rawServerRooms)
    internal suspend fun markRoomReadUpTo(roomId: String, messageId: String, forceSync: Boolean = false) =
        markRoomReadUpToImpl(roomId, messageId, forceSync)
    internal fun onRoomUnreadFromServer(event: ChatRoomUnreadEvent) = onRoomUnreadFromServerImpl(event)
    internal fun publishMessagesDerived(messages: List<ChatMessage>) = publishMessagesDerivedImpl(messages)
    internal fun publishMessagesDerivedNow(messages: List<ChatMessage>) = publishMessagesDerivedNowImpl(messages)
    internal fun maybeRefreshPinBarUi() = maybeRefreshPinBarUiImpl()
    internal fun publishMessagesDerivedAfterPatch(messages: List<ChatMessage>, messageIndex: Int) =
        publishMessagesDerivedAfterPatchImpl(messages, messageIndex)
    internal fun publishMessagesDerivedImmediate(messages: List<ChatMessage>) =
        publishMessagesDerivedImmediateImpl(messages)
    internal suspend fun buildDerivedAfterUpsert(
        messages: List<ChatMessage>,
        previousMessages: List<ChatMessage>,
        previousDerived: ChatMessagesListDerived,
    ) = buildDerivedAfterUpsertImpl(messages, previousMessages, previousDerived)

    internal fun diskChatRoomsOrNull(): List<ChatRoomDto>? {
        if (currentUserId.isBlank()) return null
        return kotlinx.coroutines.runBlocking(Dispatchers.IO) {
            roomStoreBindings.loadRoomsSnapshot()
        } ?: launchDiskCache.loadChatRooms(currentUserId)
    }

    internal fun loadRoomSnapshotFromStore(roomId: String): ChatRoomMessageCache? {
        if (currentUserId.isBlank() || roomId.isBlank()) return null
        val snapshot = kotlinx.coroutines.runBlocking(Dispatchers.IO) {
            chatSyncEngine.loadRoomSnapshotFromStore(currentUserId, roomId)
        } ?: return null
        val scrubbed = if (snapshot.messages.isEmpty()) {
            emptyList()
        } else {
            filterMessagesForRoom(
                messagesWithoutLocallyRemoved(snapshot.messages),
                roomId,
            )
        }
        val capped = capNewestFirst(scrubbed, CHAT_PAGE_SIZE)
        return ChatRoomMessageCache(
            messages = capped,
            hasMoreOlder = snapshot.hasMoreOlder,
        ).also { entry ->
            roomMessageCache[roomId] = entry
            if (capped.isNotEmpty()) {
                ChatSessionCache.updateMessages(roomId, capped)
            }
        }
    }

    /** Select raid/hub for overlay outgoing without full openRoom network gate. */
    internal fun ensureSelectedRoomForOverlayOutgoing(roomId: String) =
        ensureSelectedRoomForOverlayOutgoingImpl(roomId)

    /**
     * Синхронно подставить комнаты/ленту из RAM, [ChatSessionCache] или [LaunchDiskCache]
     * до первого кадра оверлея (без «Пока нет сообщений…» и ожидания bootstrap).
     */
    fun primeOverlayChatFromCache(
        preferAllianceHubRoom: Boolean = true,
        preferOverlayRaidRoom: Boolean = false,
    ) {
        syncBundle.overlaySync.primeOverlayChatFromCache(preferAllianceHubRoom, preferOverlayRaidRoom)
    }

    /** Pull server timeline after showing overlay cache (missed messages while offline). */
    internal fun scheduleOverlayRoomHistorySync(roomId: String) {
        syncBundle.overlaySync.scheduleOverlayRoomHistorySync(roomId)
    }

    /** Оверлей-чат: по умолчанию комната «Альянс» (hub), как вкладка чата в приложении. */
    fun refreshChatForOverlay() {
        syncReadStateFromPreferences()
        primeOverlayChatFromCache(preferAllianceHubRoom = true)
        val hubReady = overlayHubAlreadyReady(_state.value.rooms)
        if (!hubReady) {
            primeFromLaunchDisk()
            primeOverlayChatFromCache(preferAllianceHubRoom = true)
        }
        if (!overlayHubAlreadyReady(_state.value.rooms)) {
            scheduleBootstrap(preferAllianceHubRoom = true, force = false)
        } else if (!hubReady) {
            syncOverlayRoomsQuietly()
        }
        refreshPinBarForSelectedRoom()
    }

    /** Список комнат в фоне — без сброса уже показанной ленты hub. */
    internal fun syncOverlayRoomsQuietly() {
        syncBundle.roomsListSync.syncOverlayRoomsQuietly()
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
            refreshPinBarForSelectedRoom()
            reconnectRealtimeIfNeeded()
            viewModelScope.launch {
                if (!overlayChatPanelVisible) return@launch
                delay(32)
                if (!overlayChatPanelVisible) return@launch
                rehydrateSelectedRoomMessagesFromCache()
                refreshPinBarForSelectedRoom()
                if (_state.value.selectedRoomId.isNullOrBlank()) {
                    ensureAllianceHubRoomSelected()
                }
                markOverlayPanelReadToNewestIncoming()
                recomputeRoomUnreadBadges()
                val roomId = _state.value.selectedRoomId
                if (!roomId.isNullOrBlank()) {
                    refreshMessagesInBackground(roomId, force = true)
                }
                if (!hubReady && !overlayHubReadyForPanel()) {
                    scheduleBootstrap(preferAllianceHubRoom = true, force = false)
                }
            }
            startOverlayAutoMarkReadCollector()
            return
        }
        overlayAutoMarkReadJob?.cancel()
        overlayAutoMarkReadJob = null
        markOverlayPanelReadToNewestIncoming()
        snapshotSelectedRoomToMessageCache()
        schedulePersistChatSnapshot()
        viewModelScope.launch {
            awaitPendingMarkRead()
            recomputeRoomUnreadBadges()
            syncRoomsFromServer(reconfirmVisibleRoom = false)
        }
    }

    internal fun isIncomingMessageVisible(message: ChatMessage): Boolean {
        val roomId = message.roomId.trim()
        if (roomId.isEmpty()) return false
        return com.lastasylum.alliance.data.chat.ChatMessageVisibilityPolicy.isMessageVisible(
            message,
            hiddenBeforeForRoom(roomId),
        )
    }

    internal fun hiddenBeforeForRoom(roomId: String): String? =
        chatRoomPreferences.getHiddenBeforeMessageId(roomId)

    internal fun filterMessagesForRoom(
        messages: List<ChatMessage>,
        roomId: String,
    ): List<ChatMessage> =
        com.lastasylum.alliance.ui.chat.filterMessagesForRoom(
            messages,
            roomId,
            hiddenBeforeForRoom(roomId),
        )

    /** Reaction/edit/delete socket rows — update list/cache, never bump unread. */
    internal fun applyKnownChatMessageUpdate(message: ChatMessage) {
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
            roomId == selected -> applyIncomingMessage(message)
            else -> stashIncomingMessageForRoom(message)
        }
    }

    internal fun isRoomActivelyViewed(
        roomId: String,
        incomingMessage: ChatMessage? = null,
    ): Boolean {
        if (_state.value.selectedRoomId != roomId) return false
        if (overlayChatPanelVisible) {
            val selfId = currentUserId.trim()
            val isPeer = incomingMessage?.let { msg ->
                selfId.isNotEmpty() && msg.senderId.trim() != selfId
            } ?: false
            if (isPeer) return true
            if (!CombatOverlayService.isOverlayChatTabActive()) return false
            return appInForeground ||
                com.lastasylum.alliance.overlay.OverlayChatInteractionHold.isFullscreenChatTeamPanelVisible ||
                CombatOverlayService.isOverlayChatPanelOpenInGame()
        }
        if (!appInForeground) return false
        return isChatTabActive
    }


    internal fun reconnectRealtimeIfNeeded() {
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

    /** Gap-fill after socket reconnect: merge stash + REST for selected room only. */
    internal fun onChatSocketConnected() {
        forceBackgroundRefreshAfterReconnect = true
        rehydrateSelectedRoomMessagesFromCache()
        reconnectRealtimeIfNeeded()
        viewModelScope.launch {
            chatSyncEngine.reconnectOutboxResume(viewModelScope)
            OutboxResumeScheduler.schedule(getApplication(), currentUserId)
        }
        val selectedId = _state.value.selectedRoomId?.trim()?.takeIf { it.isNotEmpty() } ?: return
        viewModelScope.launch { hydratePeerReadCursor(selectedId) }
        refreshMessagesInBackground(selectedId, force = true)
    }

    internal suspend fun openRoom(
        roomId: String,
        rooms: List<ChatRoomDto>,
        hadCachedMessages: Boolean = false,
        messagesAlreadyInState: Boolean = false,
        deferNetworkMessages: Boolean = false,
    ) {
        syncBundle.roomOpenSync.openRoom(
            roomId, rooms, hadCachedMessages, messagesAlreadyInState, deferNetworkMessages,
        )
    }

    internal fun refreshMessagesInBackground(roomId: String, force: Boolean = false) {
        syncBundle.roomPagingSync.refreshMessagesInBackground(roomId, force)
    }

    internal fun applyLoadedMessagePage(
        roomId: String,
        loaded: List<ChatMessage>,
        pageSizeForHasMore: Int,
    ) {
        syncBundle.roomPagingSync.applyLoadedMessagePage(roomId, loaded, pageSizeForHasMore)
    }

    fun setMessageListScrollInProgress(inProgress: Boolean) {
        val flush = listDeriveDefer.setScrollInProgress(inProgress)
        if (flush != null) {
            publishMessagesDerivedNow(flush)
        }
    }


    fun loadOlderMessages() {
        if (!_state.value.hasMoreOlder || _state.value.isLoadingOlder || _state.value.isLoading) {
            return
        }
        viewModelScope.launch { loadOlderMessagesAwait() }
    }

    /** Loads one older page; returns whether any messages were appended. */
    internal suspend fun loadOlderMessagesAwait(ignoreHiddenWatermark: Boolean = false): Boolean {
        return syncBundle.roomPagingSync.loadOlderMessagesAwait(ignoreHiddenWatermark)
    }

    fun jumpToQuotedMessage(messageId: String) {
        val id = messageId.trim()
        if (id.isEmpty()) return
        _state.update {
            it.copy(
                scrollToMessageId = id,
                highlightMessageId = id,
                transientNotice = null,
            )
        }
        viewModelScope.launch {
            val found = jumpToChatPinnedMessage(
                messageId = id,
                messageIdsNewestFirst = {
                    _state.value.messages.mapNotNull { it._id }
                },
                hasMoreOlder = { _state.value.hasMoreOlder },
                isLoadingOlder = { _state.value.isLoadingOlder },
                loadOlder = { loadOlderMessagesAwait(ignoreHiddenWatermark = true) },
                timelineIndexForMessageId = { targetId ->
                    chatLazyIndexForMessageId(
                        _state.value.messages,
                        _listDerived.value,
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

    internal fun publishRooms(next: List<ChatRoomDto>) = publishRoomsImpl(next)
    internal fun messagesWithoutLocallyRemoved(messages: List<ChatMessage>) =
        messagesWithoutLocallyRemovedImpl(messages)
    internal fun isAllianceRaidRoom(roomId: String): Boolean = isAllianceRaidRoomImpl(roomId)
    internal fun refreshPinBarForSelectedRoom(resetBarIndex: Boolean = false) =
        refreshPinBarForSelectedRoomImpl(resetBarIndex)

    fun sendMessage(text: String, replyOverride: String? = null) = sendMessageImpl(text, replyOverride)
    fun sendDraftMessage() = sendDraftMessageImpl()
    fun retrySendFailure() = retrySendFailureImpl()
    fun dismissSendFailure() = dismissSendFailureImpl()
    fun pinMessage(messageId: String, previewSource: ChatMessage? = null) = pinMessageImpl(messageId, previewSource)
    fun unpinSelectedRoom() = unpinSelectedRoomImpl()
    fun unpinOnePinnedMessage(messageId: String) = unpinOnePinnedMessageImpl(messageId)
    fun dismissPinBarForRoom() = dismissPinBarForRoomImpl()
    fun restorePinBarForRoom() = restorePinBarForRoomImpl()
    fun isPinBarDismissedForRoom(roomId: String, activePinId: String) = isPinBarDismissedForRoomImpl(roomId, activePinId)
    fun onJumpToPinnedMessage(messageId: String) = onJumpToPinnedMessageImpl(messageId)
    fun onPinnedBarTap() = onPinnedBarTapImpl()
    fun onRoomPinChanged(event: ChatRoomPinChangedEvent) = onRoomPinChangedImpl(event)
    fun shouldSuppressOwnOutgoingRealtimeEcho(message: ChatMessage) =
        shouldSuppressOwnOutgoingRealtimeEchoImpl(message)

    fun setDraftMessage(value: String) = setDraftMessageImpl(value)
    fun onImagesPicked(uris: List<Uri>, append: Boolean = false) = onImagesPickedImpl(uris, append)
    fun removePickedImage(uri: Uri) = removePickedImageImpl(uri)
    fun clearPickedImages() = clearPickedImagesImpl()
    fun beginReplyToMessage(messageId: String) = beginReplyToMessageImpl(messageId)
    fun clearReplyToMessage() = clearReplyToMessageImpl()
    fun beginEditMessage(messageId: String) = beginEditMessageImpl(messageId)
    fun cancelEditMessage() = cancelEditMessageImpl()
    fun openMessageActions(messageId: String) = openMessageActionsImpl(messageId)
    fun dismissMessageActions() = dismissMessageActionsImpl()
    fun toggleReaction(messageId: String, emoji: String) = toggleReactionImpl(messageId, emoji)
    fun requestDeleteMessage(messageId: String) = requestDeleteMessageImpl(messageId)
    fun dismissDeleteMessage() = dismissDeleteMessageImpl()
    fun confirmDeleteMessage() = confirmDeleteMessageImpl()
    fun beginMessageSelection(messageId: String) = beginMessageSelectionImpl(messageId)
    fun toggleMessageSelection(messageId: String) = toggleMessageSelectionImpl(messageId)
    fun clearMessageSelection() = clearMessageSelectionImpl()
    fun requestBulkDelete() = requestBulkDeleteImpl()
    fun dismissBulkDeleteConfirm() = dismissBulkDeleteConfirmImpl()
    fun confirmDeleteSelectedMessages() = confirmDeleteSelectedMessagesImpl()

    fun editMessage(messageId: String, newText: String) = editMessageImpl(messageId, newText)
    fun forwardMessage(messageId: String) = forwardMessageImpl(messageId)

    internal fun applyLocallyRemovedFilterToLoadedCaches() = applyLocallyRemovedFilterToLoadedCachesImpl()
    internal fun publishRoomPin(room: ChatRoomDto) = publishRoomPinImpl(room)

    internal fun applyPinBarUi(state: ChatState): ChatState = applyPinBarUiImpl(state)
    internal fun updatePinBarUi() = updatePinBarUiImpl()
    internal fun insertOptimisticOutgoingSynchronously(message: ChatMessage, clearComposer: Boolean) =
        insertOptimisticOutgoingSynchronouslyImpl(message, clearComposer)
    internal fun removePendingOutgoingMessage(pendingId: String?) = removePendingOutgoingMessageImpl(pendingId)
    internal fun shouldDeferOwnOutgoingSocketEcho(message: ChatMessage) =
        shouldDeferOwnOutgoingSocketEchoImpl(message)
    internal fun applyMessageReplaceSynchronously(updated: ChatMessage) =
        applyMessageReplaceSynchronouslyImpl(updated)
    internal fun scrubRemovedMessage(state: ChatState, removedId: String) =
        scrubRemovedMessageImpl(state, removedId)
    internal fun persistMessageRemoved(removedId: String, roomId: String) =
        persistMessageRemovedImpl(removedId, roomId)
    internal fun applyRoomPinChangedEvent(event: ChatRoomPinChangedEvent) =
        applyRoomPinChangedEventImpl(event)

    internal fun onIncomingMessage(message: ChatMessage) = onIncomingMessageImpl(message)
    internal fun onDeletedMessage(event: ChatMessageDeletedEvent) = onDeletedMessageImpl(event)
    internal fun onTypingFromPeer(event: ChatTypingEvent) = onTypingFromPeerImpl(event)
    internal fun onRoomReadEvent(event: ChatRoomReadEvent) = onRoomReadEventImpl(event)
    internal fun shouldBlockOwnOutgoingRealtime(message: ChatMessage) =
        shouldBlockOwnOutgoingRealtimeImpl(message)
    internal fun stashIncomingMessageForRoom(message: ChatMessage) =
        stashIncomingMessageForRoomImpl(message)
    internal fun confirmPendingOutgoingMessage(pendingId: String?, sent: ChatMessage) {
        vmScope.launch {
            incomingApplyMutex.withLock {
                withContext(Dispatchers.Main.immediate) {
                    confirmPendingOutgoingMessageImpl(pendingId, sent)
                }
            }
        }
    }
    internal fun applyIncomingMessage(message: ChatMessage, clearComposer: Boolean = false) =
        applyIncomingMessageImpl(message, clearComposer)
    internal fun launchTextOutgoingSend(
        roomId: String,
        text: String,
        replyToMessageId: String?,
        source: OutboxSendSource = OutboxSendSource.ChatUi,
    ) = launchTextOutgoingSendImpl(roomId, text, replyToMessageId, source)

    internal fun messagesForRoomMerge(roomId: String) = messagesForRoomMergeImpl(roomId)
    internal fun applyIncomingBatch(batch: List<ChatMessage>, clearComposer: Boolean = false) =
        applyIncomingBatchImpl(batch, clearComposer)
    internal fun publishRaidMessageToOverlayStrip(message: ChatMessage) =
        publishRaidMessageToOverlayStripImpl(message)
    internal fun syncPinHistoryForRoom(
        roomId: String,
        room: ChatRoomDto,
        messages: List<ChatMessage>,
    ) = syncPinHistoryForRoomImpl(roomId, room, messages)
    internal fun applyRoomPinEvent(rooms: List<ChatRoomDto>, event: ChatRoomPinChangedEvent) =
        applyRoomPinEventImpl(rooms, event)
    internal fun persistPinHistory(roomId: String) = persistPinHistoryImpl(roomId)
    internal fun isPinResponseParseError(error: Throwable) = isPinResponseParseErrorImpl(error)
    internal fun clearRoomPinAfterMessageRemoved(
        state: ChatState,
        removedId: String,
        roomId: String,
    ) = clearRoomPinAfterMessageRemovedImpl(state, removedId, roomId)
    internal fun markMessageRemovedLocally(messageId: String) = markMessageRemovedLocallyImpl(messageId)
    internal fun flushRoomMessagesToDiskNow(roomId: String) = flushRoomMessagesToDiskNowImpl(roomId)
    internal fun scheduleRehydrateSelectedRoomFromStash(roomId: String) =
        scheduleRehydrateSelectedRoomFromStashImpl(roomId)
    internal fun notifyOverlayStripMessageRemoved(messageId: String) =
        notifyOverlayStripMessageRemovedImpl(messageId)

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

    internal fun ensureChatVoiceRecognizer(): ChatVoiceRecognizer {
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
        TeamMembershipNotifier.removeListener(onTeamMembershipChanged)
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

    private companion object {
        const val PIN_DIAG_TAG = "PinDiag"
    }
}

/** Flow-driven active outbox sends for the selected room. */
internal data class OutboxRoomSnapshot(
    val pendingToClientId: Map<String, String> = emptyMap(),
    val activeClientMessageIds: Set<String> = emptySet(),
    val newestPendingId: String? = null,
)

/** Telegram-like instant feedback before REST round-trip. */

