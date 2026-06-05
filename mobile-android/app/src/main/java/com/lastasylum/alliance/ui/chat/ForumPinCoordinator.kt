package com.lastasylum.alliance.ui.chat

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

    private var pinHistory: List<PinnedMessagePreviewDto> = emptyList()
    private var pinBarIndex: Int = 0
    private var lastSyncedActivePinId: String? = null

    fun onEnterTopic() {
        pinHistory = pinHistoryPrefs.load(scopeKey)
        pinBarIndex = 0
        lastSyncedActivePinId = null
        applyPinBarUi(emptyList())
    }

    fun applyTopicPin(event: TeamForumTopicPinChangedEvent) {
        val merged = currentSnapshot().mergePinFromEvent(event)
        applySnapshot(merged)
        applyPinBarUi(emptyList())
    }

    fun applyTopicFromServer(topic: TeamForumTopicDto, messages: List<TeamForumMessageDto>) {
        if (pinInFlight) {
            val merged = topic.toPinSnapshot().mergePinFromPrevious(currentSnapshot(), pinOperationInFlight = true)
            applySnapshot(merged)
        } else {
            applySnapshot(topic.toPinSnapshot())
        }
        applyPinBarUi(messages)
    }

    fun onPinSuccess(topic: TeamForumTopicDto, messages: List<TeamForumMessageDto>) {
        applySnapshot(topic.toPinSnapshot())
        applyPinBarUi(messages)
    }

    fun onUnpinSuccess(topic: TeamForumTopicDto) {
        applySnapshot(topic.toPinSnapshot())
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
        preview?.let { p ->
            pinHistory = pushPinHistory(pinHistory, p)
            pinBarIndex = 0
            pinHistoryPrefs.save(scopeKey, pinHistory)
        }
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
        if (pinnedMessageId?.trim() == messageId.trim()) {
            pinnedMessageId = null
            pinnedMessage = null
            pinnedAt = null
            pinnedByUserId = null
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
        if (pinId.isEmpty()) return
        val serverPreview = resolveForumPinnedPreview(
            pinnedMessageId,
            pinnedMessage,
            messages,
        )
        val existing = pinHistory.map { entry ->
            messages.find { it.id.trim() == entry.id.trim() }?.toPinnedPreview() ?: entry
        }
        val (updated, resetIndex) = syncRoomPinHistory(
            existing,
            serverPreview,
            pinnedMessageId,
        )
        pinHistory = updated
        val prevActivePin = lastSyncedActivePinId
        if (prevActivePin != pinId) {
            lastSyncedActivePinId = pinId
            pinBarIndex = 0
        } else if (resetIndex || pinBarIndex !in pinHistory.indices) {
            pinBarIndex = 0
        }
        pinHistoryPrefs.save(scopeKey, pinHistory)
    }

    fun applyPinBarUi(messages: List<TeamForumMessageDto>) {
        val pinId = pinnedMessageId?.trim().orEmpty()
        if (pinId.isEmpty()) {
            pinBarPreview = null
            pinHistoryCount = 0
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
}
