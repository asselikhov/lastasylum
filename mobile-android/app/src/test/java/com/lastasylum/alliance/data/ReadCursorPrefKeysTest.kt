package com.lastasylum.alliance.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadCursorPrefKeysTest {
    private val roomId = "507f1f77bcf86cd799439011"
    private val userId = "507f191e810c19729de860ea"
    private val teamId = "507f1f77bcf86cd799439012"
    private val topicId = "507f1f77bcf86cd799439013"

    @Test
    fun isLegacyChatReadKey_detectsUnscopedRoomKey() {
        assertTrue(ReadCursorPrefKeys.isLegacyChatReadKey("last_read_msg_$roomId"))
    }

    @Test
    fun isLegacyChatReadKey_rejectsUserScopedKey() {
        assertFalse(
            ReadCursorPrefKeys.isLegacyChatReadKey(
                ReadCursorPrefKeys.chatReadKey(userId, roomId),
            ),
        )
    }

    @Test
    fun isLegacyForumReadKey_detectsTeamTopicKey() {
        assertTrue(ReadCursorPrefKeys.isLegacyForumReadKey("forum_last_read_$teamId:$topicId"))
    }

    @Test
    fun isLegacyForumReadKey_rejectsUserScopedKey() {
        assertFalse(
            ReadCursorPrefKeys.isLegacyForumReadKey(
                ReadCursorPrefKeys.forumReadKey(userId, teamId, topicId),
            ),
        )
    }

    @Test
    fun parseLegacyForumReadKey_returnsTeamAndTopic() {
        assertEquals(
            teamId to topicId,
            ReadCursorPrefKeys.parseLegacyForumReadKey("forum_last_read_$teamId:$topicId"),
        )
    }

    @Test
    fun parseLegacyForumReadKey_returnsNullForScopedKey() {
        assertNull(
            ReadCursorPrefKeys.parseLegacyForumReadKey(
                ReadCursorPrefKeys.forumReadKey(userId, teamId, topicId),
            ),
        )
    }

    @Test
    fun mergeReadMessageIds_keepsNewerObjectId() {
        assertEquals(
            "000000000000000000000080",
            ReadCursorPrefKeys.mergeReadMessageIds(
                existing = "000000000000000000000040",
                incoming = "000000000000000000000080",
            ),
        )
    }
}
