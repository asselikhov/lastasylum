package com.lastasylum.alliance.push

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameEventPushStripSuppressorTest {
    @After
    fun tearDown() {
        GameEventPushStripSuppressor.clearForTests()
    }

    @Test
    fun ackPushDelivered_suppressesStripForSameMessageId() {
        GameEventPushStripSuppressor.ackPushDelivered("msg-1")
        assertTrue(GameEventPushStripSuppressor.shouldSuppressStrip("msg-1"))
        assertFalse(GameEventPushStripSuppressor.shouldSuppressStrip("msg-2"))
    }

    @Test
    fun shouldSuppressStrip_ignoresBlankIds() {
        GameEventPushStripSuppressor.ackPushDelivered("  ")
        assertFalse(GameEventPushStripSuppressor.shouldSuppressStrip(null))
        assertFalse(GameEventPushStripSuppressor.shouldSuppressStrip("  "))
    }
}
