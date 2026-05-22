package com.lastasylum.alliance.data.voice

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceAudioPipelineTest {
    @Test
    fun normalizePlayFrame_keepsExactSize() {
        val pcm = ByteArray(VoiceAudioPipeline.FRAME_BYTES_PCM) { it.toByte() }
        assertArrayEquals(pcm, VoiceAudioPipeline.normalizePlayFrame(pcm))
    }

    @Test
    fun normalizePlayFrame_padsShortDecoderOutput() {
        val short = byteArrayOf(1, 2, 3)
        val out = VoiceAudioPipeline.normalizePlayFrame(short)
        assertEquals(VoiceAudioPipeline.FRAME_BYTES_PCM, out.size)
        assertEquals(1.toByte(), out[0])
        assertEquals(2.toByte(), out[1])
        assertEquals(3.toByte(), out[2])
        assertEquals(0.toByte(), out[3])
    }

    @Test
    fun normalizePlayFrame_truncatesOversizedDecoderOutput() {
        val big = ByteArray(VoiceAudioPipeline.FRAME_BYTES_PCM + 4) { (it + 1).toByte() }
        val out = VoiceAudioPipeline.normalizePlayFrame(big)
        assertEquals(VoiceAudioPipeline.FRAME_BYTES_PCM, out.size)
        assertEquals(1.toByte(), out[0])
        assertEquals(VoiceAudioPipeline.FRAME_BYTES_PCM.toByte(), out[VoiceAudioPipeline.FRAME_BYTES_PCM - 1])
    }
}
