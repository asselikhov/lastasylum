package com.lastasylum.alliance.data.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceJitterBufferTest {
    @Test
    fun pollMixedFrame_normalizesMultipleSpeakers() {
        val buffer = VoiceJitterBuffer(minFramesBeforeStart = 1)
        val maxFrame = maxAmplitudeFrame()
        buffer.push("speaker-a", maxFrame)
        buffer.push("speaker-b", maxFrame.copyOf())

        val mixed = buffer.pollMixedFrame()
        requireNotNull(mixed)

        val sample = (mixed[0].toInt() and 0xff) or (mixed[1].toInt() shl 8)
        val signed = sample.toShort().toInt()
        assertTrue(signed < Short.MAX_VALUE)
        assertEquals(Short.MAX_VALUE / 2, signed)
    }

    @Test
    fun hasPendingAudio_trueWhilePreBuffering() {
        val buffer = VoiceJitterBuffer(minFramesBeforeStart = 2)
        buffer.push("speaker-a", silentFrame())

        assertTrue(buffer.hasPendingAudio())
        assertEquals(null, buffer.pollMixedFrame())
        assertTrue(buffer.hasPendingAudio())
    }

    private fun maxAmplitudeFrame(): ByteArray {
        val frame = ByteArray(VoiceAudioPipeline.FRAME_BYTES_PCM)
        frame[0] = 0xFF.toByte()
        frame[1] = 0x7F.toByte()
        return frame
    }

    private fun silentFrame(): ByteArray = ByteArray(VoiceAudioPipeline.FRAME_BYTES_PCM)
}
