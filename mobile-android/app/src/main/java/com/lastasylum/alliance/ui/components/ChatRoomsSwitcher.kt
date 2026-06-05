package com.lastasylum.alliance.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PushPin
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
import androidx.compose.ui.graphics.vector.ImageVector
import com.lastasylum.alliance.ui.util.parseServerNumberFromChatScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lastasylum.alliance.ui.theme.SquadRelayPrimary
import com.lastasylum.alliance.ui.theme.SquadRelaySurfaces

private val chipColorSpring = spring<Color>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessHigh,
)

data class ChatRoomTabSpec(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val accent: Color,
    val accentEnd: Color = accent,
    val unreadCount: Int = 0,
    val hasPinned: Boolean = false,
    /** Например `#` у серверной комнаты — показывается вместо [icon], без дубля в [label]. */
    val iconGlyph: String? = null,
)

/**
 * Лента комнат чата: иконка + полное название без обрезки (горизонтальный скролл при нехватке места).
 */
@Composable
fun ChatRoomsSwitcher(
    tabs: List<ChatRoomTabSpec>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (tabs.isEmpty()) return
    val trackShape = RoundedCornerShape(11.dp)
    val scrollState = rememberScrollState()
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = trackShape,
        color = SquadRelaySurfaces.subtleColor(alpha = 0.32f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(start = 3.dp, end = 3.dp, top = 5.dp, bottom = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEach { tab ->
                ChatRoomChip(
                    tab = tab,
                    selected = tab.id == selectedId,
                    onClick = { onSelect(tab.id) },
                )
            }
        }
    }
}

@Composable
private fun ChatRoomChip(
    tab: ChatRoomTabSpec,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val chipShape = RoundedCornerShape(9.dp)
    val containerColor by animateColorAsState(
        targetValue = if (selected) {
            tab.accent.copy(alpha = 0.20f)
        } else {
            Color.Transparent
        },
        animationSpec = chipColorSpring,
        label = "chatRoomChipBg",
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) {
            tab.accent.copy(alpha = 0.72f)
        } else {
            scheme.outline.copy(alpha = 0.10f)
        },
        animationSpec = chipColorSpring,
        label = "chatRoomChipBorder",
    )
    val iconTint by animateColorAsState(
        targetValue = if (selected) Color.White else Color.White.copy(alpha = 0.92f),
        animationSpec = chipColorSpring,
        label = "chatRoomChipIcon",
    )
    val iconBackdropAlpha = if (selected) 1f else 0.88f
    val labelColor by animateColorAsState(
        targetValue = if (selected) {
            scheme.onSurface.copy(alpha = 0.96f)
        } else {
            scheme.onSurfaceVariant.copy(alpha = 0.78f)
        },
        animationSpec = chipColorSpring,
        label = "chatRoomChipLabel",
    )
    val interaction = remember(tab.id) { MutableInteractionSource() }
    val showUnread = tab.unreadCount > 0
    Row(
        modifier = modifier
            .width(IntrinsicSize.Min)
            .widthIn(min = 52.dp)
            .clip(chipShape)
            .border(
                width = if (selected) 1.dp else 0.5.dp,
                color = borderColor,
                shape = chipShape,
            )
            .background(containerColor)
            .clickable(
                interactionSource = interaction,
                indication = ripple(bounded = true, color = tab.accent.copy(alpha = 0.35f)),
                onClick = onClick,
            )
            .padding(
                start = 10.dp,
                end = 10.dp,
                top = if (showUnread) 9.dp else 7.dp,
                bottom = 7.dp,
            ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .padding(top = if (showUnread) 2.dp else 0.dp)
                .height(24.dp)
                .widthIn(min = 24.dp),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .height(24.dp)
                    .widthIn(min = 24.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                tab.accent.copy(alpha = iconBackdropAlpha),
                                tab.accentEnd.copy(alpha = iconBackdropAlpha),
                            ),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (tab.iconGlyph != null) {
                    Text(
                        text = tab.iconGlyph,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = iconTint,
                    )
                } else {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = iconTint,
                    )
                }
            }
            if (showUnread) {
                ChatRoomUnreadBadge(
                    count = tab.unreadCount,
                    selected = selected,
                    accent = tab.accent,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 5.dp, y = (-5).dp),
                )
            } else if (tab.hasPinned) {
                Icon(
                    imageVector = Icons.Outlined.PushPin,
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-4).dp)
                        .size(10.dp),
                    tint = tab.accent.copy(alpha = if (selected) 1f else 0.85f),
                )
            }
        }
        Text(
            text = tab.label,
            modifier = Modifier.padding(start = 5.dp),
            softWrap = false,
            maxLines = 1,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 12.sp,
                letterSpacing = 0.sp,
            ),
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = labelColor,
        )
    }
}

@Composable
private fun ChatRoomUnreadBadge(
    count: Int,
    selected: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    if (count <= 0) return
    val badgeColor = if (selected) accent else Color(0xFFEF4444)
    val textColor = if (selected) Color.White else Color.White
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = badgeColor,
        shadowElevation = 1.dp,
        tonalElevation = 0.dp,
    ) {
        Text(
            text = if (count > 99) "99+" else count.toString(),
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            fontWeight = FontWeight.Bold,
            color = textColor,
            maxLines = 1,
        )
    }
}

/** Акценты вкладок комнат чата — у каждого типа свой градиент. */
object ChatRoomTabAccents {
    private data class AccentPair(val start: Color, val end: Color)

    private val globalUnion = AccentPair(Color(0xFF6366F1), Color(0xFF818CF8))
    private val allianceHub = AccentPair(Color(0xFF10B981), Color(0xFF34D399))
    private val raid = AccentPair(Color(0xFFEA580C), Color(0xFFFF7A45))
    private val other = AccentPair(SquadRelayPrimary, Color(0xFFC4B5FD))

    private val serverPalette = listOf(
        AccentPair(Color(0xFF0EA5E9), Color(0xFF38BDF8)),
        AccentPair(Color(0xFF14B8A6), Color(0xFF2DD4BF)),
        AccentPair(Color(0xFF8B5CF6), Color(0xFFA78BFA)),
        AccentPair(Color(0xFFEC4899), Color(0xFFF472B6)),
        AccentPair(Color(0xFFEAB308), Color(0xFFFACC15)),
        AccentPair(Color(0xFF22C55E), Color(0xFF4ADE80)),
        AccentPair(Color(0xFFF97316), Color(0xFFFB923C)),
        AccentPair(Color(0xFF3B82F6), Color(0xFF60A5FA)),
    )

    fun accentFor(
        kind: ChatRoomVisualKind,
        allianceId: String?,
    ): Pair<Color, Color> {
        val pair = when (kind) {
            ChatRoomVisualKind.GlobalUnion -> globalUnion
            ChatRoomVisualKind.AllianceHub -> allianceHub
            ChatRoomVisualKind.Raid -> raid
            ChatRoomVisualKind.Server -> {
                val n = parseServerNumberFromChatScope(allianceId) ?: 1
                serverPalette[(n - 1).coerceAtLeast(0) % serverPalette.size]
            }
            ChatRoomVisualKind.Other -> other
        }
        return pair.start to pair.end
    }
}

/** Тип комнаты для раскраски вкладок (зеркало [ChatScreen]). */
enum class ChatRoomVisualKind {
    GlobalUnion,
    Server,
    Raid,
    AllianceHub,
    Other,
}
