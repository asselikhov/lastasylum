package com.lastasylum.alliance.ui.components.gameevents

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.lastasylum.alliance.R
import com.lastasylum.alliance.gameevents.GameEventCatalog
import com.lastasylum.alliance.gameevents.GameEventCategory
import com.lastasylum.alliance.gameevents.GameEventDefinition

fun gameEventCategoryAccent(category: GameEventCategory): Color {
    val argb = GameEventCatalog.notificationColor(category)
    return Color(argb)
}

fun gameEventShortLabel(event: GameEventDefinition): String {
    val prefix = when (event.category) {
        GameEventCategory.HQ -> "[ШТАБ] "
        GameEventCategory.PVE -> "[PvE] "
        GameEventCategory.PVP -> "[PvP] "
    }
    return event.messageText.removePrefix(prefix).trim()
}

@Composable
fun GameEventCategoryTabs(
    selected: GameEventCategory,
    onSelect: (GameEventCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    val trackShape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(trackShape)
            .background(Color(0xFF0C1018))
            .border(1.dp, Color(0xFF243040), trackShape)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        GameEventCategory.entries.forEach { category ->
            val isSelected = category == selected
            val accent = gameEventCategoryAccent(category)
            val tabShape = RoundedCornerShape(9.dp)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(tabShape)
                    .then(
                        if (isSelected) {
                            Modifier.background(accent.copy(alpha = 0.92f))
                        } else {
                            Modifier
                                .background(Color(0xFF141A24))
                                .border(1.dp, accent.copy(alpha = 0.28f), tabShape)
                        },
                    )
                    .clickable { onSelect(category) }
                    .padding(vertical = 10.dp, horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = gameEventCategoryTabLabel(category),
                    color = if (isSelected) Color.White else accent.copy(alpha = 0.92f),
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun gameEventCategoryTabLabel(category: GameEventCategory): String = when (category) {
    GameEventCategory.HQ -> stringResource(R.string.game_event_section_hq)
    GameEventCategory.PVE -> stringResource(R.string.game_event_section_pve)
    GameEventCategory.PVP -> stringResource(R.string.game_event_section_pvp)
}
