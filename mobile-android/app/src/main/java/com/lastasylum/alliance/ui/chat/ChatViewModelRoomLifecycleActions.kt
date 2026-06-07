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
import com.lastasylum.alliance.data.chat.ChatSocketIngress
import com.lastasylum.alliance.data.chat.ChatUnreadCounts
import com.lastasylum.alliance.data.chat.ChatTeamRoomsMembership
import com.lastasylum.alliance.data.chat.sync.applyOverlayLoadTimeoutPolicy
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
internal fun ChatViewModel.shouldAutoMarkReadSelectedRoomImpl(): Boolean {
        val roomId = vmState.value.selectedRoomId ?: return false
        // Overlay HUD: read cursor advances when the panel closes, not while browsing.
        if (overlayChatPanelVisible) return false
        return isRoomActivelyViewed(roomId)
    }

internal fun ChatViewModel.shouldOverlayAutoMarkReadSelectedRoomImpl(): Boolean {
        if (!overlayChatPanelVisible) return false
        val roomId = vmState.value.selectedRoomId?.trim().orEmpty()
        if (roomId.isEmpty()) return false
        return CombatOverlayService.isOverlayChatPanelOpenInGame() ||
            com.lastasylum.alliance.overlay.OverlayChatInteractionHold.isFullscreenChatTeamPanelVisible
    }

internal fun ChatViewModel.markOverlayPanelReadToNewestIncomingImpl() {
        if (!overlayChatPanelVisible) return
        val roomId = vmState.value.selectedRoomId?.trim().orEmpty()
        if (roomId.isEmpty()) return
        val self = currentUserId.trim()
        val targetId = vmState.value.messages.firstOrNull { message ->
            val id = message._id?.trim().orEmpty()
            isValidMarkReadMessageId(id) &&
                (self.isBlank() || message.senderId.trim() != self)
        }?._id
            ?: vmState.value.messages.firstOrNull()?._id
        if (isValidMarkReadMessageId(targetId)) {
            vmScope.launch { markRoomReadUpTo(roomId, targetId!!) }
        }
    }

internal suspend fun ChatViewModel.hydratePeerReadCursorImpl(roomId: String, force: Boolean = false) {
        val rid = roomId.trim()
        if (rid.isEmpty()) return
        val lastAt = lastPeerReadFetchAtMs[rid] ?: 0L
        if (!shouldFetchPeerReadCursor(lastAtMs = lastAt, force = force)) return
        lastPeerReadFetchAtMs[rid] = System.currentTimeMillis()
        vmRepository.getPeerReadCursor(rid)
            .onSuccess { peerUpto ->
                val publish = PeerReadCursorLogic.hydratePeerRead(
                    otherReadUptoByRoom = otherReadUptoByRoom,
                    selectedRoomId = vmState.value.selectedRoomId,
                    roomId = rid,
                    peerUptoMessageId = peerUpto,
                )
                if (publish != null) {
                    _otherReadUptoMessageId.value = publish
                }
            }
    }

    /** Mark visible overlay messages read (viewport); advances cursor only forward. */
internal fun ChatViewModel.markOverlayVisibleMessagesAsReadImpl(messageIds: List<String>) {
        if (!overlayChatPanelVisible) return
        val roomId = vmState.value.selectedRoomId?.trim().orEmpty()
        if (roomId.isEmpty()) return
        val room = vmState.value.rooms.find { it.id == roomId } ?: return
        val lastRead = resolvedLastReadMessageId(room)?.trim().orEmpty()
        val self = currentUserId.trim()
        var watermark: String? = null
        for (raw in messageIds) {
            val id = raw.trim()
            if (!isValidMarkReadMessageId(id)) continue
            if (lastRead.isNotEmpty() && !isObjectIdNewer(id, lastRead)) continue
            val senderId = vmState.value.messages.find { it._id?.trim() == id }?.senderId?.trim().orEmpty()
            if (self.isNotBlank() && senderId == self) continue
            watermark = when (val prev = watermark) {
                null -> id
                else -> if (isObjectIdNewer(id, prev)) id else prev
            }
        }
        val markId = watermark ?: return
        val cursor = lastRead
        if (cursor.isNotEmpty() && !isObjectIdNewer(markId, cursor)) return
        vmScope.launch { markRoomReadUpTo(roomId, markId) }
    }

    /** Oldest unread incoming in the open room (reverse list: last matching row). */
internal fun ChatViewModel.jumpToFirstUnreadInSelectedRoomImpl() {
        val roomId = vmState.value.selectedRoomId?.trim().orEmpty()
        if (roomId.isEmpty()) return
        val room = vmState.value.rooms.find { it.id == roomId } ?: return
        val lastRead = resolvedLastReadMessageId(room)?.trim().orEmpty()
        if (lastRead.isEmpty()) return
        val self = currentUserId.trim()
        val targetId = vmState.value.messages.lastOrNull { message ->
            val id = message._id?.trim().orEmpty()
            if (!isValidMarkReadMessageId(id)) return@lastOrNull false
            if (self.isNotBlank() && message.senderId.trim() == self) return@lastOrNull false
            isObjectIdNewer(id, lastRead)
        }?._id ?: return
        jumpToQuotedMessage(targetId)
    }

internal fun ChatViewModel.isValidMarkReadMessageIdImpl(messageId: String?): Boolean {
        val id = messageId?.trim().orEmpty()
        return id.isNotEmpty() && !id.startsWith("pending-")
    }

    /** Peer opened the thread — advance local read cursor so sender gets ✓✓ via room:read. */
internal fun ChatViewModel.scheduleMarkReadForVisibleIncomingImpl(message: ChatMessage) {
        val roomId = vmState.value.selectedRoomId?.trim().orEmpty()
        val messageId = message._id?.trim().orEmpty()
        if (roomId.isEmpty() || !isValidMarkReadMessageId(messageId)) return
        if (!shouldAutoMarkReadSelectedRoom()) return
        if (message.senderId.trim() == currentUserId.trim()) return
        vmScope.launch {
            markRoomReadUpTo(roomId, messageId)
        }
    }

internal fun ChatViewModel.scheduleMarkReadAfterIncomingBatchImpl(
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
        vmScope.launch {
            kotlinx.coroutines.yield()
            markRoomReadUpTo(rid, markId)
        }
    }

    /** While the user is in this room, never show a local unread badge on the tab/chip. */
internal fun ChatViewModel.clearUnreadWhileActivelyViewingImpl(roomId: String) {
        val rid = roomId.trim()
        if (rid.isEmpty() || !isRoomActivelyViewed(rid)) return
        clearOptimisticUnreadFloor(rid)
        vmState.update { st ->
            st.copy(rooms = clearUnreadForRoom(st.rooms, rid))
        }
        ChatSessionCache.update(vmState.value.rooms)
        syncTabUnreadBadge()
        syncOverlayAllianceHubBadge()
    }

    /** After HTTP confirm — advance read cursor so peer gets room:read and ✓✓ on sender side. */
internal fun ChatViewModel.acknowledgeOwnOutgoingInActiveRoomImpl(roomId: String, serverMessageId: String?) {
        clearUnreadWhileActivelyViewing(roomId)
        val id = serverMessageId?.trim().orEmpty()
        if (!isValidMarkReadMessageId(id)) return
        mergeReadCursor(roomId, id)
        vmScope.launch { markRoomReadUpTo(roomId, id) }
    }

internal fun ChatViewModel.recomputeRoomUnreadBadgesImpl() {
        val rooms = vmState.value.rooms
        if (rooms.isEmpty()) return
        val adjusted = mergeRoomsUnreadFromServer(rooms)
        vmState.update { it.copy(rooms = adjusted) }
        syncTabUnreadBadge(adjusted)
        ChatSessionCache.update(adjusted)
        syncOverlayAllianceHubBadge(adjusted)
    }

    /**
     * Reload per-room read cursors from SharedPreferences (e.g. user read in the main app)
     * and recompute tab badges before overlay or tab resume uses cached room lists.
     */
internal fun ChatViewModel.syncReadStateFromPreferencesImpl() {
        hydrateReadCursorsFromPreferences()
        val rooms = vmState.value.rooms
        if (rooms.isEmpty()) return
        val adjusted = mergeRoomsUnreadFromServer(rooms)
        syncTabUnreadBadge(adjusted)
        vmState.update { it.copy(rooms = adjusted) }
        ChatSessionCache.update(adjusted)
        syncOverlayAllianceHubBadge(adjusted)
    }

    /** Overlay panel closed — wait for debounced mark-read POST before releasing shared VM state. */
internal suspend fun ChatViewModel.awaitPendingMarkReadImpl() {
        markReadCoalescer.flushAndAwait()
    }

    /** Mark every room with unread up to the latest known message (overlay DoneAll). */
internal suspend fun ChatViewModel.markAllRoomsReadUpToLatestImpl() {
        val rooms = vmState.value.rooms
        if (rooms.isEmpty()) return
        val selectedId = vmState.value.selectedRoomId?.trim().orEmpty()
        for (room in rooms) {
            val hasUnread = effectiveUnreadForRoom(room) > 0 || room.unreadCount > 0
            if (!hasUnread) continue
            val messageId = when (room.id.trim()) {
                selectedId -> vmState.value.messages.firstOrNull()?._id?.trim().orEmpty()
                else -> ""
            }.takeIf { isValidMarkReadMessageId(it) }
                ?: vmRepository.loadRecentMessages(room.id, beforeMessageId = null, limit = 1)
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
        CombatOverlayService.refreshStatusHudAfterMarkAll()
    }

internal fun ChatViewModel.startOverlayAutoMarkReadCollectorImpl() {
        overlayAutoMarkReadJob?.cancel()
        overlayAutoMarkReadJob = vmScope.launch {
            vmState
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
                    if (!shouldOverlayAutoMarkReadSelectedRoom()) return@collect
                    markRoomReadUpTo(rid, newestId!!)
                }
        }
    }

internal fun ChatViewModel.scheduleBootstrapImpl(
        preferAllianceHubRoom: Boolean = false,
        preferOverlayRaidRoom: Boolean = false,
        force: Boolean = false,
    ) {
        bootstrapJob?.cancel()
        bootstrapJob = vmScope.launch {
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
                    val timeoutMessage = res.getString(R.string.overlay_panel_load_timeout)
                    vmState.update { applyOverlayLoadTimeoutPolicy(it, timeoutMessage) }
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

internal fun ChatViewModel.overlayHubAlreadyReadyImpl(rooms: List<ChatRoomDto>): Boolean =
        syncBundle.overlaySync.overlayHubAlreadyReady(rooms)

internal fun ChatViewModel.overlayHubRoomsReadyImpl(rooms: List<ChatRoomDto>): Boolean =
        syncBundle.overlaySync.overlayHubRoomsReady(rooms)

    /** Overlay panel: hub room selected with rooms list (messages may still load in background). */
internal fun ChatViewModel.overlayHubRoomsReadyForPanelImpl(): Boolean = overlayHubRoomsReadyImpl(vmState.value.rooms)

    /** Overlay panel: hub room has messages to show without waiting on network. */
internal fun ChatViewModel.overlayHubReadyForPanelImpl(): Boolean =
        overlayHubAlreadyReadyImpl(vmState.value.rooms) ||
            overlayHubRoomsReadyImpl(vmState.value.rooms)

internal fun ChatViewModel.overlayRaidAlreadyReadyImpl(rooms: List<ChatRoomDto>): Boolean =
        syncBundle.overlaySync.overlayRaidAlreadyReady(rooms)

internal suspend fun ChatViewModel.resolveRoomsForBootstrapImpl(
        preferAllianceHubRoom: Boolean,
        preferOverlayRaidRoom: Boolean = false,
    ): Result<List<ChatRoomDto>> =
        syncBundle.roomsListSync.resolveRoomsForBootstrap(preferAllianceHubRoom, preferOverlayRaidRoom)

    /** Refresh profile gate when returning from profile or opening chat. */
internal fun ChatViewModel.refreshTeamProfileGateImpl() {
        vmScope.launch {
            usersRepositoryInternal.getMyProfile()
            val teamId = usersRepositoryInternal.peekMyProfile()?.playerTeamId
            val hasTeam = loadTeamProfileGate()
            val cached = ChatSessionCache.getFreshRooms()
            val roomsResult = if (
                cached != null &&
                ChatTeamRoomsMembership.cacheMatchesProfile(cached, teamId)
            ) {
                Result.success(cached)
            } else {
                if (cached != null) ChatRoomsSessionCache.invalidate()
                vmRepository.listRooms()
            }
            vmState.value = if (roomsResult.isSuccess) {
                val nextRooms = applyRoomsFromServer(
                    roomsResult.getOrElse { vmState.value.rooms },
                )
                syncRaidRoomPreference(nextRooms)
                applyPinBarUi(
                    vmState.value.copy(
                        hasTeamProfileForGlobalChat = hasTeam,
                        rooms = nextRooms,
                    ),
                )
            } else {
                vmState.value.copy(hasTeamProfileForGlobalChat = hasTeam)
            }
        }
    }

    /** Sync tab badges from API (e.g. after overlay chat or app resume). */
internal fun ChatViewModel.syncRoomsFromServerImpl(reconfirmVisibleRoom: Boolean = true) {
        syncBundle.roomsListSync.syncRoomsFromServer(reconfirmVisibleRoom)
    }

    /** При входе на вкладку «Чат» — комната «Альянс», если доступна. */
internal fun ChatViewModel.ensureAllianceHubRoomSelectedImpl() {
        val hubId = allianceHubRoomId(vmState.value.rooms) ?: return
        if (vmState.value.selectedRoomId == hubId) return
        if (vmState.value.rooms.isEmpty()) return
        selectRoom(hubId)
    }

    /** User left the Chat tab — stop treating selected room as actively viewed. */
internal fun ChatViewModel.onChatTabPausedImpl() {
        isChatTabActive = false
        snapshotSelectedRoomToMessageCache()
    }

    /** Returning to the Chat tab: refresh badges; read cursor only when room is visible. */
internal fun ChatViewModel.onChatTabResumedImpl() {
        isChatTabActive = true
        reconnectRealtimeIfNeeded()
        syncReadStateFromPreferences()
        vmScope.launch {
            // Let the tab compose and draw once before room sync / cache hydration.
            delay(32)
            if (!isChatTabActive) return@launch
            refreshStickerPackAccess()
            val cachedRooms = ChatSessionCache.getFreshRooms()
            val roomsStale = System.currentTimeMillis() - lastRoomsSyncedAtMs > CHAT_ROOMS_SYNC_ON_RESUME_TTL_MS
            when {
                cachedRooms != null && vmState.value.rooms.isEmpty() -> {
                    val next = applyRoomsFromServer(cachedRooms)
                    publishRooms(next)
                    lastRoomsSyncedAtMs = System.currentTimeMillis()
                }
                vmState.value.rooms.isNotEmpty() && !roomsStale -> {
                    recomputeRoomUnreadBadges()
                }
                else -> {
                    syncRoomsFromServer(reconfirmVisibleRoom = false)
                }
            }
            val roomId = vmState.value.selectedRoomId?.trim().orEmpty()
            if (roomId.isEmpty()) return@launch
            rehydrateSelectedRoomMessagesFromCache()
            refreshPinBarForSelectedRoom()
            refreshMessagesInBackground(roomId, force = false)
            if (vmState.value.selectedRoomId.isNullOrBlank()) {
                ensureAllianceHubRoomSelected()
            }
            val activeRoomId = vmState.value.selectedRoomId
            val newestId = vmState.value.messages.firstOrNull()?._id
            val roomUnread = vmState.value.rooms.find { it.id == activeRoomId }?.unreadCount ?: 0
            if (!activeRoomId.isNullOrBlank() && !newestId.isNullOrBlank() && roomUnread <= 0) {
                markRoomReadUpTo(activeRoomId, newestId)
            }
        }
    }

    /** Overlay fullscreen chat closed — reclaim socket + refresh server unread counts. */
internal fun ChatViewModel.onOverlayChatPanelClosedImpl() {
        syncReadStateFromPreferences()
        vmScope.launch {
            syncRoomsFromServer()
            reconnectRealtimeIfNeeded()
        }
    }

internal fun ChatViewModel.sortChatRoomsForDisplayImpl(rooms: List<ChatRoomDto>): List<ChatRoomDto> =
        ChatRoomsUseCase.sortForDisplay(rooms)

internal fun ChatViewModel.syncRaidRoomPreferenceImpl(rooms: List<ChatRoomDto>) {
        vmRepository.applyOverlayRoomsFromRooms(rooms)
    }

    /**
     * Только профиль/стикеры/гейт глобального чата — без [vmRepository.listRooms].
     * Используется при входе на вкладку чата, чтобы не дублировать сетевую нагрузку с [bootstrap].
     */
internal fun ChatViewModel.refreshTeamProfileGateLightImpl() {
        vmScope.launch {
            usersRepositoryInternal.getMyProfile()
            val teamId = usersRepositoryInternal.peekMyProfile()?.playerTeamId
            val cached = ChatSessionCache.getFreshRooms()
            val staleRooms = cached != null &&
                !ChatTeamRoomsMembership.cacheMatchesProfile(cached, teamId)
            if (staleRooms) {
                ChatRoomsSessionCache.invalidate()
            }
            val hasTeam = loadTeamProfileGate()
            if (staleRooms && (isChatTabActive || overlayChatPanelVisible)) {
                bootstrap(preferAllianceHubRoom = true, force = true)
                return@launch
            }
            vmState.value = vmState.value.copy(hasTeamProfileForGlobalChat = hasTeam)
        }
    }

internal fun ChatViewModel.refreshStickerPackAccessImpl() {
        vmScope.launch {
            val keys = usersRepositoryInternal.getMyProfile().getOrNull()
                ?.enabledStickerPacks
                ?.toSet()
                ?: emptySet()
            vmState.update { it.copy(enabledStickerPackKeys = keys) }
        }
    }

internal suspend fun ChatViewModel.loadTeamProfileGateImpl(forceRefresh: Boolean = false): Boolean {
        val now = System.currentTimeMillis()
        if (!forceRefresh) {
            cachedTeamProfileGate?.let { cached ->
                if (now - profileGateLoadedAtMs < CHAT_PROFILE_GATE_TTL_MS) {
                    return cached
                }
            }
        }
        val p = usersRepositoryInternal.peekMyProfile()
            ?: usersRepositoryInternal.getMyProfile().getOrNull()
        val keys = p?.enabledStickerPacks?.toSet() ?: emptySet()
        vmState.value = vmState.value.copy(
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

internal fun ChatViewModel.canModerateChatImpl(message: ChatMessage): Boolean =
        canDeleteChatMessage(
            message = message,
            currentUserId = currentUserId,
            isAppAdmin = vmState.value.isAppAdmin,
            playerTeamSquadRole = vmState.value.playerTeamSquadRole,
        )

internal fun ChatViewModel.allianceHubRoomIdImpl(rooms: List<ChatRoomDto>): String? =
        ChatRoomsUseCase.allianceHubRoomId(rooms)

internal fun ChatViewModel.allianceRaidRoomIdImpl(rooms: List<ChatRoomDto>): String? =
        ChatRoomsUseCase.allianceRaidRoomId(rooms, chatRoomPreferencesInternal.getRaidRoomId())

internal fun ChatViewModel.messagesBelongToRoomImpl(messages: List<ChatMessage>, roomId: String): Boolean {
        if (messages.isEmpty()) return true
        val rid = roomId.trim()
        if (rid.isEmpty()) return true
        return messages.all { it.roomId.trim() == rid }
    }

internal fun ChatViewModel.resolveOverlayPreferredRoomIdImpl(
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

    /** Keep user/prefs room when valid; hub default only when nothing is selected yet. */
internal fun ChatViewModel.resolveStartupRoomIdImpl(
        rooms: List<ChatRoomDto>,
        preferOverlayRaidRoom: Boolean = false,
    ): String {
        val fromState = vmState.value.selectedRoomId?.trim().orEmpty()
        if (fromState.isNotEmpty() && rooms.any { it.id == fromState }) return fromState
        val fromPrefs = chatRoomPreferencesInternal.getSelectedRoomId()?.trim().orEmpty()
        if (fromPrefs.isNotEmpty() && rooms.any { it.id == fromPrefs }) return fromPrefs
        return resolveOverlayPreferredRoomId(
            rooms = rooms,
            preferOverlayRaidRoom = preferOverlayRaidRoom,
        ) ?: rooms.first().id
    }

internal fun ChatViewModel.isGlobalChatRoomImpl(
        roomId: String,
        rooms: List<ChatRoomDto> = vmState.value.rooms,
    ): Boolean =
        rooms.find { it.id == roomId }?.allianceId == ChatAllianceIds.GLOBAL

internal fun ChatViewModel.isRaidChatRoomImpl(
        roomId: String,
        rooms: List<ChatRoomDto> = vmState.value.rooms,
    ): Boolean {
        val room = rooms.find { it.id == roomId } ?: return false
        return ChatRoomKindResolver.kindOf(room) == ChatRoomKind.Raid
    }

internal fun ChatViewModel.globalSendBlockedImpl(
        roomId: String,
        messageText: String,
        replyToMessageId: String?,
    ): Boolean {
        val room = vmState.value.rooms.find { it.id == roomId } ?: return false
        if (room.allianceId != ChatAllianceIds.GLOBAL ||
            vmState.value.hasTeamProfileForGlobalChat
        ) {
            return false
        }
        vmState.value = vmState.value.copy(
            sendFailure = ChatSendFailure(
                messageText = messageText,
                replyToMessageId = replyToMessageId,
                errorMessage = res.getString(com.lastasylum.alliance.R.string.chat_global_team_required),
            ),
        )
        return true
    }

internal suspend fun ChatViewModel.bootstrapImpl(
        preferAllianceHubRoom: Boolean = false,
        preferOverlayRaidRoom: Boolean = false,
        force: Boolean = false,
        deferNetworkMessages: Boolean = false,
    ) {
        syncBundle.bootstrapSync.bootstrap(
            preferAllianceHubRoom = preferAllianceHubRoom,
            preferOverlayRaidRoom = preferOverlayRaidRoom,
            force = force,
            deferNetworkMessages = deferNetworkMessages,
        )
    }

internal fun ChatViewModel.clearHistoryForSelectedRoomImpl() {
        val roomId = vmState.value.selectedRoomId?.trim().orEmpty()
        if (roomId.isEmpty() || vmState.value.isLoading) return
        vmScope.launch {
            syncBundle.roomPagingSync.cancelBackgroundRefresh(roomId)
            markRoomClearedAuthoritativeEmpty(roomId)
            vmRepository.clearRoomHistoryForUser(roomId)
                .onSuccess { response ->
                    response.hiddenBeforeMessageId?.trim()?.takeIf { it.isNotEmpty() }?.let {
                        chatRoomPreferencesInternal.setHiddenBeforeMessageId(roomId, it)
                    }
                    response.lastReadMessageId?.trim()?.takeIf { it.isNotEmpty() }?.let {
                        chatRoomPreferencesInternal.setLastReadMessageId(roomId, it)
                        lastMarkedReadByRoom[roomId] = it
                    }
                    withContext(Dispatchers.IO) {
                        messageStore.clearRoomMessages(currentUserId, roomId)
                    }
                    applyClearedRoomHistoryLocal(roomId, response.unreadCount)
                    CombatOverlayService.notifyRoomHistoryCleared(roomId)
                }
                .onFailure { e ->
                    clearRoomAuthoritativeEmpty(roomId)
                    vmState.value = vmState.value.copy(
                        transientNotice = e.toUserMessageRu(res),
                    )
                }
        }
    }

internal fun ChatViewModel.applyClearedRoomHistoryLocalImpl(roomId: String, unreadCount: Int) {
        synchronized(chatMutationLock) {
            roomMessageCache[roomId] = ChatRoomMessageCache(
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
        val updatedRooms = vmState.value.rooms.map {
            if (it.id == roomId) it.copy(unreadCount = unreadCount.coerceAtLeast(0)) else it
        }
        vmState.value = vmState.value.copy(
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

internal fun ChatViewModel.selectRoomImpl(roomId: String) {
        if (roomId == vmState.value.selectedRoomId) return
        bootstrapJob?.cancel()
        openRoomJob?.cancel()
        if (isGlobalChatRoom(roomId)) {
            refreshTeamProfileGateLight()
        }
        val previousRoomId = vmState.value.selectedRoomId
        if (previousRoomId != null && vmState.value.messages.isNotEmpty()) {
            roomMessageCache[previousRoomId] = ChatRoomMessageCache(
                messages = vmState.value.messages,
                hasMoreOlder = vmState.value.hasMoreOlder,
            )
        }
        val previousNewestId = previousRoomId?.let { rid ->
            roomMessageCache[rid]?.messages?.firstOrNull()?._id
        }
        val cached = roomMessageCache[roomId]
            ?: ChatSessionCache.getFreshMessages(roomId)?.let { sessionMessages ->
                val capped = capMessagesForMemory(sessionMessages)
                ChatRoomMessageCache(
                    messages = capped,
                    hasMoreOlder = sessionMessages.size >= CHAT_INITIAL_PAGE_SIZE,
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
                filterMessagesForRoom(vmState.value.messages, roomId),
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
        val roomKnown = vmState.value.rooms.any { it.id == roomId }
        vmState.value = vmState.value.copy(
            selectedRoomId = roomId,
            messages = cachedMessages,
            isLoading = !hasCachedMessages && !roomKnown,
            hasMoreOlder = cached?.hasMoreOlder ?: true,
            isLoadingOlder = false,
            error = null,
            replyToMessage = null,
            editingMessage = null,
            scrollToMessageId = null,
            highlightMessageId = null,
            transientNotice = null,
            rooms = clearUnreadForRoomIfViewing(
                vmState.value.rooms,
                roomId,
                treatAsViewing = isChatTabActive ||
                    (overlayChatPanelVisible && CombatOverlayService.isOverlayChatTabActive()),
            ),
        )
        refreshPinBarForSelectedRoom(resetBarIndex = true)
        if (hasCachedMessages) {
            knownMessageIds.addAll(cachedMessages.mapNotNull { it._id })
            rebuildMessageIdIndex(cachedMessages, messageIdIndex)
            publishMessagesDerivedImmediate(cachedMessages)
        } else {
            _listDerived.value = ChatMessagesListDerived.Empty
        }
        vmRepository.ensureRoomJoined(roomId)
        _otherReadUptoMessageId.value = otherReadUptoByRoom[roomId]
        roomStoreBindings.bindSelectedRoom(roomId)
        bindOutboxObservers(roomId)
        openRoomJob = vmScope.launch {
            chatRoomPreferencesInternal.setSelectedRoomId(roomId)
            if (previousRoomId != null && !previousNewestId.isNullOrBlank()) {
                launch(Dispatchers.IO) {
                    markRoomReadUpTo(previousRoomId, previousNewestId)
                }
            }
            if (!isActiveSelectedRoom(roomId)) return@launch
            openRoom(
                roomId = roomId,
                rooms = vmState.value.rooms,
                hadCachedMessages = cached != null || roomKnown,
                messagesAlreadyInState = hasCachedMessages,
            )
        }
    }

internal fun ChatViewModel.ensureSelectedRoomForOverlayOutgoingImpl(roomId: String) {
        val rid = roomId.trim()
        if (rid.isEmpty() || vmState.value.selectedRoomId == rid) return
        vmState.update { it.copy(selectedRoomId = rid, error = null) }
        roomStoreBindings.bindSelectedRoom(rid)
        bindOutboxObservers(rid)
        _otherReadUptoMessageId.value = otherReadUptoByRoom[rid]
    }

internal fun ChatViewModel.clearUnreadForRoomImpl(
        rooms: List<ChatRoomDto>,
        roomId: String,
    ): List<ChatRoomDto> = run {
        // The UI cleared unread, so optimistic unread floor must be cleared too
        // otherwise badges can keep accumulating after leaving and returning.
        clearOptimisticUnreadFloor(roomId)
        rooms.map { if (it.id == roomId) it.copy(unreadCount = 0) else it }
    }

    /** Avoid zeroing server unread in UI when the room is not actively viewed (background / other tab). */
internal fun ChatViewModel.clearUnreadForRoomIfViewingImpl(
        rooms: List<ChatRoomDto>,
        roomId: String,
        treatAsViewing: Boolean = false,
    ): List<ChatRoomDto> {
        val viewing = treatAsViewing ||
            (vmState.value.selectedRoomId == roomId && isRoomActivelyViewed(roomId))
        return if (viewing) clearUnreadForRoom(rooms, roomId) else rooms
    }

internal fun ChatViewModel.hydrateReadCursorsFromPreferencesImpl() {
        chatRoomPreferencesInternal.loadAllLastReadMessageIds().forEach { (roomId, messageId) ->
            mergeReadCursor(roomId, messageId)
        }
    }

internal fun ChatViewModel.hydrateReadCursorsFromRoomsImpl(rooms: List<ChatRoomDto>) {
        rooms.forEach { room ->
            val serverLast = room.lastReadMessageId?.trim().orEmpty()
            if (serverLast.isNotBlank()) {
                mergeReadCursor(room.id, serverLast)
            }
        }
    }

internal fun ChatViewModel.mergeReadCursorImpl(roomId: String, messageId: String) {
        if (roomId.isBlank() || messageId.isBlank()) return
        val current = lastMarkedReadByRoom[roomId]
        if (current == null || isObjectIdNewer(messageId, current)) {
            lastMarkedReadByRoom[roomId] = messageId
            chatRoomPreferencesInternal.setLastReadMessageId(roomId, messageId)
        }
    }

internal fun ChatViewModel.applyRoomsFromServerImpl(serverRooms: List<ChatRoomDto>): List<ChatRoomDto> {
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

internal fun ChatViewModel.mergeRoomsUnreadFromServerImpl(serverRooms: List<ChatRoomDto>): List<ChatRoomDto> {
        val previousById = vmState.value.rooms.associateBy { it.id }
        val pinInFlight = vmState.value.pinInFlight
        val selectedRoomId = vmState.value.selectedRoomId?.trim().orEmpty()
        return serverRooms.map { room ->
            val previous = previousById[room.id]
            val serverPinId = room.pinnedMessageId?.trim().orEmpty()
            val prevPinId = previous?.pinnedMessageId?.trim().orEmpty()
            if (serverPinId == prevPinId) {
                pinStateAuthoritativeRoomIds.remove(room.id)
            }
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
                previous = previous,
                pinOperationInFlight = (pinInFlight && room.id == selectedRoomId) ||
                    room.id in pinStateAuthoritativeRoomIds,
            )
        }
    }

internal fun ChatViewModel.clearOptimisticUnreadFloorImpl(roomId: String) {
        if (roomId.isBlank()) return
        optimisticUnreadFloorByRoom.remove(roomId)
    }

internal fun ChatViewModel.syncTabUnreadBadgeImpl(rooms: List<ChatRoomDto> = vmState.value.rooms) {
        val badge = ChatUnreadCounts.tabBadgeTotal(rooms)
        if (vmState.value.tabUnreadBadge != badge) {
            vmState.update { it.copy(tabUnreadBadge = badge) }
        }
    }

internal fun ChatViewModel.deviceLastReadMessageIdImpl(roomId: String): String? {
        val fromMemory = lastMarkedReadByRoom[roomId]?.trim().orEmpty()
        val fromPrefs = chatRoomPreferencesInternal.getLastReadMessageId(roomId)?.trim().orEmpty()
        return listOf(fromMemory, fromPrefs)
            .filter { it.isNotBlank() }
            .reduceOrNull { acc, next ->
                if (isObjectIdNewer(next, acc)) next else acc
            }
    }

internal fun ChatViewModel.deviceLastReadMessageIdImpl(room: ChatRoomDto): String? =
        deviceLastReadMessageId(room.id)

internal fun ChatViewModel.resolvedLastReadMessageIdImpl(room: ChatRoomDto): String? {
        val fromMemory = lastMarkedReadByRoom[room.id]?.trim().orEmpty()
        val fromPrefs = chatRoomPreferencesInternal.getLastReadMessageId(room.id)?.trim().orEmpty()
        val server = room.lastReadMessageId?.trim().orEmpty()
        return listOf(fromMemory, fromPrefs, server)
            .filter { it.isNotBlank() }
            .reduceOrNull { acc, next ->
                if (isObjectIdNewer(next, acc)) next else acc
            }
    }

internal fun ChatViewModel.effectiveUnreadForRoomImpl(room: ChatRoomDto): Int =
        effectiveUnreadCount(
            serverUnread = room.unreadCount,
            lastReadMessageId = room.lastReadMessageId,
            localLastReadMessageId = deviceLastReadMessageId(room),
        )

internal fun ChatViewModel.shouldTrackUnreadForMessageImpl(roomId: String, messageId: String): Boolean {
        val room = vmState.value.rooms.find { it.id == roomId }
        val lastRead = room?.let { resolvedLastReadMessageId(it) }
            ?: chatRoomPreferencesInternal.getLastReadMessageId(roomId)
        return ChatUnreadUseCase.shouldTrackUnreadForMessage(messageId, lastRead)
    }

internal fun ChatViewModel.bumpRoomUnreadLocallyImpl(roomId: String, messageId: String) {
        if (!ChatSocketIngress.claimForUnreadBump(roomId, messageId)) return
        val room = vmState.value.rooms.find { it.id == roomId }
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
        vmState.update { st ->
            st.copy(
                rooms = st.rooms.map { r ->
                    if (r.id != roomId) r else r.copy(unreadCount = displayed)
                },
            )
        }
        ChatSessionCache.update(vmState.value.rooms)
        syncTabUnreadBadge()
        if (room != null && ChatRoomKindResolver.isAllianceHubRoom(room)) {
            syncOverlayAllianceHubBadge(vmState.value.rooms)
        }
    }

internal fun ChatViewModel.scheduleUnreadSyncFromServerImpl() {
        unreadSyncJob?.cancel()
        unreadSyncJob = vmScope.launch {
            delay(CHAT_UNREAD_SYNC_DEBOUNCE_MS)
            syncRoomsFromServer(reconfirmVisibleRoom = false)
        }
    }

internal fun ChatViewModel.shouldSkipBackgroundMessageRefreshForRoomImpl(roomId: String): Boolean = false

    /** Pull peer rows from [roomMessageCache] into visible UI after tab/foreground resume. */
internal fun ChatViewModel.rehydrateSelectedRoomMessagesFromCacheImpl(): Boolean =
        syncBundle.rehydrateSync.rehydrateSelectedRoomMessagesFromCache()

    /** Merge socket stash in [roomMessageCache] into the visible list for [roomId]. */
internal fun ChatViewModel.rehydrateRoomMessagesFromCacheImpl(roomId: String): Boolean =
        syncBundle.rehydrateSync.rehydrateRoomMessagesFromCache(roomId)

    /** Keep RAM cache aligned with visible feed when overlay/tab closes. */
internal fun ChatViewModel.snapshotSelectedRoomToMessageCacheImpl() {
        syncBundle.rehydrateSync.snapshotSelectedRoomToMessageCache()
    }

internal suspend fun ChatViewModel.reconfirmReadForVisibleRoomImpl() {
        val roomId = vmState.value.selectedRoomId ?: return
        val newestId = vmState.value.messages.firstOrNull()?._id ?: return
        if (!isValidMarkReadMessageId(newestId)) return
        val room = vmState.value.rooms.find { it.id == roomId }
        val cursor = room?.let { resolvedLastReadMessageId(it) } ?: deviceLastReadMessageId(roomId)
        if (!cursor.isNullOrBlank() && !isObjectIdNewer(newestId, cursor)) return
        markRoomReadUpTo(roomId, newestId)
    }

    /**
     * Server unread can lag; re-push read cursor when API still reports unread
     * but local cursor proves the room was read (including legacy prefs or missing server state).
     * Skips optimistic socket bumps (displayed > raw API count).
     */
internal suspend fun ChatViewModel.reconcileStaleServerUnreadImpl(
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

internal suspend fun ChatViewModel.markRoomReadUpToImpl(
        roomId: String,
        messageId: String,
        forceSync: Boolean = false,
    ) {
        if (roomId.isBlank() || messageId.isBlank()) return
        if (!isValidMarkReadMessageId(messageId)) return
        markReadCoalescer.schedule(
            roomId = roomId,
            messageId = messageId,
            forceSync = forceSync,
            getCurrentCursor = { deviceLastReadMessageId(roomId) },
            onOptimisticAdvance = { rid, mid -> applyOptimisticMarkReadLocal(rid, mid) },
            onNetworkMarkRead = { rid, mid -> performNetworkMarkRead(rid, mid) },
        )
    }

internal fun ChatViewModel.applyOptimisticMarkReadLocal(roomId: String, messageId: String) {
    clearOptimisticUnreadFloor(roomId)
    mergeReadCursor(roomId, messageId)
    ChatSessionCache.patchRoomRead(roomId, messageId)
    vmState.update { st ->
        st.copy(rooms = clearUnreadForRoom(st.rooms, roomId))
    }
    syncTabUnreadBadge()
    ChatSessionCache.update(vmState.value.rooms)
    if (ChatRoomKindResolver.allianceHubRoom(vmState.value.rooms)?.id == roomId) {
        syncOverlayAllianceHubBadge(vmState.value.rooms)
    }
}

internal suspend fun ChatViewModel.performNetworkMarkRead(roomId: String, messageId: String) {
    chatSyncEngine.markRoomRead(currentUserId, roomId, messageId)
        .onFailure {
            scheduleUnreadSyncFromServer()
        }
}

internal fun ChatViewModel.onRoomUnreadFromServerImpl(event: ChatRoomUnreadEvent) {
        val roomId = event.roomId.trim()
        if (roomId.isBlank()) return
        if (roomId == vmState.value.selectedRoomId && isRoomActivelyViewed(roomId)) {
            clearUnreadWhileActivelyViewing(roomId)
            vmState.value.messages.firstOrNull()?._id?.let { newestId ->
                if (isValidMarkReadMessageId(newestId)) {
                    vmScope.launch { markRoomReadUpTo(roomId, newestId) }
                }
            }
            return
        }
        if (vmState.value.rooms.none { it.id == roomId }) {
            val hubId = chatRoomPreferencesInternal.getHubRoomId()?.trim().orEmpty()
            if (hubId.isNotEmpty() && roomId == hubId) {
                ChatSessionCache.patchRoomUnread(
                    roomId,
                    event.unreadCount.coerceAtLeast(0),
                    event.lastReadMessageId,
                )
                syncOverlayAllianceHubBadge()
            }
            scheduleUnreadSyncFromServer()
            return
        }
        val serverLast = event.lastReadMessageId?.trim().orEmpty()
        val serverUnread = event.unreadCount.coerceAtLeast(0)
        val roomDto = vmState.value.rooms.find { it.id == roomId }
        val localLast = roomDto?.let { deviceLastReadMessageId(it) }
            ?: chatRoomPreferencesInternal.getLastReadMessageId(roomId)
        if (!localLast.isNullOrBlank() && serverLast.isNotBlank() &&
            isObjectIdNewer(localLast, serverLast)
        ) {
            val floor = optimisticUnreadFloorByRoom[roomId] ?: 0
            val displayed = roomDto?.unreadCount ?: 0
            if (floor > 0 || displayed > serverUnread) {
                return
            }
        }
        if (serverUnread > 0 && !localLast.isNullOrBlank()) {
            val suppressed = effectiveUnreadCount(
                serverUnread = serverUnread,
                lastReadMessageId = serverLast.takeIf { it.isNotEmpty() },
                localLastReadMessageId = localLast,
            ) == 0
            if (suppressed) {
                clearOptimisticUnreadFloor(roomId)
                vmState.update { st ->
                    val rooms = st.rooms.map { room ->
                        if (room.id != roomId) room
                        else room.copy(
                            unreadCount = 0,
                            lastReadMessageId = localLast,
                        )
                    }
                    st.copy(rooms = rooms)
                }
                ChatSessionCache.update(vmState.value.rooms)
                syncTabUnreadBadge()
                syncOverlayAllianceHubBadge()
                vmScope.launch { markRoomReadUpTo(roomId, localLast, forceSync = true) }
                return
            }
        }
        val selectedId = vmState.value.selectedRoomId
        if (serverLast.isNotBlank() &&
            (serverUnread > 0 || (selectedId == roomId && isChatTabActive))
        ) {
            mergeReadCursor(roomId, serverLast)
        }
        vmState.update { st ->
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
        ChatSessionCache.update(vmState.value.rooms)
        syncTabUnreadBadge()
        syncOverlayAllianceHubBadge()
        if (serverUnread > 0) {
            val selectedId = vmState.value.selectedRoomId
            val activelyViewing = selectedId == roomId && isRoomActivelyViewed(roomId)
            if (!activelyViewing) {
                vmRepository.ensureRoomJoined(roomId)
                refreshMessagesInBackground(roomId, force = true)
            }
        }
    }

