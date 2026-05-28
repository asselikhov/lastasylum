package com.lastasylum.alliance.ui.components.premium

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.ui.components.team.FeedCardDesignTokens
import com.lastasylum.alliance.ui.components.team.FeedCardVariant
import com.lastasylum.alliance.ui.components.team.TeamFeedCardTokens
import com.lastasylum.alliance.ui.theme.premium.PremiumCardShape
import com.lastasylum.alliance.ui.theme.premium.PremiumSurfaces

/**
 * Interactive feed card shell: glass, press scale, optional accent bar and unread border.
 */
@Composable
fun PremiumFeedCardShell(
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    variant: FeedCardVariant = FeedCardVariant.News,
    isUnread: Boolean = false,
    accentColor: androidx.compose.ui.graphics.Color? = null,
    showLiveAccent: Boolean = false,
    listMode: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    contentPadding: PaddingValues = PaddingValues(FeedCardDesignTokens.contentPadding),
    topContent: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
    footer: @Composable () -> Unit = {},
) {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) TeamFeedCardTokens.pressScale else 1f,
        animationSpec = TeamFeedCardTokens.pressAnimSpec,
        label = "feedCardScale",
    )
    val restElevation = if (listMode) {
        FeedCardDesignTokens.listShadowElevation
    } else {
        when {
            isUnread -> FeedCardDesignTokens.shadowUnread
            else -> FeedCardDesignTokens.shadowRest
        }
    }
    val elevation: Dp = if (listMode) {
        if (pressed) FeedCardDesignTokens.shadowPressed else restElevation
    } else {
        val animated by animateDpAsState(
            targetValue = when {
                pressed -> FeedCardDesignTokens.shadowPressed
                isUnread -> FeedCardDesignTokens.shadowUnread
                else -> FeedCardDesignTokens.shadowRest
            },
            animationSpec = TeamFeedCardTokens.pressElevationAnimSpec,
            label = "feedCardElevation",
        )
        animated
    }
    val layerAlpha = when (variant) {
        FeedCardVariant.Member -> if (showLiveAccent) {
            FeedCardDesignTokens.layerAlphaIngame
        } else {
            FeedCardDesignTokens.layerAlphaOffline
        }
        else -> if (isUnread) FeedCardDesignTokens.layerAlphaUnread else FeedCardDesignTokens.layerAlphaDefault
    }
    val border = when {
        isUnread -> BorderStroke(1.5.dp, FeedCardDesignTokens.unreadBorderColor.copy(alpha = 0.5f))
        else -> PremiumSurfaces.panelBorder()
    }
    val clickModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = ripple(),
            onClick = onClick,
        )
    } else {
        Modifier
    }
    val accent = accentColor ?: if (showLiveAccent) FeedCardDesignTokens.liveRingColor else null

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(clickModifier),
    ) {
        PremiumGlassSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = PremiumCardShape,
            shadowElevation = elevation,
            layerAlpha = layerAlpha,
            border = border,
            showInnerGlow = !listMode,
        ) {
            Box(Modifier.fillMaxWidth()) {
                if (accent != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .width(FeedCardDesignTokens.accentBarWidth)
                            .matchParentSize()
                            .background(accent.copy(alpha = 0.85f)),
                    )
                }
                Column(Modifier.fillMaxWidth()) {
                    topContent()
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(contentPadding),
                        content = content,
                    )
                    footer()
                }
            }
        }
    }
}

/** @see PremiumFeedCardShell */
@Composable
fun PremiumFeedCard(
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    isUnread: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(FeedCardDesignTokens.contentPadding),
    content: @Composable ColumnScope.() -> Unit,
) {
    PremiumFeedCardShell(
        onClick = onClick,
        modifier = modifier,
        isUnread = isUnread,
        contentPadding = contentPadding,
        content = content,
    )
}
