package com.lastasylum.alliance.game

/**
 * Protobuf payloads for in-game [WorldMapViewC2S] / [EnterWorldMapC2S] (v1.0.81 RE).
 * Wire order from Frida capture of in-game chat coord taps.
 */
internal object GameMapProtocol {
    fun encodeEnterWorldMap(server: Int): ByteArray =
        protobufMessage(field(1, encodeVarint(server)))

    fun encodeWorldMapView(
        x: Int,
        y: Int,
        server: Int,
        crossServer: Boolean,
    ): ByteArray {
        val inner = mutableListOf<Byte>().apply {
            addAll(field(2, encodeVarint(y)).toList())
            addAll(field(1, encodeVarint(server)).toList())
            addAll(field(3, encodeVarint(x)).toList())
        }
        val chunks = mutableListOf<ByteArray>().apply {
            add(field(1, encodeVarint(1)))
            add(field(3, encodeVarint(0x2b)))
            add(embedded(4, inner.toByteArray()))
            if (crossServer) {
                add(field(5, encodeVarint(1)))
            }
            add(field(2, encodeVarint(0x13)))
        }
        return protobufMessage(*chunks.toTypedArray())
    }

    private fun protobufMessage(vararg chunks: ByteArray): ByteArray {
        var total = 0
        for (chunk in chunks) total += chunk.size
        val out = ByteArray(total)
        var offset = 0
        for (chunk in chunks) {
            chunk.copyInto(out, destinationOffset = offset)
            offset += chunk.size
        }
        return out
    }

    private fun embedded(fieldNumber: Int, payload: ByteArray): ByteArray {
        val tag = encodeTag(fieldNumber, 2)
        val len = encodeVarint(payload.size)
        return tag + len + payload
    }

    private fun field(fieldNumber: Int, value: ByteArray): ByteArray =
        encodeTag(fieldNumber, 0) + value

    private fun encodeTag(fieldNumber: Int, wireType: Int): ByteArray =
        encodeVarint((fieldNumber shl 3) or wireType)

    private fun encodeVarint(value: Int): ByteArray {
        var n = value
        val out = ArrayList<Byte>(5)
        while (n and 0x7f.inv() != 0) {
            out.add(((n and 0x7f) or 0x80).toByte())
            n = n ushr 7
        }
        out.add(n.toByte())
        return out.toByteArray()
    }
}
