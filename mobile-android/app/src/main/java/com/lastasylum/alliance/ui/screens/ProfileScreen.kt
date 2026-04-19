package com.lastasylum.alliance.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.users.MyProfileDto
import com.lastasylum.alliance.di.AppContainer
import com.lastasylum.alliance.ui.theme.SquadRelayDimens
import com.lastasylum.alliance.ui.util.telegramAvatarUrl
import com.lastasylum.alliance.ui.util.telegramDisplayHandle
import kotlinx.coroutines.launch

private enum class ProfileEditDialog { None, DisplayName, Team, Telegram }

private fun teamDisplayValue(profile: MyProfileDto): String {
    val custom = profile.teamDisplayName?.trim().orEmpty()
    return if (custom.isNotEmpty()) custom else profile.allianceName
}

@Composable
private fun membershipLabel(status: String): String {
    return when (status.lowercase()) {
        "pending" -> stringResource(R.string.admin_status_pending)
        "active" -> stringResource(R.string.admin_status_active)
        "removed" -> stringResource(R.string.admin_status_removed)
        else -> status
    }
}

@Composable
private fun ProfileStatRow(
    label: String,
    value: String,
    subtitle: String? = null,
    editable: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (editable && onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.let { s ->
                Text(
                    text = s,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (editable && onClick != null) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun ProfileScreen(
    username: String,
    onLogout: () -> Unit,
) {
    val context = LocalContext.current
    val app = remember { AppContainer.from(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()

    var profile by remember { mutableStateOf<MyProfileDto?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }

    var dialog by remember { mutableStateOf(ProfileEditDialog.None) }
    var draft by remember { mutableStateOf("") }
    var dialogError by remember { mutableStateOf<String?>(null) }
    var dialogSaving by remember { mutableStateOf(false) }

    LaunchedEffect(app) {
        app.usersRepository.getMyProfile()
            .onSuccess {
                profile = it
                loadError = null
            }
            .onFailure {
                loadError = context.getString(R.string.profile_load_error)
            }
    }

    val displayName = profile?.username ?: username
    val initialLetter = remember(displayName) {
        displayName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    }

    fun openDialog(which: ProfileEditDialog, initialDraft: String) {
        dialog = which
        draft = initialDraft
        dialogError = null
    }

    fun closeDialog() {
        dialog = ProfileEditDialog.None
        dialogError = null
        dialogSaving = false
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
        Text(
            text = stringResource(R.string.profile_header_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 4.dp),
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Row(
                modifier = Modifier.padding(SquadRelayDimens.panelInnerPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = initialLetter,
                            style = MaterialTheme.typography.headlineSmall,
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
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    loadError?.let { err ->
                        Text(
                            text = err,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    profile?.email?.let { email ->
                        Text(
                            text = email,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.profile_section_account),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 4.dp),
                )
                profile?.let { p ->
                    ProfileStatRow(
                        label = stringResource(R.string.profile_field_ingame_name),
                        value = p.username,
                        editable = true,
                        onClick = { openDialog(ProfileEditDialog.DisplayName, p.username) },
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                    )
                    ProfileStatRow(
                        label = stringResource(R.string.profile_field_email),
                        value = p.email,
                        editable = false,
                        onClick = null,
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                    )
                    ProfileStatRow(
                        label = stringResource(R.string.profile_field_role),
                        value = p.role,
                        editable = false,
                        onClick = null,
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                    )
                    ProfileStatRow(
                        label = stringResource(R.string.profile_field_membership),
                        value = membershipLabel(p.membershipStatus),
                        editable = false,
                        onClick = null,
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                    )
                    ProfileStatRow(
                        label = stringResource(R.string.profile_field_team),
                        value = teamDisplayValue(p),
                        subtitle = stringResource(R.string.profile_team_code_hint, p.allianceName),
                        editable = true,
                        onClick = {
                            openDialog(
                                ProfileEditDialog.Team,
                                p.teamDisplayName?.trim().orEmpty(),
                            )
                        },
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                    )
                    ProfileStatRow(
                        label = stringResource(R.string.profile_field_telegram),
                        value = telegramDisplayHandle(p.telegramUsername)
                            ?: stringResource(R.string.profile_value_not_set),
                        editable = true,
                        onClick = {
                            openDialog(
                                ProfileEditDialog.Telegram,
                                p.telegramUsername?.let { h -> "@$h" } ?: "",
                            )
                        },
                    )
                } ?: run {
                    Text(
                        text = stringResource(R.string.profile_load_error),
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box(Modifier.padding(bottom = 8.dp))
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

    when (dialog) {
        ProfileEditDialog.DisplayName -> {
            AlertDialog(
                onDismissRequest = { if (!dialogSaving) closeDialog() },
                title = { Text(stringResource(R.string.profile_edit_name_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = draft,
                            onValueChange = {
                                draft = it
                                dialogError = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = !dialogSaving,
                            supportingText = {
                                Text(stringResource(R.string.profile_hint_ingame_name))
                            },
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Words,
                                keyboardType = KeyboardType.Text,
                            ),
                        )
                        dialogError?.let { e ->
                            Text(e, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val trimmed = draft.trim()
                            if (trimmed.length < 3) return@Button
                            scope.launch {
                                dialogSaving = true
                                dialogError = null
                                app.usersRepository.updateMyUsername(trimmed)
                                    .onSuccess {
                                        profile = it
                                        closeDialog()
                                    }
                                    .onFailure {
                                        dialogError = context.getString(R.string.profile_save_error_generic)
                                    }
                                dialogSaving = false
                            }
                        },
                        enabled = !dialogSaving && draft.trim().length >= 3,
                    ) {
                        if (dialogSaving) {
                            CircularProgressIndicator(
                                Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Text(stringResource(R.string.profile_action_save))
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { if (!dialogSaving) closeDialog() }) {
                        Text(stringResource(R.string.profile_action_cancel))
                    }
                },
            )
        }

        ProfileEditDialog.Team -> {
            AlertDialog(
                onDismissRequest = { if (!dialogSaving) closeDialog() },
                title = { Text(stringResource(R.string.profile_edit_team_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = draft,
                            onValueChange = {
                                draft = it
                                dialogError = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = !dialogSaving,
                            supportingText = {
                                Text(stringResource(R.string.profile_hint_team))
                            },
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Words,
                                keyboardType = KeyboardType.Text,
                            ),
                        )
                        dialogError?.let { e ->
                            Text(e, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            scope.launch {
                                dialogSaving = true
                                dialogError = null
                                app.usersRepository.updateMyTeamDisplayName(draft.trim())
                                    .onSuccess {
                                        profile = it
                                        closeDialog()
                                    }
                                    .onFailure {
                                        dialogError = context.getString(R.string.profile_save_error_generic)
                                    }
                                dialogSaving = false
                            }
                        },
                        enabled = !dialogSaving,
                    ) {
                        if (dialogSaving) {
                            CircularProgressIndicator(
                                Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Text(stringResource(R.string.profile_action_save))
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { if (!dialogSaving) closeDialog() }) {
                        Text(stringResource(R.string.profile_action_cancel))
                    }
                },
            )
        }

        ProfileEditDialog.Telegram -> {
            AlertDialog(
                onDismissRequest = { if (!dialogSaving) closeDialog() },
                title = { Text(stringResource(R.string.profile_edit_telegram_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = draft,
                            onValueChange = {
                                draft = it
                                dialogError = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = !dialogSaving,
                            supportingText = {
                                Text(stringResource(R.string.profile_hint_telegram))
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                            ),
                        )
                        dialogError?.let { e ->
                            Text(e, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                confirmButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            enabled = !dialogSaving && profile?.telegramUsername != null,
                            onClick = {
                                scope.launch {
                                    dialogSaving = true
                                    dialogError = null
                                    app.usersRepository.updateMyTelegram("")
                                        .onSuccess {
                                            profile = it
                                            closeDialog()
                                        }
                                        .onFailure {
                                            dialogError =
                                                context.getString(R.string.profile_save_error_telegram)
                                        }
                                    dialogSaving = false
                                }
                            },
                        ) {
                            Text(stringResource(R.string.profile_action_clear_link))
                        }
                        Button(
                            onClick = {
                                scope.launch {
                                    dialogSaving = true
                                    dialogError = null
                                    val raw = draft.trim().removePrefix("@").trim()
                                    app.usersRepository.updateMyTelegram(raw)
                                        .onSuccess {
                                            profile = it
                                            closeDialog()
                                        }
                                        .onFailure {
                                            dialogError =
                                                context.getString(R.string.profile_save_error_telegram)
                                        }
                                    dialogSaving = false
                                }
                            },
                            enabled = !dialogSaving,
                        ) {
                            if (dialogSaving) {
                                CircularProgressIndicator(
                                    Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            } else {
                                Text(stringResource(R.string.profile_action_save))
                            }
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { if (!dialogSaving) closeDialog() }) {
                        Text(stringResource(R.string.profile_action_cancel))
                    }
                },
            )
        }

        ProfileEditDialog.None -> Unit
    }
}
