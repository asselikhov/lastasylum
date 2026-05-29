package com.lastasylum.alliance.data.voice

import java.util.ArrayDeque

/**
 * Decodes compressed voice frames off the socket thread.
 * Drops oldest queued frames when the bounded queue overflows.
 */
internal class VoiceDecodeWorker(
    private val onDecoded: (userId: String, codec: String, payload: ByteArray) -> Unit,
    private val maxQueueSize: Int = 32,
) {
    private data class CompressedFrame(
        val userId: String,
        val codec: String,
        val payload: ByteArray,
    )

    private val queue = ArrayDeque<CompressedFrame>()
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
                        while (queue.isEmpty() && running) {
                            lock.wait(50)
                        }
                        if (!running) return@Thread
                        if (queue.isEmpty()) null else queue.removeFirst()
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
            while (queue.size >= maxQueueSize) {
                queue.removeFirst()
            }
            queue.addLast(CompressedFrame(userId, codec, payload.copyOf()))
            lock.notify()
        }
    }

    fun stop() {
        if (!running) return
        running = false
        synchronized(lock) {
            queue.clear()
            lock.notifyAll()
        }
        thread?.join(400)
        thread = null
    }
}
