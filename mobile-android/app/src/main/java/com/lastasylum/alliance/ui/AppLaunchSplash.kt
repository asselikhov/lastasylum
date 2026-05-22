package com.lastasylum.alliance.ui

import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lastasylum.alliance.R
import com.lastasylum.alliance.ui.components.AtmosphericBackground
import com.lastasylum.alliance.ui.theme.SquadRelayAtmosphericPurple
import com.lastasylum.alliance.ui.theme.SquadRelayAtmosphericSky
import kotlinx.coroutines.delay

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

/** Восстановление сессии: анимация + неопределённый прогресс. */
@Composable
fun SessionBootstrapSplash(modifier: Modifier = Modifier) {
    LaunchSplashContent(
        modifier = modifier,
        subtitle = stringResource(R.string.launch_splash_session_subtitle),
        progress = null,
        showWarmupHint = false,
    )
}

/**
 * После входа: прогрев данных ([warmup]) и короткая анимация, затем [onComplete].
 */
@Composable
fun PostAuthLaunchSplash(
    onComplete: () -> Unit,
    warmup: suspend () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = rememberAnimatorScaleDisabled()
    var warmupDone by remember { mutableStateOf(false) }
    var displayProgress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(warmup) {
        val startedAt = System.currentTimeMillis()
        warmup()
        warmupDone = true
        val elapsed = System.currentTimeMillis() - startedAt
        if (elapsed < LAUNCH_SPLASH_MIN_MS) {
            delay(LAUNCH_SPLASH_MIN_MS - elapsed)
        }
    }

    val targetProgress = if (warmupDone) 1f else 0.12f
    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = if (reduceMotion) {
            tween(durationMillis = 0)
        } else {
            tween(durationMillis = 520, easing = FastOutSlowInEasing)
        },
        label = "launch_warmup_progress",
    )

    LaunchedEffect(animatedProgress, warmupDone) {
        displayProgress = animatedProgress
        if (warmupDone && animatedProgress >= 0.999f) {
            onComplete()
        }
    }

    LaunchSplashContent(
        modifier = modifier,
        subtitle = stringResource(R.string.launch_splash_welcome_subtitle),
        progress = displayProgress,
        showWarmupHint = !warmupDone,
    )
}

@Composable
private fun LaunchSplashContent(
    subtitle: String,
    progress: Float?,
    showWarmupHint: Boolean,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = rememberAnimatorScaleDisabled()
    val scheme = MaterialTheme.colorScheme
    val infinite = rememberInfiniteTransition(label = "splash_ambient")
    val pulse by infinite.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "splash_pulse",
    )
    val shimmer by infinite.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "splash_shimmer",
    )
    val titleAlpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = if (reduceMotion) {
            tween(0)
        } else {
            tween(700, easing = FastOutSlowInEasing)
        },
        label = "splash_title_alpha",
    )
    val titleScale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = if (reduceMotion) {
            tween(0)
        } else {
            tween(900, easing = FastOutSlowInEasing)
        },
        label = "splash_title_scale",
    )

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        AtmosphericBackground(Modifier.fillMaxSize())
        Box(
            Modifier
                .size(300.dp)
                .align(Alignment.Center)
                .offset(y = (-36).dp)
                .graphicsLayer {
                    scaleX = pulse
                    scaleY = pulse
                    alpha = shimmer
                }
                .blur(52.dp),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            SquadRelayAtmosphericPurple.copy(alpha = 0.45f),
                            SquadRelayAtmosphericSky.copy(alpha = 0.1f),
                            Color.Transparent,
                        ),
                    ),
                    radius = size.minDimension / 2f,
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                ),
                color = scheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .graphicsLayer {
                        alpha = titleAlpha
                        scaleX = 0.92f + titleScale * 0.08f
                        scaleY = 0.92f + titleScale * 0.08f
                    },
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onSurfaceVariant.copy(alpha = 0.92f),
                textAlign = TextAlign.Center,
            )
            if (showWarmupHint) {
                Text(
                    text = stringResource(R.string.launch_splash_preparing),
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.primary.copy(alpha = 0.88f),
                    textAlign = TextAlign.Center,
                )
            }
            if (progress == null) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    color = scheme.primary,
                    trackColor = scheme.surface.copy(alpha = 0.35f),
                )
            } else {
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    color = scheme.primary,
                    trackColor = scheme.surface.copy(alpha = 0.35f),
                )
            }
        }
    }
}

/** Минимальное время splash, чтобы анимация не мелькала. */
const val LAUNCH_SPLASH_MIN_MS = 900L

/** @deprecated Используйте [LAUNCH_SPLASH_MIN_MS]; оставлено для совместимости. */
const val LAUNCH_SPLASH_MS = LAUNCH_SPLASH_MIN_MS
