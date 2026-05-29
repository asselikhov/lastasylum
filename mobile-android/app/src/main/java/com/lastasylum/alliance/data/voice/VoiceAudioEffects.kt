package com.lastasylum.alliance.data.voice

import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.media.AudioRecord
import android.util.Log

/** Platform AEC / NS for voice communication capture (AGC disabled — uplink gain is manual). */
object VoiceAudioEffects {
    private const val TAG = "VoiceAudioEffects"

    fun attachToCapture(record: AudioRecord): VoiceCaptureEffects {
        val sessionId = record.audioSessionId
        var aec: AcousticEchoCanceler? = null
        var ns: NoiseSuppressor? = null
        if (AcousticEchoCanceler.isAvailable()) {
            runCatching {
                aec = AcousticEchoCanceler.create(sessionId)?.apply { enabled = true }
            }.onFailure { e -> Log.w(TAG, "AEC attach failed", e) }
        }
        if (NoiseSuppressor.isAvailable()) {
            runCatching {
                ns = NoiseSuppressor.create(sessionId)?.apply { enabled = true }
            }.onFailure { e -> Log.w(TAG, "NS attach failed", e) }
        }
        return VoiceCaptureEffects(aec, ns)
    }
}

class VoiceCaptureEffects(
    private val aec: AcousticEchoCanceler?,
    private val ns: NoiseSuppressor?,
) {
    fun release() {
        runCatching { aec?.release() }
        runCatching { ns?.release() }
    }
}
