package com.lastasylum.alliance.data.voice

import java.util.concurrent.ConcurrentHashMap

/**
 * Per-speaker jitter queues with ~80 ms pre-buffer before playout starts.
 */
class VoiceJitterBuffer(
    private val frameBytes: Int = VoiceAudioPipeline.FRAME_BYTES_PCM,
    private val minFramesBeforeStart: Int = 3,
    private val maxFramesPerSpeaker: Int = 8,
) {
    private val queues = ConcurrentHashMap<String, ArrayDeque<ByteArray>>()
    private val playoutStarted = ConcurrentHashMap<String, Boolean>()

    fun clear() {
        queues.clear()
        playoutStarted.clear()
    }

    fun removeSpeaker(userId: String) {
        queues.remove(userId)
        playoutStarted.remove(userId)
    }

    fun push(userId: String, pcm: ByteArray) {
        if (pcm.size != frameBytes) return
        val q = queues.getOrPut(userId) { ArrayDeque(maxFramesPerSpeaker) }
        synchronized(q) {
            while (q.size >= maxFramesPerSpeaker) q.removeFirst()
            q.addLast(pcm.copyOf())
        }
    }

    /**
     * Returns one mixed 20 ms frame, or null if still pre-buffering / no data.
     */
    fun pollMixedFrame(): ByteArray? {
        var anyReady = false
        val mix = ShortArray(VoiceAudioPipeline.FRAME_SAMPLES)
        for ((userId, q) in queues) {
            val frame = synchronized(q) {
                if (q.isEmpty()) return@synchronized null
                val started = playoutStarted[userId] == true
                if (!started && q.size < minFramesBeforeStart) return@synchronized null
                if (!started) playoutStarted[userId] = true
                q.removeFirst()
            } ?: continue
            anyReady = true
            var i = 0
            while (i + 1 < frame.size) {
                val sample = (frame[i].toInt() and 0xff) or (frame[i + 1].toInt() shl 8)
                val s = sample.toShort()
                val idx = i / 2
                if (idx < mix.size) {
                    val sum = mix[idx].toInt() + s.toInt()
                    mix[idx] = sum.coerceIn(
                        Short.MIN_VALUE.toInt(),
                        Short.MAX_VALUE.toInt(),
                    ).toShort()
                }
                i += 2
            }
        }
        if (!anyReady) return null
        val out = ByteArray(frameBytes)
        for (i in mix.indices) {
            val v = mix[i].toInt()
            out[i * 2] = (v and 0xff).toByte()
            out[i * 2 + 1] = ((v shr 8) and 0xff).toByte()
        }
        return out
    }
}
