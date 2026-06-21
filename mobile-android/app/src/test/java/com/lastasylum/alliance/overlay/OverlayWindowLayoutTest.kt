package com.lastasylum.alliance.overlay

import android.view.WindowManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayWindowLayoutTest {
    @Test
    fun popupFlags_includeNotFocusable() {
        val f = OverlayWindowLayout.popupWindowFlags()
        assertTrue(f and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0)
    }

    @Test
    fun overlayModalFlags_excludeNotFocusable() {
        val f = OverlayWindowLayout.overlayModalWindowFlags()
        assertEquals(0, f and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        assertTrue(f and WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL != 0)
    }
}
