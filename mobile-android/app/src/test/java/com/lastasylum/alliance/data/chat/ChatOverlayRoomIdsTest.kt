package com.lastasylum.alliance.data.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatOverlayRoomIdsTest {
    @Test
    fun bootstrap_includesRaidHubSelected() {
        val ids = ChatOverlayRoomIds.forOverlayBootstrap("raid-1", "hub-1", "sel-1")
        assertEquals(listOf("raid-1", "hub-1", "sel-1"), ids)
    }

    @Test
    fun bootstrap_dedupesSelectedWhenSameAsRaid() {
        val ids = ChatOverlayRoomIds.forOverlayBootstrap("room-a", null, "room-a")
        assertEquals(listOf("room-a"), ids)
    }
}
