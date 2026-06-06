package com.lastasylum.alliance.ui.components.team

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.ui.theme.premium.PremiumColors
import kotlin.math.sin

@Composable
fun ForumTopicAnimatedShell(
    onClick: () -> Unit,
    accent: ForumTopicCardTokens.Accent,
    activityLevel: ForumTopicCardTokens.ActivityLevel,
    animationTier: FeedAnimationTier,
    modifier: Modifier = Modifier,
    emberBoost: Float = 1f,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable BoxScope.() -> Unit,
) {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) ForumTopicCardTokens.pressScale else 1f,
        animationSpec = ForumTopicCardTokens.pressAnimSpec,
        label = "forumCardScale",
    )
    val glowBoost by animateFloatAsState(
        targetValue = if (pressed) 1.35f else 1f,
        animationSpec = ForumTopicCardTokens.pressAnimSpec,
        label = "forumCardGlowBoost",
    )

    val animActive = animationTier != FeedAnimationTier.Off
    val fullAnim = animationTier == FeedAnimationTier.Full
    val liteAnim = animationTier == FeedAnimationTier.Lite
    val isHot = activityLevel == ForumTopicCardTokens.ActivityLevel.Hot

    val drift: Float
    val pulse: Float
    val flicker: Float
    val heatPhase: Float
    if (animActive) {
        val infinite = rememberInfiniteTransition(label = "forumCardAmbient")
        drift = infinite.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(if (fullAnim) 14_000 else 18_000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "forumDrift",
        ).value
        pulse = if (fullAnim) {
            infinite.animateFloat(
                initialValue = 0.55f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2_800, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "forumPulse",
            ).value
        } else {
            0.75f
        }
        flicker = infinite.animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    when {
                        fullAnim && isHot -> 720
                        fullAnim -> 2_200
                        else -> 1_800
                    },
                    easing = FastOutSlowInEasing,
                ),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "forumFlicker",
        ).value
        heatPhase = if (fullAnim) {
            infinite.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(if (isHot) 9_000 else 20_000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
                label = "forumHeatPhase",
            ).value
        } else {
            drift
        }
    } else {
        drift = 0f
        pulse = 0.75f
        flicker = 0.5f
        heatPhase = 0f
    }

    val glassAlpha = ForumTopicCardTokens.glassAlpha(activityLevel)
    val borderColors = if (isHot) {
        ForumTopicCardTokens.gradientBorderColors(accent, activityLevel, glowBoost)
    } else {
        ForumTopicCardTokens.gradientBorderColors(
            accent,
            ForumTopicCardTokens.ActivityLevel.Calm,
            glowBoost,
        )
    }
    val outerShape = ForumTopicCardTokens.cardShape
    val innerShape = ForumTopicCardTokens.cardInnerShape
    val showActivityStrip = animActive && isHot

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(ForumTopicCardTokens.cardFixedHeight)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .drawBehind {
                drawTacticalCardShadow(activityLevel, accent, glowBoost, flicker)
            }
            .clip(outerShape)
            .background(Brush.linearGradient(borderColors), outerShape)
            .padding(ForumTopicCardTokens.borderWidth)
            .clip(innerShape)
            .background(ForumTopicCardTokens.glassFillBrush(glassAlpha), innerShape)
            .drawWithContent {
                when {
                    fullAnim && isHot -> drawForumFireBackground(
                        accent, drift, flicker, heatPhase, emberBoost,
                    )
                    liteAnim && isHot -> drawForumFireBackgroundLite(accent, drift, flicker)
                    fullAnim && !isHot -> drawForumSereneBackground(
                        accent = accent,
                        drift = drift,
                        pulse = pulse,
                        breathPhase = heatPhase,
                    )
                    liteAnim && !isHot -> drawForumSereneBackgroundLite(accent, drift, pulse)
                }
                if (showActivityStrip) {
                    drawForumActivityStripOverlay(
                        accent = accent,
                        activityLevel = activityLevel,
                        drift = drift,
                        pulse = pulse,
                        flicker = flicker,
                        hot = isHot && (fullAnim || liteAnim),
                    )
                }
                drawTacticalInnerEdgeGlow(accent, activityLevel, pulse)
                drawContent()
            }
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = PremiumColors.accentCyan.copy(alpha = 0.18f)),
                onClick = onClick,
            ),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(ForumTopicCardTokens.accentRailWidth)
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            railColor(activityLevel, accent).copy(alpha = 0.92f),
                            railColor(activityLevel, accent).copy(alpha = 0.35f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = ForumTopicCardTokens.accentRailWidth + 8.dp,
                    end = ForumTopicCardTokens.cardPaddingH,
                    top = ForumTopicCardTokens.cardPaddingV,
                    bottom = ForumTopicCardTokens.cardPaddingV,
                ),
            contentAlignment = Alignment.CenterStart,
            content = content,
        )
    }
}

private fun railColor(
    activityLevel: ForumTopicCardTokens.ActivityLevel,
    accent: ForumTopicCardTokens.Accent,
): Color = when (activityLevel) {
    ForumTopicCardTokens.ActivityLevel.Hot -> ForumTopicCardTokens.FirePalette.orange
    ForumTopicCardTokens.ActivityLevel.Warm,
    ForumTopicCardTokens.ActivityLevel.Calm,
    -> accent.primary.copy(alpha = 0.72f)
}

/** @deprecated Use [ForumTopicAnimatedShell] with explicit [FeedAnimationTier]. */
@Composable
fun ForumTopicTacticalCardShell(
    onClick: () -> Unit,
    accent: ForumTopicCardTokens.Accent,
    activityLevel: ForumTopicCardTokens.ActivityLevel,
    modifier: Modifier = Modifier,
    animationsEnabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable BoxScope.() -> Unit,
) {
    val tier = if (animationsEnabled) {
        when (activityLevel) {
            ForumTopicCardTokens.ActivityLevel.Hot -> FeedAnimationTier.Full
            ForumTopicCardTokens.ActivityLevel.Warm -> FeedAnimationTier.Lite
            ForumTopicCardTokens.ActivityLevel.Calm -> FeedAnimationTier.Off
        }
    } else {
        FeedAnimationTier.Off
    }
    ForumTopicAnimatedShell(
        onClick = onClick,
        accent = accent,
        activityLevel = activityLevel,
        animationTier = tier,
        modifier = modifier,
        interactionSource = interactionSource,
        content = content,
    )
}

private fun DrawScope.drawTacticalCardShadow(
    activityLevel: ForumTopicCardTokens.ActivityLevel,
    accent: ForumTopicCardTokens.Accent,
    glowBoost: Float,
    flicker: Float,
) {
    val firePalette = ForumTopicCardTokens.FirePalette
    val corner = FeedCardDesignTokens.compactCornerRadius.toPx()
    val intensity = when (activityLevel) {
        ForumTopicCardTokens.ActivityLevel.Hot -> 1.15f
        ForumTopicCardTokens.ActivityLevel.Warm -> 1.0f
        ForumTopicCardTokens.ActivityLevel.Calm -> 0.85f
    }
    val baseAlpha = 0.14f * intensity * glowBoost.coerceAtMost(1.4f)
    drawRoundRect(
        color = Color.Black.copy(alpha = 0.36f),
        topLeft = Offset(0f, 6.dp.toPx()),
        size = size,
        cornerRadius = CornerRadius(corner),
    )
    val glowCenter = if (activityLevel == ForumTopicCardTokens.ActivityLevel.Hot) {
        Offset(
            size.width * (0.5f + sin(flicker * 6.28f) * 0.04f),
            size.height * 0.88f,
        )
    } else {
        Offset(size.width * 0.18f, size.height * 0.12f)
    }
    val glowPrimary = if (activityLevel == ForumTopicCardTokens.ActivityLevel.Hot) {
        firePalette.orange
    } else {
        accent.primary
    }
    val glowSecondary = if (activityLevel == ForumTopicCardTokens.ActivityLevel.Hot) {
        firePalette.deep
    } else {
        accent.secondary
    }
    drawRoundRect(
        brush = Brush.radialGradient(
            colors = listOf(
                glowPrimary.copy(alpha = baseAlpha),
                glowSecondary.copy(alpha = baseAlpha * 0.45f),
                Color.Transparent,
            ),
            center = glowCenter,
            radius = size.maxDimension * 0.85f,
        ),
        size = size,
        cornerRadius = CornerRadius(corner),
    )
}

private fun DrawScope.drawTacticalInnerEdgeGlow(
    accent: ForumTopicCardTokens.Accent,
    activityLevel: ForumTopicCardTokens.ActivityLevel,
    pulse: Float,
) {
    val corner = FeedCardDesignTokens.compactInnerCornerRadius.toPx()
    val edgeAlpha = when (activityLevel) {
        ForumTopicCardTokens.ActivityLevel.Hot -> 0.10f + pulse * 0.05f
        ForumTopicCardTokens.ActivityLevel.Warm -> 0.08f + pulse * 0.03f
        ForumTopicCardTokens.ActivityLevel.Calm -> 0.06f
    }
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = edgeAlpha),
                accent.primary.copy(alpha = edgeAlpha * 0.35f),
                Color.Transparent,
            ),
            endY = size.height * 0.4f,
        ),
        size = size,
        cornerRadius = CornerRadius(corner),
    )
    drawRoundRect(
        brush = Brush.horizontalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.08f + pulse * 0.04f),
                Color.White.copy(alpha = 0.03f),
                Color.Transparent,
            ),
            endX = size.width * 0.55f,
        ),
        topLeft = Offset(0f, 0f),
        size = androidx.compose.ui.geometry.Size(size.width, 1.dp.toPx()),
        cornerRadius = CornerRadius(corner, corner),
    )
}
