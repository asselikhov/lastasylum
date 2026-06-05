package com.lastasylum.alliance.data.chat

import com.squareup.moshi.JsonDataException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ChatRoomDtoParseTest {
    private val moshi = SquadRelayMoshi.build()

    private val adapter = moshi.adapter(ChatRoomDto::class.java)

    @Test
    fun parsePinRoomResponse_withPinHistoryAndPinnedMessages() {
        val json = """
            {
              "_id": "507f1f77bcf86cd799439013",
              "id": "507f1f77bcf86cd799439013",
              "allianceId": "pt:507f1f77bcf86cd799439012",
              "title": "Рейд",
              "sortOrder": 2,
              "archivedAt": null,
              "pinnedMessageId": "507f1f77bcf86cd799439014",
              "pinnedAt": "2026-01-01T12:00:00.000Z",
              "pinnedByUserId": "507f1f77bcf86cd799439011",
              "pinHistory": [
                {
                  "messageId": "507f1f77bcf86cd799439014",
                  "pinnedAt": "2026-01-01T12:00:00.000Z",
                  "pinnedByUserId": "507f1f77bcf86cd799439011"
                }
              ],
              "createdAt": "2025-06-01T00:00:00.000Z",
              "updatedAt": "2026-06-01T00:00:00.000Z",
              "__v": 0,
              "pinnedMessage": {
                "id": "507f1f77bcf86cd799439014",
                "text": "hello",
                "senderUsername": "ally",
                "senderTeamTag": "TAG",
                "senderServerNumber": 42,
                "createdAt": "2026-01-01T00:00:00.000Z",
                "editedAt": null,
                "hasImage": false,
                "isSticker": false,
                "imageThumbnailUrl": null,
                "pinnedByUsername": "officer"
              },
              "pinnedMessages": [
                {
                  "id": "507f1f77bcf86cd799439014",
                  "text": "hello",
                  "senderUsername": "ally",
                  "senderTeamTag": "TAG",
                  "senderServerNumber": 42,
                  "createdAt": "2026-01-01T00:00:00.000Z",
                  "editedAt": null,
                  "hasImage": false,
                  "isSticker": false,
                  "imageThumbnailUrl": null,
                  "pinnedByUsername": "officer"
                }
              ]
            }
        """.trimIndent()

        val room = adapter.fromJson(json)
        assertNotNull(room)
        assertEquals("507f1f77bcf86cd799439013", room!!.id)
        assertEquals("507f1f77bcf86cd799439014", room.pinnedMessageId)
        assertNotNull(room.pinnedMessage)
        assertEquals(1, room.pinnedMessagesOrEmpty().size)
    }

    @Test
    fun parsePinRoomResponse_stubPreviewWhenMessageMissing() {
        val json = """
            {
              "_id": "507f1f77bcf86cd799439013",
              "allianceId": "pt:507f1f77bcf86cd799439012",
              "title": "Рейд",
              "sortOrder": 2,
              "pinnedMessageId": "507f1f77bcf86cd799439014",
              "pinnedAt": "2026-01-01T12:00:00.000Z",
              "pinnedByUserId": "507f1f77bcf86cd799439011",
              "pinHistory": [
                {
                  "messageId": "507f1f77bcf86cd799439014",
                  "pinnedAt": "2026-01-01T12:00:00.000Z",
                  "pinnedByUserId": "507f1f77bcf86cd799439011"
                }
              ],
              "pinnedMessage": {
                "id": "507f1f77bcf86cd799439014",
                "text": "",
                "senderUsername": "",
                "senderTeamTag": null,
                "senderServerNumber": null,
                "createdAt": "1970-01-01T00:00:00.000Z",
                "editedAt": null,
                "hasImage": false,
                "isSticker": false,
                "imageThumbnailUrl": null,
                "pinnedByUsername": "officer"
              },
              "pinnedMessages": [
                {
                  "id": "507f1f77bcf86cd799439014",
                  "text": "",
                  "senderUsername": "",
                  "senderTeamTag": null,
                  "senderServerNumber": null,
                  "createdAt": "1970-01-01T00:00:00.000Z",
                  "editedAt": null,
                  "hasImage": false,
                  "isSticker": false,
                  "imageThumbnailUrl": null,
                  "pinnedByUsername": "officer"
                }
              ]
            }
        """.trimIndent()

        val room = adapter.fromJson(json)
        assertNotNull(room)
        assertEquals("", room!!.pinnedMessage?.text)
    }

    @Test
    fun parsePinRoomResponse_withoutTitleFails() {
        val json = """
            {
              "_id": "507f1f77bcf86cd799439013",
              "allianceId": "pt:team",
              "pinnedMessageId": "507f1f77bcf86cd799439014",
              "pinnedMessage": {
                "id": "507f1f77bcf86cd799439014",
                "text": "hi",
                "senderUsername": "a",
                "createdAt": "2026-01-01T00:00:00.000Z"
              }
            }
        """.trimIndent()

        assertThrows(JsonDataException::class.java) { adapter.fromJson(json) }
    }

    @Test
    fun parsePinRoomResponse_nullPinnedMessagesAccepted() {
        val json = """
            {
              "_id": "507f1f77bcf86cd799439013",
              "allianceId": "pt:team",
              "title": "Рейд",
              "pinnedMessageId": "507f1f77bcf86cd799439014",
              "pinnedMessages": null
            }
        """.trimIndent()

        val room = adapter.fromJson(json)
        assertNotNull(room)
        assertEquals(emptyList<PinnedMessagePreviewDto>(), room!!.pinnedMessagesOrEmpty())
    }

    @Test
    fun parsePinRoomResponse_nullSenderUsernameInPreviewAccepted() {
        val json = """
            {
              "_id": "507f1f77bcf86cd799439013",
              "allianceId": "pt:team",
              "title": "Рейд",
              "pinnedMessageId": "507f1f77bcf86cd799439014",
              "pinnedMessage": {
                "id": "507f1f77bcf86cd799439014",
                "text": "hi",
                "senderUsername": null,
                "createdAt": "2026-01-01T00:00:00.000Z"
              },
              "pinnedMessages": []
            }
        """.trimIndent()

        val room = adapter.fromJson(json)
        assertNotNull(room)
        assertEquals("", room!!.pinnedMessage?.senderUsername)
    }

    @Test
    fun parsePinRoomResponse_previewUsesUnderscoreIdField() {
        val json = """
            {
              "_id": "507f1f77bcf86cd799439013",
              "allianceId": "pt:team",
              "title": "Рейд",
              "pinnedMessageId": "507f1f77bcf86cd799439014",
              "pinnedMessage": {
                "_id": "507f1f77bcf86cd799439014",
                "text": "hi",
                "senderUsername": "a",
                "createdAt": "2026-01-01T00:00:00.000Z"
              },
              "pinnedMessages": []
            }
        """.trimIndent()

        val room = adapter.fromJson(json)
        assertNotNull(room)
        assertEquals("507f1f77bcf86cd799439014", room!!.pinnedMessage?.id)
    }
}
