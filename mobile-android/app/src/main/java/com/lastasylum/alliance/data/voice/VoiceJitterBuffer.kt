package com.lastasylum.alliance.data.voice

import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/**
 * Per-speaker jitter queues with pre-buffer before playout starts.
 */
class VoiceJitterBuffer(
    private val frameBytes: Int = VoiceAudioPipeline.FRAME_BYTES_PCM,
    private val minFramesBeforeStart: Int = 2,
    private val maxFramesPerSpeaker: Int = 12,
) {
    private val queues = ConcurrentHashMap<String, ArrayDeque<ByteArray>>()
    private val playoutStarted = ConcurrentHashMap<String, Boolean>()
    private val lastFrameByUser = ConcurrentHashMap<String, ByteArray>()
    private val plcTicksByUser = ConcurrentHashMap<String, Int>()

    fun clear() {
        queues.clear()
        playoutStarted.clear()
        lastFrameByUser.clear()
        plcTicksByUser.clear()
    }

    fun removeSpeaker(userId: String) {
        queues.remove(userId)
        playoutStarted.remove(userId)
        lastFrameByUser.remove(userId)
        plcTicksByUser.remove(userId)
    }

    fun push(userId: String, pcm: ByteArray) {
        if (pcm.size != frameBytes) return
        val q = queues.getOrPut(userId) { ArrayDeque(maxFramesPerSpeaker) }
        synchronized(q) {
            while (q.size >= maxFramesPerSpeaker) q.removeFirst()
            q.addLast(pcm.copyOf())
        }
    }

    /** True when audio is queued, pre-buffering, or PLC is still active. */
    fun hasPendingAudio(): Boolean {
        for ((userId, q) in queues) {
            synchronized(q) {
                if (q.isNotEmpty()) return true
            }
            if (playoutStarted[userId] == true &&
                plcTicksByUser.getOrDefault(userId, 0) < PLC_MAX_TICKS &&
                lastFrameByUser[userId] != null
            ) {
                return true
            }
        }
        return false
    }

    /**
     * Returns one mixed 20 ms frame, or null if still pre-buffering / no data.
     */
    fun pollMixedFrame(): ByteArray? {
        var anyReady = false
        var activeSpeakers = 0
        val mix = ShortArray(VoiceAudioPipeline.FRAME_SAMPLES)
        for ((userId, q) in queues) {
            val frame = synchronized(q) {
                if (q.isNotEmpty()) {
                    val started = playoutStarted[userId] == true
                    if (!started && q.size < minFramesBeforeStart) return@synchronized null
                    if (!started) playoutStarted[userId] = true
                    plcTicksByUser.remove(userId)
                    q.removeFirst().also { lastFrameByUser[userId] = it.copyOf() }
                } else if (playoutStarted[userId] == true) {
                    val plcTick = plcTicksByUser.getOrDefault(userId, 0)
                    if (plcTick < PLC_MAX_TICKS) {
                        plcTicksByUser[userId] = plcTick + 1
                        lastFrameByUser[userId]?.let { attenuateFrame(it) }
                    } else {
                        playoutStarted[userId] = false
                        plcTicksByUser.remove(userId)
                        null
                    }
                } else {
                    null
                }
            } ?: continue
            anyReady = true
            activeSpeakers++
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
        if (activeSpeakers > 1) {
            for (i in mix.indices) {
                mix[i] = (mix[i].toInt() / activeSpeakers).toShort()
            }
        }
        val out = ByteArray(frameBytes)
        for (i in mix.indices) {
            val v = mix[i].toInt()
            out[i * 2] = (v and 0xff).toByte()
            out[i * 2 + 1] = ((v shr 8) and 0xff).toByte()
        }
        return out
    }

    private fun attenuateFrame(frame: ByteArray): ByteArray {
        val out = frame.copyOf()
        var i = 0
        while (i + 1 < out.size) {
            val sample = (out[i].toInt() and 0xff) or (out[i + 1].toInt() shl 8)
            val attenuated = (sample * PLC_ATTENUATION).roundToInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            out[i] = (attenuated and 0xff).toByte()
            out[i + 1] = ((attenuated shr 8) and 0xff).toByte()
            i += 2
        }
        return out
    }

    companion object {
        private const val PLC_MAX_TICKS = 2
        private const val PLC_ATTENUATION = 0.5f
    }
}
