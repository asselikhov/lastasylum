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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
    var selectedId by remember { mutableStateOf<String?>(null) }
    val scroll = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scroll),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GameEventTableSection(
            header = stringResource(R.string.game_event_section_hq),
            accent = categoryAccent(GameEventCategory.HQ),
            events = GameEventCatalog.eventsByCategory(GameEventCategory.HQ),
            selectedId = selectedId,
            onSelect = { selectedId = it },
        )
        GameEventTableSection(
            header = stringResource(R.string.game_event_section_pve),
            accent = categoryAccent(GameEventCategory.PVE),
            events = GameEventCatalog.eventsByCategory(GameEventCategory.PVE),
            selectedId = selectedId,
            onSelect = { selectedId = it },
        )
        GameEventTableSection(
            header = stringResource(R.string.game_event_section_pvp),
            accent = categoryAccent(GameEventCategory.PVP),
            events = GameEventCatalog.eventsByCategory(GameEventCategory.PVP),
            selectedId = selectedId,
            onSelect = { selectedId = it },
        )
        val selected = selectedId?.let { GameEventCatalog.byId(it) }
        TextButton(
            onClick = { selected?.let(onNotify) },
            enabled = selected != null,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
        ) {
            Text(
                text = stringResource(R.string.overlay_cmd_excavation_notify),
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (selected == null) {
            Text(
                text = stringResource(R.string.overlay_game_event_pick_hint),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF7A90A4),
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
    }
}

@Composable
private fun GameEventTableSection(
    header: String,
    accent: Color,
    events: List<GameEventDefinition>,
    selectedId: String?,
    onSelect: (String) -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color(0xFF141A24))
            .border(1.dp, accent.copy(alpha = 0.45f), shape),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(accent.copy(alpha = 0.22f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                text = header,
                color = accent,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
            )
        }
        HorizontalDivider(color = Color(0xFF2A3544))
        events.forEachIndexed { index, event ->
            GameEventRow(
                event = event,
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
    event: GameEventDefinition,
    accent: Color,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
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
            text = event.messageText,
            color = Color(0xFFE8F0FA),
            fontSize = 12.sp,
            modifier = Modifier.weight(1f),
        )
    }
}

private fun categoryAccent(category: GameEventCategory): Color {
    val argb = GameEventCatalog.notificationColor(category)
    return Color(argb)
}
