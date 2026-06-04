package com.lastasylum.alliance.data.teams

import com.lastasylum.alliance.data.chat.ChatReaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONArray
import org.json.JSONObject

class TeamForumSocketReactionParseTest {

    @Test
    fun forumReactionEvent_parsesCompactPayload() {
        val payload = JSONObject()
            .put("teamId", "team1")
            .put("topicId", "topic1")
            .put("messageId", "msg1")
            .put(
                "reactions",
                JSONArray()
                    .put(
                        JSONObject()
                            .put("emoji", "\uD83D\uDC4D")
                            .put("count", 2)
                            .put(
                                "userIds",
                                JSONArray().put("u1").put("u2"),
                            ),
                    ),
            )
        val event = parseForumReactionEventForTest(payload)
        assertEquals("team1", event.teamId)
        assertEquals("topic1", event.topicId)
        assertEquals("msg1", event.messageId)
        assertEquals(1, event.reactions.size)
        assertEquals(2, event.reactions[0].count)
    }

    private fun parseForumReactionEventForTest(json: JSONObject): TeamForumMessageReactionEvent {
        val reactions = json.optJSONArray("reactions")?.let { arr ->
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    add(
                        ChatReaction(
                            emoji = o.getString("emoji"),
                            count = o.optInt("count", 0),
                            reactedByMe = false,
                        ),
                    )
                }
            }
        } ?: emptyList()
        return TeamForumMessageReactionEvent(
            teamId = json.getString("teamId"),
            topicId = json.getString("topicId"),
            messageId = json.getString("messageId"),
            reactions = reactions,
        )
    }
}
