package com.lastasylum.alliance.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.BuildConfig
import coil.compose.AsyncImage
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.settings.UserSettingsPreferences
import com.lastasylum.alliance.data.users.MyProfileDto
import com.lastasylum.alliance.di.AppContainer
import com.lastasylum.alliance.overlay.CombatOverlayService
import com.lastasylum.alliance.ui.components.SettingsDivider
import com.lastasylum.alliance.ui.components.SettingsToggleRow
import com.lastasylum.alliance.ui.theme.SquadRelayDimens
import com.lastasylum.alliance.ui.util.telegramAvatarUrl
import com.lastasylum.alliance.ui.util.telegramDisplayHandle
import com.lastasylum.alliance.update.fetchNewerApkDownloadUrl
import com.lastasylum.alliance.update.openApkDownload
import com.lastasylum.alliance.update.toastNoUpdateAvailable
import com.lastasylum.alliance.update.toastUpdateCheckFailed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
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
    var overlayPreset by remember {
        mutableStateOf(app.userSettingsPreferences.getOverlayLayoutPreset())
    }
    val scope = rememberCoroutineScope()
    val buildTimeStr = remember {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            .format(Date(BuildConfig.BUILD_TIME_MS))
    }
    val scroll = rememberScrollState()
    var profile by remember { mutableStateOf<MyProfileDto?>(null) }
    var telegramInput by remember { mutableStateOf("") }
    var profileLoadError by remember { mutableStateOf<String?>(null) }
    var telegramSaveError by remember { mutableStateOf<String?>(null) }
    var telegramSaving by remember { mutableStateOf(false) }

    LaunchedEffect(app) {
        app.usersRepository.getMyProfile()
            .onSuccess {
                profile = it
                telegramInput = it.telegramUsername?.let { h -> "@$h" } ?: ""
                profileLoadError = null
            }
            .onFailure {
                profileLoadError = context.getString(R.string.profile_telegram_load_error)
            }
    }

    val displayName = profile?.username ?: username
    val initialLetter = remember(displayName) {
        displayName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
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
            Row(
                modifier = Modifier.padding(SquadRelayDimens.panelInnerPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SquadRelayDimens.blockGap),
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = initialLetter,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    val avatarUrl = telegramAvatarUrl(profile?.telegramUsername)
                    if (avatarUrl != null) {
                        AsyncImage(
                            model = avatarUrl,
                            contentDescription = stringResource(R.string.profile_telegram_avatar_cd),
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(SquadRelayDimens.headerSubtitleGap),
                ) {
                    profileLoadError?.let { err ->
                        Text(
                            text = err,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    telegramDisplayHandle(profile?.telegramUsername)?.let { handle ->
                        Text(
                            text = handle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Text(
                            text = stringResource(R.string.profile_role, profile?.role ?: role),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
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
                verticalArrangement = Arrangement.spacedBy(SquadRelayDimens.itemGap),
            ) {
                Text(
                    text = stringResource(R.string.profile_telegram_section),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                OutlinedTextField(
                    value = telegramInput,
                    onValueChange = {
                        telegramSaveError = null
                        telegramInput = it
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.profile_telegram_field_label)) },
                    placeholder = { Text(stringResource(R.string.profile_telegram_hint)) },
                    singleLine = true,
                    enabled = !telegramSaving,
                )
                telegramSaveError?.let { err ->
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = {
                            if (telegramSaving) return@TextButton
                            scope.launch {
                                telegramSaving = true
                                telegramSaveError = null
                                app.usersRepository.updateMyTelegram("")
                                    .onSuccess {
                                        profile = it
                                        telegramInput = ""
                                    }
                                    .onFailure {
                                        telegramSaveError = context.getString(
                                            R.string.profile_telegram_save_error,
                                        )
                                    }
                                telegramSaving = false
                            }
                        },
                        enabled = !telegramSaving && profile?.telegramUsername != null,
                    ) {
                        Text(stringResource(R.string.profile_telegram_clear))
                    }
                    Button(
                        onClick = {
                            if (telegramSaving) return@Button
                            scope.launch {
                                telegramSaving = true
                                telegramSaveError = null
                                val raw = telegramInput.trim().removePrefix("@").trim()
                                app.usersRepository.updateMyTelegram(raw)
                                    .onSuccess {
                                        profile = it
                                        telegramInput = it.telegramUsername?.let { h -> "@$h" } ?: ""
                                    }
                                    .onFailure {
                                        telegramSaveError = context.getString(
                                            R.string.profile_telegram_save_error,
                                        )
                                    }
                                telegramSaving = false
                            }
                        },
                        enabled = !telegramSaving,
                    ) {
                        if (telegramSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Text(stringResource(R.string.profile_telegram_save))
                        }
                    }
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 0.dp,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                SettingsToggleRow(
                    title = stringResource(R.string.profile_quiet_title),
                    subtitle = stringResource(R.string.profile_quiet_subtitle),
                    checked = quietMode,
                    onCheckedChange = { v ->
                        quietMode = v
                        app.userSettingsPreferences.setQuietMode(v)
                        CombatOverlayService.refreshQuietNotificationIfRunning(context)
                    },
                )
                SettingsDivider()
                SettingsToggleRow(
                    title = stringResource(R.string.profile_compact_title),
                    subtitle = stringResource(R.string.profile_compact_subtitle),
                    checked = compactOverlay,
                    onCheckedChange = { v ->
                        compactOverlay = v
                        app.userSettingsPreferences.setCompactOverlay(v)
                        CombatOverlayService.requestRebuildOverlayIfRunning(context)
                    },
                )
                SettingsDivider()
                Column(
                    modifier = Modifier.padding(SquadRelayDimens.panelInnerPadding),
                    verticalArrangement = Arrangement.spacedBy(SquadRelayDimens.itemGap),
                ) {
                    Text(
                        text = stringResource(R.string.profile_overlay_layout_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(SquadRelayDimens.itemGap),
                        verticalArrangement = Arrangement.spacedBy(SquadRelayDimens.itemGap),
                    ) {
                        listOf(
                            UserSettingsPreferences.PRESET_BALANCED to R.string.profile_overlay_preset_balanced,
                            UserSettingsPreferences.PRESET_COMMANDER to R.string.profile_overlay_preset_commander,
                            UserSettingsPreferences.PRESET_MINIMAL to R.string.profile_overlay_preset_minimal,
                        ).forEach { (key, labelRes) ->
                            val selected = overlayPreset == key
                            OutlinedButton(
                                onClick = {
                                    overlayPreset = key
                                    app.userSettingsPreferences.setOverlayLayoutPreset(key)
                                    CombatOverlayService.requestRebuildOverlayIfRunning(context)
                                },
                                border = if (selected) {
                                    BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                                } else {
                                    ButtonDefaults.outlinedButtonBorder(enabled = true)
                                },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (selected) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHigh
                                    },
                                ),
                            ) {
                                Text(
                                    text = stringResource(labelRes),
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
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
                verticalArrangement = Arrangement.spacedBy(SquadRelayDimens.itemGap),
            ) {
                Text(
                    text = stringResource(R.string.profile_build_title),
                    style = MaterialTheme.typography.titleSmall,
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
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.7f)),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error,
            ),
        ) {
            Text(stringResource(R.string.profile_logout))
        }
    }
}
