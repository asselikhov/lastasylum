package com.lastasylum.alliance.ui.screens

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lastasylum.alliance.R
import com.lastasylum.alliance.di.AppContainer
import com.lastasylum.alliance.game.GameAutoHelpBridge
import com.lastasylum.alliance.game.GameDeepLinkNavigator
import com.lastasylum.alliance.game.GameMapPatchStatus
import com.lastasylum.alliance.game.GamePatchInstaller
import com.lastasylum.alliance.overlay.CombatOverlayService
import com.lastasylum.alliance.overlay.GameForegroundGate
import com.lastasylum.alliance.overlay.OverlayGamePackageSuggestions
import com.lastasylum.alliance.overlay.OverlayPermissions
import com.lastasylum.alliance.ui.components.SettingsNavigationRow
import com.lastasylum.alliance.ui.components.SettingsToggleRow
import com.lastasylum.alliance.ui.theme.SquadRelayDimens
import com.lastasylum.alliance.ui.theme.SquadRelaySurfaces
import com.lastasylum.alliance.ui.theme.premium.PremiumColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OverlayControlScreen() {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val appContainer = remember(appContext) { AppContainer.from(appContext) }
    val prefs = remember(appContext) { appContainer.userSettingsPreferences }
    val haptics = LocalHapticFeedback.current

    var targetPkg by remember { mutableStateOf(prefs.getOverlayTargetGamePackage()) }
    var overlayEnabled by remember { mutableStateOf(prefs.isOverlayPanelEnabled()) }
    var autoHelpEnabled by remember { mutableStateOf(prefs.isAutoHelpEnabled()) }
    var autoHelpIntervalSec by remember { mutableIntStateOf(prefs.getAutoHelpIntervalSec()) }
    var detectedGamePackages by remember {
        mutableStateOf<List<OverlayGamePackageSuggestions.DetectedGamePackage>>(emptyList())
    }
    var showGameEventsDialog by remember { mutableStateOf(false) }
    var mapPatchStatus by remember {
        mutableStateOf(
            GameMapPatchStatus.read(
                appContext,
                GameDeepLinkNavigator.targetPackages(appContext),
            ),
        )
    }
    val scope = rememberCoroutineScope()
    var patchInProgress by remember { mutableStateOf(false) }
    var patchProgress by remember { mutableFloatStateOf(-1f) }
    var preparedPatchApk by remember { mutableStateOf<File?>(null) }
    var awaitingGameUninstall by remember { mutableStateOf(false) }
    val undetectedGamePackages = remember(detectedGamePackages) {
        detectedGamePackages.filter { !it.alreadyInFilter }
    }
    val postNotifDenied = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) != android.content.pm.PackageManager.PERMISSION_GRANTED

    fun refreshMapPatchStatus() {
        mapPatchStatus = GameMapPatchStatus.read(
            appContext,
            GameDeepLinkNavigator.targetPackages(appContext),
        )
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
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                overlayEnabled = prefs.isOverlayPanelEnabled()
                refreshOverlayRuntime()
                refreshMapPatchStatus()
                val pendingApk = preparedPatchApk
                if (awaitingGameUninstall && pendingApk != null &&
                    !GamePatchInstaller.isStockGameInstalled(appContext)
                ) {
                    awaitingGameUninstall = false
                    preparedPatchApk = null
                    Toast.makeText(
                        context,
                        R.string.game_patch_install_starting,
                        Toast.LENGTH_SHORT,
                    ).show()
                    GamePatchInstaller.installPrepared(context, pendingApk)
                }
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

    LaunchedEffect(Unit) {
        GameAutoHelpBridge.sync(appContext)
    }

    if (showGameEventsDialog) {
        GameEventPushSettingsDialog(
            prefs = prefs,
            onDismiss = { showGameEventsDialog = false },
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
        verticalArrangement = Arrangement.spacedBy(SquadRelayDimens.blockGap),
    ) {
        item {
            Text(
                text = stringResource(R.string.settings_screen_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 2.dp, top = 2.dp, bottom = 2.dp),
            )
        }

        item {
            SettingsCard(
                icon = Icons.Outlined.Layers,
                title = stringResource(R.string.settings_section_overlay),
            ) {
                SettingsToggleRow(
                    title = stringResource(R.string.overlay_switch_panel),
                    subtitle = null,
                    checked = overlayEnabled,
                    onCheckedChange = { on ->
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
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
            SettingsCard(
                icon = Icons.Outlined.SportsEsports,
                title = stringResource(R.string.settings_section_game_filter),
                accent = PremiumColors.accentCyan,
            ) {
                OutlinedTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SquadRelayDimens.listRowHorizontalPadding),
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
            }
        }

        item {
            SettingsCard(
                icon = Icons.Outlined.NearMe,
                title = stringResource(R.string.settings_section_map_patch),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SquadRelayDimens.listRowHorizontalPadding),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PatchStatusPill(mapPatchStatus.state)

                    val patchActionable = mapPatchStatus.state ==
                        GameMapPatchStatus.State.PATCH_NOT_INSTALLED ||
                        mapPatchStatus.state == GameMapPatchStatus.State.PATCH_OUTDATED
                    val ready = mapPatchStatus.state == GameMapPatchStatus.State.PATCH_READY
                    val success = PremiumColors.liveIndicator

                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            if (patchInProgress) return@Button
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            scope.launch {
                                patchInProgress = true
                                patchProgress = -1f
                                when (
                                    val result = GamePatchInstaller.prepareLatestPatch(context) { p ->
                                        patchProgress = p
                                    }
                                ) {
                                    is GamePatchInstaller.Prepare.Ready -> {
                                        if (GamePatchInstaller.isStockGameInstalled(appContext)) {
                                            preparedPatchApk = result.apk
                                            awaitingGameUninstall = true
                                            Toast.makeText(
                                                context,
                                                R.string.game_patch_uninstall_prompt,
                                                Toast.LENGTH_LONG,
                                            ).show()
                                            GamePatchInstaller.requestUninstallStockGame(context)
                                        } else {
                                            Toast.makeText(
                                                context,
                                                R.string.game_patch_install_starting,
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                            GamePatchInstaller.installPrepared(context, result.apk)
                                        }
                                    }
                                    GamePatchInstaller.Prepare.Unavailable ->
                                        Toast.makeText(
                                            context,
                                            R.string.game_patch_unavailable,
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    is GamePatchInstaller.Prepare.Failed ->
                                        Toast.makeText(
                                            context,
                                            result.messageRes,
                                            Toast.LENGTH_LONG,
                                        ).show()
                                }
                                patchInProgress = false
                                patchProgress = -1f
                            }
                        },
                        enabled = patchActionable && !patchInProgress,
                        colors = if (ready) {
                            ButtonDefaults.buttonColors(
                                disabledContainerColor = success.copy(alpha = 0.18f),
                                disabledContentColor = success,
                            )
                        } else {
                            ButtonDefaults.buttonColors()
                        },
                    ) {
                        when {
                            patchInProgress -> {
                                if (patchProgress in 0f..1f) {
                                    CircularProgressIndicator(
                                        progress = { patchProgress },
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        stringResource(
                                            R.string.game_patch_preparing_progress,
                                            (patchProgress * 100f).toInt().coerceIn(0, 100),
                                        ),
                                    )
                                } else {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.game_patch_preparing))
                                }
                            }
                            ready -> {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.game_patch_button_ready))
                            }
                            mapPatchStatus.state == GameMapPatchStatus.State.PATCH_OUTDATED -> {
                                Icon(
                                    imageVector = Icons.Outlined.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.game_patch_button_update))
                            }
                            else -> {
                                Icon(
                                    imageVector = Icons.Outlined.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.game_patch_button))
                            }
                        }
                    }
                }
            }
        }

        item {
            SettingsCard(
                icon = Icons.Outlined.Bolt,
                title = stringResource(R.string.settings_section_auto_help),
                accent = PremiumColors.accentCyan,
            ) {
                SettingsToggleRow(
                    title = stringResource(R.string.auto_help_switch_title),
                    subtitle = null,
                    checked = autoHelpEnabled,
                    onCheckedChange = { on ->
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        autoHelpEnabled = on
                        prefs.setAutoHelpEnabled(on)
                        GameAutoHelpBridge.write(context, on, autoHelpIntervalSec)
                    },
                )
                if (autoHelpEnabled) {
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
                        listOf(15, 30, 60).forEach { sec ->
                            FilterChip(
                                selected = autoHelpIntervalSec == sec,
                                onClick = {
                                    autoHelpIntervalSec = sec
                                    prefs.setAutoHelpIntervalSec(sec)
                                    GameAutoHelpBridge.write(context, autoHelpEnabled, sec)
                                },
                                label = {
                                    Text(
                                        text = stringResource(
                                            R.string.auto_help_interval_seconds,
                                            sec,
                                        ),
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }

        item {
            SettingsCard(
                icon = Icons.Outlined.Notifications,
                title = stringResource(R.string.settings_section_notifications),
            ) {
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
                    subtitle = null,
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
private fun PatchStatusPill(state: GameMapPatchStatus.State) {
    val target = when (state) {
        GameMapPatchStatus.State.PATCH_READY -> PremiumColors.liveIndicator
        GameMapPatchStatus.State.PATCH_OUTDATED -> MaterialTheme.colorScheme.error
        GameMapPatchStatus.State.PATCH_NOT_INSTALLED -> MaterialTheme.colorScheme.primary
        GameMapPatchStatus.State.GAME_NOT_FOUND -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val color by animateColorAsState(targetValue = target, label = "patchStatusColor")
    val label = when (state) {
        GameMapPatchStatus.State.PATCH_READY -> stringResource(R.string.map_patch_badge_ready)
        GameMapPatchStatus.State.PATCH_OUTDATED ->
            stringResource(R.string.map_patch_badge_outdated)
        GameMapPatchStatus.State.PATCH_NOT_INSTALLED ->
            stringResource(R.string.map_patch_badge_not_installed)
        GameMapPatchStatus.State.GAME_NOT_FOUND ->
            stringResource(R.string.map_patch_badge_game_not_found)
    }
    Surface(
        shape = MaterialTheme.shapes.small,
        color = color.copy(alpha = 0.12f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = color,
            )
        }
    }
}

@Composable
private fun SettingsCard(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    content: @Composable ColumnScope.() -> Unit,
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
                .padding(top = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = SquadRelayDimens.listRowHorizontalPadding,
                        vertical = 2.dp,
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(accent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(19.dp),
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            content()
        }
    }
}
