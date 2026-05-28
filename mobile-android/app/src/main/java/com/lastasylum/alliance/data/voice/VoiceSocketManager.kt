package com.lastasylum.alliance.data.voice

import android.os.Handler
import android.os.Looper
import android.util.Log
import io.socket.client.Ack
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
    @Volatile
    private var voiceJoined = false
    private val joinedCallbacks = CopyOnWriteArrayList<() -> Unit>()
    private val joinFailedCallbacks = CopyOnWriteArrayList<(String) -> Unit>()
    private val stateAckCallbacks = CopyOnWriteArrayList<() -> Unit>()
    private var joinTimeoutRunnable: Runnable? = null
    @Volatile
    private var joinFailedListener: ((String) -> Unit)? = null
    private val pendingUpstreamFrames = ArrayDeque<ByteArray>(8)
    /** Last toggles — included in voice:join on (re)connect so the server never stays at micOff/soundOff defaults. */
    private var lastMicOn: Boolean = false
    private var lastSoundOn: Boolean = false

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

    fun isVoiceJoined(): Boolean = voiceJoined

    /** Runs after server ack [voice:joined] (socket room is ready for state + frames). */
    fun whenJoined(block: () -> Unit) {
        if (voiceJoined) {
            mainHandler.post(block)
            return
        }
        joinedCallbacks.add(block)
        scheduleJoinTimeout()
    }

    fun setJoinFailedListener(listener: ((String) -> Unit)?) {
        joinFailedListener = listener
    }

    fun addJoinFailedListener(listener: (String) -> Unit) {
        if (!joinFailedCallbacks.contains(listener)) joinFailedCallbacks.add(listener)
    }

    fun removeJoinFailedListener(listener: (String) -> Unit) {
        joinFailedCallbacks.remove(listener)
    }

    fun connect(
        baseUrl: String,
        roomId: String,
        tokenProvider: () -> String?,
        initialMicOn: Boolean = false,
        initialSoundOn: Boolean = false,
    ) {
        intentionalDisconnect = false
        cancelReconnect()
        lastBaseUrl = baseUrl.trimEnd('/')
        this.tokenProvider = tokenProvider
        lastMicOn = initialMicOn
        lastSoundOn = initialSoundOn
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

    fun emitState(micOn: Boolean, soundOn: Boolean, onAck: (() -> Unit)? = null) {
        lastMicOn = micOn
        lastSoundOn = soundOn
        val rid = roomId ?: return
        val payload = JSONObject()
            .put("roomId", rid)
            .put("micOn", micOn)
            .put("soundOn", soundOn)
        val sock = socket ?: return
        if (onAck != null) {
            sock.emit(
                "voice:state",
                payload,
                Ack { args ->
                    if (!isVoiceErrorPayload(args)) {
                        extractPeerFromStateAck(args)?.let { peer ->
                            dispatchPeer(VoicePeerEvent.State(peer))
                        }
                        mainHandler.post { onAck() }
                    }
                },
            )
        } else {
            sock.emit("voice:state", payload)
        }
    }

    fun emitFrame(codec: String, payload: ByteArray) {
        if (roomId == null) return
        val codecByte = when (codec) {
            VoiceOpusCodec.CODEC_OPUS -> VoiceWire.CODEC_OPUS
            VoiceOpusCodec.CODEC_OPUS_CONFIG -> VoiceWire.CODEC_OPUS_CONFIG
            else -> return
        }
        val packet = VoiceWire.packUpstream(frameSeq++, codecByte, payload)
        if (!voiceJoined) {
            synchronized(pendingUpstreamFrames) {
                while (pendingUpstreamFrames.size >= 8) pendingUpstreamFrames.removeFirst()
                pendingUpstreamFrames.addLast(packet)
            }
            return
        }
        flushPendingUpstreamFrames()
        socket?.emit("voice:frame", packet)
    }

    private fun flushPendingUpstreamFrames() {
        val pending = synchronized(pendingUpstreamFrames) {
            if (pendingUpstreamFrames.isEmpty()) return
            pendingUpstreamFrames.toList().also { pendingUpstreamFrames.clear() }
        }
        val sock = socket ?: return
        for (packet in pending) {
            sock.emit("voice:frame", packet)
        }
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
                    voiceJoined = false
                    emitVoiceJoin(this, roomId)
                }
                on(Socket.EVENT_DISCONNECT) {
                    voiceJoined = false
                    if (!intentionalDisconnect) scheduleReconnect()
                }
                on(Socket.EVENT_CONNECT_ERROR) { args ->
                    Log.w(TAG, "Voice connect error: ${args.firstOrNull()}")
                    if (!intentionalDisconnect) scheduleReconnect()
                }
                on("exception") { args ->
                    Log.w(TAG, "Voice server exception: ${args.firstOrNull()}")
                }
                on("voice:joined") { args ->
                    onVoiceJoinedPayload(args)
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
                on("voice:state-ack") { args ->
                    extractPeerFromStateAck(args)?.let { peer ->
                        dispatchPeer(VoicePeerEvent.State(peer))
                    }
                    mainHandler.post {
                        stateAckCallbacks.forEach { callback ->
                            runCatching { callback() }
                        }
                        stateAckCallbacks.clear()
                    }
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

    private fun emitVoiceJoin(sock: Socket, roomId: String) {
        val payload = JSONObject()
            .put("roomId", roomId)
            .put("micOn", lastMicOn)
            .put("soundOn", lastSoundOn)
        sock.emit(
            "voice:join",
            payload,
            Ack { args ->
                mainHandler.post { onVoiceJoinedPayload(args) }
            },
        )
    }

    private fun onVoiceJoinedPayload(args: Array<out Any>) {
        if (isVoiceErrorPayload(args)) {
            failJoin(extractVoiceErrorMessage(args) ?: "Voice join failed")
            return
        }
        val payload = extractVoiceJoinedData(args)
        if (payload == null || payload.optString("roomId", "").trim().isBlank()) {
            failJoin("Invalid voice join response")
            return
        }
        payload.optString("roomId", "").trim().takeIf { it.isNotEmpty() }?.let { resolved ->
            roomId = resolved
        }
        if (!voiceJoined) {
            cancelJoinTimeout()
            voiceJoined = true
            flushPendingUpstreamFrames()
            emitState(lastMicOn, lastSoundOn)
            mainHandler.post {
                joinedCallbacks.forEach { callback ->
                    runCatching { callback() }
                }
                joinedCallbacks.clear()
            }
        }
        val peers = payload.optJSONArray("peers") ?: return
        for (i in 0 until peers.length()) {
            val peer = peers.optJSONObject(i) ?: continue
            dispatchPeer(VoicePeerEvent.Joined(peer.toPeerState()))
        }
    }

    private fun scheduleJoinTimeout() {
        cancelJoinTimeout()
        joinTimeoutRunnable = Runnable {
            if (!voiceJoined) {
                failJoin("Voice join timed out")
            }
        }
        mainHandler.postDelayed(joinTimeoutRunnable!!, JOIN_TIMEOUT_MS)
    }

    private fun cancelJoinTimeout() {
        joinTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        joinTimeoutRunnable = null
    }

    private fun failJoin(reason: String) {
        if (voiceJoined) return
        cancelJoinTimeout()
        Log.w(TAG, reason)
        joinedCallbacks.clear()
        joinFailedListener?.let { mainHandler.post { it(reason) } }
        joinFailedCallbacks.forEach { listener ->
            mainHandler.post { runCatching { listener(reason) } }
        }
    }

    private fun isVoiceErrorPayload(args: Array<out Any>): Boolean {
        val first = args.firstOrNull() ?: return false
        if (first is JSONObject) {
            if (first.has("statusCode") || first.has("message")) return true
            if (first.optString("event") == "exception") return true
        }
        return first is String && first.isNotBlank()
    }

    private fun extractVoiceErrorMessage(args: Array<out Any>): String? {
        val first = args.firstOrNull() ?: return null
        when (first) {
            is JSONObject -> {
                first.optString("message").trim().takeIf { it.isNotEmpty() }?.let { return it }
                first.optString("error").trim().takeIf { it.isNotEmpty() }?.let { return it }
            }
            is String -> return first.trim().takeIf { it.isNotEmpty() }
        }
        return null
    }

    /** Nest may deliver join via Socket.IO ack and/or explicit `voice:joined` emit. */
    private fun extractPeerFromStateAck(args: Array<out Any>): VoicePeerState? {
        val first = args.firstOrNull() as? JSONObject ?: return null
        val data = when {
            first.optString("event") == "voice:state-ack" -> first.optJSONObject("data")
            first.has("userId") -> first
            else -> null
        } ?: return null
        return data.toPeerState().takeIf { it.userId.isNotBlank() }
    }

    private fun extractVoiceJoinedData(args: Array<out Any>): JSONObject? {
        val first = args.firstOrNull() ?: return null
        if (first is JSONObject) {
            if (first.has("data") && first.optString("event") == "voice:joined") {
                return first.optJSONObject("data")
            }
            return first
        }
        return null
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
        cancelJoinTimeout()
        voiceJoined = false
        joinedCallbacks.clear()
        stateAckCallbacks.clear()
        synchronized(pendingUpstreamFrames) { pendingUpstreamFrames.clear() }
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
        private const val JOIN_TIMEOUT_MS = 15_000L
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
