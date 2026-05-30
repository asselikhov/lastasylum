package com.lastasylum.alliance.ui.chat

import com.lastasylum.alliance.data.teams.TeamForumMessageDto

/** Defers full forum timeline rebuild while the topic [LazyColumn] is scrolling. */
internal class ForumListDeriveDefer {
    @Volatile
    var scrollInProgress: Boolean = false
        private set

    private var pendingGeneration: Int? = null
    private var pendingMessages: List<TeamForumMessageDto>? = null

    fun setScrollInProgress(inProgress: Boolean): Pair<Int, List<TeamForumMessageDto>>? {
        scrollInProgress = inProgress
        return if (!inProgress && pendingGeneration != null && pendingMessages != null) {
            val gen = pendingGeneration!!
            val msgs = pendingMessages!!
            pendingGeneration = null
            pendingMessages = null
            gen to msgs
        } else {
            null
        }
    }

    /** @return true if derive should be postponed */
    fun deferFullDerive(generation: Int, messages: List<TeamForumMessageDto>): Boolean {
        if (!scrollInProgress) {
            pendingGeneration = null
            pendingMessages = null
            return false
        }
        pendingGeneration = generation
        pendingMessages = messages
        return true
    }
}
