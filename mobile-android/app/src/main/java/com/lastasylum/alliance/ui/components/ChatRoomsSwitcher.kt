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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lastasylum.alliance.R
import com.lastasylum.alliance.ui.theme.SquadRelayPrimary
import com.lastasylum.alliance.ui.theme.SquadRelaySecondary
import com.lastasylum.alliance.ui.theme.SquadRelaySurfaces

private val roomIndicatorSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMedium,
)

data class ChatRoomTabSpec(
    val id: String,
    val label: String,
    val subtitle: String,
    val icon: ImageVector,
    val accentStart: Color,
    val accentEnd: Color,
    val unreadCount: Int = 0,
)

/**
 * Переключатель комнат чата: стеклянная панель, плавный индикатор, иконка + подпись канала.
 */
@Composable
fun ChatRoomsSwitcher(
    tabs: List<ChatRoomTabSpec>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (tabs.isEmpty()) return
    val scheme = MaterialTheme.colorScheme
    val selectedIndex = tabs.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
    val selectedTab = tabs[selectedIndex]
    val outerShape = RoundedCornerShape(20.dp)
    val segmentShape = RoundedCornerShape(14.dp)
    val inset = 5.dp

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.chat_rooms_switcher_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurface.copy(alpha = 0.94f),
                )
                Text(
                    text = stringResource(R.string.chat_rooms_switcher_caption),
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant.copy(alpha = 0.78f),
                )
            }
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = SquadRelaySurfaces.subtleColor(0.55f),
                border = SquadRelaySurfaces.panelBorder(alpha = 0.12f),
            ) {
                Text(
                    text = "${selectedIndex + 1}/${tabs.size}",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = scheme.onSurfaceVariant.copy(alpha = 0.85f),
                )
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = outerShape,
            color = SquadRelaySurfaces.panelColor(alpha = 0.52f),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = SquadRelaySurfaces.panelBorder(alpha = 0.20f),
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(inset)
                    .height(58.dp),
            ) {
                val tabWidth = maxWidth / tabs.size
                val density = LocalDensity.current
                val targetOffsetPx = with(density) { (tabWidth * selectedIndex).toPx() }
                val indicatorOffsetPx by animateFloatAsState(
                    targetValue = targetOffsetPx,
                    animationSpec = roomIndicatorSpring,
                    label = "chatRoomIndicatorPx",
                )

                Box(modifier = Modifier.matchParentSize()) {
                    Box(
                        modifier = Modifier
                            .width(tabWidth)
                            .fillMaxHeight()
                            .graphicsLayer { translationX = indicatorOffsetPx }
                            .clip(segmentShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        selectedTab.accentStart.copy(alpha = 0.94f),
                                        selectedTab.accentEnd.copy(alpha = 0.88f),
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
                            tab.accentStart.copy(alpha = 0.72f)
                        }
                        val titleColor = if (selected) {
                            Color.White.copy(alpha = 0.97f)
                        } else {
                            scheme.onSurface.copy(alpha = 0.88f)
                        }
                        val subtitleColor = if (selected) {
                            Color.White.copy(alpha = 0.78f)
                        } else {
                            scheme.onSurfaceVariant.copy(alpha = 0.62f)
                        }
                        val interaction = remember(tab.id) { MutableInteractionSource() }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(segmentShape)
                                .clickable(
                                    interactionSource = interaction,
                                    indication = ripple(bounded = true, color = tab.accentStart),
                                    onClick = { onSelect(tab.id) },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 6.dp, vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Box(contentAlignment = Alignment.TopEnd) {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = iconTint,
                                    )
                                    if (tab.unreadCount > 0) {
                                        ChatRoomUnreadDot(
                                            count = tab.unreadCount,
                                            selected = selected,
                                            accent = tab.accentStart,
                                            modifier = Modifier.offset(x = 6.dp, y = (-4).dp),
                                        )
                                    }
                                }
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    text = tab.label,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontSize = 12.sp,
                                        letterSpacing = 0.1.sp,
                                    ),
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                    color = titleColor,
                                )
                                Text(
                                    text = tab.subtitle,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                    color = subtitleColor,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatRoomUnreadDot(
    count: Int,
    selected: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val badgeText = if (count > 99) "99+" else count.toString()
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = if (selected) {
            Color.White.copy(alpha = 0.95f)
        } else {
            Color(0xFFEF4444)
        },
        shadowElevation = if (selected) 0.dp else 1.dp,
    ) {
        Text(
            text = badgeText,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
            ),
            color = if (selected) accent else Color.White,
        )
    }
}

/** Акценты комнат чата в тоне SquadRelay. */
object ChatRoomTabAccents {
    val unionStart = Color(0xFF6366F1)
    val unionEnd = Color(0xFF818CF8)

    val raidStart = Color(0xFFFF6B35)
    val raidEnd = Color(0xFFFF9F6B)

    val hubStart = Color(0xFF0D9488)
    val hubEnd = Color(0xFF2DD4BF)

    val otherStart = SquadRelayPrimary
    val otherEnd = Color(0xFFC4B5FD)

    val defaultStart = SquadRelaySecondary
    val defaultEnd = Color(0xFF67E8F9)
}
