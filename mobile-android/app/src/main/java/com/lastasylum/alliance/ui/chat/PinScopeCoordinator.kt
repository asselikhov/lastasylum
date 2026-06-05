package com.lastasylum.alliance.ui.chat

import android.util.Log
import com.lastasylum.alliance.data.chat.PinHistoryPreferences
import com.lastasylum.alliance.data.chat.PinnedMessagePreviewDto
import com.lastasylum.alliance.data.teams.TeamForumMessageDto
import com.lastasylum.alliance.data.teams.TeamForumTopicPinChangedEvent

/**
 * Shared Telegram-style pin bar state for one scope (chat room or forum topic).
 */
open class PinScopeCoordinator(
    protected val pinHistoryPrefs: PinHistoryPreferences,
    protected val scopeKey: String,
) {
    var pinnedMessageId: String? = null
    var pinnedMessage: PinnedMessagePreviewDto? = null
    var pinnedAt: String? = null
    var pinnedByUserId: String? = null
    var pinInFlight: Boolean = false
    var pinStateAuthoritative: Boolean = false
    var pinBarPreview: PinnedMessagePreviewDto? = null
    var pinHistoryCount: Int = 0
    var pinnedMessages: List<PinnedMessagePreviewDto> = emptyList()

    protected var pinHistory: List<PinnedMessagePreviewDto> = emptyList()
    protected var pinBarIndex: Int = 0
    protected var lastSyncedActivePinId: String? = null

    var barIndex: Int
        get() = pinBarIndex
        set(value) {
            pinBarIndex = value
        }

    fun replacePinHistory(history: List<PinnedMessagePreviewDto>) {
        pinHistory = history
        pinnedMessages = history
    }

    fun currentPinHistory(): List<PinnedMessagePreviewDto> = pinHistory

    fun isPinBarDismissed(): Boolean {
        val pinId = pinnedMessageId?.trim().orEmpty()
        if (pinId.isEmpty()) return false
        return pinHistoryPrefs.isPinBarDismissed(scopeKey, pinId)
    }

    fun dismissPinBar() {
        val pinId = pinnedMessageId?.trim().orEmpty()
        if (pinId.isEmpty()) return
        pinHistoryPrefs.setDismissedPinBar(scopeKey, pinId)
        pinBarPreview = null
    }

    fun restorePinBar() {
        pinHistoryPrefs.clearDismissedPinBar(scopeKey)
    }

    fun persistHistory() {
        if (scopeKey.isBlank()) return
        pinHistoryPrefs.save(scopeKey, pinHistory)
    }

    fun loadMergedHistory(serverHistory: List<PinnedMessagePreviewDto>): List<PinnedMessagePreviewDto> {
        val local = pinHistoryPrefs.load(scopeKey)
        return mergePinHistory(serverHistory, local)
    }

    fun applyTopicPin(event: TeamForumTopicPinChangedEvent, messages: List<TeamForumMessageDto>) {
        if (pinStateAuthoritative || pinInFlight) {
            val localPinId = pinnedMessageId?.trim().orEmpty()
            val eventPinId = event.pinnedMessageId?.trim().orEmpty()
            if (localPinId != eventPinId) {
                Log.d(TAG, "ignore stale topic pin-changed local=$localPinId event=$eventPinId")
                return
            }
            pinStateAuthoritative = false
        }
        val merged = currentSnapshot().mergePinFromEventWithHistory(event)
        applySnapshot(merged.snapshot)
        pinHistory = merged.pinnedMessages
        pinnedMessages = merged.pinnedMessages
        val newPinId = event.pinnedMessageId?.trim().orEmpty()
        if (newPinId.isNotEmpty()) {
            pinHistoryPrefs.clearDismissedPinBar(scopeKey)
        }
        persistHistory()
        applyPinBarUi(messages)
    }

    fun prepareOptimisticPin(
        messageId: String,
        previewSource: TeamForumMessageDto?,
        messages: List<TeamForumMessageDto>,
        actorUserId: String,
        actorUsername: String? = null,
    ) {
        val trimmedId = messageId.trim()
        val msg = previewSource?.takeIf { it.id == trimmedId }
            ?: messages.find { it.id == trimmedId }
        val preview = msg?.toPinnedPreview()?.let { p ->
            if (actorUsername.isNullOrBlank()) p else p.copy(pinnedByUsername = actorUsername)
        }
        pinnedMessageId = trimmedId
        pinnedMessage = preview ?: pinnedMessage?.takeIf { it.id == trimmedId }
        pinnedAt = java.time.Instant.now().toString()
        pinnedByUserId = actorUserId
        pinStateAuthoritative = true
        applyPinBarUi(messages)
    }

    fun prepareOptimisticUnpin(messages: List<TeamForumMessageDto>) {
        pinnedMessageId = null
        pinnedMessage = null
        pinnedAt = null
        pinnedByUserId = null
        pinHistory = emptyList()
        pinnedMessages = emptyList()
        pinStateAuthoritative = true
        persistHistory()
        applyPinBarUi(messages)
    }

    fun prepareOptimisticUnpinOne(messageId: String, messages: List<TeamForumMessageDto>) {
        val id = messageId.trim()
        if (id.isEmpty()) return
        pinHistory = removePinFromHistory(pinHistory, id)
        pinnedMessages = pinHistory
        if (pinnedMessageId?.trim() == id) {
            val next = pinHistory.firstOrNull()
            if (next != null) {
                pinnedMessageId = next.id
                pinnedMessage = next
            } else {
                pinnedMessageId = null
                pinnedMessage = null
                pinnedAt = null
                pinnedByUserId = null
            }
        }
        pinStateAuthoritative = true
        persistHistory()
        applyPinBarUi(messages)
    }

    fun onPinSuccessHistory(
        history: List<PinnedMessagePreviewDto>,
        snapshot: TopicPinSnapshot,
        messages: List<TeamForumMessageDto>,
    ) {
        applySnapshot(snapshot)
        pinHistory = history
        pinnedMessages = history
        pinStateAuthoritative = false
        snapshot.pinnedMessageId?.trim()?.takeIf { it.isNotEmpty() }?.let {
            pinHistoryPrefs.clearDismissedPinBar(scopeKey)
        }
        persistHistory()
        applyPinBarUi(messages)
    }

    fun onUnpinSuccessHistory(
        history: List<PinnedMessagePreviewDto>,
        snapshot: TopicPinSnapshot,
        messages: List<TeamForumMessageDto> = emptyList(),
    ) {
        applySnapshot(snapshot)
        pinHistory = history
        pinnedMessages = history
        pinStateAuthoritative = false
        if (pinnedMessageId.isNullOrBlank()) {
            pinHistory = emptyList()
            pinnedMessages = emptyList()
            pinBarIndex = 0
        }
        persistHistory()
        applyPinBarUi(messages)
    }

    fun clearPinIfMessageDeleted(messageId: String, messages: List<TeamForumMessageDto>) {
        val removed = messageId.trim()
        if (removed.isEmpty()) return
        pinHistory = removePinFromHistory(pinHistory, removed)
        pinnedMessages = pinHistory
        if (pinnedMessageId?.trim() == removed) {
            pinnedMessageId = null
            pinnedMessage = null
            pinnedAt = null
            pinnedByUserId = null
        }
        persistHistory()
        applyPinBarUi(messages)
    }

    fun refreshPinAfterMessageEdit(messageId: String, messages: List<TeamForumMessageDto>) {
        val id = messageId.trim()
        if (id.isEmpty()) return
        if (pinnedMessageId?.trim() != id) return
        messages.find { it.id.trim() == id }?.toPinnedPreview()?.let { preview ->
            pinnedMessage = preview
            pinHistory = pinHistory.map { entry ->
                if (entry.id.trim() == id) preview else entry
            }
            pinnedMessages = pinHistory
            persistHistory()
            applyPinBarUi(messages)
        }
    }

    fun onPinnedBarTap(
        messages: List<TeamForumMessageDto>,
        jumpToMessage: (String) -> Unit,
    ): String? {
        val pinId = pinnedMessageId?.trim().orEmpty()
        if (pinId.isEmpty()) return null
        val targetId = pinBarPreview?.id?.trim().orEmpty().ifEmpty { pinId }
        jumpToMessage(targetId)
        if (pinHistory.size > 1) {
            pinBarIndex = advancePinBarIndex(pinHistory, pinBarIndex)
            applyPinBarUi(messages)
        }
        return targetId
    }

    fun applyPinBarUi(messages: List<TeamForumMessageDto>) {
        val pinId = pinnedMessageId?.trim().orEmpty()
        if (pinId.isEmpty()) {
            pinBarPreview = null
            pinHistoryCount = 0
            pinnedMessages = emptyList()
            return
        }
        if (isPinBarDismissed()) {
            syncPinHistory(messages)
            pinBarPreview = null
            pinHistoryCount = pinHistoryDisplayCount(pinHistory)
            pinnedMessages = pinHistory
            return
        }
        syncPinHistory(messages)
        pinBarIndex = pinBarIndex.coerceIn(0, (pinHistory.size - 1).coerceAtLeast(0))
        val serverPreview = resolveForumPinnedPreview(
            pinnedMessageId,
            pinnedMessage,
            messages,
        )
        pinBarPreview = pinBarPreviewAtIndex(pinHistory, pinBarIndex, serverPreview, pinnedMessageId)
        pinHistoryCount = pinHistoryDisplayCount(pinHistory)
    }

    fun applyPinBarUiChat(
        messages: List<com.lastasylum.alliance.data.chat.ChatMessage>,
        serverPreview: PinnedMessagePreviewDto?,
    ): PinBarUiResult {
        val pinId = pinnedMessageId?.trim().orEmpty()
        if (pinId.isEmpty()) {
            pinBarPreview = null
            pinHistoryCount = 0
            pinnedMessages = emptyList()
            return PinBarUiResult(null, 0, emptyList(), pinBarIndex)
        }
        if (isPinBarDismissed()) {
            pinHistory = refreshPinHistoryPreviews(pinHistory, messages)
            pinnedMessages = pinHistory
            pinBarPreview = null
            pinHistoryCount = pinHistoryDisplayCount(pinHistory)
            return PinBarUiResult(null, pinHistoryCount, pinHistory, pinBarIndex)
        }
        pinHistory = refreshPinHistoryPreviews(
            if (pinnedMessages.isNotEmpty()) pinnedMessages else pinHistory,
            messages,
        )
        pinnedMessages = pinHistory
        val prevActivePin = lastSyncedActivePinId
        if (prevActivePin != pinId) {
            lastSyncedActivePinId = pinId
            pinBarIndex = 0
        } else if (pinBarIndex !in pinHistory.indices) {
            pinBarIndex = 0
        }
        pinBarIndex = pinBarIndex.coerceIn(0, (pinHistory.size - 1).coerceAtLeast(0))
        val preview = pinBarPreviewAtIndex(pinHistory, pinBarIndex, serverPreview, pinnedMessageId)
        pinBarPreview = preview
        pinHistoryCount = pinHistoryDisplayCount(pinHistory)
        return PinBarUiResult(preview, pinHistoryCount, pinHistory, pinBarIndex)
    }

    protected fun syncPinHistory(messages: List<TeamForumMessageDto>) {
        val pinId = pinnedMessageId?.trim().orEmpty()
        if (pinId.isEmpty()) {
            pinHistory = emptyList()
            pinnedMessages = emptyList()
            return
        }
        val baseHistory = if (pinnedMessages.isNotEmpty()) pinnedMessages else pinHistory
        pinHistory = baseHistory.map { entry ->
            messages.find { it.id.trim() == entry.id.trim() }?.toPinnedPreview() ?: entry
        }
        pinnedMessages = pinHistory
        val prevActivePin = lastSyncedActivePinId
        if (prevActivePin != pinId) {
            lastSyncedActivePinId = pinId
            pinBarIndex = 0
        } else if (pinBarIndex !in pinHistory.indices) {
            pinBarIndex = 0
        }
    }

    fun rollbackTo(snapshot: TopicPinSnapshot, messages: List<TeamForumMessageDto>) {
        applySnapshot(snapshot)
        applyPinBarUi(messages)
    }

    fun applySnapshot(snapshot: TopicPinSnapshot) {
        pinnedMessageId = snapshot.pinnedMessageId
        pinnedAt = snapshot.pinnedAt
        pinnedByUserId = snapshot.pinnedByUserId
        pinnedMessage = snapshot.pinnedMessage
    }

    protected fun currentSnapshot(): TopicPinSnapshot = TopicPinSnapshot(
        pinnedMessageId = pinnedMessageId,
        pinnedAt = pinnedAt,
        pinnedByUserId = pinnedByUserId,
        pinnedMessage = pinnedMessage,
    )

    data class PinBarUiResult(
        val preview: PinnedMessagePreviewDto?,
        val historyCount: Int,
        val history: List<PinnedMessagePreviewDto>,
        val barIndex: Int,
    )

    protected companion object {
        const val TAG = "PinScopeCoordinator"
    }
}
