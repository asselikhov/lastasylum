package com.lastasylum.alliance.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lastasylum.alliance.ui.theme.SquadRelayPrimary
import com.lastasylum.alliance.ui.theme.SquadRelaySecondary
import com.lastasylum.alliance.ui.theme.SquadRelaySurfaces

private val tabIndicatorSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMedium,
)

data class TeamSectionTabSpec(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val accentStart: Color,
    val accentEnd: Color,
)

/**
 * Панель разделов команды: лёгкий сдвиг индикатора через [graphicsLayer] (без relayout),
 * без пружины и теней — плавное переключение без рывков.
 */
@Composable
fun TeamSectionTabBar(
    tabs: List<TeamSectionTabSpec>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (tabs.isEmpty()) return
    val scheme = MaterialTheme.colorScheme
    val selectedIndex = tabs.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
    val selectedTab = tabs[selectedIndex]
    val outerShape = RoundedCornerShape(18.dp)
    val segmentShape = RoundedCornerShape(12.dp)
    val inset = 4.dp
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = outerShape,
        color = SquadRelaySurfaces.panelColor(alpha = 0.48f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = SquadRelaySurfaces.panelBorder(alpha = 0.18f),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(inset)
                .height(50.dp),
        ) {
            val tabWidth = maxWidth / tabs.size
            val density = LocalDensity.current
            val targetOffsetPx = with(density) { (tabWidth * selectedIndex).toPx() }
            val indicatorOffsetPx by animateFloatAsState(
                targetValue = targetOffsetPx,
                animationSpec = tabIndicatorSpring,
                label = "teamSectionIndicatorPx",
            )

            Box(modifier = Modifier.matchParentSize()) {
                Box(
                    modifier = Modifier
                        .width(tabWidth)
                        .fillMaxHeight()
                        .graphicsLayer {
                            translationX = indicatorOffsetPx
                        }
                        .clip(segmentShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    selectedTab.accentStart.copy(alpha = 0.93f),
                                    selectedTab.accentEnd.copy(alpha = 0.86f),
                                ),
                            ),
                        ),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                tabs.forEachIndexed { index, tab ->
                    val selected = index == selectedIndex
                    val iconTint = if (selected) {
                        Color.White.copy(alpha = 0.98f)
                    } else {
                        tab.accentStart.copy(alpha = 0.70f)
                    }
                    val labelColor = if (selected) {
                        Color.White.copy(alpha = 0.96f)
                    } else {
                        scheme.onSurface.copy(alpha = 0.66f)
                    }
                    val interaction = remember(tab.id) { MutableInteractionSource() }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(segmentShape)
                            .clickable(
                                interactionSource = interaction,
                                indication = ripple(bounded = true, color = tab.accentStart),
                                onClick = { onSelect(tab.id) },
                            )
                            .padding(vertical = 5.dp, horizontal = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = iconTint,
                        )
                        Text(
                            text = tab.label,
                            modifier = Modifier.padding(top = 2.dp),
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                letterSpacing = 0.15.sp,
                            ),
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            color = labelColor,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Раздел остаётся в дереве композиции (состояние списков/навигации сохраняется),
 * но скрыт и не принимает касания, пока не активен.
 */
@Composable
fun TeamRetainedSection(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .zIndex(if (visible) 1f else 0f)
            .graphicsLayer { alpha = if (visible) 1f else 0f }
            .then(
                if (visible) {
                    Modifier
                } else {
                    Modifier.pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent(PointerEventPass.Initial)
                            }
                        }
                    }
                },
            ),
    ) {
        content()
    }
}

/** Акценты разделов команды в тоне SquadRelay. */
object TeamSectionTabAccents {
    val newsStart = SquadRelayPrimary
    val newsEnd = Color(0xFFC4B5FD)

    val forumStart = SquadRelaySecondary
    val forumEnd = Color(0xFF67E8F9)

    val membersStart = Color(0xFFF59E0B)
    val membersEnd = Color(0xFFFBBF24)
}
