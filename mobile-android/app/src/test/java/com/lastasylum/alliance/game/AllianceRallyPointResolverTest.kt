package com.lastasylum.alliance.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AllianceRallyPointResolverTest {
    @Test
    fun fromBookmark_matchesRallyName() {
        val target = RaidShareTarget(
            seq = 1L,
            open = false,
            x = 400,
            y = 700,
            serverNumber = 109,
            shareType = 1,
            name = "Пункт сбора",
            playerName = null,
            union = null,
            cat = null,
            lv = null,
            power = null,
            kills = null,
            dialogTopPx = null,
            powerIcon = null,
            killsIcon = null,
            secretTaskId = null,
            grade = null,
            stars = null,
            qualityType = null,
        )
        val point = AllianceRallyPointResolver.fromBookmark(target, null)
        assertNotNull(point)
        assertEquals(400, point!!.x)
    }

    @Test
    fun fromAllianceTagBookmark_usesCoordsWithoutNameHint() {
        val target = RaidShareTarget(
            seq = 1L,
            open = false,
            x = 396,
            y = 689,
            serverNumber = 109,
            shareType = 1,
            name = "Метка",
            playerName = null,
            union = null,
            cat = null,
            lv = null,
            power = null,
            kills = null,
            dialogTopPx = null,
            powerIcon = null,
            killsIcon = null,
            secretTaskId = null,
            grade = null,
            stars = null,
            qualityType = null,
        )
        val point = AllianceRallyPointResolver.fromAllianceTagBookmark(target, null)
        assertNotNull(point)
        assertEquals(396, point!!.x)
        assertEquals(689, point.y)
    }

    @Test
    fun fromBookmark_ignoresUnrelatedTargets() {
        val target = RaidShareTarget(
            seq = 1L,
            open = false,
            x = 400,
            y = 700,
            serverNumber = 109,
            shareType = 1,
            name = "Монстр",
            playerName = null,
            union = null,
            cat = "SlgMonsterInfo",
            lv = 5,
            power = null,
            kills = null,
            dialogTopPx = null,
            powerIcon = null,
            killsIcon = null,
            secretTaskId = null,
            grade = null,
            stars = null,
            qualityType = null,
        )
        assertNull(AllianceRallyPointResolver.fromBookmark(target, null))
    }
}
