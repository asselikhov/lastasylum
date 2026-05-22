package com.lastasylum.alliance.data.voice

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.lastasylum.alliance.BuildConfig
import com.lastasylum.alliance.data.auth.TokenStore
import android.os.Build
import com.lastasylum.alliance.data.settings.UserSettingsPreferences
import java.util.concurrent.ConcurrentHashMap

/**
 * Orchestrates raid voice room: binary socket relay, VAD uplink, jitter playback.
 */
class VoiceChatSession(
    private val context: Context,
    private val tokenStore: TokenStore,
    private val userSettings: UserSettingsPreferences,
    private val socketManager: VoiceSocketManager,
    private val onStateChanged: (micOn: Boolean, soundOn: Boolean) -> Unit,
    private val onMicForegroundChanged: (micActive: Boolean) -> Unit,
    private val onActiveSpeakersChanged: (count: Int) -> Unit = {},
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val audioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var roomId: String? = null
    private var localUserId: String? = null
    private var previousAudioMode: Int = AudioManager.MODE_NORMAL

    var micOn: Boolean = false
        private set
    var soundOn: Boolean = false
        private set

    private val remoteSpeechUntilMs = ConcurrentHashMap<String, Long>()
    private val speechHoldMs = 350L

    private val audioPipeline = VoiceAudioPipeline(
        onEncodedFrame = { frame ->
            if (!micOn) return@VoiceAudioPipeline
            socketManager.emitFrame(frame.codec, frame.payload)
        },
        onLocalSpeechActivity = { speaking ->
            val uid = localUserId ?: return@VoiceAudioPipeline
            if (speaking) {
                remoteSpeechUntilMs[uid] = SystemClock.elapsedRealtime() + speechHoldMs
            }
            scheduleSpeakerCountUpdate()
        },
    )

    private val frameListener: (VoiceFrameEvent) -> Unit = listener@{ event ->
        if (event.userId == localUserId) return@listener
        // Server relays only when peer mic is on; mark active if peer-state not received yet.
        audioPipeline.setRemotePeerMic(event.userId, true)
        remoteSpeechUntilMs[event.userId] = SystemClock.elapsedRealtime() + speechHoldMs
        scheduleSpeakerCountUpdate()
        audioPipeline.enqueueRemoteFrame(event.userId, event.codec, event.payload)
    }

    private val peerListener: (VoicePeerEvent) -> Unit = { event ->
        TeamVoicePresenceStore.apply(event)
        when (event) {
            is VoicePeerEvent.Joined -> audioPipeline.setRemotePeerMic(event.peer.userId, event.peer.micOn)
            is VoicePeerEvent.State -> audioPipeline.setRemotePeerMic(event.peer.userId, event.peer.micOn)
            is VoicePeerEvent.Left -> {
                audioPipeline.removeRemotePeer(event.userId)
                remoteSpeechUntilMs.remove(event.userId)
                scheduleSpeakerCountUpdate()
            }
        }
    }

    private val speakerUpdateRunnable = Runnable {
        val now = SystemClock.elapsedRealtime()
        val selfId = localUserId
        val count = remoteSpeechUntilMs.entries.count { (userId, until) ->
            until > now && userId != selfId
        }
        onActiveSpeakersChanged(count)
    }

    fun start(roomId: String, localUserId: String) {
        if (this.roomId == roomId) return
        stop()
        this.roomId = roomId
        this.localUserId = localUserId
        micOn = userSettings.isOverlayVoiceMicEnabled()
        soundOn = userSettings.isOverlayVoiceSoundEnabled()
        socketManager.addFrameListener(frameListener)
        socketManager.addPeerListener(peerListener)
        audioPipeline.setSoundEnabled(soundOn)
        applyMicCapture()
        enterCommunicationAudioMode()
        socketManager.connect(
            baseUrl = BuildConfig.API_BASE_URL,
            roomId = roomId,
            tokenProvider = { tokenStore.getAccessToken() },
            initialMicOn = micOn,
            initialSoundOn = soundOn,
        )
        notifyState()
    }

    fun stop() {
        leaveCommunicationAudioMode()
        socketManager.removeFrameListener(frameListener)
        socketManager.removePeerListener(peerListener)
        socketManager.disconnect()
        TeamVoicePresenceStore.clear()
        audioPipeline.release()
        remoteSpeechUntilMs.clear()
        mainHandler.removeCallbacks(speakerUpdateRunnable)
        onActiveSpeakersChanged(0)
        roomId = null
        if (micOn) {
            micOn = false
            onMicForegroundChanged(false)
        }
        notifyState()
    }

    fun setMicEnabled(enabled: Boolean): Boolean {
        if (enabled && !hasRecordAudioPermission()) return false
        if (enabled && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        micOn = enabled
        userSettings.setOverlayVoiceMicEnabled(enabled)
        applyMicCapture()
        socketManager.emitState(micOn, soundOn)
        onMicForegroundChanged(micOn)
        notifyState()
        return true
    }

    fun setSoundEnabled(enabled: Boolean) {
        soundOn = enabled
        userSettings.setOverlayVoiceSoundEnabled(enabled)
        audioPipeline.setSoundEnabled(soundOn)
        socketManager.emitState(micOn, soundOn)
        notifyState()
    }

    fun toggleMic(): Boolean = setMicEnabled(!micOn)

    fun toggleSound() {
        setSoundEnabled(!soundOn)
    }

    fun onAccessTokenRefreshed() {
        socketManager.emitState(micOn, soundOn)
        socketManager.reconnectWithFreshToken()
    }

    fun hasRecordAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED

    private fun applyMicCapture() {
        if (micOn && hasRecordAudioPermission()) {
            audioPipeline.startCapture()
        } else {
            audioPipeline.stopCapture()
        }
    }

    private fun scheduleSpeakerCountUpdate() {
        mainHandler.removeCallbacks(speakerUpdateRunnable)
        mainHandler.post(speakerUpdateRunnable)
    }

    private fun enterCommunicationAudioMode() {
        previousAudioMode = audioManager.mode
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.isSpeakerphoneOn = true
    }

    private fun leaveCommunicationAudioMode() {
        audioManager.mode = previousAudioMode
    }

    private fun notifyState() {
        mainHandler.post {
            localUserId?.let { uid ->
                TeamVoicePresenceStore.setLocal(uid, "", micOn, soundOn)
            }
            onStateChanged(micOn, soundOn)
        }
    }
}
