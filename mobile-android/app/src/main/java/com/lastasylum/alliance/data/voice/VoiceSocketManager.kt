package com.lastasylum.alliance.data.voice

import android.os.Handler
import android.os.Looper
import android.util.Log
import io.socket.client.IO
import io.socket.client.Socket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.min
import org.json.JSONObject

class VoiceSocketManager {
    private var socket: Socket? = null
    private var roomId: String? = null
    private var lastBaseUrl: String? = null
    private var tokenProvider: (() -> String?)? = null
    private val frameListeners = CopyOnWriteArrayList<(VoiceFrameEvent) -> Unit>()
    private val peerListeners = CopyOnWriteArrayList<(VoicePeerEvent) -> Unit>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var reconnectAttempt = 0
    private var intentionalDisconnect = false
    private var reconnectScheduled = false
    private var frameSeq = 0

    private val reconnectRunnable = Runnable {
        reconnectScheduled = false
        if (intentionalDisconnect) return@Runnable
        val base = lastBaseUrl ?: return@Runnable
        val rid = roomId ?: return@Runnable
        val token = tokenProvider?.invoke() ?: return@Runnable
        openSocket(base, token, rid)
    }

    fun addFrameListener(listener: (VoiceFrameEvent) -> Unit) {
        if (!frameListeners.contains(listener)) frameListeners.add(listener)
    }

    fun removeFrameListener(listener: (VoiceFrameEvent) -> Unit) {
        frameListeners.remove(listener)
    }

    fun addPeerListener(listener: (VoicePeerEvent) -> Unit) {
        if (!peerListeners.contains(listener)) peerListeners.add(listener)
    }

    fun removePeerListener(listener: (VoicePeerEvent) -> Unit) {
        peerListeners.remove(listener)
    }

    fun connect(baseUrl: String, roomId: String, tokenProvider: () -> String?) {
        intentionalDisconnect = false
        cancelReconnect()
        lastBaseUrl = baseUrl.trimEnd('/')
        this.tokenProvider = tokenProvider
        val rid = roomId.trim()
        if (rid.isEmpty()) return
        val token = tokenProvider() ?: return
        this.roomId = rid
        openSocket(lastBaseUrl!!, token, rid)
    }

    fun reconnectWithFreshToken() {
        val rid = roomId ?: return
        val base = lastBaseUrl ?: return
        val token = tokenProvider?.invoke() ?: return
        intentionalDisconnect = false
        cancelReconnect()
        openSocket(base, token, rid)
    }

    fun emitState(micOn: Boolean, soundOn: Boolean) {
        val rid = roomId ?: return
        socket?.emit(
            "voice:state",
            JSONObject()
                .put("roomId", rid)
                .put("micOn", micOn)
                .put("soundOn", soundOn),
        )
    }

    fun emitFrame(codec: String, payload: ByteArray) {
        if (roomId == null) return
        val codecByte = when (codec) {
            VoiceOpusCodec.CODEC_OPUS -> VoiceWire.CODEC_OPUS
            else -> return
        }
        val packet = VoiceWire.packUpstream(frameSeq++, codecByte, payload)
        socket?.emit("voice:frame", packet)
    }

    fun disconnect() {
        intentionalDisconnect = true
        cancelReconnect()
        val rid = roomId
        if (rid != null) {
            socket?.emit("voice:leave", JSONObject().put("roomId", rid))
        }
        disconnectSocket()
    }

    private fun cancelReconnect() {
        mainHandler.removeCallbacks(reconnectRunnable)
        reconnectScheduled = false
    }

    private fun scheduleReconnect() {
        if (intentionalDisconnect || reconnectScheduled) return
        reconnectScheduled = true
        val attempt = reconnectAttempt++
        val delayMs = min(30_000L, (1L shl min(attempt, 5)) * 1000L)
        mainHandler.postDelayed(reconnectRunnable, delayMs)
    }

    private fun openSocket(baseUrl: String, accessToken: String, roomId: String) {
        disconnectSocketOnly()
        try {
            val options = IO.Options.builder()
                .setPath("/socket.io/")
                .setAuth(mapOf("token" to accessToken))
                .build()
            socket = IO.socket("$baseUrl/voice", options).apply {
                on(Socket.EVENT_CONNECT) {
                    reconnectAttempt = 0
                    emit("voice:join", JSONObject().put("roomId", roomId))
                }
                on(Socket.EVENT_DISCONNECT) {
                    if (!intentionalDisconnect) scheduleReconnect()
                }
                on(Socket.EVENT_CONNECT_ERROR) {
                    if (!intentionalDisconnect) scheduleReconnect()
                }
                on("voice:joined") { args ->
                    val payload = args.firstOrNull() as? JSONObject ?: return@on
                    val peers = payload.optJSONArray("peers") ?: return@on
                    for (i in 0 until peers.length()) {
                        val peer = peers.optJSONObject(i) ?: continue
                        dispatchPeer(VoicePeerEvent.Joined(peer.toPeerState()))
                    }
                }
                on("voice:peer-joined") { args ->
                    val payload = args.firstOrNull() as? JSONObject ?: return@on
                    val peer = payload.optJSONObject("peer")?.toPeerState() ?: return@on
                    dispatchPeer(VoicePeerEvent.Joined(peer))
                }
                on("voice:peer-left") { args ->
                    val payload = args.firstOrNull() as? JSONObject ?: return@on
                    val userId = payload.optString("userId", "")
                    if (userId.isBlank()) return@on
                    dispatchPeer(VoicePeerEvent.Left(userId))
                }
                on("voice:peer-state") { args ->
                    val payload = args.firstOrNull() as? JSONObject ?: return@on
                    val peer = payload.optJSONObject("peer")?.toPeerState() ?: return@on
                    dispatchPeer(VoicePeerEvent.State(peer))
                }
                on("voice:frame") { args ->
                    val bytes = VoiceWire.asByteArray(args.firstOrNull()) ?: return@on
                    val event = VoiceWire.unpackDownstream(bytes) ?: return@on
                    frameListeners.forEach { l -> runCatching { l(event) } }
                }
                connect()
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Voice socket setup failed", e)
            disconnectSocketOnly()
            if (!intentionalDisconnect) scheduleReconnect()
        }
    }

    private fun dispatchPeer(event: VoicePeerEvent) {
        peerListeners.forEach { l -> runCatching { l(event) } }
    }

    private fun disconnectSocketOnly() {
        socket?.disconnect()
        socket?.off()
        socket = null
    }

    private fun disconnectSocket() {
        disconnectSocketOnly()
        roomId = null
        lastBaseUrl = null
        tokenProvider = null
        frameSeq = 0
    }

    private fun JSONObject.toPeerState(): VoicePeerState = VoicePeerState(
        userId = optString("userId", ""),
        username = optString("username", ""),
        micOn = optBoolean("micOn", false),
        soundOn = optBoolean("soundOn", false),
    )

    companion object {
        private const val TAG = "VoiceSocket"
    }
}

data class VoicePeerState(
    val userId: String,
    val username: String,
    val micOn: Boolean,
    val soundOn: Boolean,
)

sealed class VoicePeerEvent {
    data class Joined(val peer: VoicePeerState) : VoicePeerEvent()
    data class State(val peer: VoicePeerState) : VoicePeerEvent()
    data class Left(val userId: String) : VoicePeerEvent()
}

data class VoiceFrameEvent(
    val userId: String,
    val username: String,
    val codec: String,
    val payload: ByteArray,
    val seq: Int = 0,
)
