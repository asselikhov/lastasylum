package com.lastasylum.alliance.data.chat

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatOverlayRoomIdsTest {
    @After
    fun tearDown() {
        ChatSessionCache.clear()
    }

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

    @Test
    fun bootstrap_fillsRaidFromSessionCacheWhenPrefsEmpty() {
        ChatSessionCache.update(
            listOf(
                ChatRoomDto(
                    id = "raid-cached",
                    allianceId = "pt:team",
                    title = "Рейд",
                    sortOrder = 2,
                ),
            ),
        )
        val ids = ChatOverlayRoomIds.forOverlayBootstrap(null, null, null)
        assertEquals(listOf("raid-cached"), ids)
    }
}
