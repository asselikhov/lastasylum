package com.lastasylum.alliance.ui.components.team.journal

import org.junit.Assert.assertEquals
import org.junit.Test

class TeamNewsImageGalleryLayoutTest {
    @Test
    fun layout_byCount() {
        assertEquals(TeamNewsGalleryLayout.None, teamNewsGalleryLayout(0))
        assertEquals(TeamNewsGalleryLayout.SingleHero, teamNewsGalleryLayout(1))
        assertEquals(TeamNewsGalleryLayout.TwoColumn, teamNewsGalleryLayout(2))
        assertEquals(TeamNewsGalleryLayout.ThreeMasonry, teamNewsGalleryLayout(3))
        assertEquals(TeamNewsGalleryLayout.FourGrid, teamNewsGalleryLayout(4))
        assertEquals(TeamNewsGalleryLayout.FourPlusOverlay, teamNewsGalleryLayout(5))
        assertEquals(TeamNewsGalleryLayout.FourPlusOverlay, teamNewsGalleryLayout(12))
    }
}
