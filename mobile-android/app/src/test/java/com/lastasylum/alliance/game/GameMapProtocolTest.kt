package com.lastasylum.alliance.game

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class GameMapProtocolTest {
    @Test
    fun enterWorldMap_matchesCapturedSample() {
        assertArrayEquals(
            bytes("08 6d"),
            GameMapProtocol.encodeEnterWorldMap(109),
        )
    }

    @Test
    fun worldMapView_sameKingdom_matchesCapturedSample() {
        assertArrayEquals(
            bytes("08 01 18 2b 22 08 10 ef 03 08 6d 18 f9 03 10 13"),
            GameMapProtocol.encodeWorldMapView(505, 495, 109, crossServer = false),
        )
    }

    @Test
    fun worldMapView_crossKingdom_matchesCapturedSample() {
        assertArrayEquals(
            bytes("08 01 18 2b 22 08 10 92 05 08 6d 18 a7 05 28 01 10 13"),
            GameMapProtocol.encodeWorldMapView(679, 658, 109, crossServer = true),
        )
    }

    @Test
    fun mapForegroundUrls_prefersWorldThenMap() {
        val urls = GameSearchDeepLinks.mapForegroundUrls(322, 636, 109)
        assertEquals(
            listOf(
                "globalphslink://world/109",
                "globalphslink://map",
            ),
            urls,
        )
    }

    private fun bytes(hex: String): ByteArray =
        hex.split(' ').map { it.toInt(16).toByte() }.toByteArray()
}
