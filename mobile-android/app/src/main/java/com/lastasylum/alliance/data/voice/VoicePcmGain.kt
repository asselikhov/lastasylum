package com.lastasylum.alliance.data.voice

/** Software gain for 16-bit LE PCM voice frames. */
internal object VoicePcmGain {

    fun apply(frame: ByteArray, gain: Float): ByteArray {
        if (gain == 1f) return frame
        val out = frame.copyOf()
        applyInPlace(out, gain)
        return out
    }

    fun applyInPlace(frame: ByteArray, gain: Float) {
        if (gain == 1f) return
        var i = 0
        while (i + 1 < frame.size) {
            val sample = (frame[i].toInt() and 0xff) or (frame[i + 1].toInt() shl 8)
            val scaled = (sample * gain).toInt().coerceIn(
                Short.MIN_VALUE.toInt(),
                Short.MAX_VALUE.toInt(),
            )
            frame[i] = (scaled and 0xff).toByte()
            frame[i + 1] = ((scaled shr 8) and 0xff).toByte()
            i += 2
        }
    }
}
