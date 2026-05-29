package com.lastasylum.alliance.data.voice

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
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
 * Orchestrates team voice (alliance hub room): binary socket relay, VAD uplink, jitter playback.
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
            is VoicePeerEvent.Joined -> {
                audioPipeline.setRemotePeerMic(event.peer.userId, event.peer.micOn)
                if (micOn) {
                    audioPipeline.resendEncoderConfig()
                }
            }
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
        val rid = roomId.trim()
        if (rid.isEmpty()) return
        val sameRoom = this.roomId == rid
        if (!sameRoom) {
            stop()
            this.roomId = rid
            this.localUserId = localUserId
            soundOn = userSettings.isOverlayVoiceSoundEnabled()
            micOn = userSettings.isOverlayVoiceMicEnabled() &&
                hasRecordAudioPermission() &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
            socketManager.addFrameListener(frameListener)
            socketManager.addPeerListener(peerListener)
            audioPipeline.setSoundEnabled(soundOn)
            applyVolumeFromPrefs()
            enterCommunicationAudioMode()
            socketManager.connect(
                baseUrl = BuildConfig.API_BASE_URL,
                roomId = rid,
                tokenProvider = { tokenStore.getAccessToken() },
                initialMicOn = micOn,
                initialSoundOn = soundOn,
            )
        } else {
            this.localUserId = localUserId
            socketManager.emitState(micOn, soundOn)
        }
        notifyState()
        publishVoiceStateToServer()
        socketManager.whenJoined {
            applyMicCapture()
            if (micOn) onMicForegroundChanged(true)
        }
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
        if (enabled && !soundOn) {
            soundOn = true
            userSettings.setOverlayVoiceSoundEnabled(true)
            audioPipeline.setSoundEnabled(true)
        }
        micOn = enabled
        userSettings.setOverlayVoiceMicEnabled(enabled)
        onMicForegroundChanged(micOn)
        notifyState()
        applyMicCapture()
        publishVoiceStateToServer()
        return true
    }

    fun setSoundEnabled(enabled: Boolean) {
        soundOn = enabled
        userSettings.setOverlayVoiceSoundEnabled(enabled)
        audioPipeline.setSoundEnabled(soundOn)
        notifyState()
        publishVoiceStateToServer()
    }

    fun setPlaybackVolume(level: Float) {
        val clamped = level.coerceIn(
            UserSettingsPreferences.OVERLAY_VOICE_VOLUME_MIN,
            UserSettingsPreferences.OVERLAY_VOICE_VOLUME_MAX,
        )
        userSettings.setOverlayVoiceSoundVolume(clamped)
        audioPipeline.setPlaybackGain(clamped)
    }

    fun setCaptureVolume(level: Float) {
        val clamped = level.coerceIn(
            UserSettingsPreferences.OVERLAY_VOICE_VOLUME_MIN,
            UserSettingsPreferences.OVERLAY_VOICE_VOLUME_MAX,
        )
        userSettings.setOverlayVoiceMicVolume(clamped)
        audioPipeline.setCaptureGain(clamped)
    }

    private fun applyVolumeFromPrefs() {
        audioPipeline.setPlaybackGain(userSettings.getOverlayVoiceSoundVolume())
        audioPipeline.setCaptureGain(userSettings.getOverlayVoiceMicVolume())
    }

    fun whenVoiceReady(block: () -> Unit) {
        socketManager.whenJoined(block)
    }

    private fun publishVoiceStateToServer() {
        val publish = {
            socketManager.emitState(micOn, soundOn) {
                applyMicCapture()
                notifyState()
            }
        }
        if (socketManager.isVoiceJoined()) {
            publish()
        } else {
            socketManager.whenJoined(publish)
        }
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
        if (micOn && hasRecordAudioPermission() && socketManager.isVoiceJoined()) {
            audioPipeline.startCapture()
            audioPipeline.resendEncoderConfig()
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val speaker = audioManager.availableCommunicationDevices
                .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
            if (speaker != null) {
                audioManager.setCommunicationDevice(speaker)
            }
        } else {
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = true
        }
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
