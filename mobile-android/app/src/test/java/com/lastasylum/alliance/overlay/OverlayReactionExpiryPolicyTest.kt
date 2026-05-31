package com.lastasylum.alliance.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayReactionExpiryPolicyTest {

    @Test
    fun heroExpiry_textReactionIsLonger() {
        val textId = encodeTextReactionId("hello")!!
        val normal = OverlayReactionExpiryPolicy.heroExpiryMs("heart", extended = false, burstMode = false)
        val text = OverlayReactionExpiryPolicy.heroExpiryMs(textId, extended = false, burstMode = false)
        assertTrue(text > normal)
    }

    @Test
    fun miniExpiry_staggerIncreasesWithIndex() {
        val a = OverlayReactionExpiryPolicy.miniExpiryMs("heart", burstMode = false, slotIndex = 0)
        val b = OverlayReactionExpiryPolicy.miniExpiryMs("heart", burstMode = false, slotIndex = 2)
        assertEquals(OverlayReactionStageLayout.STAGGER_MS * 2, b - a)
    }

    @Test
    fun miniExpiry_stickerShorterThanLottie() {
        val lottie = OverlayReactionExpiryPolicy.miniExpiryMs("heart", burstMode = false, slotIndex = 0)
        val sticker = OverlayReactionExpiryPolicy.miniExpiryMs("sticker_01", burstMode = false, slotIndex = 0)
        assertTrue(sticker < lottie)
    }
}
