package com.lastasylum.alliance.ui.chat

import com.lastasylum.alliance.data.chat.ChatMessage

/**
 * Defers full timeline rebuild while the message [LazyColumn] is scrolling (fling).
 * Patch/instant derives bypass this gate in [ChatViewModel].
 */
internal class ChatListDeriveDefer {
    @Volatile
    var scrollInProgress: Boolean = false
        private set

    private var pendingMessages: List<ChatMessage>? = null

    fun setScrollInProgress(inProgress: Boolean): List<ChatMessage>? {
        scrollInProgress = inProgress
        return if (!inProgress) {
            pendingMessages?.also { pendingMessages = null }
        } else {
            null
        }
    }

    /** @return true if derive should be postponed */
    fun deferFullDerive(messages: List<ChatMessage>): Boolean {
        if (!scrollInProgress) {
            pendingMessages = null
            return false
        }
        pendingMessages = messages
        return true
    }

    fun peekPending(): List<ChatMessage>? = pendingMessages
}
