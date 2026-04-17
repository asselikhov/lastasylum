package com.lastasylum.alliance.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.overlay.CombatOverlayService
import com.lastasylum.alliance.overlay.OverlayPermissions

@Composable
fun OverlayControlScreen(
    contentPadding: PaddingValues,
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "Overlay Control",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = "Enable floating battle overlay and push-to-talk mode before entering the game.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = "Current role privileges: $role",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
        )
        Button(onClick = {
            if (!hasMicPermission.value) {
                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                return@Button
            }
            if (OverlayPermissions.canDrawOverlays(context)) {
                CombatOverlayService.startService(context)
            } else {
                OverlayPermissions.openOverlayPermissionSettings(context)
            }
        }) {
            Text(text = "Start Combat Mode")
        }
        OutlinedButton(onClick = { CombatOverlayService.stopService(context) }) {
            Text(text = "Stop Combat Mode")
        }
        OutlinedButton(onClick = {
            if (!hasMicPermission.value) {
                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                return@OutlinedButton
            }
            if (!isRecording.value) {
                CombatOverlayService.startRecording(context)
            } else {
                CombatOverlayService.stopRecording(context)
            }
            isRecording.value = !isRecording.value
        }, colors = ButtonDefaults.outlinedButtonColors()) {
            Text(text = if (isRecording.value) "Stop PTT Recording" else "Start PTT Recording")
        }
        OutlinedButton(onClick = {
            OverlayPermissions.openOverlayPermissionSettings(context)
        }) {
            Text(text = "Open Overlay Permission")
        }
    }
}
