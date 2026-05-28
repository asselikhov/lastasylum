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
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
fun ForumTopicTacticalCardShell(
    onClick: () -> Unit,
    accent: ForumTopicCardTokens.Accent,
    activityLevel: ForumTopicCardTokens.ActivityLevel,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable BoxScope.() -> Unit,
) {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) ForumTopicCardTokens.pressScale else 1f,
        animationSpec = ForumTopicCardTokens.pressAnimSpec,
        label = "forumTacticalCardScale",
    )
    val glowBoost by animateFloatAsState(
        targetValue = if (pressed) 1.35f else 1f,
        animationSpec = ForumTopicCardTokens.pressAnimSpec,
        label = "forumTacticalGlowBoost",
    )
    val infinite = rememberInfiniteTransition(label = "forumTacticalAmbient")
    val drift by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(12_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "forumTacticalDrift",
    )
    val pulse by infinite.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2_400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "forumTacticalPulse",
    )

    val glassAlpha = ForumTopicCardTokens.glassAlpha(activityLevel)
    val borderColors = ForumTopicCardTokens.gradientBorderColors(accent, activityLevel, glowBoost)
    val outerShape = ForumTopicCardTokens.cardShape
    val innerShape = ForumTopicCardTokens.cardInnerShape
    val showActivityStrip = activityLevel != ForumTopicCardTokens.ActivityLevel.Calm

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .drawBehind {
                drawTacticalCardShadow(activityLevel, accent, glowBoost)
            }
            .clip(outerShape)
            .background(Brush.linearGradient(borderColors), outerShape)
            .padding(ForumTopicCardTokens.borderWidth)
            .clip(innerShape)
            .background(ForumTopicCardTokens.glassFill(glassAlpha), innerShape)
            .drawWithContent {
                drawTacticalFogParticles(drift, accent)
                drawTacticalInnerEdgeGlow(accent, activityLevel, pulse)
                drawContent()
            }
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = PremiumColors.accentCyan.copy(alpha = 0.18f)),
                onClick = onClick,
            ),
    ) {
        if (showActivityStrip) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ForumTopicCardTokens.activityStripHeight)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                accent.primary.copy(alpha = 0.15f + pulse * 0.35f),
                                accent.secondary.copy(alpha = 0.22f + pulse * 0.28f),
                                PremiumColors.accentCyan.copy(alpha = 0.18f + pulse * 0.22f),
                                Color.Transparent,
                            ),
                        ),
                    ),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = ForumTopicCardTokens.cardPaddingH,
                    vertical = ForumTopicCardTokens.cardPaddingV,
                ),
            content = content,
        )
    }
}

private fun DrawScope.drawTacticalCardShadow(
    activityLevel: ForumTopicCardTokens.ActivityLevel,
    accent: ForumTopicCardTokens.Accent,
    glowBoost: Float,
) {
    val baseAlpha = when (activityLevel) {
        ForumTopicCardTokens.ActivityLevel.Hot -> 0.34f
        ForumTopicCardTokens.ActivityLevel.Warm -> 0.24f
        ForumTopicCardTokens.ActivityLevel.Calm -> 0.16f
    } * glowBoost.coerceAtMost(1.4f)
    drawRoundRect(
        color = Color.Black.copy(alpha = 0.42f),
        topLeft = Offset(0f, 10.dp.toPx()),
        size = size,
        cornerRadius = CornerRadius(22.dp.toPx()),
    )
    drawRoundRect(
        brush = Brush.radialGradient(
            colors = listOf(
                accent.primary.copy(alpha = baseAlpha),
                accent.secondary.copy(alpha = baseAlpha * 0.45f),
                Color.Transparent,
            ),
            center = Offset(size.width * 0.18f, size.height * 0.12f),
            radius = size.maxDimension * 0.95f,
        ),
        size = size,
        cornerRadius = CornerRadius(22.dp.toPx()),
    )
}

private fun DrawScope.drawTacticalFogParticles(drift: Float, accent: ForumTopicCardTokens.Accent) {
    val specs = rememberFogParticleSpecs()
    specs.forEachIndexed { index, spec ->
        val phase = drift * 6.283f + index * 1.7f
        val x = size.width * (spec.baseX + sin(phase) * spec.wobbleX)
        val y = size.height * (spec.baseY + sin(phase * 0.85f) * spec.wobbleY)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    accent.secondary.copy(alpha = spec.alpha),
                    Color.Transparent,
                ),
                center = Offset(x, y),
                radius = spec.radius,
            ),
            radius = spec.radius,
            center = Offset(x, y),
        )
    }
}

private fun DrawScope.drawTacticalInnerEdgeGlow(
    accent: ForumTopicCardTokens.Accent,
    activityLevel: ForumTopicCardTokens.ActivityLevel,
    pulse: Float,
) {
    val edgeAlpha = when (activityLevel) {
        ForumTopicCardTokens.ActivityLevel.Hot -> 0.14f + pulse * 0.08f
        ForumTopicCardTokens.ActivityLevel.Warm -> 0.10f + pulse * 0.05f
        ForumTopicCardTokens.ActivityLevel.Calm -> 0.06f
    }
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = edgeAlpha),
                accent.primary.copy(alpha = edgeAlpha * 0.35f),
                Color.Transparent,
            ),
            startY = 0f,
            endY = size.height * 0.45f,
        ),
        size = size,
        cornerRadius = CornerRadius(20.dp.toPx()),
    )
}

private data class FogParticleSpec(
    val baseX: Float,
    val baseY: Float,
    val wobbleX: Float,
    val wobbleY: Float,
    val radius: Float,
    val alpha: Float,
)

private fun rememberFogParticleSpecs(): List<FogParticleSpec> = listOf(
    FogParticleSpec(0.12f, 0.22f, 0.04f, 0.03f, 38f, 0.07f),
    FogParticleSpec(0.78f, 0.18f, 0.05f, 0.04f, 44f, 0.05f),
    FogParticleSpec(0.62f, 0.72f, 0.03f, 0.05f, 52f, 0.04f),
    FogParticleSpec(0.28f, 0.68f, 0.04f, 0.03f, 34f, 0.06f),
)
