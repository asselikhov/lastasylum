package com.lastasylum.alliance.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.lastasylum.alliance.R
import com.lastasylum.alliance.di.AppContainer
import com.lastasylum.alliance.overlay.GameForegroundGate
import com.lastasylum.alliance.overlay.OverlayPermissions
import com.lastasylum.alliance.ui.theme.SquadRelaySurfaces

@Composable
fun PermissionOnboardingGate() {
    val context = LocalContext.current
    val app = remember { AppContainer.from(context.applicationContext) }
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (app.onboardingPreferences.isPermissionOnboardingDone()) return@LaunchedEffect
        val micOk = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val overlayOk = OverlayPermissions.canDrawOverlays(context)
        val usageOk = GameForegroundGate.hasUsageStatsAccessForOverlay(context)
        val batteryOk = OverlayPermissions.isBatteryOptimizationIgnored(context)
        val notifOk = if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        if (!micOk || !overlayOk || !usageOk || !batteryOk || !notifOk) {
            visible = true
        } else {
            app.onboardingPreferences.setPermissionOnboardingDone(true)
        }
    }

    if (!visible) return

    AlertDialog(
        onDismissRequest = { },
        containerColor = SquadRelaySurfaces.dialogColor(),
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = { Text(stringResource(R.string.onboarding_permissions_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = stringResource(R.string.onboarding_permissions_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(R.string.onboarding_permissions_bullet_overlay),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = stringResource(R.string.onboarding_permissions_bullet_usage),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = stringResource(R.string.onboarding_permissions_bullet_battery),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = stringResource(R.string.onboarding_permissions_bullet_mic),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = stringResource(R.string.onboarding_permissions_bullet_notif),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = stringResource(R.string.onboarding_permissions_oem_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Column {
                TextButton(
                    onClick = { OverlayPermissions.openOverlayPermissionSettings(context) },
                ) {
                    Text(stringResource(R.string.onboarding_permissions_overlay))
                }
                TextButton(
                    onClick = { OverlayPermissions.openUsageAccessSettings(context) },
                ) {
                    Text(stringResource(R.string.onboarding_permissions_usage))
                }
                TextButton(
                    onClick = { OverlayPermissions.openBatteryUnrestrictedSettings(context) },
                ) {
                    Text(stringResource(R.string.onboarding_permissions_battery))
                }
                TextButton(
                    onClick = {
                        val overlayGranted = OverlayPermissions.canDrawOverlays(context)
                        val usageGranted = GameForegroundGate.usageAccessMode(context) ==
                            GameForegroundGate.UsageAccessMode.FULL
                        val notifGranted = if (Build.VERSION.SDK_INT >= 33) {
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS,
                            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                        } else {
                            true
                        }
                        if (!overlayGranted || !usageGranted || !notifGranted) {
                            return@TextButton
                        }
                        visible = false
                        app.onboardingPreferences.setPermissionOnboardingDone(true)
                    },
                ) {
                    Text(stringResource(R.string.onboarding_permissions_later))
                }
            }
        },
    )
}
