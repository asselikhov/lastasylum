package com.lastasylum.alliance.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RaidShareTargetTest {

    private fun target(
        name: String? = null,
        playerName: String? = null,
        union: String? = null,
        cat: String? = null,
        lv: Int? = null,
        power: Long? = null,
        kills: Long? = null,
        secretTaskId: Int? = null,
        grade: Int? = null,
        stars: Int? = null,
        qualityType: Int? = null,
    ): RaidShareTarget = RaidShareTarget(
        seq = 1L,
        open = true,
        x = 100,
        y = 200,
        serverNumber = 109,
        shareType = 1,
        name = name,
        playerName = playerName,
        union = union,
        cat = cat,
        lv = lv,
        power = power,
        kills = kills,
        dialogTopPx = null,
        powerIcon = null,
        killsIcon = null,
        secretTaskId = secretTaskId,
        grade = grade,
        stars = stars,
        qualityType = qualityType,
    )

    @Test
    fun titleLine_insertsSpaceBetweenAllianceTagAndNick() {
        val t = target(name = "[OBZH]Ник", cat = "player", lv = 26)
        assertEquals("[OBZH] Ник", t.titleLine())
    }

    @Test
    fun titleLine_keepsExistingSpace() {
        val t = target(name = "[OBZH] Ник", cat = "player")
        assertEquals("[OBZH] Ник", t.titleLine())
    }

    @Test
    fun powerAndKills_compactFormatting() {
        val t = target(name = "[A]B", cat = "player", power = 54_901_923L, kills = 63_068L)
        assertEquals("54.9M", t.powerLabel())
        assertEquals("63.1K", t.killsLabel())
    }

    @Test
    fun chest_showsAllianceTagBeforeNick() {
        val t = target(playerName = "Ник", union = "OBZH", secretTaskId = 7, grade = 5, stars = 3)
        assertTrue(t.isChest)
        assertEquals("Сундук", t.titleLine())
        val meta = t.metaParts()
        assertTrue(meta.contains("UR \u2605\u2605\u2605"))
        assertTrue(meta.contains("[OBZH] Ник"))
    }

    @Test
    fun chest_withoutUnion_showsNickOnly() {
        val t = target(playerName = "Ник", secretTaskId = 7, grade = 4)
        assertEquals("Ник", t.chestOwnerLabel())
        assertTrue(t.metaParts().contains("Ник"))
    }

    @Test
    fun convoy_qualityLabel() {
        val t = target(name = "Конвой", cat = "truck", qualityType = 4)
        assertTrue(t.isTruck)
        assertEquals("SSR", t.qualityLabel())
    }

    @Test
    fun player_metaPartsEmpty_levelGoesToPrefix() {
        val t = target(name = "[A] B", cat = "player", lv = 30)
        assertTrue(t.metaParts().isEmpty())
        assertEquals("Ур.30", t.levelPrefix())
    }

    @Test
    fun chest_hasNoLevelPrefix() {
        val t = target(playerName = "Ник", secretTaskId = 7, grade = 5, lv = 5)
        assertNull(t.levelPrefix())
    }

    @Test
    fun powerLabel_nullWhenZeroOrMissing() {
        assertNull(target(name = "[A] B", cat = "player").powerLabel())
        assertNull(target(name = "[A] B", cat = "player", power = 0L).powerLabel())
    }
}
