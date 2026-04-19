package com.lastasylum.alliance.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
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
import com.lastasylum.alliance.overlay.OverlayPermissions
import com.lastasylum.alliance.ui.theme.SquadRelayDimens

@Composable
fun OverlayControlScreen(
    role: String,
) {
    val context = LocalContext.current
    val isRecording = remember { mutableStateOf(false) }
    val hasMicPermission = remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasMicPermission.value = granted
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasMicPermission.value = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO,
                ) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val scroll = rememberScrollState()
    val appContext = context.applicationContext
    val userPrefs = remember(appContext) {
        UserSettingsPreferences(appContext)
    }
    var gameGate by remember { mutableStateOf(userPrefs.isOverlayGameGateEnabled()) }
    var targetPkg by remember { mutableStateOf(userPrefs.getOverlayTargetGamePackage()) }

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
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 0.dp,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(
                modifier = Modifier.padding(SquadRelayDimens.panelInnerPadding),
                verticalArrangement = Arrangement.spacedBy(SquadRelayDimens.blockGap),
            ) {
                Text(
                    text = stringResource(R.string.overlay_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.overlay_role_privileges, role),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    onClick = {
                        val micOk = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO,
                        ) == PackageManager.PERMISSION_GRANTED
                        hasMicPermission.value = micOk
                        if (!micOk) {
                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            return@Button
                        }
                        if (OverlayPermissions.canDrawOverlays(context)) {
                            CombatOverlayService.startService(context)
                        } else {
                            OverlayPermissions.openOverlayPermissionSettings(context)
                        }
                    },
                ) {
                    Text(text = stringResource(R.string.overlay_start_combat))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(SquadRelayDimens.itemGap),
                ) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.large,
                        onClick = { CombatOverlayService.stopService(context) },
                    ) {
                        Text(text = stringResource(R.string.overlay_stop_combat))
                    }
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.large,
                        onClick = { OverlayPermissions.openOverlayPermissionSettings(context) },
                    ) {
                        Text(text = stringResource(R.string.overlay_permission_settings))
                    }
                }
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    onClick = {
                        val micOk = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO,
                        ) == PackageManager.PERMISSION_GRANTED
                        hasMicPermission.value = micOk
                        if (!micOk) {
                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            return@OutlinedButton
                        }
                        if (!isRecording.value) {
                            CombatOverlayService.startRecording(context)
                        } else {
                            CombatOverlayService.stopRecording(context)
                        }
                        isRecording.value = !isRecording.value
                    },
                    colors = ButtonDefaults.outlinedButtonColors(),
                ) {
                    Text(
                        text = if (isRecording.value) {
                            stringResource(R.string.overlay_ptt_stop)
                        } else {
                            stringResource(R.string.overlay_ptt_start)
                        },
                    )
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 0.dp,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(
                modifier = Modifier.padding(SquadRelayDimens.panelInnerPadding),
                verticalArrangement = Arrangement.spacedBy(SquadRelayDimens.blockGap),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(SquadRelayDimens.blockGap),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.overlay_game_gate_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Switch(
                        checked = gameGate,
                        onCheckedChange = { v ->
                            gameGate = v
                            userPrefs.setOverlayGameGateEnabled(v)
                            CombatOverlayService.requestGateRecheckIfRunning(context)
                        },
                    )
                }
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = targetPkg,
                    onValueChange = { targetPkg = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.overlay_target_package_label)) },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(SquadRelayDimens.itemGap),
                ) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.large,
                        onClick = {
                            userPrefs.setOverlayTargetGamePackage(targetPkg)
                            CombatOverlayService.requestGateRecheckIfRunning(context)
                        },
                    ) {
                        Text(text = stringResource(R.string.overlay_target_package_save))
                    }
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.large,
                        onClick = { OverlayPermissions.openUsageAccessSettings(context) },
                    ) {
                        Text(text = stringResource(R.string.overlay_usage_access_button))
                    }
                }
            }
        }
    }
}
