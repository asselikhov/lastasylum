package com.lastasylum.alliance.ui.components.team

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class ForumTopicFireBackgroundTest {

    @Test
    fun flameLobeHeight_withinExpectedBounds() {
        val base = 100f
        val min = flameLobeHeight(base, phase = 0f, pulse = 0f)
        val max = flameLobeHeight(base, phase = PI.toFloat(), pulse = 1f)
        assertTrue(min in 55f..85f)
        assertTrue(max in 70f..100f)
        assertTrue(max >= min)
    }

    @Test
    fun flameLobeHeight_scalesWithBaseHeight() {
        val low = flameLobeHeight(50f, phase = 1f, pulse = 0.5f)
        val high = flameLobeHeight(100f, phase = 1f, pulse = 0.5f)
        assertEquals(low * 2f, high, 0.01f)
    }

    @Test
    fun emberOpacityAtRise_fadesMonotonically() {
        val atBase = emberOpacityAtRise(0f)
        val mid = emberOpacityAtRise(0.5f)
        val apex = emberOpacityAtRise(1f)
        assertTrue(atBase > mid)
        assertTrue(mid > apex)
        assertEquals(0f, apex, 0.001f)
    }

    @Test
    fun emberOpacityAtRise_clampsOutOfRange() {
        assertEquals(emberOpacityAtRise(0f), emberOpacityAtRise(-0.5f), 0.001f)
        assertEquals(emberOpacityAtRise(1f), emberOpacityAtRise(1.5f), 0.001f)
    }
}
