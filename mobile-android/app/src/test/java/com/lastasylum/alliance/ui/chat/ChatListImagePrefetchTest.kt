package com.lastasylum.alliance.ui.chat

import com.lastasylum.alliance.data.chat.ChatAttachment
import com.lastasylum.alliance.data.chat.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatListImagePrefetchTest {

    @Test
    fun imageUrlsForTimelineIndices_collectsAlbumAndMessageAttachments() {
        val msg = ChatMessage(
            _id = "m1",
            allianceId = "al1",
            senderId = "u1",
            senderUsername = "user",
            senderRole = "R1",
            text = "",
            createdAt = "2026-01-01T00:00:00Z",
            attachments = listOf(
                ChatAttachment(
                    kind = "image",
                    url = "/chat/attachments/f1/photo.jpg",
                    mimeType = "image/jpeg",
                    filename = "photo.jpg",
                ),
            ),
        )
        val timeline = listOf(
            ChatTimelineEntry.ChatAlbumItem(
                firstMessageIndex = 0,
                representativeMessage = msg,
                messageIndices = listOf(0),
                memberMessageIds = listOf("m1"),
                resolvedImageUrls = listOf("https://cdn.example.com/a.jpg"),
                caption = null,
            ),
            ChatTimelineEntry.ChatMessageItem(message = msg, messageIndex = 0),
        )
        val urls = imageUrlsForTimelineIndices(timeline, listOf(0, 1))
        assertTrue(urls.contains("https://cdn.example.com/a.jpg"))
        assertTrue(urls.any { it.contains("photo.jpg") })
        assertEquals(2, urls.size)
    }
}
