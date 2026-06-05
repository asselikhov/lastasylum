package com.lastasylum.alliance.ui.chat

import com.lastasylum.alliance.data.chat.PinHistoryPreferences
import com.lastasylum.alliance.data.chat.PinnedMessagePreviewDto
import com.lastasylum.alliance.data.teams.TeamForumMessageDto
import com.lastasylum.alliance.data.teams.TeamForumTopicDto
import com.lastasylum.alliance.data.teams.TeamForumTopicPinChangedEvent

/**
 * Forum topic pin bar — delegates to [PinScopeCoordinator].
 */
class ForumPinCoordinator(
    pinHistoryPrefs: PinHistoryPreferences,
    scopeKey: String,
) : PinScopeCoordinator(pinHistoryPrefs, scopeKey) {

    fun onEnterTopic(initialTopic: TeamForumTopicDto? = null) {
        if (initialTopic != null) {
            applySnapshot(initialTopic.toPinSnapshot())
            val serverHistory = serverPinHistoryFromTopic(initialTopic)
            pinHistory = loadMergedHistory(serverHistory)
            pinnedMessages = pinHistory
        } else {
            applySnapshot(
                TopicPinSnapshot(
                    pinnedMessageId = null,
                    pinnedAt = null,
                    pinnedByUserId = null,
                    pinnedMessage = null,
                ),
            )
            pinHistory = emptyList()
            pinnedMessages = emptyList()
        }
        pinBarIndex = 0
        lastSyncedActivePinId = null
        applyPinBarUi(emptyList())
    }

    fun applyTopicFromServer(topic: TeamForumTopicDto, messages: List<TeamForumMessageDto>) {
        val serverSnapshot = topic.toPinSnapshot()
        val merged = serverSnapshot.mergePinFromPrevious(
            currentSnapshot(),
            pinOperationInFlight = pinInFlight || pinStateAuthoritative,
        )
        applySnapshot(merged)
        val serverPinId = topic.pinnedMessageId?.trim().orEmpty()
        val mergedPinId = merged.pinnedMessageId?.trim().orEmpty()
        if (serverPinId == mergedPinId && !pinInFlight) {
            pinStateAuthoritative = false
            pinHistory = loadMergedHistory(serverPinHistoryFromTopic(topic))
            pinnedMessages = pinHistory
            persistHistory()
        }
        applyPinBarUi(messages)
    }

    fun onPinSuccess(topic: TeamForumTopicDto, messages: List<TeamForumMessageDto>) {
        onPinSuccessHistory(
            history = serverPinHistoryFromTopic(topic),
            snapshot = topic.toPinSnapshot(),
            messages = messages,
        )
    }

    fun onUnpinSuccess(topic: TeamForumTopicDto, messages: List<TeamForumMessageDto> = emptyList()) {
        onUnpinSuccessHistory(
            history = serverPinHistoryFromTopic(topic),
            snapshot = topic.toPinSnapshot(),
            messages = messages,
        )
    }

    fun advancePinBarIndex(messages: List<TeamForumMessageDto>) {
        if (pinHistory.size > 1) {
            pinBarIndex = advancePinBarIndex(pinHistory, pinBarIndex)
            applyPinBarUi(messages)
        }
    }

    fun pinnedMessageIds(): Set<String> =
        pinnedMessages.map { it.id.trim() }.filter { it.isNotEmpty() }.toSet()

    private fun TeamForumTopicDto.toPinSnapshot(): TopicPinSnapshot = TopicPinSnapshot(
        pinnedMessageId = pinnedMessageId,
        pinnedAt = pinnedAt,
        pinnedByUserId = pinnedByUserId,
        pinnedMessage = pinnedMessage,
    )
}
