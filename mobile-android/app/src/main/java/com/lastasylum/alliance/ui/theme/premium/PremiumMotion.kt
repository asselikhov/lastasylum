package com.lastasylum.alliance.ui.theme.premium

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

object PremiumMotion {
    const val fadeFastMs = 120
    const val fadeMediumMs = 200
    const val fadeSlowMs = 320

    val cardPressSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium,
    )

    fun fadeTween(durationMillis: Int = fadeMediumMs) =
        tween<Float>(durationMillis = durationMillis, easing = FastOutSlowInEasing)

    fun fadeIntTween(durationMillis: Int = fadeMediumMs) =
        tween<Int>(durationMillis = durationMillis, easing = FastOutSlowInEasing)
}
