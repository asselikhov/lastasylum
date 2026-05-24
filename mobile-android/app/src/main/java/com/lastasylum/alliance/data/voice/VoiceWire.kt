package com.lastasylum.alliance.data.voice

import java.nio.ByteBuffer
import org.json.JSONArray

/**
 * Binary Socket.IO payloads for voice frames (no base64 / JSON overhead).
 *
 * Upstream (client → server): [codec:1][seq:u16][len:u16][payload]
 * Downstream (server → client): [userIdLen:1][userId][codec:1][seq:u16][len:u16][payload]
 */
object VoiceWire {
    const val CODEC_OPUS: Byte = 0
    const val CODEC_PCM: Byte = 1
    const val MAX_USER_ID_BYTES = 64
    const val MAX_PAYLOAD_BYTES = 4096
    private const val UP_HEADER_BYTES = 5

    fun packUpstream(seq: Int, codec: Byte, payload: ByteArray): ByteArray {
        require(payload.size in 1..MAX_PAYLOAD_BYTES)
        val out = ByteArray(UP_HEADER_BYTES + payload.size)
        out[0] = codec
        out[1] = (seq shr 8).toByte()
        out[2] = (seq and 0xff).toByte()
        out[3] = (payload.size shr 8).toByte()
        out[4] = (payload.size and 0xff).toByte()
        System.arraycopy(payload, 0, out, UP_HEADER_BYTES, payload.size)
        return out
    }

    fun unpackDownstream(data: ByteArray): VoiceFrameEvent? {
        if (data.size < 7) return null
        val userLen = data[0].toInt() and 0xff
        if (userLen !in 1..MAX_USER_ID_BYTES) return null
        val headerLen = 1 + userLen + 1 + 4
        if (data.size < headerLen) return null
        val userId = data.copyOfRange(1, 1 + userLen).decodeToString()
        val codecByte = data[1 + userLen]
        val codec = when (codecByte) {
            CODEC_OPUS -> VoiceOpusCodec.CODEC_OPUS
            CODEC_PCM -> VoiceOpusCodec.CODEC_PCM
            else -> return null
        }
        val seq = ((data[1 + userLen + 1].toInt() and 0xff) shl 8) or
            (data[1 + userLen + 2].toInt() and 0xff)
        val payloadLen = ((data[1 + userLen + 3].toInt() and 0xff) shl 8) or
            (data[1 + userLen + 4].toInt() and 0xff)
        if (payloadLen !in 1..MAX_PAYLOAD_BYTES) return null
        if (data.size < headerLen + payloadLen) return null
        val payload = data.copyOfRange(headerLen, headerLen + payloadLen)
        return VoiceFrameEvent(
            userId = userId,
            username = "",
            codec = codec,
            payload = payload,
            seq = seq,
        )
    }

    fun asByteArray(arg: Any?): ByteArray? = when (arg) {
        is ByteArray -> arg
        is ByteBuffer -> {
            val dup = arg.duplicate()
            ByteArray(dup.remaining()).also { dup.get(it) }
        }
        is JSONArray -> {
            val out = ByteArray(arg.length())
            for (i in 0 until arg.length()) {
                out[i] = (arg.optInt(i) and 0xff).toByte()
            }
            out
        }
        is IntArray -> arg.map { (it and 0xff).toByte() }.toByteArray()
        is List<*> -> {
            val bytes = arg.mapNotNull { (it as? Number)?.toInt()?.and(0xff) }
            if (bytes.size != arg.size) null else ByteArray(bytes.size) { bytes[it].toByte() }
        }
        is Array<*> -> {
            for (item in arg) {
                asByteArray(item)?.let { return it }
            }
            null
        }
        else -> null
    }
}
