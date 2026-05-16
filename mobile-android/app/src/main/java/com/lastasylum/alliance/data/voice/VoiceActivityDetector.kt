package com.lastasylum.alliance.data.voice

import kotlin.math.sqrt

/**
 * Simple energy-based VAD with hangover — suppresses uplink during silence.
 */
class VoiceActivityDetector(
    private val rmsThreshold: Double = 450.0,
    private val hangoverFrames: Int = 10,
) {
    private var hangoverRemaining = 0

    fun reset() {
        hangoverRemaining = 0
    }

    /** True when this frame should be transmitted (speech or hangover). */
    fun shouldTransmit(pcmFrame: ByteArray): Boolean {
        val rms = computeRms(pcmFrame)
        if (rms >= rmsThreshold) {
            hangoverRemaining = hangoverFrames
            return true
        }
        if (hangoverRemaining > 0) {
            hangoverRemaining--
            return true
        }
        return false
    }

    /** For UI: is there meaningful energy in this PCM frame? */
    fun isSpeechEnergy(pcmFrame: ByteArray): Boolean =
        computeRms(pcmFrame) >= rmsThreshold

    private fun computeRms(pcm: ByteArray): Double {
        if (pcm.size < 2) return 0.0
        var sum = 0.0
        var i = 0
        var n = 0
        while (i + 1 < pcm.size) {
            val sample = (pcm[i].toInt() and 0xff) or (pcm[i + 1].toInt() shl 8)
            val s = sample.toShort().toInt()
            sum += (s * s).toDouble()
            n++
            i += 2
        }
        if (n == 0) return 0.0
        return sqrt(sum / n)
    }
}
