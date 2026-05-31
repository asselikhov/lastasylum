package com.lastasylum.alliance.overlay

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayQuickCommandStripPolicyTest {

    @After
    fun tearDown() {
        OverlayQuickCommandStripPolicy.clearForTests()
    }

    @Test
    fun markedQuickCommandSuppressesStrip() {
        val text = "Атака X:10 Y:20"
        OverlayQuickCommandStripPolicy.markOutgoingQuickCommand(text)
        assertTrue(OverlayQuickCommandStripPolicy.shouldSuppressOwnStripCard(text))
    }

    @Test
    fun clearedMarkDoesNotSuppressUnlessQuickCommandShape() {
        val text = "Атака X:10 Y:20"
        OverlayQuickCommandStripPolicy.markOutgoingQuickCommand(text)
        OverlayQuickCommandStripPolicy.clearOutgoingQuickCommand(text)
        assertTrue(OverlayQuickCommandStripPolicy.shouldSuppressOwnStripCard(text))
    }

    @Test
    fun nonQuickCommandTextNotSuppressedAfterClear() {
        val text = "hello team"
        OverlayQuickCommandStripPolicy.markOutgoingQuickCommand(text)
        OverlayQuickCommandStripPolicy.clearOutgoingQuickCommand(text)
        assertFalse(OverlayQuickCommandStripPolicy.shouldSuppressOwnStripCard(text))
    }

    @Test
    fun excavationNotifyShapeDetected() {
        assertTrue(OverlayQuickCommandStripPolicy.isQuickCommandShape("Раскопки альянса"))
    }

    @Test
    fun coordinateShapeDetected() {
        assertTrue(OverlayQuickCommandStripPolicy.isQuickCommandShape("Штурм X:1 Y:2"))
    }
}
