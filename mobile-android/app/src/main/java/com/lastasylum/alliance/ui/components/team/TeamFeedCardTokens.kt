package com.lastasylum.alliance.ui.components.team

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.ui.theme.premium.PremiumCardShape
import com.lastasylum.alliance.ui.theme.premium.PremiumChipShape

object TeamFeedCardTokens {
    val cardShape = PremiumCardShape
    val chipShape = PremiumChipShape
    val cardRadius = 26.dp
    val listSpacing = 12.dp
    val memberListSpacing = 10.dp
    val pressScale = 0.985f
    val shadowRest = 8.dp
    val shadowPressed = 4.dp
    val pressAnimSpec = tween<Float>(durationMillis = 120, easing = FastOutSlowInEasing)
    val pressElevationAnimSpec = tween<Dp>(durationMillis = 120, easing = FastOutSlowInEasing)
}
