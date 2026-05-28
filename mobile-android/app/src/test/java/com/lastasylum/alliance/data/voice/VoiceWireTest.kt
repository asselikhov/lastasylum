package com.lastasylum.alliance.data.voice

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceWireTest {
    @Test
    fun asByteArray_parsesListOfInts() {
        assertArrayEquals(
            byteArrayOf(0, 1, -1),
            VoiceWire.asByteArray(listOf(0, 1, 255)),
        )
    }

    @Test
    fun asByteArray_unpacksDownstreamFromListRoundTrip() {
        val upstream = VoiceWire.packUpstream(7, VoiceWire.CODEC_OPUS, byteArrayOf(9, 8))
        val userId = "u1"
        val userBytes = userId.toByteArray(Charsets.UTF_8)
        val packet = ByteArray(1 + userBytes.size + upstream.size).also { out ->
            out[0] = userBytes.size.toByte()
            System.arraycopy(userBytes, 0, out, 1, userBytes.size)
            System.arraycopy(upstream, 0, out, 1 + userBytes.size, upstream.size)
        }
        val wire = packet.map { it.toInt() and 0xff }
        val event = VoiceWire.unpackDownstream(VoiceWire.asByteArray(wire)!!)
        assertNotNull(event)
        assertArrayEquals(byteArrayOf(9, 8), event!!.payload)
    }

    @Test
    fun asByteArray_rejectsPartialList() {
        assertNull(VoiceWire.asByteArray(listOf(1, "x")))
    }

    @Test
    fun packUpstream_opusConfigCodec() {
        val payload = byteArrayOf(0x4F, 0x70, 0x75, 0x73)
        val packed = VoiceWire.packUpstream(0, VoiceWire.CODEC_OPUS_CONFIG, payload)
        assertEquals(VoiceWire.CODEC_OPUS_CONFIG, packed[0])
        val len = ((packed[3].toInt() and 0xff) shl 8) or (packed[4].toInt() and 0xff)
        assertEquals(payload.size, len)
        assertArrayEquals(payload, packed.copyOfRange(5, packed.size))
    }
}
