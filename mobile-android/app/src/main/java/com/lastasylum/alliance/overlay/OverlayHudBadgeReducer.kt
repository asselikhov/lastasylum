package com.lastasylum.alliance.overlay

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Single main-thread writer for [OverlayGameStatusHudState] inbox chips.
 * All partial/full badge updates should go through [commit] so [prevDisplayed] is read once per frame.
 */
class OverlayHudBadgeReducer(
    private val hudFlow: MutableStateFlow<OverlayGameStatusHudState>,
) {
    fun current(): OverlayGameStatusHudState = hudFlow.value

    fun commit(transform: (OverlayGameStatusHudState) -> OverlayGameStatusHudState) {
        val prev = hudFlow.value
        val next = transform(prev)
        if (next != prev) {
            hudFlow.value = next
        }
    }

    fun commitInboxPartialRefresh(
        allianceChatUnread: Int? = null,
        teamNewsUnread: Int? = null,
        forumUnread: Int? = null,
    ) {
        commit { prev ->
            prev.copy(
                allianceChatUnread = allianceChatUnread ?: prev.allianceChatUnread,
                teamNewsUnread = teamNewsUnread ?: prev.teamNewsUnread,
                forumUnread = forumUnread ?: prev.forumUnread,
            )
        }
    }
}
