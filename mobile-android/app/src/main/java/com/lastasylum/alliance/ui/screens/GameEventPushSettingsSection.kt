package com.lastasylum.alliance.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.settings.UserSettingsPreferences
import com.lastasylum.alliance.di.AppContainer
import com.lastasylum.alliance.gameevents.GameEventCatalog
import com.lastasylum.alliance.gameevents.GameEventCategory
import com.lastasylum.alliance.ui.components.SettingsDivider
import com.lastasylum.alliance.ui.components.SettingsToggleRow
import com.lastasylum.alliance.ui.theme.SquadRelayDimens
import kotlinx.coroutines.launch

@Composable
fun GameEventPushSettingsSection(
    prefs: UserSettingsPreferences,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = remember { AppContainer.from(context) }
    val scope = rememberCoroutineScope()
    val enabledById = remember {
        mutableStateMapOf<String, Boolean>().apply {
            for (event in GameEventCatalog.all) {
                this[event.id] = prefs.isGameEventPushEnabled(event.id)
            }
        }
    }
    Column(modifier) {
        Text(
            text = stringResource(R.string.settings_game_events_push_section),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(
                horizontal = SquadRelayDimens.listRowHorizontalPadding,
                vertical = SquadRelayDimens.listRowVerticalPadding,
            ),
        )
        for (category in GameEventCategory.entries) {
                val label = when (category) {
                    GameEventCategory.HQ -> stringResource(R.string.game_event_section_hq)
                    GameEventCategory.PVE -> stringResource(R.string.game_event_section_pve)
                    GameEventCategory.PVP -> stringResource(R.string.game_event_section_pvp)
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(
                        horizontal = SquadRelayDimens.listRowHorizontalPadding,
                        vertical = 8.dp,
                    ),
                )
                for (event in GameEventCatalog.eventsByCategory(category)) {
                    val checked = enabledById[event.id] ?: true
                    SettingsToggleRow(
                        title = event.messageText,
                        subtitle = stringResource(R.string.settings_game_event_push_subtitle),
                        checked = checked,
                        onCheckedChange = { on ->
                            enabledById[event.id] = on
                            prefs.setGameEventPushEnabled(event.id, on)
                            scope.launch {
                                app.usersRepository
                                    .updateGameEventPushEnabled(event.id, on)
                                    .onFailure {
                                        enabledById[event.id] = !on
                                        prefs.setGameEventPushEnabled(event.id, !on)
                                    }
                            }
                        },
                    )
                    SettingsDivider()
                }
        }
    }
}
