package com.lastasylum.alliance.push

import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PushNotificationSenderAvatarTest {
    @Test
    fun composeWithRank_cornersAreTransparent() {
        val bitmap = PushNotificationSenderAvatar.composeWithRank(
            avatar = null,
            squadRole = "R5",
            fallbackName = "Alice",
        )
        assertEquals(128, bitmap.width)
        assertEquals(128, bitmap.height)
        for ((x, y) in listOf(0 to 0, 127 to 0, 0 to 127, 127 to 127)) {
            assertEquals(
                "corner ($x,$y) should be transparent",
                0,
                Color.alpha(bitmap.getPixel(x, y)),
            )
        }
    }

    @Test
    fun composeWithRank_drawsAvatarAndRankChip() {
        val bitmap = PushNotificationSenderAvatar.composeWithRank(
            avatar = null,
            squadRole = "R5",
            fallbackName = "Alice",
        )
        val centerAlpha = Color.alpha(bitmap.getPixel(64, 58))
        assertTrue("avatar center should be opaque", centerAlpha > 200)
        val chipAlpha = Color.alpha(bitmap.getPixel(64, 108))
        assertTrue("rank chip area should be visible", chipAlpha > 100)
    }

    @Test
    fun composeWithRank_withoutRole_omitsChip() {
        val bitmap = PushNotificationSenderAvatar.composeWithRank(
            avatar = null,
            squadRole = null,
            fallbackName = "Bob",
        )
        val belowAvatarAlpha = Color.alpha(bitmap.getPixel(64, 118))
        assertTrue("no rank chip below avatar", belowAvatarAlpha < 50)
    }
}
