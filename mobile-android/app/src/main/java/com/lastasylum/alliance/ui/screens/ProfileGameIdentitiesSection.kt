package com.lastasylum.alliance.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.users.GameIdentityDto
import com.lastasylum.alliance.data.users.MyProfileDto
import com.lastasylum.alliance.ui.components.GlassSurface

@Composable
fun ProfileGameIdentitiesSection(
    profile: MyProfileDto,
    switching: Boolean,
    onSwitch: (GameIdentityDto) -> Unit,
    onAdd: () -> Unit,
    onEdit: (GameIdentityDto) -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        shadowElevation = 4.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.profile_section_game_identities),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 4.dp),
            )
            profile.gameIdentities.forEachIndexed { index, identity ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                    )
                }
                val isActive = identity.id == profile.activeGameIdentityId
                val teamLabel = identity.playerTeamDisplayName?.trim()?.takeIf { it.isNotEmpty() }
                    ?: identity.playerTeamTag?.trim()?.takeIf { it.isNotEmpty() }?.let { "[$it]" }
                    ?: ""
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = stringResource(
                                    R.string.profile_game_identity_line,
                                    identity.serverNumber,
                                    identity.gameNickname,
                                ),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                            )
                            if (isActive) {
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                                ) {
                                    Text(
                                        text = stringResource(R.string.profile_game_identity_active_badge),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                        Text(
                            text = if (teamLabel.isNotBlank()) {
                                stringResource(R.string.profile_game_identity_team, teamLabel)
                            } else {
                                stringResource(R.string.profile_game_identity_no_team)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (!isActive) {
                        OutlinedButton(
                            onClick = { onSwitch(identity) },
                            enabled = !switching,
                        ) {
                            Text(stringResource(R.string.profile_game_identity_switch))
                        }
                    }
                    TextButton(onClick = { onEdit(identity) }) {
                        Text(stringResource(R.string.profile_game_identity_edit))
                    }
                }
            }
            OutlinedButton(
                onClick = onAdd,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(stringResource(R.string.profile_game_identity_add))
            }
        }
    }
}

@Composable
fun GameIdentityEditorDialog(
    title: String,
    serverDraft: String,
    nicknameDraft: String,
    error: String?,
    saving: Boolean,
    showDelete: Boolean,
    onServerChange: (String) -> Unit,
    onNicknameChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = serverDraft,
                    onValueChange = onServerChange,
                    label = { Text(stringResource(R.string.profile_field_server_number)) },
                    singleLine = true,
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = {
                        Text(stringResource(R.string.profile_hint_server_number))
                    },
                )
                OutlinedTextField(
                    value = nicknameDraft,
                    onValueChange = onNicknameChange,
                    label = { Text(stringResource(R.string.profile_field_ingame_name)) },
                    singleLine = true,
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = {
                        Text(stringResource(R.string.auth_game_nickname_helper))
                    },
                )
                error?.let { e ->
                    Text(e, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = onSave, enabled = !saving) {
                if (saving) {
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (showDelete && onDelete != null) {
                    TextButton(onClick = onDelete, enabled = !saving) {
                        Text(
                            stringResource(R.string.profile_game_identity_delete),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                TextButton(onClick = { if (!saving) onDismiss() }) {
                    Text(stringResource(R.string.profile_action_cancel))
                }
            }
        },
    )
}
