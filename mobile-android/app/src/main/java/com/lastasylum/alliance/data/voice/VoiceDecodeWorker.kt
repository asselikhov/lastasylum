package com.lastasylum.alliance.data.voice

import java.util.ArrayDeque

/**
 * Decodes compressed voice frames off the socket thread.
 * Per-user bounded queues with round-robin scheduling and config protection.
 */
internal class VoiceDecodeWorker(
    private val onDecoded: (userId: String, codec: String, payload: ByteArray) -> Unit,
    private val maxFramesPerUser: Int = 8,
) {
    private data class CompressedFrame(
        val userId: String,
        val codec: String,
        val payload: ByteArray,
    )

    private val queuesByUser = HashMap<String, ArrayDeque<CompressedFrame>>()
    private val rrOrder = ArrayDeque<String>()
    private var rrIndex = 0
    private val lock = Object()
    @Volatile
    private var running = false
    private var thread: Thread? = null

    fun start() {
        if (running) return
        running = true
        thread = Thread(
            {
                while (running) {
                    val frame = synchronized(lock) {
                        while (!hasAnyFrameLocked() && running) {
                            lock.wait(50)
                        }
                        if (!running) return@Thread
                        pollNextFrameLocked()
                    } ?: continue
                    onDecoded(frame.userId, frame.codec, frame.payload)
                }
            },
            "voice-decode",
        ).apply {
            priority = Thread.NORM_PRIORITY + 1
            start()
        }
    }

    fun offer(userId: String, codec: String, payload: ByteArray) {
        if (!running) return
        synchronized(lock) {
            val q = queuesByUser.getOrPut(userId) { ArrayDeque() }
            val frame = CompressedFrame(userId, codec, payload.copyOf())
            if (codec == VoiceOpusCodec.CODEC_OPUS_CONFIG) {
                q.removeAll { it.codec == VoiceOpusCodec.CODEC_OPUS_CONFIG }
                q.addFirst(frame)
            } else {
                while (q.size >= maxFramesPerUser && evictOldestOpus(q)) {
                    // Drop oldest audio frames; never evict config.
                }
                if (q.size >= maxFramesPerUser) {
                    return
                }
                q.addLast(frame)
            }
            if (!rrOrder.contains(userId)) {
                rrOrder.addLast(userId)
            }
            lock.notify()
        }
    }

    fun removeUser(userId: String) {
        synchronized(lock) {
            queuesByUser.remove(userId)
            rrOrder.remove(userId)
            if (rrOrder.isEmpty()) {
                rrIndex = 0
            } else {
                rrIndex %= rrOrder.size
            }
        }
    }

    fun stop() {
        if (!running) return
        running = false
        synchronized(lock) {
            queuesByUser.clear()
            rrOrder.clear()
            rrIndex = 0
            lock.notifyAll()
        }
        thread?.join(400)
        thread = null
    }

    private fun hasAnyFrameLocked(): Boolean =
        queuesByUser.values.any { it.isNotEmpty() }

    private fun pollNextFrameLocked(): CompressedFrame? {
        if (rrOrder.isEmpty()) return null
        val size = rrOrder.size
        for (i in 0 until size) {
            val idx = (rrIndex + i) % rrOrder.size
            val userId = rrOrder.elementAt(idx)
            val q = queuesByUser[userId] ?: continue
            if (q.isEmpty()) continue
            rrIndex = (idx + 1) % rrOrder.size
            return q.removeFirst()
        }
        return null
    }

    private fun evictOldestOpus(q: ArrayDeque<CompressedFrame>): Boolean {
        val iter = q.iterator()
        while (iter.hasNext()) {
            if (iter.next().codec != VoiceOpusCodec.CODEC_OPUS_CONFIG) {
                iter.remove()
                return true
            }
        }
        return false
    }
}
