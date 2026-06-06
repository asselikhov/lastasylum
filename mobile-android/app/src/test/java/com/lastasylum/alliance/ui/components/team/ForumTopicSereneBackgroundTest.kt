package com.lastasylum.alliance.ui.components.team

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class ForumTopicSereneBackgroundTest {

    @Test
    fun sereneBreathAlpha_withinExpectedRange() {
        val low = sereneBreathAlpha(0f)
        val high = sereneBreathAlpha(0.25f)
        assertTrue(low in 0.04f..0.11f)
        assertTrue(high in 0.04f..0.11f)
        assertTrue(high >= low)
    }

    @Test
    fun sereneBreathAlpha_cyclesWithPhase() {
        val a = sereneBreathAlpha(0f)
        val b = sereneBreathAlpha(0.5f)
        assertTrue(a != b)
    }

    @Test
    fun sereneOrbAlpha_scalesWithPulse() {
        val lowPulse = sereneOrbAlpha(phase = PI.toFloat(), pulse = 0f)
        val highPulse = sereneOrbAlpha(phase = PI.toFloat(), pulse = 1f)
        assertTrue(highPulse > lowPulse)
    }

    @Test
    fun sereneOrbAlpha_withinSoftBounds() {
        val alpha = sereneOrbAlpha(phase = 1f, pulse = 0.5f)
        assertTrue(alpha in 0.03f..0.10f)
    }
}
