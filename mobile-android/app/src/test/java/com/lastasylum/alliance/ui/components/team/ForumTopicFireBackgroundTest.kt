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

    @Test
    fun flameTurbulenceOffset_withinAmplitudeBounds() {
        val amplitude = 32f
        repeat(8) { lobeIndex ->
            repeat(12) { step ->
                val phase = step / 12f
                val offset = flameTurbulenceOffset(phase, lobeIndex, amplitude)
                assertTrue(offset in -amplitude..amplitude)
            }
        }
    }

    @Test
    fun flameTurbulenceOffset_zeroAtZeroPhaseAndIndex() {
        val offset = flameTurbulenceOffset(0f, 0, 40f)
        assertEquals(0f, offset, 0.001f)
    }

    @Test
    fun sparkArcPosition_appliesRiseAndSway() {
        val (x, y) = sparkArcPosition(baseX = 100f, baseY = 200f, rise = 30f, sway = 12f)
        assertEquals(112f, x, 0.001f)
        assertEquals(170f, y, 0.001f)
    }

    @Test
    fun sparkArcPosition_riseIncreasesUpward() {
        val low = sparkArcPosition(50f, 100f, rise = 10f, sway = 0f).second
        val high = sparkArcPosition(50f, 100f, rise = 40f, sway = 0f).second
        assertTrue(high < low)
    }

    @Test
    fun crackleSpotAlpha_withinUnitRange() {
        repeat(5) { spotIndex ->
            repeat(10) { step ->
                val crackle = step / 10f
                val alpha = crackleSpotAlpha(crackle, spotIndex)
                assertTrue(alpha in 0f..1f)
            }
        }
    }

    @Test
    fun crackleSpotAlpha_variesBySpotIndex() {
        val a0 = crackleSpotAlpha(0.25f, 0)
        val a1 = crackleSpotAlpha(0.25f, 1)
        assertTrue(a0 != a1 || crackleSpotAlpha(0.5f, 0) != crackleSpotAlpha(0.5f, 1))
    }
}
