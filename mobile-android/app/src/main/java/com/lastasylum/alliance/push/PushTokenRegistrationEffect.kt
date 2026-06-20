package com.lastasylum.alliance.push

import android.Manifest
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay

/**
 * Retries FCM → backend registration while signed in.
 * On Android 13+ prompts for [POST_NOTIFICATIONS] once (needed on many devices for FCM).
 */
@Composable
fun PushTokenRegistrationEffect(enabled: Boolean) {
    if (!enabled) return
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val lifecycleOwner = LocalLifecycleOwner.current
    var permissionPass by remember { mutableIntStateOf(0) }
    var resumePass by remember { mutableIntStateOf(0) }

    DisposableEffect(lifecycleOwner, enabled) {
        if (!enabled) return@DisposableEffect onDispose {}
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumePass++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        permissionPass++
    }

    LaunchedEffect(enabled, permissionPass, resumePass) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && activity != null) {
            val granted = ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted && permissionPass == 0) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return@LaunchedEffect
            }
        }
        val delays = listOf(0L, 1_000L, 2_000L, 4_000L, 8_000L, 15_000L, 30_000L, 60_000L)
        for (waitMs in delays) {
            if (waitMs > 0) delay(waitMs)
            if (PushTokenRegistrationCoordinator.registerWithBackend(context).isSuccess) return@LaunchedEffect
        }
    }
}
