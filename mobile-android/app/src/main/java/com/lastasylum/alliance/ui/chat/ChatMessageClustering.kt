package com.lastasylum.alliance.ui.chat

import com.lastasylum.alliance.data.chat.ChatMessage

/**
 * Main chat list is **newest-first** (index 0 = visual bottom with [reverseLayout]).
 * Overlay strip preview is **oldest-first** (index last = visual bottom).
 */

/** Newest-first: bottom of a same-sender visual stack (Telegram tail corner). */
fun chatMessageIsClusterChainBottomNewestFirst(
    messages: List<ChatMessage>,
    messageIndex: Int,
): Boolean {
    if (messages.isEmpty() || messageIndex !in messages.indices) return true
    if (messageIndex == 0) return true
    val m = messages[messageIndex]
    val newer = messages[messageIndex - 1]
    return !sameSenderSameDay(m, newer)
}

/** Oldest-first: bottom of stack is the last index in the list. */
fun chatMessageIsClusterChainBottomOldestFirst(
    messages: List<ChatMessage>,
    messageIndex: Int,
): Boolean {
    if (messages.isEmpty() || messageIndex !in messages.indices) return true
    if (messageIndex == messages.lastIndex) return true
    val m = messages[messageIndex]
    val newer = messages[messageIndex + 1]
    return !sameSenderSameDay(m, newer)
}

/** Oldest-first: name row only on the first bubble of a run (top of stack). */
fun chatMessageShowsClusterHeaderOldestFirst(
    messages: List<ChatMessage>,
    messageIndex: Int,
): Boolean {
    if (messages.isEmpty() || messageIndex !in messages.indices) return true
    if (messageIndex == 0) return true
    val m = messages[messageIndex]
    val older = messages[messageIndex - 1]
    return !sameSenderSameDay(m, older)
}

/** Newest-first: name/role header on the oldest bubble of a run. */
fun chatMessageShowsClusterHeaderNewestFirst(
    messages: List<ChatMessage>,
    messageIndex: Int,
): Boolean {
    if (messages.isEmpty() || messageIndex !in messages.indices) return true
    if (messageIndex == messages.lastIndex) return true
    val m = messages[messageIndex]
    val older = messages[messageIndex + 1]
    return !sameSenderSameDay(m, older)
}

fun chatMessageClusterTightInnerTopNewestFirst(
    messages: List<ChatMessage>,
    messageIndex: Int,
): Boolean {
    if (messageIndex <= 0 || messageIndex !in messages.indices) return false
    val m = messages[messageIndex]
    val newer = messages[messageIndex - 1]
    return sameSenderSameDay(m, newer)
}

private fun sameSenderSameDay(a: ChatMessage, b: ChatMessage): Boolean {
    val sid = a.senderId.trim()
    val bid = b.senderId.trim()
    if (sid.isEmpty() || bid.isEmpty() || sid != bid) return false
    val d0 = chatDayKey(a.createdAt)
    val d1 = chatDayKey(b.createdAt)
    return d0 == null || d1 == null || d0 == d1
}
