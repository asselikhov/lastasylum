package com.lastasylum.alliance.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lastasylum.alliance.ui.theme.SquadRelayPrimary
import com.lastasylum.alliance.ui.theme.SquadRelaySecondary
import com.lastasylum.alliance.ui.theme.SquadRelaySurfaces

data class TeamSectionTabSpec(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val accentStart: Color,
    val accentEnd: Color,
)

/**
 * Панель разделов команды (Новости / Форум / Участники): стеклянная подложка,
 * скользящий градиентный индикатор и иконка + подпись в каждом сегменте.
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
    val outerShape = RoundedCornerShape(20.dp)
    val segmentShape = RoundedCornerShape(14.dp)
    val inset = 5.dp

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = outerShape,
        color = SquadRelaySurfaces.panelColor(alpha = 0.50f),
        tonalElevation = 0.dp,
        shadowElevation = 6.dp,
        border = SquadRelaySurfaces.panelBorder(alpha = 0.20f),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(inset)
                .height(56.dp),
        ) {
            val tabWidth = maxWidth / tabs.size
            val indicatorOffset by animateDpAsState(
                targetValue = tabWidth * selectedIndex,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
                label = "teamSectionIndicator",
            )

            Box(
                modifier = Modifier
                    .offset(x = indicatorOffset)
                    .width(tabWidth)
                    .fillMaxHeight()
                    .shadow(
                        elevation = 8.dp,
                        shape = segmentShape,
                        ambientColor = selectedTab.accentStart.copy(alpha = 0.35f),
                        spotColor = selectedTab.accentEnd.copy(alpha = 0.45f),
                    )
                    .clip(segmentShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                selectedTab.accentStart.copy(alpha = 0.95f),
                                selectedTab.accentEnd.copy(alpha = 0.88f),
                            ),
                        ),
                    ),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                tabs.forEachIndexed { index, tab ->
                    val selected = index == selectedIndex
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
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = null,
                            modifier = Modifier.size(if (selected) 22.dp else 20.dp),
                            tint = if (selected) {
                                Color.White.copy(alpha = 0.98f)
                            } else {
                                tab.accentStart.copy(alpha = 0.72f)
                            },
                        )
                        Text(
                            text = tab.label,
                            modifier = Modifier.padding(top = 3.dp),
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                letterSpacing = 0.2.sp,
                            ),
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (selected) {
                                Color.White.copy(alpha = 0.96f)
                            } else {
                                scheme.onSurface.copy(alpha = 0.68f)
                            },
                        )
                    }
                }
            }
        }
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
