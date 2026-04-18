package com.lastasylum.alliance.overlay

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OverlayWindowDragHelperTest {
    @Test
    fun dragSlopPx_isPositiveAndBounded() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val px = OverlayWindowDragHelper.dragSlopPx(ctx, minDp = 3, maxDp = 12)
        assertTrue(px >= 1)
        val dm = ctx.resources.displayMetrics
        val maxPx = (12 * dm.density).toInt().coerceAtLeast(1)
        assertTrue(px <= maxPx)
    }
}
