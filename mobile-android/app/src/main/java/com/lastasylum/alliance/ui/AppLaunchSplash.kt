package com.lastasylum.alliance.ui

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.R
import com.lastasylum.alliance.ui.components.AtmosphericBackground
import com.lastasylum.alliance.ui.components.GlassSurface

@Composable
private fun rememberAnimatorScaleDisabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }
}

/** Восстановление сессии: неопределённый прогресс. */
@Composable
fun SessionBootstrapSplash(modifier: Modifier = Modifier) {
    LaunchSplashScaffold(
        modifier = modifier,
        subtitle = stringResource(R.string.launch_splash_session_subtitle),
        progress = null,
    )
}

/** После успешного входа — короткий экран приветствия (не блокирует вход дольше [LAUNCH_SPLASH_MS]). */
@Composable
fun PostAuthLaunchSplash(
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = rememberAnimatorScaleDisabled()
    val animationSpec = remember(reduceMotion) {
        if (reduceMotion) {
            tween<Float>(
                durationMillis = 0,
                delayMillis = LAUNCH_SPLASH_MS.toInt(),
                easing = LinearEasing,
            )
        } else {
            tween(LAUNCH_SPLASH_MS.toInt(), easing = LinearEasing)
        }
    }
    val progress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = animationSpec,
        label = "launch_splash_progress",
    )
    var fired by remember { mutableStateOf(false) }
    LaunchedEffect(progress) {
        if (progress >= 1f && !fired) {
            fired = true
            onComplete()
        }
    }
    LaunchSplashScaffold(
        modifier = modifier,
        subtitle = stringResource(R.string.launch_splash_welcome_subtitle),
        progress = progress,
    )
}

@Composable
private fun LaunchSplashScaffold(
    subtitle: String,
    progress: Float?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        AtmosphericBackground(Modifier.fillMaxSize())
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp),
            shape = RoundedCornerShape(28.dp),
            shadowElevation = 12.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                val scheme = MaterialTheme.colorScheme
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                if (progress == null) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = scheme.primary,
                        trackColor = scheme.surface.copy(alpha = 0.38f),
                    )
                } else {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = scheme.primary,
                        trackColor = scheme.surface.copy(alpha = 0.38f),
                    )
                }
            }
        }
    }
}

const val LAUNCH_SPLASH_MS = 350L
