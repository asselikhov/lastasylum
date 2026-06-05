package com.lastasylum.alliance.ui.chat

import android.util.Log
import com.lastasylum.alliance.data.chat.PinHistoryPreferences
import com.lastasylum.alliance.data.chat.PinnedMessagePreviewDto
import com.lastasylum.alliance.data.teams.TeamForumMessageDto
import com.lastasylum.alliance.data.teams.TeamForumTopicDto
import com.lastasylum.alliance.data.teams.TeamForumTopicPinChangedEvent

/**
 * Telegram-style pin bar state for a single forum topic (mirrors [ChatViewModel] pin logic).
 */
class ForumPinCoordinator(
    private val pinHistoryPrefs: PinHistoryPreferences,
    private val scopeKey: String,
) {
    var pinnedMessageId: String? = null
    var pinnedMessage: PinnedMessagePreviewDto? = null
    var pinnedAt: String? = null
    var pinnedByUserId: String? = null
    var pinInFlight: Boolean = false
    var pinBarPreview: PinnedMessagePreviewDto? = null
    var pinHistoryCount: Int = 0
    var pinnedMessages: List<PinnedMessagePreviewDto> = emptyList()

    private var pinHistory: List<PinnedMessagePreviewDto> = emptyList()
    private var pinBarIndex: Int = 0
    private var lastSyncedActivePinId: String? = null

    fun onEnterTopic(initialTopic: TeamForumTopicDto? = null) {
        if (initialTopic != null) {
            applySnapshot(initialTopic.toPinSnapshot())
        } else {
            applySnapshot(
                TopicPinSnapshot(
                    pinnedMessageId = null,
                    pinnedAt = null,
                    pinnedByUserId = null,
                    pinnedMessage = null,
                ),
            )
        }
        pinHistory = initialTopic?.let { serverPinHistoryFromTopic(it) }.orEmpty()
        pinnedMessages = pinHistory
        pinBarIndex = 0
        lastSyncedActivePinId = null
        applyPinBarUi(emptyList())
    }

    fun applyTopicPin(event: TeamForumTopicPinChangedEvent, messages: List<TeamForumMessageDto>) {
        val merged = currentSnapshot().mergePinFromEventWithHistory(event)
        applySnapshot(merged.snapshot)
        pinHistory = merged.pinnedMessages
        pinnedMessages = merged.pinnedMessages
        val newPinId = event.pinnedMessageId?.trim().orEmpty()
        if (newPinId.isNotEmpty()) {
            pinHistoryPrefs.clearDismissedPinBar(scopeKey)
        }
        Log.d(
            TAG,
            "topic pin-changed topicId=${event.topicId} history=${merged.pinnedMessages.size} active=$newPinId",
        )
        applyPinBarUi(messages)
    }

    fun applyTopicFromServer(topic: TeamForumTopicDto, messages: List<TeamForumMessageDto>) {
        if (pinInFlight) {
            val merged = topic.toPinSnapshot().mergePinFromPrevious(currentSnapshot(), pinOperationInFlight = true)
            applySnapshot(merged)
        } else {
            applySnapshot(topic.toPinSnapshot())
        }
        pinHistory = serverPinHistoryFromTopic(topic)
        pinnedMessages = pinHistory
        applyPinBarUi(messages)
    }

    fun onPinSuccess(topic: TeamForumTopicDto, messages: List<TeamForumMessageDto>) {
        applySnapshot(topic.toPinSnapshot())
        pinHistory = serverPinHistoryFromTopic(topic)
        pinnedMessages = pinHistory
        topic.pinnedMessageId?.trim()?.takeIf { it.isNotEmpty() }?.let {
            pinHistoryPrefs.clearDismissedPinBar(scopeKey)
        }
        applyPinBarUi(messages)
    }

    fun onUnpinSuccess(topic: TeamForumTopicDto) {
        applySnapshot(topic.toPinSnapshot())
        pinHistory = serverPinHistoryFromTopic(topic)
        pinnedMessages = pinHistory
        if (pinnedMessageId.isNullOrBlank()) {
            pinHistory = emptyList()
            pinnedMessages = emptyList()
            pinBarIndex = 0
        }
        applyPinBarUi(emptyList())
    }

    fun prepareOptimisticPin(
        messageId: String,
        previewSource: TeamForumMessageDto?,
        messages: List<TeamForumMessageDto>,
        actorUserId: String,
    ) {
        val trimmedId = messageId.trim()
        val msg = previewSource?.takeIf { it.id == trimmedId }
            ?: messages.find { it.id == trimmedId }
        val preview = msg?.toPinnedPreview()
        pinnedMessageId = trimmedId
        pinnedMessage = preview ?: pinnedMessage?.takeIf { it.id == trimmedId }
        pinnedAt = java.time.Instant.now().toString()
        pinnedByUserId = actorUserId
        applyPinBarUi(messages)
    }

    fun prepareOptimisticUnpin(messages: List<TeamForumMessageDto>) {
        pinnedMessageId = null
        pinnedMessage = null
        pinnedAt = null
        pinnedByUserId = null
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
        applyPinBarUi(messages)
    }

    fun refreshPinAfterMessageEdit(
        messageId: String,
        messages: List<TeamForumMessageDto>,
    ) {
        val id = messageId.trim()
        if (id.isEmpty()) return
        if (pinnedMessageId?.trim() != id) return
        messages.find { it.id.trim() == id }?.toPinnedPreview()?.let { preview ->
            pinnedMessage = preview
            pinHistory = pinHistory.map { entry ->
                if (entry.id.trim() == id) preview else entry
            }
            pinnedMessages = pinHistory
            applyPinBarUi(messages)
        }
    }

    fun advancePinBarIndex(messages: List<TeamForumMessageDto>) {
        if (pinHistory.size > 1) {
            pinBarIndex = advancePinBarIndex(pinHistory, pinBarIndex)
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

    private fun syncPinHistory(messages: List<TeamForumMessageDto>) {
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

    fun applyPinBarUi(messages: List<TeamForumMessageDto>) {
        val pinId = pinnedMessageId?.trim().orEmpty()
        if (pinId.isEmpty()) {
            pinBarPreview = null
            pinHistoryCount = 0
            pinnedMessages = emptyList()
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

    private fun currentSnapshot(): TopicPinSnapshot = TopicPinSnapshot(
        pinnedMessageId = pinnedMessageId,
        pinnedAt = pinnedAt,
        pinnedByUserId = pinnedByUserId,
        pinnedMessage = pinnedMessage,
    )

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

    private fun TeamForumTopicDto.toPinSnapshot(): TopicPinSnapshot = TopicPinSnapshot(
        pinnedMessageId = pinnedMessageId,
        pinnedAt = pinnedAt,
        pinnedByUserId = pinnedByUserId,
        pinnedMessage = pinnedMessage,
    )

    private companion object {
        const val TAG = "PinDiag"
    }
}
