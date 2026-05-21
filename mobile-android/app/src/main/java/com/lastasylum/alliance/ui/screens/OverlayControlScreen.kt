package com.lastasylum.alliance.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.settings.UserSettingsPreferences
import com.lastasylum.alliance.di.AppContainer
import com.lastasylum.alliance.overlay.CombatOverlayService
import com.lastasylum.alliance.overlay.GameForegroundGate
import com.lastasylum.alliance.overlay.OverlayPermissions
import com.lastasylum.alliance.push.FcmTokenManager
import com.lastasylum.alliance.ui.components.SettingsSection
import com.lastasylum.alliance.ui.components.SettingsToggleRow
import com.lastasylum.alliance.ui.theme.SquadRelayDimens
import com.lastasylum.alliance.ui.theme.SquadRelaySurfaces
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun OverlayControlScreen() {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val appContainer = remember(appContext) { AppContainer.from(appContext) }
    val prefs = remember(appContext) { appContainer.userSettingsPreferences }
    val scope = rememberCoroutineScope()
    val squadRelayAppLabel = remember(context) {
        context.applicationInfo.loadLabel(context.packageManager).toString()
    }

    val overlayVisible by CombatOverlayService.overlayVisible.collectAsStateWithLifecycle()
    var targetPkg by remember { mutableStateOf(prefs.getOverlayTargetGamePackage()) }
    var targetActivities by remember { mutableStateOf(prefs.getOverlayTargetGameActivityTokens().joinToString(",")) }
    var overlayEnabled by remember { mutableStateOf(prefs.isOverlayPanelEnabled()) }
    var overlayHudOnly by remember { mutableStateOf(prefs.isOverlayHudOnlyMode()) }
    var overlayLightStrip by remember { mutableStateOf(prefs.isOverlayLightStrip()) }
    var excavationPushEnabled by remember { mutableStateOf(prefs.isExcavationPushEnabled()) }
    var pushRegisteredOnServer by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(Unit) {
        appContainer.usersRepository.getMyProfile().getOrNull()?.let { profile ->
            prefs.setExcavationPushEnabled(profile.excavationPushEnabled)
            excavationPushEnabled = profile.excavationPushEnabled
            pushRegisteredOnServer = profile.pushNotificationsRegistered
        }
    }

    val userId = remember {
        (android.os.Process.myUid() / 100000)
    }

    fun micOk(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECORD_AUDIO,
    ) == PackageManager.PERMISSION_GRANTED

    fun overlayOk(): Boolean = OverlayPermissions.canDrawOverlays(context)

    fun usageOk(): Boolean = GameForegroundGate.hasUsageStatsAccessForOverlay(context)

    fun refreshOverlayRuntime() {
        GameForegroundGate.invalidateUsageAccessCache()
        if (overlayEnabled) {
            CombatOverlayService.requestGateRecheckIfRunning(context)
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                overlayEnabled = prefs.isOverlayPanelEnabled()
                overlayHudOnly = prefs.isOverlayHudOnlyMode()
                overlayLightStrip = prefs.isOverlayLightStrip()
                refreshOverlayRuntime()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(targetPkg) {
        delay(450)
        val trimmed = targetPkg.trim()
        if (trimmed.isEmpty()) return@LaunchedEffect
        if (trimmed == prefs.getOverlayTargetGamePackage()) return@LaunchedEffect
        prefs.setOverlayTargetGamePackage(trimmed)
        CombatOverlayService.requestGateRecheckIfRunning(context)
    }

    LaunchedEffect(targetActivities) {
        delay(450)
        val trimmed = targetActivities.trim()
        if (trimmed == prefs.getOverlayTargetGameActivityTokens().joinToString(",")) return@LaunchedEffect
        prefs.setOverlayTargetGameActivityTokens(trimmed)
        CombatOverlayService.requestGateRecheckIfRunning(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SquadRelayDimens.contentPaddingHorizontal, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.settings_screen_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                horizontal = SquadRelayDimens.contentPaddingHorizontal,
                vertical = SquadRelayDimens.itemGap,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.settings_screen_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (userId != 0) {
                    Text(
                        text = "XSpace/профиль (userId=$userId): переключатель здесь не управляет панелью основного профиля.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }

            item {
                SettingsPanelCard {
                    SettingsSection(title = stringResource(R.string.settings_section_overlay)) {
                        SettingsToggleRow(
                            title = stringResource(R.string.overlay_switch_panel),
                            subtitle = stringResource(R.string.overlay_switch_panel_subtitle),
                            checked = overlayEnabled,
                            onCheckedChange = { on ->
                                if (on) {
                                    overlayEnabled = true
                                    prefs.setOverlayPanelEnabled(true)
                                    when {
                                        !overlayOk() -> {
                                            overlayEnabled = false
                                            prefs.setOverlayPanelEnabled(false)
                                            OverlayPermissions.openOverlayPermissionSettings(context)
                                        }
                                        !usageOk() -> {
                                            overlayEnabled = false
                                            prefs.setOverlayPanelEnabled(false)
                                            OverlayPermissions.openUsageAccessSettings(context)
                                        }
                                        else -> Unit
                                    }
                                    if (overlayEnabled && !CombatOverlayService.setEnabled(context, true)) {
                                        overlayEnabled = false
                                        prefs.setOverlayPanelEnabled(false)
                                    }
                                } else {
                                    overlayEnabled = false
                                    prefs.setOverlayPanelEnabled(false)
                                    CombatOverlayService.setEnabled(context, false)
                                }
                            },
                        )
                        if (overlayEnabled) {
                            SettingsToggleRow(
                                title = stringResource(R.string.overlay_hud_only_title),
                                subtitle = stringResource(R.string.overlay_hud_only_subtitle),
                                checked = overlayHudOnly,
                                onCheckedChange = { on ->
                                    overlayHudOnly = on
                                    prefs.setOverlayHudOnlyMode(on)
                                    CombatOverlayService.requestRebuildOverlayIfRunning(context)
                                    refreshOverlayRuntime()
                                },
                            )
                            SettingsToggleRow(
                                title = stringResource(R.string.overlay_light_strip_title),
                                subtitle = stringResource(R.string.overlay_light_strip_subtitle),
                                checked = overlayLightStrip,
                                enabled = !overlayHudOnly,
                                onCheckedChange = { on ->
                                    overlayLightStrip = on
                                    prefs.setOverlayLightStrip(on)
                                    CombatOverlayService.requestRebuildOverlayIfRunning(context)
                                },
                            )
                        }
                    }
                }
            }

            if (overlayEnabled && !overlayVisible) {
                item {
                    Text(
                        text = if (usageOk()) {
                            stringResource(R.string.overlay_switch_panel_offgame_hint)
                        } else {
                            stringResource(R.string.overlay_usage_hint_gate)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (!overlayOk()) {
                item {
                    SettingsHintCard(
                        message = stringResource(
                            R.string.overlay_overlay_must_be_squadrelay,
                            squadRelayAppLabel,
                        ),
                        actionLabel = stringResource(R.string.overlay_open_squadrelay_overlay_settings),
                        onAction = { OverlayPermissions.openOverlayPermissionSettings(context) },
                        isError = true,
                    )
                }
            } else if (overlayEnabled && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                item {
                    Text(
                        text = stringResource(R.string.overlay_restricted_settings_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (overlayEnabled && !usageOk()) {
                item {
                    SettingsHintCard(
                        message = stringResource(R.string.overlay_usage_hint_gate),
                        actionLabel = stringResource(R.string.overlay_open_usage_settings),
                        onAction = { OverlayPermissions.openUsageAccessSettings(context) },
                        isError = false,
                    )
                }
            }

            if (overlayEnabled && !OverlayPermissions.isBatteryOptimizationIgnored(context)) {
                item {
                    SettingsHintCard(
                        message = stringResource(R.string.overlay_battery_hint),
                        actionLabel = stringResource(R.string.overlay_open_battery_settings),
                        onAction = { OverlayPermissions.openBatteryUnrestrictedSettings(context) },
                        isError = false,
                    )
                }
            }

            if (!micOk()) {
                item {
                    Text(
                        text = stringResource(R.string.overlay_mic_optional_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                SettingsPanelCard {
                    SettingsSection(title = stringResource(R.string.settings_section_notifications)) {
                        SettingsToggleRow(
                            title = stringResource(R.string.settings_excavation_push_title),
                            subtitle = stringResource(R.string.settings_excavation_push_subtitle),
                            checked = excavationPushEnabled,
                            onCheckedChange = { on ->
                                excavationPushEnabled = on
                                prefs.setExcavationPushEnabled(on)
                                scope.launch {
                                    appContainer.usersRepository
                                        .updateExcavationPushEnabled(on)
                                        .onFailure {
                                            excavationPushEnabled = !on
                                            prefs.setExcavationPushEnabled(!on)
                                        }
                                }
                            },
                        )
                    }
                }
            }

            if (pushRegisteredOnServer == false) {
                item {
                    SettingsHintCard(
                        message = stringResource(R.string.settings_push_not_registered_hint),
                        actionLabel = stringResource(R.string.settings_push_register_retry),
                        onAction = {
                            scope.launch {
                                FcmTokenManager.registerWithBackend(appContext)
                                appContainer.usersRepository.getMyProfile().getOrNull()?.let { profile ->
                                    pushRegisteredOnServer = profile.pushNotificationsRegistered
                                }
                            }
                        },
                        isError = true,
                    )
                }
            }

            if (overlayEnabled) {
                item {
                    SettingsPanelCard {
                        SettingsSection(title = stringResource(R.string.settings_section_game_filter)) {
                            OutlinedTextField(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        horizontal = SquadRelayDimens.listRowHorizontalPadding,
                                        vertical = 8.dp,
                                    ),
                                value = targetPkg,
                                onValueChange = { targetPkg = it },
                                singleLine = true,
                                label = { Text(stringResource(R.string.overlay_package_field_label)) },
                                textStyle = MaterialTheme.typography.bodyMedium,
                            )
                            OutlinedTextField(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        horizontal = SquadRelayDimens.listRowHorizontalPadding,
                                        vertical = 8.dp,
                                    ),
                                value = targetActivities,
                                onValueChange = { targetActivities = it },
                                singleLine = true,
                                label = { Text(stringResource(R.string.overlay_activity_filter_label)) },
                                supportingText = {
                                    Text(stringResource(R.string.overlay_activity_filter_hint))
                                },
                                textStyle = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsPanelCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = SquadRelaySurfaces.panelColor(0.48f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun SettingsHintCard(
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    isError: Boolean,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = if (isError) {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
        } else {
            SquadRelaySurfaces.panelColor(0.35f)
        },
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
        ),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            TextButton(onClick = onAction, contentPadding = PaddingValues(0.dp)) {
                Text(actionLabel)
            }
        }
    }
}
