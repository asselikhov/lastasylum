package com.lastasylum.alliance.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.BuildConfig
import com.lastasylum.alliance.R
import com.lastasylum.alliance.di.AppContainer
import com.lastasylum.alliance.overlay.CombatOverlayService
import com.lastasylum.alliance.ui.theme.SquadRelayDimens
import com.lastasylum.alliance.update.fetchNewerApkDownloadUrl
import com.lastasylum.alliance.update.openApkDownload
import com.lastasylum.alliance.update.toastNoUpdateAvailable
import com.lastasylum.alliance.update.toastUpdateCheckFailed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(
    username: String,
    role: String,
    onLogout: () -> Unit,
) {
    val context = LocalContext.current
    val app = remember {
        AppContainer.from(context.applicationContext)
    }
    var quietMode by remember {
        mutableStateOf(app.userSettingsPreferences.isQuietMode())
    }
    var compactOverlay by remember {
        mutableStateOf(app.userSettingsPreferences.isCompactOverlay())
    }
    val scope = rememberCoroutineScope()
    val buildTimeStr = remember {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            .format(Date(BuildConfig.BUILD_TIME_MS))
    }
    val scroll = rememberScrollState()
    val initial = remember(username) {
        username.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(
                horizontal = SquadRelayDimens.screenPaddingHorizontal - 4.dp,
                vertical = SquadRelayDimens.screenPaddingVertical,
            ),
        verticalArrangement = Arrangement.spacedBy(SquadRelayDimens.sectionGap),
    ) {
        Text(
            text = stringResource(R.string.profile_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )

        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        ) {
            Row(
                modifier = Modifier.padding(SquadRelayDimens.cardInnerPadding + 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = initial,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = stringResource(R.string.profile_pilot, username),
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Text(
                            text = stringResource(R.string.profile_role, role),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }

        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        ) {
            Column(
                modifier = Modifier.padding(SquadRelayDimens.cardInnerPadding),
                verticalArrangement = Arrangement.spacedBy(SquadRelayDimens.blockGap),
            ) {
                SettingRow(
                    title = stringResource(R.string.profile_quiet_title),
                    subtitle = stringResource(R.string.profile_quiet_subtitle),
                    checked = quietMode,
                    onToggle = { v ->
                        quietMode = v
                        app.userSettingsPreferences.setQuietMode(v)
                        CombatOverlayService.refreshQuietNotificationIfRunning(context)
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingRow(
                    title = stringResource(R.string.profile_compact_title),
                    subtitle = stringResource(R.string.profile_compact_subtitle),
                    checked = compactOverlay,
                    onToggle = { v ->
                        compactOverlay = v
                        app.userSettingsPreferences.setCompactOverlay(v)
                        CombatOverlayService.requestRebuildOverlayIfRunning(context)
                    },
                )
            }
        }

        Text(
            text = stringResource(R.string.profile_settings_overlay_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f),
        )

        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        ) {
            Column(
                modifier = Modifier.padding(SquadRelayDimens.cardInnerPadding),
                verticalArrangement = Arrangement.spacedBy(SquadRelayDimens.itemGap),
            ) {
                Text(
                    text = stringResource(R.string.profile_build_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(
                        R.string.profile_build_line,
                        BuildConfig.VERSION_NAME,
                        BuildConfig.VERSION_CODE,
                        buildTimeStr,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            runCatching { fetchNewerApkDownloadUrl() }
                                .onSuccess { url ->
                                    if (url != null) {
                                        context.openApkDownload(url)
                                    } else {
                                        context.toastNoUpdateAvailable()
                                    }
                                }
                                .onFailure {
                                    context.toastUpdateCheckFailed()
                                }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Text(stringResource(R.string.profile_check_update))
                }
            }
        }

        OutlinedButton(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error,
            ),
        ) {
            Text(stringResource(R.string.profile_logout))
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
            ),
        )
    }
}
