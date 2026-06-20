package com.lastasylum.alliance.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.settings.UserSettingsPreferences
import com.lastasylum.alliance.di.AppContainer
import com.lastasylum.alliance.gameevents.GameEventCatalog
import com.lastasylum.alliance.gameevents.GameEventCategory
import com.lastasylum.alliance.gameevents.GameEventDefinition
import com.lastasylum.alliance.ui.components.gameevents.GameEventCategoryTabs
import com.lastasylum.alliance.ui.components.gameevents.gameEventCategoryAccent
import com.lastasylum.alliance.ui.components.gameevents.gameEventShortLabel
import com.lastasylum.alliance.ui.theme.SquadRelaySurfaces
import kotlinx.coroutines.launch

fun countEnabledGameEventPushes(prefs: UserSettingsPreferences): Int =
    GameEventCatalog.all.count { prefs.isGameEventPushEnabled(it.id) }

@Composable
fun GameEventPushSettingsDialog(
    prefs: UserSettingsPreferences,
    onDismiss: () -> Unit,
    onEnabledCountChanged: () -> Unit = {},
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 520.dp),
            shape = RoundedCornerShape(20.dp),
            color = SquadRelaySurfaces.dialogColor(alpha = 1f),
            border = SquadRelaySurfaces.panelBorder(alpha = 0.15f),
        ) {
            GameEventPushSettingsContent(
                prefs = prefs,
                onDismiss = onDismiss,
                onEnabledCountChanged = onEnabledCountChanged,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun GameEventPushSettingsContent(
    prefs: UserSettingsPreferences,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = {},
    onEnabledCountChanged: () -> Unit = {},
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = remember { AppContainer.from(context) }
    val scope = rememberCoroutineScope()
    var selectedCategory by remember { mutableStateOf(GameEventCategory.HQ) }
    val enabledById = remember {
        mutableStateMapOf<String, Boolean>().apply {
            for (event in GameEventCatalog.all) {
                this[event.id] = prefs.isGameEventPushEnabled(event.id)
            }
        }
    }
    val accent = gameEventCategoryAccent(selectedCategory)
    val events = remember(selectedCategory) {
        GameEventCatalog.eventsByCategory(selectedCategory)
    }
    val enabledCount = enabledById.values.count { it }
    val totalCount = GameEventCatalog.all.size

    Column(
        modifier = modifier.padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(R.string.settings_game_events_push_section),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.settings_game_event_push_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.settings_game_events_dialog_close),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        GameEventCategoryTabs(
            selected = selectedCategory,
            onSelect = { selectedCategory = it },
        )

        GameEventPushToggleTable(
            accent = accent,
            events = events,
            enabledById = enabledById,
            onToggle = { event, on ->
                enabledById[event.id] = on
                prefs.setGameEventPushEnabled(event.id, on)
                onEnabledCountChanged()
                scope.launch {
                    app.usersRepository
                        .updateGameEventPushEnabled(event.id, on)
                        .onFailure {
                            enabledById[event.id] = !on
                            prefs.setGameEventPushEnabled(event.id, !on)
                            onEnabledCountChanged()
                        }
                }
            },
        )

        Text(
            text = stringResource(
                R.string.settings_game_events_summary,
                enabledCount,
                totalCount,
            ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun GameEventPushToggleTable(
    accent: Color,
    events: List<GameEventDefinition>,
    enabledById: Map<String, Boolean>,
    onToggle: (GameEventDefinition, Boolean) -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 280.dp)
            .clip(shape)
            .background(Color(0xFF141A24))
            .border(1.dp, accent.copy(alpha = 0.4f), shape)
            .verticalScroll(rememberScrollState()),
    ) {
        events.forEachIndexed { index, event ->
            GameEventPushToggleRow(
                label = gameEventShortLabel(event),
                accent = accent,
                checked = enabledById[event.id] ?: true,
                onCheckedChange = { onToggle(event, it) },
            )
            if (index < events.lastIndex) {
                HorizontalDivider(color = Color(0xFF222C38), thickness = 0.5.dp)
            }
        }
    }
}

@Composable
private fun GameEventPushToggleRow(
    label: String,
    accent: Color,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = if (checked) Color(0xFFF4F8FF) else Color(0xFFD0DCE8),
            fontWeight = if (checked) FontWeight.Medium else FontWeight.Normal,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = accent.copy(alpha = 0.55f),
                checkedThumbColor = accent,
                uncheckedTrackColor = Color(0xFF2A3444),
                uncheckedThumbColor = Color(0xFF6A7A8C),
            ),
        )
    }
}
