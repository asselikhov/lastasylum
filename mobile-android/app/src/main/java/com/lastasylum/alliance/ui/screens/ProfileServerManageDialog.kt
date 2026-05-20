package com.lastasylum.alliance.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.users.GameIdentityDto
import com.lastasylum.alliance.data.users.MyProfileDto
import com.lastasylum.alliance.ui.util.formatServerLabel

@Composable
fun ProfileServerManageDialog(
    profile: MyProfileDto,
    saving: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSwitch: (GameIdentityDto) -> Unit,
    onAddServer: (serverNumber: Int, gameNickname: String) -> Unit,
) {
    var showAdd by remember { mutableStateOf(false) }
    var serverDraft by remember { mutableStateOf("") }
    var nickDraft by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(stringResource(R.string.profile_server_manage_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.profile_server_manage_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                profile.gameIdentities.forEach { identity ->
                    val selected = identity.id == profile.activeGameIdentityId
                    Surface(
                        onClick = {
                            if (!selected && !saving) onSwitch(identity)
                        },
                        shape = MaterialTheme.shapes.medium,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        },
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = selected,
                                onClick = {
                                    if (!selected && !saving) onSwitch(identity)
                                },
                                enabled = !saving,
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = formatServerLabel(identity.serverNumber)
                                        ?: identity.serverNumber.toString(),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    text = identity.gameNickname,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                val team = identity.playerTeamDisplayName?.trim()
                                    ?: identity.playerTeamTag?.let { "[$it]" }
                                Text(
                                    text = if (!team.isNullOrBlank()) {
                                        stringResource(R.string.profile_game_identity_team, team)
                                    } else {
                                        stringResource(R.string.profile_game_identity_no_team)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                if (!showAdd) {
                    OutlinedButton(
                        onClick = { showAdd = true },
                        enabled = !saving,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.profile_server_add_new))
                    }
                } else {
                    OutlinedTextField(
                        value = serverDraft,
                        onValueChange = {
                            serverDraft = it.filter { c -> c.isDigit() }.take(4)
                        },
                        label = { Text(stringResource(R.string.profile_field_server_number)) },
                        singleLine = true,
                        enabled = !saving,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = nickDraft,
                        onValueChange = { nickDraft = it.trimStart() },
                        label = { Text(stringResource(R.string.auth_game_nickname)) },
                        singleLine = true,
                        enabled = !saving,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = {
                                showAdd = false
                                serverDraft = ""
                                nickDraft = ""
                            },
                            enabled = !saving,
                        ) {
                            Text(stringResource(R.string.profile_action_cancel))
                        }
                        Button(
                            onClick = {
                                val sn = serverDraft.toIntOrNull() ?: return@Button
                                val nick = nickDraft.trim()
                                if (sn !in 1..9999 || nick.length < 2) return@Button
                                onAddServer(sn, nick)
                            },
                            enabled = !saving &&
                                serverDraft.toIntOrNull()?.let { it in 1..9999 } == true &&
                                nickDraft.trim().length >= 2,
                        ) {
                            Text(stringResource(R.string.profile_action_save))
                        }
                    }
                }
                error?.let { e ->
                    Text(
                        e,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (!saving) onDismiss() }) {
                if (saving) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.profile_action_close))
                }
            }
        },
    )
}
