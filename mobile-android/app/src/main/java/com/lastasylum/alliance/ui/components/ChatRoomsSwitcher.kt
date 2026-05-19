package com.lastasylum.alliance.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lastasylum.alliance.ui.theme.SquadRelayPrimary
import com.lastasylum.alliance.ui.theme.SquadRelaySecondary
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
    val unreadCount: Int = 0,
)

/**
 * Компактная лента комнат чата: только иконка + название, без шапки и без тяжёлого sliding-indicator.
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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(trackShape)
            .background(SquadRelaySurfaces.subtleColor(alpha = 0.32f))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEach { tab ->
            ChatRoomChip(
                tab = tab,
                selected = tab.id == selectedId,
                onClick = { onSelect(tab.id) },
                modifier = Modifier.weight(1f),
            )
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
        targetValue = if (selected) tab.accent else tab.accent.copy(alpha = 0.62f),
        animationSpec = chipColorSpring,
        label = "chatRoomChipIcon",
    )
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
    Surface(
        modifier = modifier
            .widthIn(min = 48.dp)
            .clip(chipShape)
            .clickable(
                interactionSource = interaction,
                indication = ripple(bounded = true, color = tab.accent.copy(alpha = 0.35f)),
                onClick = onClick,
            ),
        shape = chipShape,
        color = containerColor,
        border = BorderStroke(if (selected) 1.dp else 0.5.dp, borderColor),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = tab.icon,
                    contentDescription = null,
                    modifier = Modifier.size(17.dp),
                    tint = iconTint,
                )
                if (tab.unreadCount > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(7.dp)
                            .background(
                                if (selected) tab.accent else Color(0xFFEF4444),
                                CircleShape,
                            ),
                    )
                }
            }
            Text(
                text = tab.label,
                modifier = Modifier.padding(start = 6.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 12.sp,
                    letterSpacing = 0.02.sp,
                ),
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = labelColor,
            )
        }
    }
}

/** Акценты комнат чата. */
object ChatRoomTabAccents {
    val union = Color(0xFF818CF8)
    val raid = Color(0xFFFF7A45)
    val hub = Color(0xFF2DD4BF)
    val other = SquadRelayPrimary
    val fallback = SquadRelaySecondary
}
