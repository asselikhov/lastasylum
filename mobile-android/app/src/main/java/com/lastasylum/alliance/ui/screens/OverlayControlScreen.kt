package com.lastasylum.alliance.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.settings.UserSettingsPreferences
import com.lastasylum.alliance.overlay.CombatOverlayService
import com.lastasylum.alliance.overlay.GameForegroundGate
import com.lastasylum.alliance.overlay.OverlayPermissions
import com.lastasylum.alliance.ui.components.PrimaryPanel
import com.lastasylum.alliance.ui.theme.SquadRelayDimens
import kotlinx.coroutines.delay

@Composable
fun OverlayControlScreen() {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val prefs = remember(appContext) { UserSettingsPreferences(appContext) }

    var overlayRunning by remember { mutableStateOf(CombatOverlayService.isServiceInstanceActive) }
    var gameGateOnly by remember { mutableStateOf(prefs.isOverlayGameGateEnabled()) }
    var targetPkg by remember { mutableStateOf(prefs.getOverlayTargetGamePackage()) }
    var pendingEnable by remember { mutableStateOf(false) }

    val latestPendingEnable = rememberUpdatedState(pendingEnable)
    val latestGameGate = rememberUpdatedState(gameGateOnly)

    fun micOk(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECORD_AUDIO,
    ) == PackageManager.PERMISSION_GRANTED

    fun overlayOk(): Boolean = OverlayPermissions.canDrawOverlays(context)

    fun usageOk(): Boolean = !latestGameGate.value ||
        GameForegroundGate.hasUsageStatsAccess(context)

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted && latestPendingEnable.value) {
            when {
                !overlayOk() -> OverlayPermissions.openOverlayPermissionSettings(context)
                !usageOk() -> OverlayPermissions.openUsageAccessSettings(context)
                CombatOverlayService.startService(context) -> {
                    overlayRunning = true
                    pendingEnable = false
                }
                else -> pendingEnable = false
            }
        } else if (!granted) {
            pendingEnable = false
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                overlayRunning = CombatOverlayService.isServiceInstanceActive
                if (latestPendingEnable.value && !overlayRunning && micOk() && overlayOk() && usageOk()) {
                    if (CombatOverlayService.startService(context)) {
                        overlayRunning = true
                        pendingEnable = false
                    }
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

    val scroll = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .navigationBarsPadding()
            .imePadding()
            .padding(
                horizontal = SquadRelayDimens.contentPaddingHorizontal,
                vertical = SquadRelayDimens.screenTopPadding,
            ),
        verticalArrangement = Arrangement.spacedBy(SquadRelayDimens.sectionGap),
    ) {
        PrimaryPanel {
            Text(
                text = stringResource(R.string.overlay_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(20.dp))

            OverlaySwitchRow(
                title = stringResource(R.string.overlay_switch_panel),
                checked = overlayRunning,
                onCheckedChange = { on ->
                    if (on) {
                        pendingEnable = true
                        when {
                            !micOk() -> micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            !overlayOk() -> OverlayPermissions.openOverlayPermissionSettings(context)
                            !usageOk() -> OverlayPermissions.openUsageAccessSettings(context)
                            CombatOverlayService.startService(context) -> {
                                overlayRunning = true
                                pendingEnable = false
                            }
                            else -> pendingEnable = false
                        }
                    } else {
                        pendingEnable = false
                        CombatOverlayService.stopService(context)
                        overlayRunning = false
                    }
                },
            )

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(8.dp))

            OverlaySwitchRow(
                title = stringResource(R.string.overlay_switch_game_only),
                checked = gameGateOnly,
                onCheckedChange = { v ->
                    gameGateOnly = v
                    prefs.setOverlayGameGateEnabled(v)
                    CombatOverlayService.requestGateRecheckIfRunning(context)
                },
            )

            if (gameGateOnly) {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = targetPkg,
                    onValueChange = { targetPkg = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.overlay_package_field_label)) },
                    textStyle = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun OverlaySwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
            ),
        )
    }
}
