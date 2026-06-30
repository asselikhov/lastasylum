package com.lastasylum.alliance.ui.screens

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Refresh
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
import com.lastasylum.alliance.BuildConfig
import com.lastasylum.alliance.R
import com.lastasylum.alliance.di.AppContainer
import com.lastasylum.alliance.game.GameAutoAssaultBridge
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
import com.lastasylum.alliance.update.AppUpdateCheckResult
import com.lastasylum.alliance.update.checkAppUpdate
import com.lastasylum.alliance.update.downloadAppUpdateApk
import com.lastasylum.alliance.update.installDownloadedApk
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

private enum class SettingsTab { OVERLAY, AUTOMATION, NOTIFICATIONS }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OverlayControlScreen() {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val appContainer = remember(appContext) { AppContainer.from(appContext) }
    val prefs = remember(appContext) { appContainer.userSettingsPreferences }
    val haptics = LocalHapticFeedback.current

    var selectedTab by remember { mutableStateOf(SettingsTab.OVERLAY) }
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
    // Скачанный APK, установка которого отложена до удаления сток-игры. Источник истины —
    // prefs, чтобы пережить убийство фонового процесса (OEM-киллеры) во время удаления игры.
    var pendingPatchApk by remember {
        mutableStateOf(prefs.getPendingPatchApkPath()?.let { File(it) }?.takeIf { it.exists() })
    }
    var autoInstallTriggered by remember { mutableStateOf(false) }

    fun setPendingPatch(apk: File?) {
        pendingPatchApk = apk
        prefs.setPendingPatchApkPath(apk?.absolutePath)
        if (apk == null) autoInstallTriggered = false
    }

    fun launchPatchInstall(apk: File) {
        if (GamePatchInstaller.installPrepared(context, apk)) {
            setPendingPatch(null)
            Toast.makeText(context, R.string.game_patch_install_starting, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, R.string.game_patch_install_failed, Toast.LENGTH_LONG).show()
        }
    }
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
                if (mapPatchStatus.state == GameMapPatchStatus.State.PATCH_READY) {
                    setPendingPatch(null)
                }
                val pendingApk = pendingPatchApk?.takeIf { it.exists() }
                if (pendingApk != null && !autoInstallTriggered &&
                    !GamePatchInstaller.isStockGameInstalled(appContext)
                ) {
                    autoInstallTriggered = true
                    launchPatchInstall(pendingApk)
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
        GameAutoAssaultBridge.sync(appContext)
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
            SettingsTabBar(
                selected = selectedTab,
                onSelect = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    selectedTab = it
                },
            )
        }

        when (selectedTab) {
            SettingsTab.OVERLAY -> {
                item {
                    SettingsPlainCard {
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
                    SettingsPlainCard {
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
                    SettingsPlainCard {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = SquadRelayDimens.listRowHorizontalPadding),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            PatchStatusPill(mapPatchStatus.state)
                            PatchVersionLine(mapPatchStatus)

                            val ready = mapPatchStatus.state == GameMapPatchStatus.State.PATCH_READY
                            // Патч скачан, но ещё не установлен (например, процесс перезапустился
                            // после удаления игры) — даём добить установку вручную.
                            val installPending = !ready &&
                                pendingPatchApk?.takeIf { it.exists() } != null
                            val patchActionable = installPending ||
                                mapPatchStatus.state ==
                                GameMapPatchStatus.State.PATCH_NOT_INSTALLED ||
                                mapPatchStatus.state == GameMapPatchStatus.State.PATCH_OUTDATED
                            val success = PremiumColors.liveIndicator

                            Button(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                shape = RoundedCornerShape(14.dp),
                                onClick = {
                                    if (patchInProgress) return@Button
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    val pending = pendingPatchApk?.takeIf { it.exists() }
                                    if (pending != null &&
                                        !GamePatchInstaller.isStockGameInstalled(appContext)
                                    ) {
                                        launchPatchInstall(pending)
                                        return@Button
                                    }
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
                                                    setPendingPatch(result.apk)
                                                    Toast.makeText(
                                                        context,
                                                        R.string.game_patch_uninstall_prompt,
                                                        Toast.LENGTH_LONG,
                                                    ).show()
                                                    GamePatchInstaller.requestUninstallStockGame(context)
                                                } else {
                                                    setPendingPatch(result.apk)
                                                    launchPatchInstall(result.apk)
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
                                    installPending -> {
                                        Icon(
                                            imageVector = Icons.Outlined.Download,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(stringResource(R.string.game_patch_install_ready_button))
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
                    AppVersionUpdateCard()
                }
            }

            SettingsTab.AUTOMATION -> {
                item {
                    SettingsPlainCard {
                        SettingsToggleRow(
                            title = stringResource(R.string.auto_help_switch_title),
                            subtitle = null,
                            checked = autoHelpEnabled,
                            onCheckedChange = { on ->
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                autoHelpEnabled = on
                                prefs.setAutoHelpEnabled(on)
                                // Авто-помощь теперь событийная (нажимает при появлении кнопки), интервал
                                // не используется — передаём сохранённое значение лишь для совместимости API.
                                GameAutoHelpBridge.write(context, on, autoHelpIntervalSec)
                            },
                        )
                    }
                }
            }

            SettingsTab.NOTIFICATIONS -> {
                item {
                    SettingsPlainCard {
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
    }
}

@Composable
private fun SettingsTabBar(
    selected: SettingsTab,
    onSelect: (SettingsTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabs = listOf(
        SettingsTab.OVERLAY to R.string.settings_tab_overlay,
        SettingsTab.AUTOMATION to R.string.settings_tab_automation,
        SettingsTab.NOTIFICATIONS to R.string.settings_tab_notifications,
    )
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = CircleShape,
        color = SquadRelaySurfaces.panelColor(0.48f),
        border = SquadRelaySurfaces.panelBorder(alpha = 0.15f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            tabs.forEach { (tab, labelRes) ->
                val isSelected = tab == selected
                val background by animateColorAsState(
                    targetValue = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        Color.Transparent
                    },
                    label = "settingsTabBg",
                )
                val foreground by animateColorAsState(
                    targetValue = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    label = "settingsTabFg",
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(CircleShape)
                        .background(background)
                        .clickable { onSelect(tab) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(labelRes),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        color = foreground,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsPlainCard(
    modifier: Modifier = Modifier,
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
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            content = content,
        )
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
private fun PatchVersionLine(status: GameMapPatchStatus.Status) {
    val installedGame = status.patchForGameVersion
        ?.takeIf { it.isNotBlank() && it != "unknown" }
        ?: status.gameVersionName
    val installedBridge = status.patchBridgeVersion?.takeIf { it.isNotBlank() }
    val hasInstalled = status.state == GameMapPatchStatus.State.PATCH_READY ||
        status.state == GameMapPatchStatus.State.PATCH_OUTDATED
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (hasInstalled && (installedGame != null || installedBridge != null)) {
            Text(
                text = stringResource(
                    R.string.settings_patch_version_installed,
                    installedGame ?: "—",
                    installedBridge ?: "—",
                ),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            text = stringResource(
                R.string.settings_patch_version_available,
                status.supportedGameVersion.ifBlank { "—" },
                BuildConfig.MAP_BRIDGE_VERSION.trim().ifBlank { "—" },
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private enum class AppUpdateUiState { CHECKING, UP_TO_DATE, AVAILABLE, FAILED }

@Composable
private fun AppVersionUpdateCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var uiState by remember { mutableStateOf(AppUpdateUiState.CHECKING) }
    var downloadUrl by remember { mutableStateOf<String?>(null) }
    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(-1f) }

    suspend fun runCheck() {
        uiState = AppUpdateUiState.CHECKING
        when (val result = checkAppUpdate()) {
            is AppUpdateCheckResult.Available -> {
                downloadUrl = result.downloadUrl
                uiState = AppUpdateUiState.AVAILABLE
            }
            AppUpdateCheckResult.UpToDate -> {
                downloadUrl = null
                uiState = AppUpdateUiState.UP_TO_DATE
            }
            AppUpdateCheckResult.Failed -> {
                downloadUrl = null
                uiState = AppUpdateUiState.FAILED
            }
        }
    }

    LaunchedEffect(Unit) { runCheck() }

    SettingsPlainCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SquadRelayDimens.listRowHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.settings_app_version_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(
                        R.string.settings_app_version_value,
                        BuildConfig.VERSION_NAME,
                        BuildConfig.VERSION_CODE,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            AppUpdateStatusPill(uiState)

            val success = PremiumColors.liveIndicator
            val upToDate = uiState == AppUpdateUiState.UP_TO_DATE

            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                onClick = {
                    if (downloading) return@Button
                    when (uiState) {
                        AppUpdateUiState.AVAILABLE -> {
                            val url = downloadUrl?.trim().orEmpty()
                            if (url.isEmpty()) return@Button
                            scope.launch {
                                downloading = true
                                progress = -1f
                                val result = downloadAppUpdateApk(context, url) { p ->
                                    progress = p
                                }
                                downloading = false
                                progress = -1f
                                val apk = result.getOrNull()
                                if (apk == null) {
                                    Toast.makeText(
                                        context,
                                        R.string.chat_apk_download_failed,
                                        Toast.LENGTH_LONG,
                                    ).show()
                                } else {
                                    Toast.makeText(
                                        context,
                                        R.string.app_update_install_starting,
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                    context.installDownloadedApk(apk)
                                }
                            }
                        }
                        AppUpdateUiState.FAILED -> scope.launch { runCheck() }
                        else -> Unit
                    }
                },
                enabled = !downloading &&
                    (uiState == AppUpdateUiState.AVAILABLE || uiState == AppUpdateUiState.FAILED),
                colors = if (upToDate) {
                    ButtonDefaults.buttonColors(
                        disabledContainerColor = success.copy(alpha = 0.18f),
                        disabledContentColor = success,
                    )
                } else {
                    ButtonDefaults.buttonColors()
                },
            ) {
                when {
                    downloading -> {
                        if (progress in 0f..1f) {
                            CircularProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(
                                    R.string.app_update_downloading_progress,
                                    (progress * 100f).toInt().coerceIn(0, 100),
                                ),
                            )
                        } else {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.app_update_downloading))
                        }
                    }
                    uiState == AppUpdateUiState.CHECKING -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.app_update_checking))
                    }
                    upToDate -> {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.app_update_button_up_to_date))
                    }
                    uiState == AppUpdateUiState.AVAILABLE -> {
                        Icon(
                            imageVector = Icons.Outlined.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.app_update_button_update))
                    }
                    else -> {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.app_update_button_retry))
                    }
                }
            }
        }
    }
}

@Composable
private fun AppUpdateStatusPill(state: AppUpdateUiState) {
    val target = when (state) {
        AppUpdateUiState.UP_TO_DATE -> PremiumColors.liveIndicator
        AppUpdateUiState.AVAILABLE -> MaterialTheme.colorScheme.primary
        AppUpdateUiState.FAILED -> MaterialTheme.colorScheme.error
        AppUpdateUiState.CHECKING -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val color by animateColorAsState(targetValue = target, label = "appUpdateStatusColor")
    val label = when (state) {
        AppUpdateUiState.UP_TO_DATE -> stringResource(R.string.app_update_badge_up_to_date)
        AppUpdateUiState.AVAILABLE -> stringResource(R.string.app_update_badge_available)
        AppUpdateUiState.FAILED -> stringResource(R.string.app_update_badge_failed)
        AppUpdateUiState.CHECKING -> stringResource(R.string.app_update_badge_checking)
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
