package com.lastasylum.alliance.ui.components.team

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.ui.theme.premium.PremiumColors

@Composable
fun PremiumJournalFeedShell(
    onClick: (() -> Unit)?,
    variant: JournalFeedVariant,
    modifier: Modifier = Modifier,
    isUnread: Boolean = false,
    contentTopPadding: androidx.compose.ui.unit.Dp = PremiumJournalFeedTokens.cardPaddingV,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    topContent: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
    footer: @Composable () -> Unit = {},
) {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && onClick != null) PremiumJournalFeedTokens.pressScale else 1f,
        animationSpec = PremiumJournalFeedTokens.pressAnimSpec,
        label = "journalFeedScale",
    )
    val glowBoost by animateFloatAsState(
        targetValue = if (pressed && onClick != null) 1.35f else 1f,
        animationSpec = PremiumJournalFeedTokens.pressAnimSpec,
        label = "journalFeedGlow",
    )
    val accent = PremiumJournalFeedTokens.accentFor(variant)
    val glassAlpha = if (isUnread) {
        PremiumJournalFeedTokens.glassAlphaUnread
    } else {
        PremiumJournalFeedTokens.glassAlphaDefault
    }
    val borderColors = PremiumJournalFeedTokens.gradientBorderColors(accent, glowBoost, isUnread)
    val outerShape = PremiumJournalFeedTokens.cardShape
    val innerShape = PremiumJournalFeedTokens.cardInnerShape
    val railColor = PremiumJournalFeedTokens.railColor(variant, isUnread)

    val clickModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = ripple(color = PremiumColors.accentCyan.copy(alpha = 0.18f)),
            onClick = onClick,
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .drawBehind {
                drawJournalCardShadow(accent, glowBoost)
            }
            .clip(outerShape)
            .background(Brush.linearGradient(borderColors), outerShape)
            .padding(PremiumJournalFeedTokens.borderWidth)
            .clip(innerShape)
            .background(PremiumJournalFeedTokens.glassFill(glassAlpha), innerShape)
            .drawWithContent {
                drawJournalInnerGlow(accent, glowBoost)
                drawContent()
            }
            .then(clickModifier),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(PremiumJournalFeedTokens.accentRailWidth)
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            railColor.copy(alpha = 0.95f),
                            railColor.copy(alpha = 0.4f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
        Column(Modifier.fillMaxWidth()) {
            topContent()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = PremiumJournalFeedTokens.accentRailWidth + 10.dp,
                        end = PremiumJournalFeedTokens.cardPaddingH,
                        top = contentTopPadding,
                        bottom = PremiumJournalFeedTokens.cardPaddingV,
                    ),
                content = content,
            )
            footer()
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawJournalCardShadow(
    accent: PremiumJournalFeedTokens.Accent,
    glowBoost: Float,
) {
    val boost = glowBoost.coerceAtMost(1.4f)
    drawRoundRect(
        color = Color.Black.copy(alpha = 0.38f),
        topLeft = Offset(0f, 8.dp.toPx()),
        size = size,
        cornerRadius = CornerRadius(22.dp.toPx()),
    )
    drawRoundRect(
        brush = Brush.radialGradient(
            colors = listOf(
                accent.primary.copy(alpha = 0.22f * boost),
                accent.secondary.copy(alpha = 0.10f * boost),
                Color.Transparent,
            ),
            center = Offset(size.width * 0.2f, size.height * 0.1f),
            radius = size.maxDimension * 0.9f,
        ),
        size = size,
        cornerRadius = CornerRadius(22.dp.toPx()),
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawJournalInnerGlow(
    accent: PremiumJournalFeedTokens.Accent,
    glowBoost: Float,
) {
    val edgeAlpha = 0.10f * glowBoost.coerceAtMost(1.35f)
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = edgeAlpha),
                accent.primary.copy(alpha = edgeAlpha * 0.4f),
                Color.Transparent,
            ),
            endY = size.height * 0.42f,
        ),
        size = size,
        cornerRadius = CornerRadius(20.dp.toPx()),
    )
}
