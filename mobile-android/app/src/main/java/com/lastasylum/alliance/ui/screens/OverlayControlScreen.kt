package com.lastasylum.alliance.ui.screens

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lastasylum.alliance.R
import com.lastasylum.alliance.di.AppContainer
import com.lastasylum.alliance.game.GameDeepLinkNavigator
import com.lastasylum.alliance.game.GameMapNavigator
import com.lastasylum.alliance.game.GameMapPatchStatus
import com.lastasylum.alliance.gameevents.GameEventCatalog
import com.lastasylum.alliance.overlay.CombatOverlayService
import com.lastasylum.alliance.overlay.GameForegroundGate
import com.lastasylum.alliance.overlay.OverlayGamePackageSuggestions
import com.lastasylum.alliance.overlay.OverlayPermissions
import com.lastasylum.alliance.ui.components.SettingsNavigationRow
import com.lastasylum.alliance.ui.components.SettingsToggleRow
import com.lastasylum.alliance.ui.util.AppBuildInfo
import com.lastasylum.alliance.ui.theme.SquadRelayDimens
import com.lastasylum.alliance.ui.theme.SquadRelaySurfaces
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OverlayControlScreen() {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val appContainer = remember(appContext) { AppContainer.from(appContext) }
    val prefs = remember(appContext) { appContainer.userSettingsPreferences }
    val totalGameEvents = remember { GameEventCatalog.all.size }

    var targetPkg by remember { mutableStateOf(prefs.getOverlayTargetGamePackage()) }
    var overlayEnabled by remember { mutableStateOf(prefs.isOverlayPanelEnabled()) }
    var detectedGamePackages by remember {
        mutableStateOf<List<OverlayGamePackageSuggestions.DetectedGamePackage>>(emptyList())
    }
    var showGameEventsDialog by remember { mutableStateOf(false) }
    var enabledGameEventsCount by remember {
        mutableIntStateOf(countEnabledGameEventPushes(prefs))
    }
    var mapPatchStatus by remember {
        mutableStateOf(
            GameMapPatchStatus.read(
                appContext,
                GameDeepLinkNavigator.targetPackages(appContext),
            ),
        )
    }
    val undetectedGamePackages = remember(detectedGamePackages) {
        detectedGamePackages.filter { !it.alreadyInFilter }
    }
    val postNotifDenied = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) != android.content.pm.PackageManager.PERMISSION_GRANTED

    fun refreshGameEventsSummary() {
        enabledGameEventsCount = countEnabledGameEventPushes(prefs)
    }

    fun refreshMapPatchStatus() {
        mapPatchStatus = GameMapPatchStatus.read(
            appContext,
            GameDeepLinkNavigator.targetPackages(appContext),
        )
    }

    @Composable
    fun mapPatchStatusText(status: GameMapPatchStatus.Status): String =
        when (status.state) {
            GameMapPatchStatus.State.PATCH_READY ->
                stringResource(
                    R.string.map_patch_status_ready,
                    status.gameVersionName ?: status.supportedGameVersion,
                )
            GameMapPatchStatus.State.PATCH_NOT_INSTALLED ->
                stringResource(R.string.map_patch_status_not_installed)
            GameMapPatchStatus.State.PATCH_OUTDATED ->
                stringResource(
                    R.string.map_patch_status_outdated,
                    status.gameVersionName.orEmpty().ifBlank { "?" },
                    status.patchForGameVersion ?: status.supportedGameVersion,
                )
            GameMapPatchStatus.State.GAME_NOT_FOUND ->
                stringResource(R.string.map_patch_status_game_not_found)
        }

    fun overlayOk(): Boolean = OverlayPermissions.canDrawOverlays(context)

    fun usageOk(): Boolean =
        GameForegroundGate.usageAccessMode(context) == GameForegroundGate.UsageAccessMode.FULL

    fun refreshOverlayRuntime() {
        GameForegroundGate.invalidateUsageAccessCache()
        if (overlayEnabled) {
            CombatOverlayService.requestGateRecheckIfRunning(context)
        }
    }

    LaunchedEffect(Unit) {
        appContainer.usersRepository.getMyProfile().getOrNull()?.let { profile ->
            prefs.applyGameEventPushEnabledFromServer(profile.gameEventPushEnabled)
            refreshGameEventsSummary()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                overlayEnabled = prefs.isOverlayPanelEnabled()
                refreshOverlayRuntime()
                refreshGameEventsSummary()
                refreshMapPatchStatus()
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

    LaunchedEffect(targetPkg) {
        detectedGamePackages = OverlayGamePackageSuggestions.detectInstalled(
            pm = context.packageManager,
            squadRelayPackage = context.packageName,
            configuredCsv = targetPkg,
        )
        refreshMapPatchStatus()
    }

    LaunchedEffect(Unit) {
        refreshMapPatchStatus()
    }

    if (showGameEventsDialog) {
        GameEventPushSettingsDialog(
            prefs = prefs,
            onDismiss = {
                showGameEventsDialog = false
                refreshGameEventsSummary()
            },
            onEnabledCountChanged = ::refreshGameEventsSummary,
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .imePadding(),
        contentPadding = PaddingValues(
            start = SquadRelayDimens.contentPaddingHorizontal,
            end = SquadRelayDimens.contentPaddingHorizontal,
            top = SquadRelayDimens.screenTopPadding,
            bottom = SquadRelayDimens.bottomNavigationBarHeight + SquadRelayDimens.sectionGap,
        ),
        verticalArrangement = Arrangement.spacedBy(SquadRelayDimens.itemGap),
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(SquadRelayDimens.headerSubtitleGap),
            ) {
                Text(
                    text = stringResource(R.string.settings_screen_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.settings_screen_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = AppBuildInfo.authStyleBuildFooter(context),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                )
            }
        }

        item {
            SettingsSectionLabel(stringResource(R.string.settings_section_overlay))
            SettingsPanelCard {
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
            }
        }

        item {
            SettingsSectionLabel(stringResource(R.string.settings_section_game_filter))
            SettingsPanelCard {
                OutlinedTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = SquadRelayDimens.listRowHorizontalPadding,
                            vertical = SquadRelayDimens.listRowVerticalPadding,
                        ),
                    value = targetPkg,
                    onValueChange = { targetPkg = it },
                    singleLine = false,
                    minLines = 1,
                    maxLines = 4,
                    label = { Text(stringResource(R.string.overlay_package_field_label)) },
                    textStyle = MaterialTheme.typography.bodyMedium,
                    shape = MaterialTheme.shapes.medium,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                    ),
                )
                if (undetectedGamePackages.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.overlay_game_detected_title),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(
                            start = SquadRelayDimens.listRowHorizontalPadding,
                            end = SquadRelayDimens.listRowHorizontalPadding,
                            top = SquadRelayDimens.itemGap,
                        ),
                    )
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = SquadRelayDimens.listRowHorizontalPadding,
                                vertical = SquadRelayDimens.itemGap,
                            ),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        undetectedGamePackages.forEach { detected ->
                            FilterChip(
                                selected = false,
                                onClick = {
                                    targetPkg = OverlayGamePackageSuggestions.appendToCsv(
                                        targetPkg,
                                        detected.packageName,
                                    )
                                },
                                label = {
                                    Text(
                                        text = stringResource(
                                            R.string.overlay_game_detected_add,
                                            detected.label,
                                        ),
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                                        alpha = 0.35f,
                                    ),
                                ),
                            )
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.overlay_game_clone_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    modifier = Modifier.padding(
                        start = SquadRelayDimens.listRowHorizontalPadding,
                        end = SquadRelayDimens.listRowHorizontalPadding,
                        top = SquadRelayDimens.itemGap,
                        bottom = SquadRelayDimens.listRowVerticalPadding,
                    ),
                )
            }
        }

        item {
            SettingsSectionLabel(stringResource(R.string.settings_section_map_patch))
            SettingsPanelCard {
                Column(
                    modifier = Modifier.padding(
                        horizontal = SquadRelayDimens.listRowHorizontalPadding,
                        vertical = SquadRelayDimens.listRowVerticalPadding,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = mapPatchStatusText(mapPatchStatus),
                        style = MaterialTheme.typography.bodyMedium,
                        color = when (mapPatchStatus.state) {
                            GameMapPatchStatus.State.PATCH_READY ->
                                MaterialTheme.colorScheme.primary
                            GameMapPatchStatus.State.PATCH_OUTDATED ->
                                MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                    )
                    Text(
                        text = stringResource(
                            R.string.map_patch_supported_version,
                            mapPatchStatus.supportedGameVersion,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.map_patch_install_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    )
                    TextButton(
                        onClick = {
                            if (!mapPatchStatus.isAutoFlyAvailable) {
                                Toast.makeText(
                                    context,
                                    context.getString(
                                        R.string.map_patch_test_fly_disabled,
                                        mapPatchStatus.supportedGameVersion,
                                    ),
                                    Toast.LENGTH_SHORT,
                                ).show()
                                return@TextButton
                            }
                            GameMapNavigator.open(
                                context = context,
                                x = 505,
                                y = 495,
                                serverNumber = 109,
                            )
                            Toast.makeText(
                                context,
                                R.string.map_patch_test_fly_toast,
                                Toast.LENGTH_SHORT,
                            ).show()
                        },
                        enabled = mapPatchStatus.state != GameMapPatchStatus.State.GAME_NOT_FOUND,
                    ) {
                        Text(stringResource(R.string.map_patch_test_fly))
                    }
                }
            }
        }

        item {
            SettingsSectionLabel(stringResource(R.string.settings_section_notifications))
            SettingsPanelCard {
                if (postNotifDenied) {
                    Column(
                        modifier = Modifier.padding(
                            horizontal = SquadRelayDimens.listRowHorizontalPadding,
                            vertical = SquadRelayDimens.listRowVerticalPadding,
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.overlay_post_notifications_denied),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        TextButton(
                            onClick = {
                                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                }
                                context.startActivity(intent)
                            },
                        ) {
                            Text(stringResource(R.string.overlay_post_notifications_open_settings))
                        }
                    }
                }
                SettingsNavigationRow(
                    title = stringResource(R.string.settings_game_events_push_section),
                    subtitle = stringResource(
                        R.string.settings_game_events_summary,
                        enabledGameEventsCount,
                        totalGameEvents,
                    ),
                    onClick = { showGameEventsDialog = true },
                    trailing = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun SettingsSectionLabel(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(
            start = SquadRelayDimens.listRowHorizontalPadding,
            end = SquadRelayDimens.listRowHorizontalPadding,
            top = SquadRelayDimens.sectionGap,
            bottom = SquadRelayDimens.sectionTitlePaddingVertical,
        ),
    )
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
        border = SquadRelaySurfaces.panelBorder(alpha = 0.15f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            content = { content() },
        )
    }
}
