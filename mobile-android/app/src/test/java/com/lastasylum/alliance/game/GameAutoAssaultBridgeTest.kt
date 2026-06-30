package com.lastasylum.alliance.game

import com.lastasylum.alliance.overlay.AllianceMember
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameAutoAssaultBridgeTest {
    private fun member(id: String, name: String) = AllianceMember(
        id = id,
        name = name,
        power = 1L,
        level = 1,
        castle = 1,
        rank = 1,
        kills = 0L,
        x = 0,
        y = 0,
        sid = 0,
        logoutMs = 0L,
    )

    @Test
    fun creatorNamesForBridge_emptyAllowedIds_returnsEmpty() {
        assertTrue(
            GameAutoAssaultBridge.creatorNamesForBridge(
                emptySet(),
                listOf(member("1", "A")),
            ).isEmpty(),
        )
    }

    @Test
    fun creatorNamesForBridge_partialRoster_omitsNamesSoLuaUsesIds() {
        val ids = setOf("1", "2", "3")
        val roster = listOf(member("1", "OnlyOne"))
        assertTrue(GameAutoAssaultBridge.creatorNamesForBridge(ids, roster).isEmpty())
    }

    @Test
    fun creatorNamesForBridge_fullRoster_returnsAllNames() {
        val ids = setOf("1", "2")
        val roster = listOf(member("1", "Alpha"), member("2", "Beta"))
        assertEquals(listOf("Alpha", "Beta"), GameAutoAssaultBridge.creatorNamesForBridge(ids, roster))
    }
}
