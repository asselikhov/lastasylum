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

class TeamForumSocketManager {
    private var socket: Socket? = null
    private var subscribedTeamId: String? = null
    private var subscribedTopicId: String? = null
    private var lastBaseUrl: String? = null
    private var tokenProvider: (() -> String?)? = null
    private val messageListeners = CopyOnWriteArrayList<(TeamForumMessageDto) -> Unit>()
    private val messageEditedListeners = CopyOnWriteArrayList<(TeamForumMessageDto) -> Unit>()
    private val messageDeletedListeners =
        CopyOnWriteArrayList<(TeamForumMessageDeletedEvent) -> Unit>()
    private val typingListeners = CopyOnWriteArrayList<(TeamForumTypingEvent) -> Unit>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var reconnectAttempt = 0
    private var intentionalDisconnect = false
    private var reconnectScheduled = false

    private val reconnectRunnable = Runnable {
        reconnectScheduled = false
        if (intentionalDisconnect) return@Runnable
        val base = lastBaseUrl ?: return@Runnable
        val tid = subscribedTeamId ?: return@Runnable
        val top = subscribedTopicId ?: return@Runnable
        val token = tokenProvider?.invoke() ?: return@Runnable
        openSocket(base, token, tid, top)
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

    fun clearListeners() {
        messageListeners.clear()
        messageEditedListeners.clear()
        messageDeletedListeners.clear()
        typingListeners.clear()
    }

    fun connect(
        baseUrl: String,
        teamId: String,
        topicId: String,
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
        if (socket?.connected() == true &&
            subscribedTeamId == teamId &&
            subscribedTopicId == topicId
        ) {
            return
        }
        subscribedTeamId = teamId
        subscribedTopicId = topicId
        openSocket(lastBaseUrl!!, token, teamId, topicId)
    }

    fun reconnectWithFreshToken() {
        val tid = subscribedTeamId ?: return
        val top = subscribedTopicId ?: return
        val base = lastBaseUrl ?: return
        val provider = tokenProvider ?: return
        val token = provider() ?: return
        intentionalDisconnect = false
        cancelReconnect()
        openSocket(base, token, tid, top)
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

    private fun openSocket(baseUrl: String, accessToken: String, teamId: String, topicId: String) {
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
                    emit(
                        "topic:join",
                        JSONObject().put("teamId", teamId).put("topicId", topicId),
                    )
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
                on("message:new") { args ->
                    val payload = args.firstOrNull() as? JSONObject ?: return@on
                    val msg = payload.toForumMessageDto() ?: return@on
                    if (msg.teamId != teamId || msg.topicId != topicId) return@on
                    dispatchMain {
                        messageListeners.forEach { l -> runCatching { l(msg) } }
                    }
                }
                on("message:edited") { args ->
                    val payload = args.firstOrNull() as? JSONObject ?: return@on
                    val msg = payload.toForumMessageDto() ?: return@on
                    if (msg.teamId != teamId || msg.topicId != topicId) return@on
                    dispatchMain {
                        messageEditedListeners.forEach { l -> runCatching { l(msg) } }
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
                    if (event.teamId != teamId || event.topicId != topicId) return@on
                    dispatchMain {
                        messageDeletedListeners.forEach { l -> runCatching { l(event) } }
                    }
                }
                on("user:typing") { args ->
                    val payload = args.firstOrNull() as? JSONObject ?: return@on
                    if (payload.optString("topicId") != topicId) return@on
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
        senderRole = optString("senderRole").takeIf { it.isNotBlank() } ?: "R1",
        senderTeamTag = optionalStringField("senderTeamTag"),
        text = optString("text"),
        replyToMessageId = optionalStringField("replyToMessageId"),
        replyTo = replyToObj?.let { obj ->
            val rid = obj.optString("id").takeIf { it.isNotBlank() } ?: return@let null
            com.lastasylum.alliance.data.teams.TeamForumReplyPreviewDto(
                id = rid,
                senderUsername = obj.optString("senderUsername"),
                senderRole = obj.optString("senderRole").takeIf { it.isNotBlank() } ?: "R1",
                senderTeamTag = obj.optionalStringField("senderTeamTag"),
                text = obj.optString("text"),
            )
        },
        editedAt = optionalStringField("editedAt"),
        deletedAt = optionalStringField("deletedAt"),
        deletedByUserId = optionalStringField("deletedByUserId"),
        imageRelativeUrl = optionalStringField("imageRelativeUrl"),
        imageRelativeUrls = urls,
        forwardedFrom = forwardedFromObj?.let { fwd ->
            val mid = fwd.optString("messageId").takeIf { it.isNotBlank() } ?: return@let null
            com.lastasylum.alliance.data.teams.TeamForumForwardedFromDto(
                messageId = mid,
                senderUserId = fwd.optString("senderUserId"),
                senderUsername = fwd.optString("senderUsername"),
                senderRole = fwd.optString("senderRole").takeIf { it.isNotBlank() } ?: "R1",
                senderTeamTag = fwd.optionalStringField("senderTeamTag"),
            )
        },
        createdAt = optString("createdAt"),
        updatedAt = optionalStringField("updatedAt").orEmpty()
            .ifBlank { optString("createdAt") },
    )
}
