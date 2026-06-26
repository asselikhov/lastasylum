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

    /** Splash (критический путь): комнаты с диска или один listRooms; без openRoom / сети сообщений. */
internal suspend fun ChatViewModel.warmUpForLaunchLightImpl() {
        val primed = primeFromLaunchDisk()
        if (vmState.value.rooms.isEmpty()) {
            val roomsResult = withContext(Dispatchers.IO) { vmRepository.listRooms() }
            roomsResult
                .onSuccess { raw ->
                    val rooms = applyRoomsFromServer(raw)
                    syncRaidRoomPreference(rooms)
                    val selected = resolveStartupRoomId(rooms, preferOverlayRaidRoom = false)
                    chatRoomPreferencesInternal.setSelectedRoomId(selected)
                    reconcileStaleServerUnread(rooms, raw)
                    vmState.update {
                        applyPinBarUi(
                            it.copy(
                                rooms = rooms,
                                isRoomsLoading = false,
                                selectedRoomId = selected,
                            ),
                        )
                    }
                    schedulePersistChatSnapshot()
                }
                .onFailure {
                    vmState.update { it.copy(isRoomsLoading = false) }
                }
        } else {
            recomputeRoomUnreadBadges()
            vmState.update { it.copy(isRoomsLoading = false) }
        }
        launchWarmupNeedsBootstrap = true
        if (
            (primed || vmState.value.rooms.isNotEmpty()) &&
            vmState.value.messages.isEmpty() &&
            !vmState.value.selectedRoomId.isNullOrBlank()
        ) {
            launchWarmupNeedsMessages = true
        }
    }

    /** Splash: комнаты сразу; сообщения — из диска или фоном после UI. */
internal suspend fun ChatViewModel.warmUpForLaunchImpl() {
        val primed = primeFromLaunchDisk()
        if (primed) {
            bootstrap(preferAllianceHubRoom = true, force = false)
            if (vmState.value.messages.isEmpty() &&
                !vmState.value.selectedRoomId.isNullOrBlank()
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
internal fun ChatViewModel.continueLaunchWarmupImpl() {
        vmScope.launch {
            if (launchWarmupNeedsBootstrap) {
                launchWarmupNeedsBootstrap = false
                bootstrap(preferAllianceHubRoom = true, force = false, deferNetworkMessages = false)
            }
            if (!launchWarmupNeedsMessages) {
                if (vmState.value.rooms.isNotEmpty()) schedulePersistChatSnapshot()
                return@launch
            }
            val roomId = vmState.value.selectedRoomId?.trim().orEmpty()
            if (roomId.isEmpty()) {
                launchWarmupNeedsMessages = false
                return@launch
            }
            launchWarmupNeedsMessages = false
            val result = withContext(Dispatchers.IO) {
                vmRepository.loadRecentMessages(
                    roomId,
                    beforeMessageId = null,
                    limit = CHAT_INITIAL_PAGE_SIZE,
                )
            }
            result
                .onSuccess { loaded ->
                    applyLoadedMessagePage(
                        roomId = roomId,
                        loaded = loaded,
                        pageSizeForHasMore = CHAT_INITIAL_PAGE_SIZE,
                    )
                }
                .onFailure { e ->
                    vmState.update {
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
internal fun ChatViewModel.primeFromLaunchDiskImpl(): Boolean {
        val payload = readLaunchDiskPrimePayload() ?: return false
        return applyLaunchDiskPrimePayload(payload)
    }

    /** Disk reads on [Dispatchers.IO] — не блокировать main при открытии оверлей-чата. */
internal suspend fun ChatViewModel.primeFromLaunchDiskForOverlayImpl(): Boolean {
        val payload = withContext(Dispatchers.IO) { readLaunchDiskPrimePayload() } ?: return false
        return withContext(Dispatchers.Main.immediate) { applyLaunchDiskPrimePayload(payload) }
    }

internal fun ChatViewModel.readLaunchDiskPrimePayloadImpl(): LaunchDiskPrimePayload? {
        if (currentUserId.isBlank()) return null
        val roomsRaw = kotlinx.coroutines.runBlocking(Dispatchers.IO) {
            roomStoreBindings.loadRoomsSnapshot()
        } ?: launchDiskCacheInternal.loadChatRooms(currentUserId) ?: return null
        val rooms = applyRoomsFromServer(roomsRaw)
        if (rooms.isEmpty()) return null
        val selected = resolveStartupRoomId(rooms, preferOverlayRaidRoom = false)
        val roomIdsToPrime = linkedSetOf(selected)
        allianceHubRoomId(rooms)?.let { roomIdsToPrime.add(it) }
        allianceRaidRoomId(rooms)?.let { roomIdsToPrime.add(it) }
        val roomCaches = linkedMapOf<String, ChatRoomMessageCache>()
        for (rid in roomIdsToPrime) {
            val cache = kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                chatSyncEngine.loadRoomSnapshotFromStore(currentUserId, rid)
            }?.let { snapshot ->
                val scrubbed = filterMessagesForRoom(
                    messagesWithoutLocallyRemoved(snapshot.messages),
                    rid,
                )
                val capped = capNewestFirst(scrubbed, CHAT_PAGE_SIZE)
                ChatRoomMessageCache(
                    messages = capped,
                    hasMoreOlder = snapshot.hasMoreOlder || capped.size >= CHAT_PAGE_SIZE,
                )
            }
            cache?.let { roomCaches[rid] = it }
        }
        return LaunchDiskPrimePayload(
            roomsRaw = roomsRaw,
            selectedRoomId = selected,
            roomCaches = roomCaches,
        )
    }

internal fun ChatViewModel.applyLaunchDiskPrimePayloadImpl(payload: LaunchDiskPrimePayload): Boolean {
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
            // Канонический порядок уже на первом кадре: DAO отдаёт createdAtMs DESC без тай-брейка,
            // а REST-рефреш позже пересортировывает через sortMessagesNewestFirst — без этого
            // лента визуально «переезжает». Сортируем тем же ключом, что и финальный merge.
            val orderedCached = sortMessagesNewestFirst(dedupeMessagesByIdNewestFirst(cached.messages))
            knownMessageIds.clear()
            messageIdIndex.clear()
            knownMessageIds.addAll(orderedCached.mapNotNull { it._id })
            rebuildMessageIdIndex(orderedCached, messageIdIndex)
            vmState.value = applyPinBarUi(
                vmState.value.copy(
                    isLoading = false,
                    isRoomsLoading = false,
                    rooms = rooms,
                    selectedRoomId = selected,
                    messages = orderedCached,
                    hasMoreOlder = cached.hasMoreOlder,
                    error = null,
                    scrollToLatestNonce = vmState.value.scrollToLatestNonce + 1L,
                ),
            )
            publishMessagesDerived(orderedCached)
        } else {
            vmState.value = applyPinBarUi(
                vmState.value.copy(
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

internal fun ChatViewModel.bindRoomStoreObserversImpl() {
        roomStoreBindings.onRoomsFromStore = { roomsRaw ->
            val applied = applyRoomsFromServer(roomsRaw)
            if (applied.isNotEmpty()) {
                vmState.update { st ->
                    if (st.rooms.isNotEmpty()) st
                    else applyPinBarUi(st.copy(rooms = applied, isRoomsLoading = false))
                }
            }
        }
        roomStoreBindings.onMessagesFromStore = messagesFromStore@{ roomId, messages, hasMoreOlder ->
            if (postHistoryWipeAuthoritativeEmpty) return@messagesFromStore
            if (!isActiveSelectedRoom(roomId)) return@messagesFromStore
            val filtered = messagesWithoutLocallyRemoved(
                filterMessagesForRoom(messages, roomId),
            )
            if (filtered.isEmpty()) return@messagesFromStore
            persistRoomMessagesToCache(roomId, filtered, hasMoreOlder)
            if (vmState.value.messages.isEmpty() ||
                !messagesBelongToRoom(vmState.value.messages, roomId)
            ) {
                // Тот же канонический порядок, что и REST/merge, иначе первый кадр из DAO
                // (createdAtMs DESC без тай-брейка) визуально пересортировывается.
                val ordered = sortMessagesNewestFirst(dedupeMessagesByIdNewestFirst(filtered))
                knownMessageIds.clear()
                messageIdIndex.clear()
                knownMessageIds.addAll(ordered.mapNotNull { it._id })
                rebuildMessageIdIndex(ordered, messageIdIndex)
                vmState.update {
                    it.copy(
                        messages = ordered,
                        isLoading = false,
                        hasMoreOlder = hasMoreOlder,
                        selectedRoomId = roomId,
                    )
                }
                publishMessagesDerived(ordered)
            }
        }
        roomStoreBindings.onReadCursorFromStore = { roomId, cursor ->
            cursor?.trim()?.takeIf { it.isNotEmpty() }?.let { mergeReadCursor(roomId, it) }
        }
        roomStoreBindings.startRoomsObserver()
        roomStoreBindings.bindSelectedRoom(vmState.value.selectedRoomId)
        bindOutboxObservers(vmState.value.selectedRoomId)
    }

internal fun ChatViewModel.bindOutboxObserversImpl(roomId: String?) {
        outboxObserverJob?.cancel()
        val rid = roomId?.trim().orEmpty()
        val uid = currentUserId.trim()
        if (uid.isEmpty() || rid.isEmpty()) {
            outboxRoomSnapshot = OutboxRoomSnapshot()
            return
        }
        outboxObserverJob = vmScope.launch {
            chatOutbox.observeActiveForRoom(uid, rid)
                .distinctUntilChanged()
                .collect { entries ->
                    val pendingToClient = entries.associate { it.pendingMessageId to it.clientMessageId }
                    val clientIds = entries.map { it.clientMessageId }.toSet()
                    val newestPending = entries.maxByOrNull { it.createdAtMs }?.pendingMessageId
                    outboxRoomSnapshot = OutboxRoomSnapshot(
                        pendingToClientId = pendingToClient,
                        activeClientMessageIds = clientIds,
                        newestPendingId = newestPending,
                    )
                }
        }
    }

internal fun ChatViewModel.schedulePersistChatSnapshotImpl() {
        if (currentUserId.isBlank()) return
        persistSnapshotJob?.cancel()
        persistSnapshotJob = vmScope.launch(Dispatchers.IO) {
            delay(300L)
            persistChatSnapshot()
        }
    }

internal fun ChatViewModel.persistChatSnapshotImpl() {
        if (currentUserId.isBlank()) return
        val rooms = vmState.value.rooms
        if (rooms.isNotEmpty()) {
            launchDiskCacheInternal.saveChatRooms(currentUserId, rooms)
        }
        val selected = vmState.value.selectedRoomId?.trim().orEmpty()
        val hubId = allianceHubRoomId(rooms)
        val raidId = allianceRaidRoomId(rooms)
        val messagesByRoom = buildMap<String, Pair<List<ChatMessage>, Boolean>> {
            if (selected.isNotEmpty()) {
                val entry = roomMessageCache[selected]
                val raw = entry?.messages?.takeIf { it.isNotEmpty() }
                    ?: vmState.value.messages.takeIf { it.isNotEmpty() }
                val messages = raw
                    ?.let { messagesWithoutLocallyRemoved(it) }
                    ?.let { filterMessagesForRoom(it, selected) }
                if (!messages.isNullOrEmpty()) {
                    put(selected, messages to (entry?.hasMoreOlder ?: vmState.value.hasMoreOlder))
                }
            }
            if (!hubId.isNullOrBlank() && hubId != selected) {
                roomMessageCache[hubId]?.let { entry ->
                    val messages = messagesWithoutLocallyRemoved(entry.messages)
                    if (messages.isNotEmpty()) put(hubId, messages to entry.hasMoreOlder)
                }
            }
            if (!raidId.isNullOrBlank() && raidId != selected && raidId != hubId) {
                roomMessageCache[raidId]?.let { entry ->
                    val messages = messagesWithoutLocallyRemoved(entry.messages)
                    if (messages.isNotEmpty()) put(raidId, messages to entry.hasMoreOlder)
                }
            }
        }
        vmScope.launch(Dispatchers.IO) {
            chatSyncEngine.dualWriteSnapshot(currentUserId, rooms, messagesByRoom)
        }
    }

