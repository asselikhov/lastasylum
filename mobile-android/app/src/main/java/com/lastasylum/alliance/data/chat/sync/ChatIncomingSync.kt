package com.lastasylum.alliance.data.chat.sync

import android.util.Log
import com.lastasylum.alliance.BuildConfig
import com.lastasylum.alliance.data.chat.ChatMessage
import com.lastasylum.alliance.data.chat.mergeIncomingChatUpdate
import com.lastasylum.alliance.ui.chat.ChatMessagesListDerived
import com.lastasylum.alliance.ui.chat.ChatState
import com.lastasylum.alliance.ui.chat.RAID_GAP_RECONCILE_THRESHOLD_MS
import com.lastasylum.alliance.ui.chat.buildChatMessagesListDerivedAfterReplaceNewest
import com.lastasylum.alliance.ui.chat.buildChatMessagesListDerived
import com.lastasylum.alliance.ui.chat.chatMessagesListContentEqual
import com.lastasylum.alliance.ui.chat.reconcileDerivedWithMessages
import com.lastasylum.alliance.ui.chat.dedupeOwnOutgoingByClientMessageId
import com.lastasylum.alliance.ui.chat.dedupeMessagesByIdNewestFirst
import com.lastasylum.alliance.ui.chat.dropMatchingPendingOutgoing
import com.lastasylum.alliance.ui.chat.filterMessagesForRoom
import com.lastasylum.alliance.ui.chat.hasMatchingPendingOutgoing
import com.lastasylum.alliance.ui.chat.isOptimisticOutgoingMessageId
import com.lastasylum.alliance.ui.chat.mergeOutgoingConfirmation
import com.lastasylum.alliance.ui.chat.rebuildMessageIdIndex
import com.lastasylum.alliance.ui.chat.replaceMatchingPendingOutgoing
import com.lastasylum.alliance.ui.chat.resolveChatListDerivedAfterMessagesUpdate
import com.lastasylum.alliance.ui.chat.sanitizeMessagesAfterRealtimeApply
import com.lastasylum.alliance.ui.chat.shouldTriggerGapReconcile
import com.lastasylum.alliance.ui.chat.stripRedundantOwnOutgoingByClientMessageId
import com.lastasylum.alliance.ui.chat.stripRedundantPendingOutgoing
import com.lastasylum.alliance.ui.chat.upsertMessagesBatch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class IncomingBatchWork(
    val previousMessages: List<ChatMessage>,
    val cappedMessages: List<ChatMessage>,
    val newestMessageKey: String?,
    val previousDerived: ChatMessagesListDerived,
    val precomputedDerived: ChatMessagesListDerived? = null,
    val echoesOnly: Boolean = false,
)

/**
 * Socket incoming batch routing + in-memory list apply.
 * VM supplies [Host] callbacks for UI state, caches, and side effects.
 */
class ChatIncomingSync(
    private val scope: CoroutineScope,
    private val incomingApplyMutex: Mutex,
    private val chatMutationLock: Any,
    private val host: Host,
) {
    interface Host {
        val currentUserId: String
        val messageMemoryCap: Int

        fun selectedRoomId(): String?
        fun stateSnapshot(): ChatState
        fun listDerived(): ChatMessagesListDerived
        fun activeOutgoingPendingId(): String?

        fun knownMessageIds(): MutableSet<String>
        fun messageIdIndex(): MutableMap<String, Int>

        fun isRoomActivelyViewed(roomId: String, message: ChatMessage?): Boolean
        fun shouldDeferOwnOutgoingSocketEcho(message: ChatMessage): Boolean
        fun shouldBlockOwnOutgoingRealtime(message: ChatMessage): Boolean
        fun stashIncomingMessageForRoom(message: ChatMessage)
        fun processRealtimeMessageForUnread(message: ChatMessage)
        fun trackRecentSocketMessageId(id: String?)
        fun transferOutgoingLazyColumnKey(pendingId: String, serverId: String)

        fun overlayChatPanelVisible(): Boolean
        fun filterMessagesForRoom(messages: List<ChatMessage>, roomId: String): List<ChatMessage>

        fun commitIncomingBatchUi(
            roomId: String?,
            scopedBatch: List<ChatMessage>,
            cappedMessages: List<ChatMessage>,
            work: IncomingBatchWork,
            derived: ChatMessagesListDerived,
            clearComposer: Boolean,
        )

        fun onIncomingBatchSideEffects(
            roomId: String,
            scopedBatch: List<ChatMessage>,
            cappedMessages: List<ChatMessage>,
            work: IncomingBatchWork,
            clearComposer: Boolean,
        )
    }

    fun dispatchIncomingBatch(batch: List<ChatMessage>) {
        if (batch.isEmpty()) return
        val selected = host.selectedRoomId()
        val applyQueue = ArrayList<ChatMessage>(batch.size)
        for (message in batch) {
            val roomId = message.roomId.trim()
            if (roomId.isBlank()) continue
            if (roomId == selected && host.isRoomActivelyViewed(roomId, message)) {
                if (!host.shouldDeferOwnOutgoingSocketEcho(message)) {
                    applyQueue.add(message)
                } else {
                    host.stashIncomingMessageForRoom(message)
                }
            } else {
                host.processRealtimeMessageForUnread(message)
            }
        }
        if (applyQueue.isNotEmpty()) {
            applyIncomingBatch(applyQueue)
        }
    }

    fun applyIncomingBatch(
        batch: List<ChatMessage>,
        clearComposer: Boolean = false,
    ) {
        if (batch.isEmpty()) return
        val roomId = host.selectedRoomId()
        val selectedRoom = roomId?.trim().orEmpty()
        val scopedBatch = if (selectedRoom.isEmpty()) {
            batch
        } else {
            batch.filter { it.roomId.trim() == selectedRoom }
        }.filterNot { host.shouldBlockOwnOutgoingRealtime(it) }
        if (scopedBatch.isEmpty()) {
            if (batch.isNotEmpty() && !BuildConfig.DEBUG) {
                Log.w("ChatIncomingSync", "non_empty_batch_filtered_empty input=${batch.size}")
            }
            return
        }
        scope.launch(Dispatchers.Default) {
            incomingApplyMutex.withLock {
                val work = synchronized(chatMutationLock) {
                    computeIncomingBatchWork(selectedRoom, scopedBatch)
                }
                if (roomId != null && host.selectedRoomId() != roomId) {
                    scopedBatch.forEach { host.stashIncomingMessageForRoom(it) }
                    return@withLock
                }
                val cappedMessages = sanitizeMessagesAfterRealtimeApply(
                    work.cappedMessages,
                    host.currentUserId,
                    host.activeOutgoingPendingId(),
                )
                val derived = resolveChatListDerivedAfterMessagesUpdate(
                    previousDerived = work.previousDerived,
                    previousMessages = work.previousMessages,
                    nextMessages = cappedMessages,
                    precomputedDerived = work.precomputedDerived?.takeIf {
                        chatMessagesListContentEqual(cappedMessages, work.cappedMessages)
                    },
                )
                withContext(Dispatchers.Main) {
                    if (roomId != null && host.selectedRoomId() != roomId) {
                        scopedBatch.forEach { host.stashIncomingMessageForRoom(it) }
                        return@withContext
                    }
                    val snapshot = host.stateSnapshot()
                    if (work.echoesOnly &&
                        !clearComposer &&
                        chatMessagesListContentEqual(snapshot.messages, cappedMessages)
                    ) {
                        return@withContext
                    }
                    host.commitIncomingBatchUi(
                        roomId = roomId,
                        scopedBatch = scopedBatch,
                        cappedMessages = cappedMessages,
                        work = work,
                        derived = derived,
                        clearComposer = clearComposer,
                    )
                    val rid = host.selectedRoomId()?.trim().orEmpty()
                    if (rid.isNotEmpty()) {
                        host.onIncomingBatchSideEffects(
                            roomId = rid,
                            scopedBatch = scopedBatch,
                            cappedMessages = cappedMessages,
                            work = work,
                            clearComposer = clearComposer,
                        )
                    }
                }
            }
        }
    }

    private fun computeIncomingBatchWork(
        selectedRoom: String,
        scopedBatch: List<ChatMessage>,
    ): IncomingBatchWork {
        val snapshot = host.stateSnapshot()
        val afterDrop = dropMatchingPendingOutgoing(
            current = host.filterMessagesForRoom(snapshot.messages, selectedRoom),
            incoming = scopedBatch,
            currentUserId = host.currentUserId,
        )
        val (echoes, fresh) = partitionOwnOutgoingEchoes(
            scopedBatch,
            host.currentUserId,
            host.messageIdIndex(),
        )
        var messages = afterDrop
        var listDerived = host.listDerived()
        if (echoes.isNotEmpty()) {
            val merged = mergeOwnOutgoingEchoesInPlace(
                echoes = echoes,
                messages = messages,
                derived = listDerived,
                currentUserId = host.currentUserId,
                messageIdIndex = host.messageIdIndex(),
            )
            messages = merged.first
            listDerived = merged.second
        }
        var newestFromPendingReplace: String? = null
        val stillFresh = ArrayList<ChatMessage>(fresh.size)
        val knownMessageIds = host.knownMessageIds()
        val messageIdIndex = host.messageIdIndex()
        for (message in fresh) {
            host.trackRecentSocketMessageId(message._id)
            if (host.shouldDeferOwnOutgoingSocketEcho(message)) {
                host.stashIncomingMessageForRoom(message)
                continue
            }
            val replacement = replaceMatchingPendingOutgoing(
                current = messages,
                incoming = message,
                currentUserId = host.currentUserId,
            )
            if (replacement != null) {
                host.transferOutgoingLazyColumnKey(replacement.pendingId, replacement.serverId)
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
                !hasMatchingPendingOutgoing(messages, message, host.currentUserId)
            ) {
                stillFresh.add(message)
            }
        }
        messages = stripRedundantPendingOutgoing(messages, host.currentUserId)
        messages = stripRedundantOwnOutgoingByClientMessageId(messages, host.currentUserId)
        messages = dedupeOwnOutgoingByClientMessageId(messages, host.currentUserId)
        messages = dedupeMessagesByIdNewestFirst(messages)
        rebuildMessageIdIndex(messages, messageIdIndex)
        listDerived = reconcileDerivedWithMessages(listDerived, messages)
        if (stillFresh.isEmpty()) {
            return IncomingBatchWork(
                previousMessages = snapshot.messages,
                cappedMessages = messages,
                newestMessageKey = newestFromPendingReplace,
                previousDerived = host.listDerived(),
                precomputedDerived = listDerived,
                echoesOnly = newestFromPendingReplace == null,
            )
        }
        val update = upsertMessagesBatch(
            current = messages,
            incoming = stillFresh,
            knownMessageIds = knownMessageIds,
            idIndex = messageIdIndex,
            maxMessages = host.messageMemoryCap,
        )
        val cappedAfterUpsert = stripRedundantPendingOutgoing(
            update.messages,
            host.currentUserId,
        ).let {
            dedupeOwnOutgoingByClientMessageId(
                stripRedundantOwnOutgoingByClientMessageId(it, host.currentUserId),
                host.currentUserId,
            )
        }.let { dedupeMessagesByIdNewestFirst(it) }
        rebuildMessageIdIndex(cappedAfterUpsert, messageIdIndex)
        listDerived = reconcileDerivedWithMessages(listDerived, cappedAfterUpsert)
        return IncomingBatchWork(
            previousMessages = messages,
            cappedMessages = cappedAfterUpsert,
            newestMessageKey = update.newestMessageKey ?: newestFromPendingReplace,
            previousDerived = listDerived,
            precomputedDerived = null,
            echoesOnly = false,
        )
    }
}

private fun partitionOwnOutgoingEchoes(
    batch: List<ChatMessage>,
    currentUserId: String,
    messageIdIndex: Map<String, Int>,
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
    currentUserId: String,
    messageIdIndex: Map<String, Int>,
): Pair<List<ChatMessage>, ChatMessagesListDerived> {
    var nextMessages = messages
    var nextDerived = derived
    val selfId = currentUserId.trim()
    for (echo in echoes) {
        val id = echo._id?.trim().orEmpty()
        val idx = messageIdIndex[id] ?: continue
        if (idx !in nextMessages.indices) continue
        val before = nextMessages
        val existing = nextMessages[idx]
        val merged = if (isOptimisticOutgoingMessageId(existing._id?.trim().orEmpty()) &&
            echo.senderId.trim() == selfId
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
