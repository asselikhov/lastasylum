package com.lastasylum.alliance.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.lastasylum.alliance.R
import com.lastasylum.alliance.gameevents.GameEventCatalog
import com.lastasylum.alliance.gameevents.GameEventCategory
import com.lastasylum.alliance.gameevents.GameEventDefinition

@Composable
fun OverlayGameEventPushPanel(
    onNotify: (GameEventDefinition) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedCategory by remember { mutableStateOf(GameEventCategory.HQ) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    val accent = categoryAccent(selectedCategory)
    val events = remember(selectedCategory) {
        GameEventCatalog.eventsByCategory(selectedCategory)
    }
    val selected = selectedId?.let { GameEventCatalog.byId(it) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        GameEventCategoryTabs(
            selected = selectedCategory,
            onSelect = { category ->
                selectedCategory = category
                val current = selectedId?.let { GameEventCatalog.byId(it) }
                if (current?.category != category) {
                    selectedId = null
                }
            },
        )
        GameEventListCard(
            accent = accent,
            events = events,
            selectedId = selectedId,
            onSelect = { selectedId = it },
        )
        NotifyActionButton(
            accent = accent,
            enabled = selected != null,
            onClick = { selected?.let(onNotify) },
        )
        if (selected == null) {
            Text(
                text = stringResource(R.string.overlay_game_event_pick_hint),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF7A90A4),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun GameEventCategoryTabs(
    selected: GameEventCategory,
    onSelect: (GameEventCategory) -> Unit,
) {
    val trackShape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(trackShape)
            .background(Color(0xFF0C1018))
            .border(1.dp, Color(0xFF243040), trackShape)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        GameEventCategory.entries.forEach { category ->
            val isSelected = category == selected
            val accent = categoryAccent(category)
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
                    text = categoryTabLabel(category),
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
private fun GameEventListCard(
    accent: Color,
    events: List<GameEventDefinition>,
    selectedId: String?,
    onSelect: (String) -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color(0xFF141A24))
            .border(1.dp, accent.copy(alpha = 0.4f), shape),
    ) {
        events.forEachIndexed { index, event ->
            GameEventRow(
                label = eventDisplayLabel(event),
                accent = accent,
                selected = selectedId == event.id,
                onSelect = { onSelect(event.id) },
            )
            if (index < events.lastIndex) {
                HorizontalDivider(color = Color(0xFF222C38), thickness = 0.5.dp)
            }
        }
    }
}

@Composable
private fun GameEventRow(
    label: String,
    accent: Color,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .then(
                if (selected) {
                    Modifier.background(accent.copy(alpha = 0.14f))
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onSelect,
            colors = RadioButtonDefaults.colors(
                selectedColor = accent,
                unselectedColor = Color(0xFF6A7A8C),
            ),
        )
        Text(
            text = label,
            color = if (selected) Color(0xFFF4F8FF) else Color(0xFFD0DCE8),
            fontSize = 12.5.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun NotifyActionButton(
    accent: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    val bg = if (enabled) accent.copy(alpha = 0.88f) else Color(0xFF2A3444)
    val fg = if (enabled) Color.White else Color(0xFF6A7A8C)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bg)
            .then(
                if (enabled) Modifier else Modifier.border(1.dp, Color(0xFF344050), shape),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.overlay_cmd_excavation_notify),
            color = fg,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun categoryTabLabel(category: GameEventCategory): String = when (category) {
    GameEventCategory.HQ -> stringResource(R.string.game_event_section_hq)
    GameEventCategory.PVE -> stringResource(R.string.game_event_section_pve)
    GameEventCategory.PVP -> stringResource(R.string.game_event_section_pvp)
}

private fun eventDisplayLabel(event: GameEventDefinition): String {
    val prefix = when (event.category) {
        GameEventCategory.HQ -> "[ШТАБ] "
        GameEventCategory.PVE -> "[PvE] "
        GameEventCategory.PVP -> "[PvP] "
    }
    return event.messageText.removePrefix(prefix).trim()
}

private fun categoryAccent(category: GameEventCategory): Color {
    val argb = GameEventCatalog.notificationColor(category)
    return Color(argb)
}
