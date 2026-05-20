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

enum class TeamPresenceSocketState {
    Disconnected,
    Connecting,
    Connected,
    Reconnecting,
}

data class TeamPresenceSocketEvent(
    val userId: String,
    val presenceStatus: String?,
    val lastPresenceAt: String?,
)

class TeamPresenceSocketManager {
    private var socket: Socket? = null
    private var subscribedTeamId: String? = null
    private var lastBaseUrl: String? = null
    private var tokenProvider: (() -> String?)? = null
    private val presenceListeners = CopyOnWriteArrayList<(TeamPresenceSocketEvent) -> Unit>()
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
        openSocket(base, token, tid)
    }

    private val _connectionState = MutableStateFlow(TeamPresenceSocketState.Disconnected)
    val connectionState: StateFlow<TeamPresenceSocketState> = _connectionState.asStateFlow()

    fun addPresenceListener(listener: (TeamPresenceSocketEvent) -> Unit) {
        if (!presenceListeners.contains(listener)) {
            presenceListeners.add(listener)
        }
    }

    fun removePresenceListener(listener: (TeamPresenceSocketEvent) -> Unit) {
        presenceListeners.remove(listener)
    }

    fun clearListeners() {
        presenceListeners.clear()
    }

    fun connect(
        baseUrl: String,
        teamId: String,
        tokenProvider: () -> String?,
    ) {
        intentionalDisconnect = false
        cancelReconnect()
        lastBaseUrl = baseUrl.trimEnd('/')
        this.tokenProvider = tokenProvider
        val token = tokenProvider() ?: run {
            emitState(TeamPresenceSocketState.Disconnected)
            return
        }
        if (socket?.connected() == true && subscribedTeamId == teamId) {
            return
        }
        subscribedTeamId = teamId
        openSocket(lastBaseUrl!!, token, teamId)
    }

    fun reconnectWithFreshToken() {
        val tid = subscribedTeamId ?: return
        val base = lastBaseUrl ?: return
        val provider = tokenProvider ?: return
        val token = provider() ?: return
        intentionalDisconnect = false
        cancelReconnect()
        openSocket(base, token, tid)
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

    private fun scheduleReconnect() {
        if (intentionalDisconnect || reconnectScheduled) return
        reconnectScheduled = true
        emitState(TeamPresenceSocketState.Reconnecting)
        val attempt = reconnectAttempt++
        val delayMs = min(30_000L, (1L shl min(attempt, 5)) * 1000L)
        mainHandler.postDelayed(reconnectRunnable, delayMs)
    }

    private fun emitState(state: TeamPresenceSocketState) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            _connectionState.value = state
        } else {
            mainHandler.post { _connectionState.value = state }
        }
    }

    private fun openSocket(baseUrl: String, accessToken: String, teamId: String) {
        emitState(TeamPresenceSocketState.Connecting)
        disconnectSocketOnly()
        try {
            val options = IO.Options.builder()
                .setPath("/socket.io/")
                .setAuth(mapOf("token" to accessToken))
                .build()
            socket = IO.socket("$baseUrl/team-presence", options).apply {
                on(Socket.EVENT_CONNECT) {
                    reconnectAttempt = 0
                    emitState(TeamPresenceSocketState.Connected)
                    emit("team:join", JSONObject().put("teamId", teamId))
                }
                on(Socket.EVENT_DISCONNECT) {
                    if (!intentionalDisconnect) {
                        scheduleReconnect()
                    } else {
                        emitState(TeamPresenceSocketState.Disconnected)
                    }
                }
                on(Socket.EVENT_CONNECT_ERROR) {
                    if (!intentionalDisconnect) {
                        scheduleReconnect()
                    }
                }
                on("team:presence") { args ->
                    val payload = args.firstOrNull() as? JSONObject ?: return@on
                    val event = payload.toPresenceEvent() ?: return@on
                    mainHandler.post {
                        presenceListeners.forEach { it(event) }
                    }
                }
                connect()
            }
        } catch (e: Exception) {
            Log.w(TAG, "team-presence socket open failed", e)
            scheduleReconnect()
        }
    }

    private fun disconnectSocketOnly() {
        socket?.off()
        socket?.disconnect()
        socket = null
    }

    private fun disconnectSocket() {
        disconnectSocketOnly()
        subscribedTeamId = null
        emitState(TeamPresenceSocketState.Disconnected)
    }

    private fun JSONObject.toPresenceEvent(): TeamPresenceSocketEvent? {
        val userId = optString("userId", "").trim()
        if (userId.isBlank()) return null
        val status = optString("presenceStatus", "").trim()
        val lastAt = optString("lastPresenceAt", "").trim()
        return TeamPresenceSocketEvent(
            userId = userId,
            presenceStatus = status.takeIf { it.isNotEmpty() },
            lastPresenceAt = lastAt.takeIf { it.isNotEmpty() },
        )
    }

    companion object {
        private const val TAG = "TeamPresenceSocket"
    }
}
