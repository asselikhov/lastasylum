package com.lastasylum.alliance.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.ui.theme.SquadRelaySurfaces

data class SquadSegmentTab(
    val id: String,
    val label: String,
    val icon: ImageVector? = null,
    val selectedContainerColor: Color? = null,
    val unselectedIconTint: Color? = null,
)

/**
 * Сегментированная панель вкладок (чат-комнаты, разделы команды): общий радиус 16 dp,
 * без вертикальных разделителей между сегментами.
 */
@Composable
fun SquadSegmentTabBar(
    tabs: List<SquadSegmentTab>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    cornerRadiusDp: Int = 16,
) {
    if (tabs.isEmpty()) return
    val scheme = MaterialTheme.colorScheme
    val barShape = RoundedCornerShape(cornerRadiusDp.dp)
    val defaultAccent = scheme.primary
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = barShape,
        color = SquadRelaySurfaces.subtleColor(),
        tonalElevation = 0.dp,
        shadowElevation = 2.dp,
        border = SquadRelaySurfaces.panelBorder(alpha = 0.14f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(barShape)
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEachIndexed { index, tab ->
                val selected = tab.id == selectedId
                val segmentShape = when {
                    tabs.size == 1 -> barShape
                    index == 0 -> RoundedCornerShape(
                        topStart = cornerRadiusDp.dp,
                        bottomStart = cornerRadiusDp.dp,
                    )
                    index == tabs.lastIndex -> RoundedCornerShape(
                        topEnd = cornerRadiusDp.dp,
                        bottomEnd = cornerRadiusDp.dp,
                    )
                    else -> RoundedCornerShape(0.dp)
                }
                val selectedBg = tab.selectedContainerColor
                    ?: defaultAccent.copy(alpha = 0.90f)
                val iconTint = when {
                    selected -> Color.White.copy(alpha = 0.95f)
                    else -> tab.unselectedIconTint ?: defaultAccent.copy(alpha = 0.88f)
                }
                val interaction = remember(tab.id) { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(segmentShape)
                        .then(
                            if (selected) {
                                Modifier.background(selectedBg)
                            } else {
                                Modifier
                            },
                        )
                        .clickable(
                            interactionSource = interaction,
                            indication = ripple(bounded = true),
                            onClick = { onSelect(tab.id) },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        tab.icon?.let { icon ->
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = iconTint,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            text = tab.label,
                            maxLines = 1,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (selected) {
                                Color.White
                            } else {
                                scheme.onSurface.copy(alpha = 0.92f)
                            },
                        )
                    }
                }
            }
        }
    }
}
