package com.lastasylum.alliance.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import com.lastasylum.alliance.ui.theme.SquadRelaySurfaces
import kotlinx.coroutines.launch

fun countEnabledGameEventPushes(prefs: UserSettingsPreferences): Int =
    GameEventCatalog.all.count { prefs.isGameEventPushEnabled(it.id) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameEventPushSettingsSheet(
    prefs: UserSettingsPreferences,
    onDismiss: () -> Unit,
    onEnabledCountChanged: () -> Unit = {},
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SquadRelaySurfaces.dialogColor(),
    ) {
        GameEventPushSettingsContent(
            prefs = prefs,
            onEnabledCountChanged = onEnabledCountChanged,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        )
    }
}

@Composable
fun GameEventPushSettingsContent(
    prefs: UserSettingsPreferences,
    modifier: Modifier = Modifier,
    onEnabledCountChanged: () -> Unit = {},
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

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_game_events_push_section),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(
                horizontal = SquadRelayDimens.listRowHorizontalPadding,
                vertical = SquadRelayDimens.listRowVerticalPadding,
            ),
        )
        Text(
            text = stringResource(R.string.settings_game_event_push_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(
                start = SquadRelayDimens.listRowHorizontalPadding,
                end = SquadRelayDimens.listRowHorizontalPadding,
                bottom = SquadRelayDimens.blockGap,
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
            val events = GameEventCatalog.eventsByCategory(category)
            events.forEachIndexed { index, event ->
                val checked = enabledById[event.id] ?: true
                SettingsToggleRow(
                    title = event.messageText,
                    subtitle = null,
                    checked = checked,
                    onCheckedChange = { on ->
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
                if (index < events.lastIndex) {
                    SettingsDivider()
                }
            }
        }
    }
}
