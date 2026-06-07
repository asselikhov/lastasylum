package com.lastasylum.alliance.ui.chat

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.lastasylum.alliance.data.chat.ChatAllianceIds
import com.lastasylum.alliance.data.chat.ChatMessage
import com.lastasylum.alliance.data.chat.ChatRaidRoomSync
import com.lastasylum.alliance.data.chat.ChatSessionCache
import com.lastasylum.alliance.data.chat.stickers.StickerPacks
import com.lastasylum.alliance.data.chat.outbox.OutboxEnqueueResult
import com.lastasylum.alliance.data.chat.outbox.OutboxSendSource
import com.lastasylum.alliance.data.chat.sync.ChatRoomMessageCache
import com.lastasylum.alliance.data.chat.sync.IncomingBatchWork
import com.lastasylum.alliance.overlay.CombatOverlayService
import com.lastasylum.alliance.ui.util.toUserMessageRu
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.InputStream
import java.util.UUID


internal fun ChatViewModel.sendMessageImpl(text: String, replyOverride: String? = null) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return
        val roomId = vmState.value.selectedRoomId ?: return
        if (isRaidChatRoom(roomId) && StickerPacks.stemForMessage(trimmed) != null) return
        val replyToMessageId = replyOverride ?: vmState.value.replyToMessage?._id
        if (globalSendBlocked(roomId, trimmed, replyToMessageId)) return
        launchTextOutgoingSend(roomId, trimmed, replyToMessageId)
    }

internal fun ChatViewModel.launchTextOutgoingSendImpl(
        roomId: String,
        text: String,
        replyToMessageId: String?,
        source: OutboxSendSource = OutboxSendSource.ChatUi,
    ) {
        val trimmed = text.trim()
        vmState.value = vmState.value.copy(isSending = false, error = null, sendFailure = null)
        deriveJob?.cancel()
        deriveDebounceJob?.cancel()

        val prepared = chatOutbox.prepareEnqueue(
            userId = currentUserId,
            roomId = roomId,
            text = trimmed,
            replyToMessageId = replyToMessageId,
            attachments = null,
            excavationAlert = false,
            source = source,
            currentUserId = currentUserId,
            currentUserRole = currentUserRole,
            senderUsername = "",
        )
        finishFastOutgoingSend(
            prepared = prepared,
            trimmed = trimmed,
            roomId = roomId,
            replyToMessageId = replyToMessageId,
        )
    }

private fun ChatViewModel.trackActiveOutgoingClientMessageId(clientMessageId: String?) {
    clientMessageId?.trim()?.takeIf { it.isNotEmpty() }?.let { activeOutgoingClientMessageIds.add(it) }
}

private fun ChatViewModel.untrackActiveOutgoingClientMessageId(clientMessageId: String?) {
    clientMessageId?.trim()?.takeIf { it.isNotEmpty() }?.let { activeOutgoingClientMessageIds.remove(it) }
}

internal fun ChatViewModel.confirmOutgoingByClientMessageIdImpl(
    clientMessageId: String,
    sent: ChatMessage,
    pendingIdHint: String? = null,
    httpAckSpanId: Long? = null,
) {
    val cid = clientMessageId.trim()
    if (cid.isEmpty()) return
    val normalizedSent = sent.withOutgoingClientMessageId(cid)
    vmScope.launch {
        incomingApplyMutex.withLock {
            val alreadyConfirmed = cid in confirmedOutgoingClientMessageIds
            val pendingId = resolvePendingOutgoingIdForConfirm(
                clientMessageId = cid,
                pendingIdHint = pendingIdHint,
                consumePendingMap = !alreadyConfirmed,
            )
            withContext(Dispatchers.Main.immediate) {
                if (alreadyConfirmed) {
                    reconcileAlreadyConfirmedOutgoing(cid, normalizedSent, pendingId)
                } else {
                    applyOutgoingConfirmation(cid, normalizedSent, pendingId)
                    if (!hasOwnOutgoingRowPairByClientMessageId(
                            vmState.value.messages,
                            cid,
                            currentUserId,
                        )
                    ) {
                        confirmedOutgoingClientMessageIds.add(cid)
                    }
                }
            }
            withContext(Dispatchers.IO) {
                chatOutbox.confirmSend(
                    userId = currentUserId,
                    clientMessageId = cid,
                    serverMessage = normalizedSent,
                    httpAckSpanId = httpAckSpanId,
                )
            }
        }
    }
}

private fun ChatViewModel.resolvePendingOutgoingIdForConfirm(
    clientMessageId: String,
    pendingIdHint: String?,
    consumePendingMap: Boolean,
): String? {
    pendingIdHint?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    if (consumePendingMap) {
        pendingOutgoingByClientMessageId.remove(clientMessageId)?.let { return it }
    } else {
        pendingOutgoingByClientMessageId[clientMessageId]?.let { return it }
    }
    outboxRoomSnapshot.pendingToClientId.entries
        .firstOrNull { it.value == clientMessageId }
        ?.key
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let { return it }
    return findOptimisticOutgoingPendingId(
        messages = vmState.value.messages,
        clientMessageId = clientMessageId,
        currentUserId = currentUserId,
    )
}

private fun ChatViewModel.applyOutgoingConfirmation(
    clientMessageId: String,
    sent: ChatMessage,
    pendingId: String?,
) {
    pendingOutgoingByClientMessageId.remove(clientMessageId)
    val serverId = sent._id?.trim().orEmpty()
    if (!pendingId.isNullOrEmpty()) {
        confirmPendingOutgoingMessageImpl(pendingId, sent)
        return
    }
    if (serverId.isNotEmpty() && vmState.value.messages.any { it._id?.trim() == serverId }) {
        repairOwnOutgoingRowPairIfNeeded(clientMessageId, sent)
        return
    }
    confirmPendingOutgoingMessageImpl(pendingId = null, sent = sent)
}

private fun ChatViewModel.repairOwnOutgoingRowPairIfNeeded(
    clientMessageId: String,
    sent: ChatMessage,
) {
    findOptimisticOutgoingPendingForConfirm(
        messages = vmState.value.messages,
        clientMessageId = clientMessageId,
        confirmed = sent,
        currentUserId = currentUserId,
    )?.let { resolvedPending ->
        confirmPendingOutgoingMessageImpl(resolvedPending, sent)
        return
    }
    if (!hasOwnOutgoingRowPairByClientMessageId(
            vmState.value.messages,
            clientMessageId,
            currentUserId,
        )
    ) {
        return
    }
    applySanitizedOutgoingListRepair()
}

private fun ChatViewModel.applySanitizedOutgoingListRepair() {
    val sanitized = sanitizeMessagesForUiList(
        messages = vmState.value.messages,
        currentUserId = currentUserId,
        activeOutgoingPendingId = outboxRoomSnapshot.newestPendingId,
    )
    if (sanitized === vmState.value.messages) return
    synchronized(chatMutationLock) {
        rebuildMessageIdIndex(sanitized, messageIdIndex)
        vmState.value = vmState.value.copy(messages = sanitized)
    }
    publishMessagesDerivedImmediate(sanitized)
    vmState.value.selectedRoomId?.trim()?.takeIf { it.isNotEmpty() }?.let { rid ->
        roomMessageCache[rid] = ChatRoomMessageCache(
            messages = sanitized,
            hasMoreOlder = vmState.value.hasMoreOlder,
        )
        ChatSessionCache.updateMessages(rid, sanitized)
    }
}

private fun ChatViewModel.reconcileAlreadyConfirmedOutgoing(
    clientMessageId: String,
    sent: ChatMessage,
    pendingId: String?,
) {
    val serverId = sent._id?.trim().orEmpty()
    val snapshot = vmState.value.messages
    findOptimisticOutgoingPendingForConfirm(
        messages = snapshot,
        clientMessageId = clientMessageId,
        confirmed = sent,
        currentUserId = currentUserId,
    )?.let { resolvedPending ->
        confirmPendingOutgoingMessageImpl(resolvedPending, sent)
        return
    }
    if (!hasOwnOutgoingRowPairByClientMessageId(snapshot, clientMessageId, currentUserId)) {
        val serverVisible = serverId.isNotEmpty() &&
            snapshot.any { it._id?.trim() == serverId }
        if (!serverVisible) {
            confirmPendingOutgoingMessageImpl(pendingId = null, sent = sent)
        }
        return
    }
    val sanitized = sanitizeMessagesForUiList(
        messages = snapshot,
        currentUserId = currentUserId,
        activeOutgoingPendingId = outboxRoomSnapshot.newestPendingId,
    )
    if (sanitized === snapshot) return
    synchronized(chatMutationLock) {
        rebuildMessageIdIndex(sanitized, messageIdIndex)
        vmState.value = vmState.value.copy(messages = sanitized)
    }
    publishMessagesDerivedImmediate(sanitized)
    vmState.value.selectedRoomId?.trim()?.takeIf { it.isNotEmpty() }?.let { rid ->
        roomMessageCache[rid] = ChatRoomMessageCache(
            messages = sanitized,
            hasMoreOlder = vmState.value.hasMoreOlder,
        )
        ChatSessionCache.updateMessages(rid, sanitized)
    }
}

private fun ChatViewModel.finishFastOutgoingSend(
    prepared: OutboxEnqueueResult,
    trimmed: String,
    roomId: String,
    replyToMessageId: String?,
    afterOptimistic: () -> Unit = {},
) {
    insertOptimisticOutgoingSynchronously(prepared.optimisticMessage, clearComposer = true)
    trackActiveOutgoingClientMessageId(prepared.clientMessageId)
    pendingOutgoingByClientMessageId[prepared.clientMessageId] = prepared.pendingMessageId
    afterOptimistic()
    vmScope.launch {
        val cid = prepared.clientMessageId
        val persistError = runCatching {
            coroutineScope {
                launch(Dispatchers.IO) {
                    runCatching {
                        repository.emitChatOutgoingViaSocket(
                            text = trimmed,
                            roomId = roomId,
                            replyToMessageId = replyToMessageId,
                            clientMessageId = cid,
                            excavationAlert = prepared.excavationAlert,
                        )
                    }
                }
                withContext(Dispatchers.IO) {
                    chatOutbox.persistEnqueue(prepared)
                }
            }
        }.exceptionOrNull()
        if (persistError != null) {
            pendingOutgoingByClientMessageId.remove(cid)
            confirmedOutgoingClientMessageIds.remove(cid)
            removePendingOutgoingMessage(prepared.pendingMessageId)
            vmState.value = vmState.value.copy(
                isSending = false,
                sendFailure = ChatSendFailure(
                    messageText = trimmed,
                    replyToMessageId = replyToMessageId,
                    errorMessage = persistError.toUserMessageRu(res),
                ),
            )
            return@launch
        }
        chatSyncEngine.sendEnqueuedOutbox(cid, skipSocket = true)
            .onSuccess { sent ->
                if (!confirmedOutgoingClientMessageIds.contains(cid)) {
                    confirmOutgoingByClientMessageId(
                        clientMessageId = cid,
                        sent = sent,
                        pendingIdHint = prepared.pendingMessageId,
                    )
                }
            }
            .onFailure { throwable ->
                if (confirmedOutgoingClientMessageIds.contains(cid)) return@onFailure
                pendingOutgoingByClientMessageId.remove(cid)
                removePendingOutgoingMessage(prepared.pendingMessageId)
                vmState.value = vmState.value.copy(
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

internal fun ChatViewModel.sendDraftMessageImpl() {
        val editing = vmState.value.editingMessage
        if (editing != null) {
            val id = editing._id?.trim().orEmpty()
            val trimmed = _draftMessage.value.trim()
            if (id.isEmpty() || trimmed.isBlank()) return
            editMessageImpl(id, trimmed)
            cancelEditMessageImpl()
            return
        }
        val text = _draftMessage.value.trim()
        if (text.isBlank() && _pickedImageUris.value.isEmpty()) return
        val roomId = vmState.value.selectedRoomId ?: return
        val replyToMessageId = vmState.value.replyToMessage?._id
        val uris = _pickedImageUris.value
        if (globalSendBlocked(roomId, text, replyToMessageId)) return

        if (uris.isEmpty() && text.isNotBlank()) {
            launchTextOutgoingSendImpl(roomId, text, replyToMessageId)
            return
        }
        vmScope.launch {
            vmState.value = vmState.value.copy(isSending = true, error = null, sendFailure = null)
            val uploadedIds = ArrayList<String>(uris.size)
            try {
                for (uri in uris) {
                    val uploadedId = uploadOneImageImpl(roomId, uri).getOrElse { t ->
                        vmState.value = vmState.value.copy(
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
                val attachments = uploadedIds.takeIf { it.isNotEmpty() }
                launchAttachmentOutgoingSendImpl(
                    roomId = roomId,
                    text = text.trim(),
                    replyToMessageId = replyToMessageId,
                    attachments = attachments,
                )
            } catch (t: Throwable) {
                vmState.value = vmState.value.copy(
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

internal fun ChatViewModel.launchAttachmentOutgoingSendImpl(
        roomId: String,
        text: String,
        replyToMessageId: String?,
        attachments: List<String>?,
        source: OutboxSendSource = OutboxSendSource.ChatUi,
    ) {
        val trimmed = text.trim()
        deriveJob?.cancel()
        deriveDebounceJob?.cancel()
        vmScope.launch {
            val prepared = chatOutbox.prepareEnqueue(
                userId = currentUserId,
                roomId = roomId,
                text = trimmed,
                replyToMessageId = replyToMessageId,
                attachments = attachments,
                excavationAlert = false,
                source = source,
                currentUserId = currentUserId,
                currentUserRole = currentUserRole,
                senderUsername = "",
            )
            finishFastOutgoingSend(
                prepared = prepared,
                trimmed = trimmed,
                roomId = roomId,
                replyToMessageId = replyToMessageId,
                afterOptimistic = { clearPickedImagesImpl() },
            )
        }
    }

internal fun ChatViewModel.prepareOverlayRaidQuickCommandOutgoingImpl(
        pendingId: String,
        roomId: String,
        text: String,
        gameEventAlert: String? = null,
    ) {
        val rid = roomId.trim()
        val body = text.trim()
        val pending = pendingId.trim()
        if (rid.isEmpty() || body.isEmpty() || pending.isEmpty()) return
        if (messageIdIndex.containsKey(pending) || knownMessageIds.contains(pending)) return
        ensureSelectedRoomForOverlayOutgoing(rid)
        val prepared = chatOutbox.prepareEnqueue(
            userId = currentUserId,
            roomId = rid,
            text = body,
            replyToMessageId = null,
            attachments = null,
            excavationAlert = false,
            source = OutboxSendSource.OverlayRaid,
            currentUserId = currentUserId,
            currentUserRole = currentUserRole,
            senderUsername = "",
            pendingMessageId = pending,
            gameEventAlert = gameEventAlert,
        )
        overlayQuickCommandPrepared[pending] = prepared
        insertOptimisticOutgoingSynchronously(prepared.optimisticMessage, clearComposer = false)
        trackActiveOutgoingClientMessageId(prepared.clientMessageId)
    }

internal suspend fun ChatViewModel.sendOverlayRaidQuickCommandImpl(
        pendingId: String,
        roomId: String,
        text: String,
        gameEventAlert: String? = null,
    ): Result<ChatMessage> {
        val rid = roomId.trim()
        val body = text.trim()
        val pending = pendingId.trim()
        if (rid.isEmpty() || body.isEmpty() || pending.isEmpty()) {
            return Result.failure(IllegalStateException("invalid_overlay_raid_send"))
        }
        withContext(Dispatchers.Main.immediate) {
            ensureSelectedRoomForOverlayOutgoing(rid)
        }
        val eventAlert = gameEventAlert?.trim()?.takeIf { it.isNotEmpty() }
        fun mergeGameEventAlert(row: OutboxEnqueueResult): OutboxEnqueueResult =
            if (eventAlert != null && row.gameEventAlert != eventAlert) {
                row.copy(gameEventAlert = eventAlert)
            } else {
                row
            }
        val prepared = withContext(Dispatchers.Main.immediate) {
            overlayQuickCommandPrepared[pending]?.let(::mergeGameEventAlert)
                ?: if (messageIdIndex.containsKey(pending) || knownMessageIds.contains(pending)) {
                    chatOutbox.prepareEnqueue(
                        userId = currentUserId,
                        roomId = rid,
                        text = body,
                        replyToMessageId = null,
                        attachments = null,
                        excavationAlert = false,
                        source = OutboxSendSource.OverlayRaid,
                        currentUserId = currentUserId,
                        currentUserRole = currentUserRole,
                        senderUsername = "",
                        pendingMessageId = pending,
                        gameEventAlert = eventAlert,
                    ).also { enqueue ->
                        overlayQuickCommandPrepared[pending] = enqueue
                        if (!messageIdIndex.containsKey(pending)) {
                            insertOptimisticOutgoingSynchronously(enqueue.optimisticMessage, clearComposer = false)
                        }
                        trackActiveOutgoingClientMessageId(enqueue.clientMessageId)
                    }
                } else {
                    val enqueue = chatOutbox.prepareEnqueue(
                        userId = currentUserId,
                        roomId = rid,
                        text = body,
                        replyToMessageId = null,
                        attachments = null,
                        excavationAlert = false,
                        source = OutboxSendSource.OverlayRaid,
                        currentUserId = currentUserId,
                        currentUserRole = currentUserRole,
                        senderUsername = "",
                        pendingMessageId = pending,
                        gameEventAlert = eventAlert,
                    )
                    overlayQuickCommandPrepared[pending] = enqueue
                    insertOptimisticOutgoingSynchronously(enqueue.optimisticMessage, clearComposer = false)
                    trackActiveOutgoingClientMessageId(enqueue.clientMessageId)
                    enqueue
                }
        }.let(::mergeGameEventAlert).also { row ->
            overlayQuickCommandPrepared[pending] = row
        }
        pendingOutgoingByClientMessageId[prepared.clientMessageId] = pending
        trackActiveOutgoingClientMessageId(prepared.clientMessageId)
        val persistError = runCatching {
            coroutineScope {
                launch(Dispatchers.IO) {
                    runCatching {
                        repository.prefireOverlayRaidSocket(
                            text = body,
                            roomId = rid,
                            clientMessageId = prepared.clientMessageId,
                            gameEventAlert = eventAlert,
                        )
                    }
                }
                withContext(Dispatchers.IO) {
                    chatOutbox.persistEnqueue(prepared)
                }
            }
        }.exceptionOrNull()
        if (persistError != null) {
            overlayQuickCommandPrepared.remove(pending)
            pendingOutgoingByClientMessageId.remove(prepared.clientMessageId)
            withContext(Dispatchers.Main.immediate) {
                removePendingOutgoingMessage(pending)
            }
            return Result.failure(persistError)
        }
        return chatSyncEngine.sendEnqueuedOutbox(prepared.clientMessageId, skipSocket = true)
            .also { result ->
                result.onSuccess { sent ->
                    overlayQuickCommandPrepared.remove(pending)
                    if (!confirmedOutgoingClientMessageIds.contains(prepared.clientMessageId)) {
                        confirmOutgoingByClientMessageId(
                            clientMessageId = prepared.clientMessageId,
                            sent = sent,
                            pendingIdHint = pending,
                        )
                    }
                }.onFailure {
                    if (confirmedOutgoingClientMessageIds.contains(prepared.clientMessageId)) return@onFailure
                    overlayQuickCommandPrepared.remove(pending)
                    pendingOutgoingByClientMessageId.remove(prepared.clientMessageId)
                    withContext(Dispatchers.Main.immediate) {
                        removePendingOutgoingMessage(pending)
                    }
                }
            }
    }

internal suspend fun ChatViewModel.sendOverlayRaidQuickCommandViaOutboxImpl(
        pendingId: String,
    ): Result<ChatMessage> {
        val pending = pendingId.trim()
        if (pending.isEmpty()) {
            return Result.failure(IllegalStateException("missing_pending"))
        }
        val clientMessageId = outboxRoomSnapshot.pendingToClientId[pending]
            ?: withContext(Dispatchers.IO) {
                chatOutbox.observeActive(currentUserId)
                    .first()
                    .firstOrNull { it.pendingMessageId == pending }
                    ?.clientMessageId
            }
            ?: return Result.failure(IllegalStateException("outbox_not_enqueued"))
        return chatSyncEngine.sendEnqueuedOutbox(clientMessageId)
    }

internal suspend fun ChatViewModel.uploadOneImageImpl(roomId: String, uri: Uri): Result<String> =
        withContext(Dispatchers.IO) {
            val ctx = vmApplication
            val cr = ctx.contentResolver
            val tmp = File.createTempFile("chat_upload_${UUID.randomUUID()}", ".part", ctx.cacheDir)
            try {
                val input = openUriInputStreamImpl(cr, uri)
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
                vmRepository.uploadImageFile(roomId, tmp, mime).map { it.fileId }
            } finally {
                runCatching { tmp.delete() }
            }
        }

internal fun ChatViewModel.openUriInputStreamImpl(cr: ContentResolver, uri: Uri): InputStream? {
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

internal fun ChatViewModel.retrySendFailureImpl() {
        val failure = vmState.value.sendFailure ?: return
        sendMessageImpl(failure.messageText, replyOverride = failure.replyToMessageId)
    }

internal fun ChatViewModel.dismissSendFailureImpl() {
        if (vmState.value.sendFailure == null) return
        vmState.value = vmState.value.copy(sendFailure = null)
    }

internal fun ChatViewModel.setDraftMessageImpl(value: String) {
        if (_draftMessage.value == value) return
        _draftMessage.value = value
        scheduleTypingEmitImpl()
    }

    /**
     * @param append false — заменить текущий выбор (галерея / системный пикер);
     * true — добавить к уже прикреплённым (повторный «+» в композере).
     */
internal fun ChatViewModel.onImagesPickedImpl(uris: List<Uri>, append: Boolean = false) {
        if (uris.isEmpty()) return
        val roomId = vmState.value.selectedRoomId ?: return
        if (isRaidChatRoom(roomId)) return
        val distinct = uris.distinctBy { it.toString() }
        val next = if (append) {
            (_pickedImageUris.value + distinct).distinctBy { it.toString() }
        } else {
            distinct
        }
        _pickedImageUris.value = next.take(12)
    }

internal fun ChatViewModel.removePickedImageImpl(uri: Uri) {
        val next = _pickedImageUris.value.filterNot { it == uri }
        if (next.size == _pickedImageUris.value.size) return
        _pickedImageUris.value = next
    }

internal fun ChatViewModel.clearPickedImagesImpl() {
        if (_pickedImageUris.value.isEmpty()) return
        _pickedImageUris.value = emptyList()
    }

internal fun ChatViewModel.scheduleTypingEmitImpl() {
        typingEmitJob?.cancel()
        val roomId = vmState.value.selectedRoomId ?: return
        val room = vmState.value.rooms.find { it.id == roomId }
        if (room?.allianceId == ChatAllianceIds.GLOBAL &&
            !vmState.value.hasTeamProfileForGlobalChat
        ) {
            return
        }
        if (_draftMessage.value.isBlank()) return
        typingEmitJob = vmScope.launch {
            try {
                delay(280)
                vmRepository.emitTypingPing(roomId)
            } catch (_: CancellationException) {
                // cancelled by newer keystroke or room switch
            }
        }
    }

internal fun ChatViewModel.beginReplyToMessageImpl(messageId: String) {
        val target = vmState.value.messages.find { it._id == messageId } ?: return
        if (target.deletedAt != null) return
        vmState.value = vmState.value.copy(
            replyToMessage = target,
            editingMessage = null,
            activeActionMessageId = null,
            selectedMessageIds = emptySet(),
            confirmBulkDelete = false,
        )
    }

internal fun ChatViewModel.clearReplyToMessageImpl() {
        if (vmState.value.replyToMessage == null) return
        vmState.value = vmState.value.copy(replyToMessage = null)
    }

internal fun ChatViewModel.beginEditMessageImpl(messageId: String) {
        val target = vmState.value.messages.find { it._id == messageId } ?: return
        if (target.deletedAt != null || target.text.isBlank()) return
        _draftMessage.value = target.text
        vmState.value = vmState.value.copy(
            editingMessage = target,
            replyToMessage = null,
            activeActionMessageId = null,
            selectedMessageIds = emptySet(),
            confirmBulkDelete = false,
        )
    }

internal fun ChatViewModel.cancelEditMessageImpl() {
        if (vmState.value.editingMessage == null) return
        _draftMessage.value = ""
        vmState.value = vmState.value.copy(editingMessage = null)
    }

internal fun ChatViewModel.openMessageActionsImpl(messageId: String) {
        vmState.value = vmState.value.copy(
            activeActionMessageId = messageId,
            selectedMessageIds = emptySet(),
            confirmBulkDelete = false,
        )
    }

internal fun ChatViewModel.dismissMessageActionsImpl() {
        if (vmState.value.activeActionMessageId == null) return
        vmState.value = vmState.value.copy(activeActionMessageId = null)
    }

internal fun ChatViewModel.requestDeleteMessageImpl(messageId: String) {
        vmState.value = vmState.value.copy(
            activeActionMessageId = null,
            confirmDeleteMessageId = messageId,
            selectedMessageIds = emptySet(),
            confirmBulkDelete = false,
        )
    }

internal fun ChatViewModel.toggleReactionImpl(messageId: String, emoji: String) {
        if (messageId.isBlank() || emoji.isBlank()) return
        val previousMessages = vmState.value.messages
        val patchIndex = previousMessages.indexOfFirst { it._id == messageId }
        val optimistic = applyOptimisticReactionToggle(
            messages = previousMessages,
            messageId = messageId,
            emoji = emoji,
        )
        if (optimistic !== previousMessages) {
            vmState.value = vmState.value.copy(messages = optimistic)
            if (patchIndex >= 0) {
                publishMessagesDerivedAfterPatch(optimistic, patchIndex)
            } else {
                publishMessagesDerived(optimistic)
            }
        }
        vmScope.launch {
            vmRepository.toggleReaction(messageId, emoji)
                .onSuccess { updated ->
                    applyMessageReplaceSynchronously(updated.normalizeEditedAtForDisplay())
                    if (vmState.value.activeActionMessageId == messageId) {
                        vmState.value = vmState.value.copy(activeActionMessageId = null)
                    }
                }
                .onFailure { e ->
                    val rollbackIndex = previousMessages.indexOfFirst { it._id == messageId }
                    vmState.value = vmState.value.copy(
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

internal fun ChatViewModel.publishRaidMessageToOverlayStripImpl(message: ChatMessage) {
        val roomId = message.roomId.trim().ifBlank { vmState.value.selectedRoomId?.trim().orEmpty() }
        if (roomId.isEmpty()) return
        val prefsRaid = chatRoomPreferences.getRaidRoomId()?.trim()
        val isRaid = when {
            !prefsRaid.isNullOrEmpty() && prefsRaid == roomId -> true
            else -> {
                val room = vmState.value.rooms.find { it.id == roomId } ?: return
                ChatRaidRoomSync.isAllianceRaidRoom(room)
            }
        }
        if (!isRaid) return
        runCatching { CombatOverlayService.publishRaidMessageToStripFromApp(message) }
        runCatching { CombatOverlayService.extendInGameOverlayUiHold() }
    }

    /** Pending row must exist before HTTP/socket echo to avoid a brief duplicate at the list head. */
internal fun ChatViewModel.insertOptimisticOutgoingSynchronouslyImpl(
        message: ChatMessage,
        clearComposer: Boolean,
    ) {
        if (clearComposer && overlayChatPanelVisible) {
            runCatching { CombatOverlayService.extendInGameOverlayUiHold() }
        }
        val work = synchronized(chatMutationLock) {
            val snapshot = vmState.value
            val withoutRacingEcho = stripRacingServerEchoForPending(
                messages = snapshot.messages,
                pending = message,
                currentUserId = currentUserId,
            )
            val update = upsertMessage(
                current = withoutRacingEcho,
                incoming = message,
                knownMessageIds = knownMessageIds,
                idIndex = messageIdIndex,
            )
            val capped = capMessagesForMemory(
                sanitizeMessagesForUiList(
                    messages = dedupeMessagesByIdNewestFirst(update.messages),
                    currentUserId = currentUserId,
                    activeOutgoingPendingId = outboxRoomSnapshot.newestPendingId,
                ),
            )
            rebuildMessageIdIndex(capped, messageIdIndex)
            message._id?.let { registerOutgoingLazyColumnKey(it) }
            Triple(snapshot, capped, update.newestMessageKey ?: message._id?.trim().orEmpty())
        }
        val (snapshot, capped, newestKey) = work
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
        vmState.value = syncSelections(nextState)
        publishMessagesDerivedImmediate(capped)
        val rid = vmState.value.selectedRoomId
        if (!rid.isNullOrBlank()) {
            roomMessageCache[rid] = ChatRoomMessageCache(
                messages = capped,
                hasMoreOlder = vmState.value.hasMoreOlder,
            )
            ChatSessionCache.updateMessages(rid, capped)
        }
        val pendingKey = message._id?.trim().orEmpty()
        val isOverlayQuickCommand = pendingKey.isNotEmpty() &&
            overlayQuickCommandPrepared.containsKey(pendingKey)
        if (!isOverlayQuickCommand) {
            publishRaidMessageToOverlayStripImpl(message)
        }
    }

internal fun ChatViewModel.removePendingOutgoingMessageImpl(pendingId: String?) {
        val id = pendingId?.trim().orEmpty()
        if (id.isEmpty()) return
        synchronized(chatMutationLock) {
            overlayQuickCommandPrepared.remove(id)
            vmState.value.messages.firstOrNull { it._id?.trim() == id }?.clientMessageId?.let {
                untrackActiveOutgoingClientMessageId(it)
            }
            dropOutgoingLazyColumnKey(id)
            knownMessageIds.remove(id)
            messageIdIndex.remove(id)
            val filtered = vmState.value.messages.filter { it._id != id }
            vmState.update { st -> st.copy(messages = filtered) }
            publishMessagesDerived(filtered)
            val rid = vmState.value.selectedRoomId ?: return
            roomMessageCache[rid] = ChatRoomMessageCache(
                messages = vmState.value.messages,
                hasMoreOlder = vmState.value.hasMoreOlder,
            )
            ChatSessionCache.updateMessages(rid, vmState.value.messages)
        }
    }

internal fun ChatViewModel.shouldDeferOwnOutgoingSocketEchoImpl(message: ChatMessage): Boolean =
        shouldBlockOwnOutgoingRealtime(message)

    /** Replace optimistic row in-place (no remove+insert, no extra scroll). */
internal fun ChatViewModel.confirmPendingOutgoingMessageImpl(pendingId: String?, sent: ChatMessage) {
        val pending = pendingId?.trim().orEmpty()
        val serverId = sent._id?.trim().orEmpty()
        if (pending.isEmpty()) {
            val cid = sent.clientMessageId?.trim().orEmpty()
            findOptimisticOutgoingPendingForConfirm(
                messages = vmState.value.messages,
                clientMessageId = cid,
                confirmed = sent,
                currentUserId = currentUserId,
            )?.let { resolvedPending ->
                confirmPendingOutgoingMessageImpl(resolvedPending, sent)
                return
            }
            if (serverId.isNotEmpty() && vmState.value.messages.any { it._id?.trim() == serverId }) {
                if (cid.isNotEmpty()) {
                    repairOwnOutgoingRowPairIfNeeded(cid, sent)
                }
                return
            }
            prependConfirmedOutgoingMessage(sent)
            return
        }
        if (serverId.isEmpty()) {
            prependConfirmedOutgoingMessage(sent)
            return
        }
        val roomId = vmState.value.selectedRoomId
        val work = synchronized(chatMutationLock) {
            val snapshot = vmState.value
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
                sanitizeMessagesForUiList(
                    messages = dedupeMessagesByIdNewestFirst(
                        stripRedundantPendingOutgoing(updated, currentUserId).let {
                            dedupeOwnOutgoingByClientMessageId(
                                stripRedundantOwnOutgoingByClientMessageId(it, currentUserId),
                                currentUserId,
                            )
                        },
                    ),
                    currentUserId = currentUserId,
                    activeOutgoingPendingId = outboxRoomSnapshot.newestPendingId,
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
        if (roomId != null && vmState.value.selectedRoomId != roomId) {
            stashIncomingMessageForRoom(sent)
            return
        }
        val safeMessages = sanitizeMessagesForUiList(
            messages = work.cappedMessages,
            currentUserId = currentUserId,
            activeOutgoingPendingId = outboxRoomSnapshot.newestPendingId,
        )
        val derived = buildChatMessagesListDerived(safeMessages)
        deriveJob?.cancel()
        deriveDebounceJob?.cancel()
        synchronized(chatMutationLock) {
            val snapshot = vmState.value
            vmState.value = syncSelections(
                snapshot.copy(
                    messages = safeMessages,
                    newestMessageKey = serverId,
                    isSending = false,
                    error = null,
                    sendFailure = null,
                    scrollToLatestNonce = snapshot.scrollToLatestNonce + 1L,
                ),
            )
            _listDerived.value = derived
            overlayQuickCommandPrepared.remove(pending)
            untrackActiveOutgoingClientMessageId(sent.clientMessageId)
            work.cappedMessages.firstOrNull { it._id?.trim() == serverId }?.clientMessageId?.let {
                untrackActiveOutgoingClientMessageId(it)
            }
        }
        val rid = vmState.value.selectedRoomId
        if (!rid.isNullOrBlank()) {
            acknowledgeOwnOutgoingInActiveRoom(rid, serverId)
            schedulePeerReadCursorPoll(rid, serverId)
            deliveryLatencyTracker.startSpan(
                com.lastasylum.alliance.data.telemetry.LatencySpanType.ChatReadReceipt,
                serverId,
            )
        }
        if (!rid.isNullOrBlank()) {
            roomMessageCache[rid] = ChatRoomMessageCache(
                messages = safeMessages,
                hasMoreOlder = vmState.value.hasMoreOlder,
            )
            ChatSessionCache.updateMessages(rid, safeMessages)
            schedulePersistChatSnapshot()
        }
        if (!CombatOverlayService.isRaidMessageAlreadyOnStrip(sent)) {
            publishRaidMessageToOverlayStripImpl(sent)
        }
    }

private fun ChatViewModel.prependConfirmedOutgoingMessage(sent: ChatMessage) {
    val serverId = sent._id?.trim().orEmpty()
    if (serverId.isEmpty()) return
    if (vmState.value.messages.any { it._id?.trim() == serverId }) return
    val work = synchronized(chatMutationLock) {
        val update = upsertMessage(
            current = vmState.value.messages,
            incoming = sent.copy(editedAt = null),
            knownMessageIds = knownMessageIds,
            idIndex = messageIdIndex,
        )
        val safeMessages = capMessagesForMemory(
            sanitizeMessagesForUiList(
                messages = update.messages,
                currentUserId = currentUserId,
                activeOutgoingPendingId = outboxRoomSnapshot.newestPendingId,
            ),
        )
        rebuildMessageIdIndex(safeMessages, messageIdIndex)
        safeMessages
    }
    vmState.value = syncSelections(
        vmState.value.copy(
            messages = work,
            newestMessageKey = serverId,
            isSending = false,
            error = null,
        ),
    )
    publishMessagesDerivedImmediate(work)
    vmState.value.selectedRoomId?.trim()?.takeIf { it.isNotEmpty() }?.let { rid ->
        roomMessageCache[rid] = ChatRoomMessageCache(
            messages = work,
            hasMoreOlder = vmState.value.hasMoreOlder,
        )
        ChatSessionCache.updateMessages(rid, work)
    }
}

internal fun ChatViewModel.applyIncomingMessageImpl(
        message: ChatMessage,
        clearComposer: Boolean = false,
        skipOutgoingEchoBlock: Boolean = false,
    ) {
        if (!skipOutgoingEchoBlock && shouldBlockOwnOutgoingRealtime(message)) return
        if (clearComposer && overlayChatPanelVisible) {
            runCatching { CombatOverlayService.extendInGameOverlayUiHold() }
        }
        applyIncomingBatchImpl(listOf(message), clearComposer = clearComposer)
    }

internal fun ChatViewModel.applyIncomingBatchImpl(
        batch: List<ChatMessage>,
        clearComposer: Boolean = false,
    ) {
        chatIncomingSync.applyIncomingBatch(batch, clearComposer)
    }
