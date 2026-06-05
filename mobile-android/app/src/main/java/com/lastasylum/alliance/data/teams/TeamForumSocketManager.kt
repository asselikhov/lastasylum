package com.lastasylum.alliance.data.teams

import android.os.Handler
import android.os.Looper
import android.util.Log
import io.socket.client.IO
import io.socket.client.Socket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.min
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.lastasylum.alliance.data.chat.PinnedMessagePreviewDto
import org.json.JSONObject

enum class TeamForumSocketState {
    Disconnected,
    Connecting,
    Connected,
    Reconnecting,
}

data class TeamForumTypingEvent(
    val teamId: String,
    val topicId: String,
    val userId: String,
    val username: String,
)

/** Peer read cursor advance in a forum topic (read receipts ✓✓). */
data class TeamForumTopicReadEvent(
    val teamId: String,
    val topicId: String,
    val userId: String,
    val messageId: String,
)

/** New message in any forum topic — for team inbox badges (overlay HUD, topic list). */
data class TeamForumTopicActivityEvent(
    val teamId: String,
    val topicId: String,
    val messageId: String,
    val senderUserId: String,
)

data class TeamForumMessageReactionEvent(
    val teamId: String,
    val topicId: String,
    val messageId: String,
    val reactions: List<com.lastasylum.alliance.data.chat.ChatReaction>,
)

class TeamForumSocketManager {
    private var socket: Socket? = null
    private var subscribedTeamId: String? = null
    private var subscribedTopicId: String? = null
    private var lastBaseUrl: String? = null
    private var tokenProvider: (() -> String?)? = null
    private val messageListeners = CopyOnWriteArrayList<(TeamForumMessageDto) -> Unit>()
    private val messageEditedListeners = CopyOnWriteArrayList<(TeamForumMessageDto) -> Unit>()
    private val messageReactionListeners =
        CopyOnWriteArrayList<(TeamForumMessageReactionEvent) -> Unit>()
    private val messageDeletedListeners =
        CopyOnWriteArrayList<(TeamForumMessageDeletedEvent) -> Unit>()
    private val typingListeners = CopyOnWriteArrayList<(TeamForumTypingEvent) -> Unit>()
    private val topicActivityListeners =
        CopyOnWriteArrayList<(TeamForumTopicActivityEvent) -> Unit>()
    private val topicPinChangedListeners =
        CopyOnWriteArrayList<(TeamForumTopicPinChangedEvent) -> Unit>()
    private val topicReadListeners =
        CopyOnWriteArrayList<(TeamForumTopicReadEvent) -> Unit>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var reconnectAttempt = 0
    private var intentionalDisconnect = false
    private var reconnectScheduled = false

    private val reconnectRunnable = Runnable {
        reconnectScheduled = false
        if (intentionalDisconnect) return@Runnable
        val base = lastBaseUrl ?: return@Runnable
        val tid = subscribedTeamId ?: return@Runnable
        val token = tokenProvider?.invoke() ?: return@Runnable
        openSocket(base, token, tid, subscribedTopicId)
    }

    private val _connectionState = MutableStateFlow(TeamForumSocketState.Disconnected)
    val connectionState: StateFlow<TeamForumSocketState> = _connectionState.asStateFlow()

    fun addMessageListener(listener: (TeamForumMessageDto) -> Unit) {
        if (!messageListeners.contains(listener)) {
            messageListeners.add(listener)
        }
    }

    fun removeMessageListener(listener: (TeamForumMessageDto) -> Unit) {
        messageListeners.remove(listener)
    }

    fun addMessageEditedListener(listener: (TeamForumMessageDto) -> Unit) {
        if (!messageEditedListeners.contains(listener)) {
            messageEditedListeners.add(listener)
        }
    }

    fun removeMessageEditedListener(listener: (TeamForumMessageDto) -> Unit) {
        messageEditedListeners.remove(listener)
    }

    fun addMessageReactionListener(listener: (TeamForumMessageReactionEvent) -> Unit) {
        if (!messageReactionListeners.contains(listener)) {
            messageReactionListeners.add(listener)
        }
    }

    fun removeMessageReactionListener(listener: (TeamForumMessageReactionEvent) -> Unit) {
        messageReactionListeners.remove(listener)
    }

    fun addMessageDeletedListener(listener: (TeamForumMessageDeletedEvent) -> Unit) {
        if (!messageDeletedListeners.contains(listener)) {
            messageDeletedListeners.add(listener)
        }
    }

    fun removeMessageDeletedListener(listener: (TeamForumMessageDeletedEvent) -> Unit) {
        messageDeletedListeners.remove(listener)
    }

    fun addTypingListener(listener: (TeamForumTypingEvent) -> Unit) {
        if (!typingListeners.contains(listener)) {
            typingListeners.add(listener)
        }
    }

    fun removeTypingListener(listener: (TeamForumTypingEvent) -> Unit) {
        typingListeners.remove(listener)
    }

    fun addTopicActivityListener(listener: (TeamForumTopicActivityEvent) -> Unit) {
        if (!topicActivityListeners.contains(listener)) {
            topicActivityListeners.add(listener)
        }
    }

    fun removeTopicActivityListener(listener: (TeamForumTopicActivityEvent) -> Unit) {
        topicActivityListeners.remove(listener)
    }

    fun addTopicPinChangedListener(listener: (TeamForumTopicPinChangedEvent) -> Unit) {
        if (!topicPinChangedListeners.contains(listener)) {
            topicPinChangedListeners.add(listener)
        }
    }

    fun removeTopicPinChangedListener(listener: (TeamForumTopicPinChangedEvent) -> Unit) {
        topicPinChangedListeners.remove(listener)
    }

    fun addTopicReadListener(listener: (TeamForumTopicReadEvent) -> Unit) {
        if (!topicReadListeners.contains(listener)) {
            topicReadListeners.add(listener)
        }
    }

    fun removeTopicReadListener(listener: (TeamForumTopicReadEvent) -> Unit) {
        topicReadListeners.remove(listener)
    }

    fun clearListeners() {
        messageListeners.clear()
        messageEditedListeners.clear()
        messageReactionListeners.clear()
        messageDeletedListeners.clear()
        typingListeners.clear()
        topicActivityListeners.clear()
        topicPinChangedListeners.clear()
        topicReadListeners.clear()
    }

    /** Team-wide inbox (overlay HUD / topic list) without subscribing to a single topic room. */
    fun connectTeamInbox(
        baseUrl: String,
        teamId: String,
        tokenProvider: () -> String?,
    ) {
        intentionalDisconnect = false
        cancelReconnect()
        lastBaseUrl = baseUrl.trimEnd('/')
        this.tokenProvider = tokenProvider
        val token = tokenProvider() ?: run {
            emitState(TeamForumSocketState.Disconnected)
            return
        }
        if (socket?.connected() == true && subscribedTeamId == teamId) {
            leaveTopic()
            subscribedTopicId = null
            emitTeamAndTopicJoin(teamId, null)
            return
        }
        subscribedTeamId = teamId
        subscribedTopicId = null
        openSocket(lastBaseUrl!!, token, teamId, null)
    }

    fun connect(
        baseUrl: String,
        teamId: String,
        topicId: String?,
        tokenProvider: () -> String?,
    ) {
        intentionalDisconnect = false
        cancelReconnect()
        lastBaseUrl = baseUrl.trimEnd('/')
        this.tokenProvider = tokenProvider
        val token = tokenProvider() ?: run {
            emitState(TeamForumSocketState.Disconnected)
            return
        }
        val topicKey = topicId?.trim()?.takeIf { it.isNotEmpty() }
        if (socket?.connected() == true && subscribedTeamId == teamId) {
            if (topicKey == null) {
                leaveTopic()
                subscribedTopicId = null
                emitTeamAndTopicJoin(teamId, null)
                return
            }
            if (subscribedTopicId == topicKey) {
                emitTeamAndTopicJoin(teamId, topicKey)
                return
            }
            joinTopic(topicKey)
            return
        }
        subscribedTeamId = teamId
        subscribedTopicId = topicKey
        openSocket(lastBaseUrl!!, token, teamId, topicKey)
    }

    fun joinTopic(topicId: String) {
        val teamId = subscribedTeamId?.trim().orEmpty()
        val topic = topicId.trim()
        if (teamId.isEmpty() || topic.isEmpty()) return
        val previous = subscribedTopicId?.trim()?.takeIf { it.isNotEmpty() }
        if (previous == topic) return
        previous?.let { prev ->
            socket?.emit(
                "topic:leave",
                JSONObject().put("teamId", teamId).put("topicId", prev),
            )
        }
        subscribedTopicId = topic
        socket?.emit(
            "topic:join",
            JSONObject().put("teamId", teamId).put("topicId", topic),
        )
    }

    fun leaveTopic() {
        val teamId = subscribedTeamId?.trim().orEmpty()
        val topic = subscribedTopicId?.trim()?.takeIf { it.isNotEmpty() } ?: return
        socket?.emit(
            "topic:leave",
            JSONObject().put("teamId", teamId).put("topicId", topic),
        )
        subscribedTopicId = null
    }

    fun reconnectWithFreshToken() {
        val tid = subscribedTeamId ?: return
        val base = lastBaseUrl ?: return
        val provider = tokenProvider ?: return
        val token = provider() ?: return
        intentionalDisconnect = false
        cancelReconnect()
        openSocket(base, token, tid, subscribedTopicId)
    }

    fun emitTyping() {
        val tid = subscribedTeamId ?: return
        val top = subscribedTopicId ?: return
        socket?.emit(
            "typing",
            JSONObject().put("teamId", tid).put("topicId", top),
        )
    }

    fun disconnect() {
        intentionalDisconnect = true
        cancelReconnect()
        disconnectSocket()
    }

    private fun cancelReconnect() {
        mainHandler.removeCallbacks(reconnectRunnable)
        reconnectScheduled = false
    }

    private fun dispatchMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    private fun scheduleReconnect() {
        if (intentionalDisconnect || reconnectScheduled) return
        reconnectScheduled = true
        emitState(TeamForumSocketState.Reconnecting)
        val attempt = reconnectAttempt++
        val delayMs = min(30_000L, (1L shl min(attempt, 5)) * 1000L)
        mainHandler.postDelayed(reconnectRunnable, delayMs)
    }

    private fun emitState(state: TeamForumSocketState) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            _connectionState.value = state
        } else {
            mainHandler.post { _connectionState.value = state }
        }
    }

    private fun emitTeamAndTopicJoin(teamId: String, topicId: String?) {
        val s = socket ?: return
        if (!s.connected()) return
        s.emit("team:join", JSONObject().put("teamId", teamId))
        val top = topicId?.trim()?.takeIf { it.isNotEmpty() } ?: return
        s.emit(
            "topic:join",
            JSONObject().put("teamId", teamId).put("topicId", top),
        )
    }

    private fun openSocket(
        baseUrl: String,
        accessToken: String,
        teamId: String,
        topicId: String?,
    ) {
        emitState(TeamForumSocketState.Connecting)
        disconnectSocketOnly()

        try {
            val options = IO.Options.builder()
                .setPath("/socket.io/")
                .setAuth(mapOf("token" to accessToken))
                .build()
            socket = IO.socket("$baseUrl/team-forum", options).apply {
                on(Socket.EVENT_CONNECT) {
                    reconnectAttempt = 0
                    emitState(TeamForumSocketState.Connected)
                    emitTeamAndTopicJoin(teamId, topicId)
                }
                on(Socket.EVENT_DISCONNECT) {
                    if (!intentionalDisconnect) {
                        scheduleReconnect()
                    } else {
                        emitState(TeamForumSocketState.Disconnected)
                    }
                }
                on(Socket.EVENT_CONNECT_ERROR) {
                    if (!intentionalDisconnect) {
                        scheduleReconnect()
                    }
                }
                on("topic:activity") { args ->
                    val payload = args.firstOrNull() as? JSONObject ?: return@on
                    val ev = TeamForumTopicActivityEvent(
                        teamId = payload.optString("teamId"),
                        topicId = payload.optString("topicId"),
                        messageId = payload.optString("messageId"),
                        senderUserId = payload.optString("senderUserId"),
                    )
                    if (ev.teamId != teamId || ev.topicId.isBlank()) return@on
                    dispatchMain {
                        topicActivityListeners.forEach { l -> runCatching { l(ev) } }
                    }
                }
                on("message:new") { args ->
                    val payload = args.firstOrNull() as? JSONObject ?: return@on
                    val msg = payload.toForumMessageDto() ?: return@on
                    val activeTopic = topicId?.trim()?.takeIf { it.isNotEmpty() } ?: return@on
                    if (msg.teamId != teamId || msg.topicId != activeTopic) return@on
                    dispatchMain {
                        messageListeners.forEach { l -> runCatching { l(msg) } }
                    }
                }
                on("message:edited") { args ->
                    val payload = args.firstOrNull() as? JSONObject ?: return@on
                    val msg = payload.toForumMessageDto() ?: return@on
                    val activeTopic = topicId?.trim()?.takeIf { it.isNotEmpty() } ?: return@on
                    if (msg.teamId != teamId || msg.topicId != activeTopic) return@on
                    dispatchMain {
                        messageEditedListeners.forEach { l -> runCatching { l(msg) } }
                    }
                }
                on("message:reaction") { args ->
                    val payload = args.firstOrNull() as? JSONObject ?: return@on
                    val event = payload.toForumMessageReactionEvent() ?: return@on
                    val activeTopic = topicId?.trim()?.takeIf { it.isNotEmpty() } ?: return@on
                    if (event.teamId != teamId || event.topicId != activeTopic) return@on
                    dispatchMain {
                        messageReactionListeners.forEach { l -> runCatching { l(event) } }
                    }
                }
                on("message:deleted") { args ->
                    val payload = args.firstOrNull() as? JSONObject ?: return@on
                    val event = TeamForumMessageDeletedEvent(
                        teamId = payload.optString("teamId"),
                        topicId = payload.optString("topicId"),
                        messageId = payload.optString("messageId"),
                        deletedAt = payload.optString("deletedAt").takeIf { it.isNotBlank() },
                        deletedByUserId = payload.optString("deletedByUserId")
                            .takeIf { it.isNotBlank() },
                    )
                    val activeTopic = topicId?.trim()?.takeIf { it.isNotEmpty() } ?: return@on
                    if (event.teamId != teamId || event.topicId != activeTopic) return@on
                    dispatchMain {
                        messageDeletedListeners.forEach { l -> runCatching { l(event) } }
                    }
                }
                on("topic:read") { args ->
                    val payload = args.firstOrNull() as? JSONObject ?: return@on
                    val ev = TeamForumTopicReadEvent(
                        teamId = payload.optString("teamId"),
                        topicId = payload.optString("topicId"),
                        userId = payload.optString("userId"),
                        messageId = payload.optString("messageId"),
                    )
                    val activeTopic = topicId?.trim()?.takeIf { it.isNotEmpty() } ?: return@on
                    if (ev.teamId != teamId || ev.topicId != activeTopic) return@on
                    dispatchMain {
                        topicReadListeners.forEach { l -> runCatching { l(ev) } }
                    }
                }
                on("topic:pin-changed") { args ->
                    val payload = args.firstOrNull() as? JSONObject ?: return@on
                    val event = payload.toTeamForumTopicPinChangedEvent() ?: return@on
                    if (event.teamId != teamId) return@on
                    dispatchMain {
                        topicPinChangedListeners.forEach { l -> runCatching { l(event) } }
                    }
                }
                on("user:typing") { args ->
                    val payload = args.firstOrNull() as? JSONObject ?: return@on
                    val activeTopic = topicId?.trim()?.takeIf { it.isNotEmpty() } ?: return@on
                    if (payload.optString("topicId") != activeTopic) return@on
                    val ev = TeamForumTypingEvent(
                        teamId = payload.optString("teamId"),
                        topicId = payload.optString("topicId"),
                        userId = payload.optString("userId"),
                        username = payload.optString("username"),
                    )
                    if (ev.userId.isBlank()) return@on
                    dispatchMain {
                        typingListeners.forEach { l -> runCatching { l(ev) } }
                    }
                }
                connect()
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Forum socket setup failed: $baseUrl/team-forum", e)
            disconnectSocketOnly()
            emitState(TeamForumSocketState.Disconnected)
            if (!intentionalDisconnect) {
                scheduleReconnect()
            }
        }
    }

    private fun disconnectSocketOnly() {
        socket?.disconnect()
        socket?.off()
        socket = null
    }

    private fun disconnectSocket() {
        disconnectSocketOnly()
        subscribedTeamId = null
        subscribedTopicId = null
        lastBaseUrl = null
        tokenProvider = null
        emitState(TeamForumSocketState.Disconnected)
    }

    private companion object {
        private const val TAG = "TeamForumSocket"
    }
}

data class TeamForumMessageDeletedEvent(
    val teamId: String,
    val topicId: String,
    val messageId: String,
    val deletedAt: String?,
    val deletedByUserId: String?,
)

/**
 * org.json turns JSON `null` into [JSONObject.NULL]; [optString] then becomes the literal `"null"`.
 * Treat missing / JSON-null / that placeholder as absent so new messages are not "deleted".
 */
private fun JSONObject.optionalStringField(key: String): String? {
    if (!has(key) || isNull(key)) return null
    val s = optString(key)
    return s.takeIf { it.isNotBlank() && !s.equals("null", ignoreCase = true) }
}

private fun JSONObject.toForumMessageDto(): TeamForumMessageDto? {
    val id = optString("id").takeIf { it.isNotBlank() } ?: return null
    val replyToObj = optJSONObject("replyTo")
    val forwardedFromObj = optJSONObject("forwardedFrom")
    val urlsArr = optJSONArray("imageRelativeUrls")
    val urls = buildList<String> {
        if (urlsArr != null) {
            for (i in 0 until urlsArr.length()) {
                val u = urlsArr.optString(i).trim()
                if (u.isNotBlank() && !u.equals("null", ignoreCase = true)) add(u)
            }
        }
    }
    return TeamForumMessageDto(
        id = id,
        topicId = optString("topicId"),
        teamId = optString("teamId"),
        senderUserId = optString("senderUserId"),
        senderUsername = optString("senderUsername"),
        senderTelegramUsername = optionalStringField("senderTelegramUsername"),
        senderRole = optString("senderRole").takeIf { it.isNotBlank() } ?: "R1",
        senderTeamTag = optionalStringField("senderTeamTag"),
        senderServerNumber = optInt("senderServerNumber").takeIf { it > 0 },
        text = optString("text"),
        replyToMessageId = optionalStringField("replyToMessageId"),
        replyTo = replyToObj?.let { obj ->
            val rid = obj.optString("id").takeIf { it.isNotBlank() } ?: return@let null
            com.lastasylum.alliance.data.teams.TeamForumReplyPreviewDto(
                id = rid,
                senderUsername = obj.optString("senderUsername"),
                senderRole = obj.optString("senderRole").takeIf { it.isNotBlank() } ?: "R1",
                senderTeamTag = obj.optionalStringField("senderTeamTag"),
                senderServerNumber = obj.optInt("senderServerNumber").takeIf { it > 0 },
                text = obj.optString("text"),
            )
        },
        editedAt = optionalStringField("editedAt"),
        deletedAt = optionalStringField("deletedAt"),
        deletedByUserId = optionalStringField("deletedByUserId"),
        imageRelativeUrl = optionalStringField("imageRelativeUrl"),
        imageRelativeUrls = urls,
        fileRelativeUrl = optionalStringField("fileRelativeUrl"),
        fileFilename = optionalStringField("fileFilename"),
        forwardedFrom = forwardedFromObj?.let { fwd ->
            val mid = fwd.optString("messageId").takeIf { it.isNotBlank() } ?: return@let null
            com.lastasylum.alliance.data.teams.TeamForumForwardedFromDto(
                messageId = mid,
                senderUserId = fwd.optString("senderUserId"),
                senderUsername = fwd.optString("senderUsername"),
                senderRole = fwd.optString("senderRole").takeIf { it.isNotBlank() } ?: "R1",
                senderTeamTag = fwd.optionalStringField("senderTeamTag"),
                senderServerNumber = fwd.optInt("senderServerNumber").takeIf { it > 0 },
            )
        },
        reactions = optJSONArray("reactions").toForumReactions(),
        createdAt = optString("createdAt"),
        updatedAt = optionalStringField("updatedAt").orEmpty()
            .ifBlank { optString("createdAt") },
    )
}

private fun JSONObject.toForumMessageReactionEvent(): TeamForumMessageReactionEvent? {
    val messageId = optString("messageId").takeIf { it.isNotBlank() } ?: return null
    val teamId = optString("teamId").takeIf { it.isNotBlank() } ?: return null
    val topicId = optString("topicId").takeIf { it.isNotBlank() } ?: return null
    return TeamForumMessageReactionEvent(
        teamId = teamId,
        topicId = topicId,
        messageId = messageId,
        reactions = optJSONArray("reactions").toForumReactions(),
    )
}

private fun org.json.JSONArray?.toForumReactions(): List<com.lastasylum.alliance.data.chat.ChatReaction> {
    if (this == null) return emptyList()
    val out = ArrayList<com.lastasylum.alliance.data.chat.ChatReaction>(length())
    for (i in 0 until length()) {
        val o = optJSONObject(i) ?: continue
        val emoji = o.optString("emoji").takeIf { it.isNotBlank() } ?: continue
        val count = when {
            o.has("count") -> o.optInt("count", 0)
            o.has("userIds") -> o.optJSONArray("userIds")?.length() ?: 0
            else -> 0
        }
        out.add(
            com.lastasylum.alliance.data.chat.ChatReaction(
                emoji = emoji,
                count = count.coerceAtLeast(0),
                reactedByMe = o.optBoolean("reactedByMe", false),
            ),
        )
    }
    return out
}

private fun JSONObject.toPinnedMessagePreviewDto(): PinnedMessagePreviewDto? {
    val id = optString("id").ifBlank { optString("_id") }
    if (id.isBlank()) return null
    return PinnedMessagePreviewDto(
        id = id,
        text = optString("text"),
        senderUsername = optString("senderUsername"),
        senderTeamTag = optionalStringField("senderTeamTag"),
        senderServerNumber = optInt("senderServerNumber").takeIf { it > 0 },
        createdAt = optString("createdAt"),
        editedAt = optionalStringField("editedAt"),
        hasImage = optBoolean("hasImage", false),
        isSticker = optBoolean("isSticker", false),
        imageThumbnailUrl = optString("imageThumbnailUrl").takeIf { it.isNotBlank() },
        pinnedByUsername = optString("pinnedByUsername").takeIf { it.isNotBlank() },
    )
}

private fun JSONObject.parsePinnedMessagesArray(): List<PinnedMessagePreviewDto> {
    val arr = optJSONArray("pinnedMessages") ?: return emptyList()
    val out = ArrayList<PinnedMessagePreviewDto>(arr.length())
    for (i in 0 until arr.length()) {
        arr.optJSONObject(i)?.toPinnedMessagePreviewDto()?.let { out.add(it) }
    }
    return out
}

private fun JSONObject.toTeamForumTopicPinChangedEvent(): TeamForumTopicPinChangedEvent? {
    val teamId = optString("teamId").ifBlank { return null }
    val topicId = optString("topicId").ifBlank { return null }
    return TeamForumTopicPinChangedEvent(
        teamId = teamId,
        topicId = topicId,
        pinnedMessageId = optionalStringField("pinnedMessageId"),
        pinnedAt = optionalStringField("pinnedAt"),
        pinnedByUserId = optionalStringField("pinnedByUserId"),
        pinnedMessage = optJSONObject("pinnedMessage")?.toPinnedMessagePreviewDto(),
        pinnedMessages = parsePinnedMessagesArray(),
    )
}
