package com.lastasylum.alliance.data.voice

import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.media.AudioRecord
import android.util.Log

/** Platform AEC / NS / AGC for voice communication capture. */
object VoiceAudioEffects {
    private const val TAG = "VoiceAudioEffects"

    fun attachToCapture(record: AudioRecord) {
        val sessionId = record.audioSessionId
        if (AcousticEchoCanceler.isAvailable()) {
            runCatching {
                AcousticEchoCanceler.create(sessionId)?.apply { enabled = true }
            }.onFailure { e -> Log.w(TAG, "AEC attach failed", e) }
        }
        if (NoiseSuppressor.isAvailable()) {
            runCatching {
                NoiseSuppressor.create(sessionId)?.apply { enabled = true }
            }.onFailure { e -> Log.w(TAG, "NS attach failed", e) }
        }
        if (AutomaticGainControl.isAvailable()) {
            runCatching {
                AutomaticGainControl.create(sessionId)?.apply { enabled = true }
            }.onFailure { e -> Log.w(TAG, "AGC attach failed", e) }
        }
    }
}
