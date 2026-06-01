package com.lastasylum.alliance.ui.chat

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.data.chat.ChatMessage
import com.lastasylum.alliance.data.chat.stickers.StickerPacks
import com.lastasylum.alliance.ui.util.parseIsoInstantEpochMilli

sealed interface ChatTimelineEntry {
    data class DaySeparator(val label: String) : ChatTimelineEntry
    data class ChatMessageItem(val message: ChatMessage, val messageIndex: Int) : ChatTimelineEntry
    data class ChatAlbumItem(
        val firstMessageIndex: Int,
        val representativeMessage: ChatMessage,
        val messageIndices: List<Int>,
        /** Stable ids for highlight/selection without scanning the full messages list in items. */
        val memberMessageIds: List<String>,
        val resolvedImageUrls: List<String>,
        val caption: String?,
    ) : ChatTimelineEntry
}

fun chatMessageKey(message: ChatMessage): String =
    message._id ?: "${message.senderId}_${message.createdAt}_${message.text.hashCode()}"

fun chatMessageIsOwn(message: ChatMessage, currentUserId: String): Boolean {
    val sid = message.senderId.trim()
    val cid = currentUserId.trim()
    if (sid.isEmpty() || cid.isEmpty()) return false
    return sid == cid
}

fun chatBubbleClusterTopSpacing(
    timeline: List<ChatTimelineEntry>,
    timelineIndex: Int,
    message: ChatMessage,
): Dp {
    val sid = message.senderId.trim()
    if (sid.isEmpty()) return 10.dp
    var i = timelineIndex + 1
    while (i < timeline.size) {
        val e = timeline[i]
        if (e is ChatTimelineEntry.DaySeparator) return 14.dp
        val o = when (e) {
            is ChatTimelineEntry.ChatMessageItem -> e.message
            is ChatTimelineEntry.ChatAlbumItem -> e.representativeMessage
        }
        val same = o.senderId.trim() == sid && o.senderId.trim().isNotEmpty()
        return if (same) 3.dp else 14.dp
    }
    return 10.dp
}

internal fun chatMessageIsAlbumCandidate(m: ChatMessage): Boolean {
    if (m.replyTo != null) return false
    val hasImages = m.attachments.any { it.kind == "image" && it.url.isNotBlank() }
    return hasImages && StickerPacks.parse(m.text) == null
}

fun buildChatTimeline(messages: List<ChatMessage>): List<ChatTimelineEntry> {
    if (messages.isEmpty()) return emptyList()
    val out = ArrayList<ChatTimelineEntry>(messages.size + 8)
    fun isAlbumCandidate(m: ChatMessage): Boolean = chatMessageIsAlbumCandidate(m)
    fun tsMillis(createdAt: String?): Long =
        parseIsoInstantEpochMilli(createdAt) ?: -1L
    val albumWindowMs = 3L * 60L * 1000L

    var i = 0
    while (i < messages.size) {
        if (i > 0) {
            val newer = messages[i - 1]
            val older = messages[i]
            val d0 = chatDayKey(newer.createdAt)
            val d1 = chatDayKey(older.createdAt)
            if (d0 != null && d1 != null && d0 != d1) {
                val label = formatChatDaySeparator(older.createdAt)
                if (label.isNotBlank()) out.add(ChatTimelineEntry.DaySeparator(label))
            }
        }

        val m = messages[i]
        if (isAlbumCandidate(m)) {
            val sid = m.senderId.trim()
            val day = chatDayKey(m.createdAt)
            val baseTs = tsMillis(m.createdAt)
            val indices = ArrayList<Int>(4)
            val urls = ArrayList<String>(8)
            var caption: String? = null
            var repIndex = i
            var j = i
            while (j < messages.size && indices.size < 10) {
                val mm = messages[j]
                if (!isAlbumCandidate(mm)) break
                if (mm.senderId.trim() != sid || sid.isBlank()) break
                if (day != null && chatDayKey(mm.createdAt) != day) break
                val tsm = tsMillis(mm.createdAt)
                if (baseTs > 0 && tsm > 0 && kotlin.math.abs(tsm - baseTs) > albumWindowMs) break
                val t = mm.text.trimEnd()
                if (t.isNotBlank()) {
                    if (caption == null) {
                        caption = t
                        repIndex = j
                    } else {
                        break
                    }
                }
                indices.add(j)
                mm.attachments
                    .filter { it.kind == "image" && it.url.isNotBlank() }
                    .forEach { urls.add(resolvedChatAttachmentImageUrl(it.url)) }
                j++
                if (j < messages.size) {
                    val next = messages[j]
                    if (next.senderId.trim() != sid) break
                }
            }
            if (indices.size >= 2 && urls.isNotEmpty()) {
                out.add(
                    ChatTimelineEntry.ChatAlbumItem(
                        firstMessageIndex = i,
                        representativeMessage = messages[repIndex],
                        messageIndices = indices,
                        memberMessageIds = indices.mapNotNull { messages[it]._id },
                        resolvedImageUrls = urls,
                        caption = caption,
                    ),
                )
                i += indices.size
                continue
            }
        }

        out.add(ChatTimelineEntry.ChatMessageItem(m, i))
        i++
    }
    return out
}

fun chatTimelineIndexForMessageId(
    timeline: List<ChatTimelineEntry>,
    messages: List<ChatMessage>,
    targetId: String,
): Int {
    val id = targetId.trim()
    if (id.isEmpty()) return -1
    return timeline.indexOfFirst { entry ->
        when (entry) {
            is ChatTimelineEntry.ChatMessageItem -> entry.message._id == id
            is ChatTimelineEntry.ChatAlbumItem ->
                entry.messageIndices.any { i -> messages.getOrNull(i)?._id == id }
            else -> false
        }
    }
}
