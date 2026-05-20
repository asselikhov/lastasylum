package com.lastasylum.alliance.ui.screens.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.R
import com.lastasylum.alliance.ui.util.accountRoleLabel
import com.lastasylum.alliance.data.admin.AdminUserOnServerDto
import com.lastasylum.alliance.ui.util.formatServerLabel
import com.lastasylum.alliance.ui.util.teamTagWithServerPrefix

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminPlayerManageSheet(
    player: AdminUserOnServerDto,
    currentUserId: String,
    onDismiss: () -> Unit,
    onSaveGameIdentity: (gameNickname: String, serverNumber: Int) -> Unit,
    onApprove: () -> Unit,
    onRemoveFromTeam: () -> Unit,
    onRestorePending: () -> Unit,
    onSetRole: (String) -> Unit,
    onDelete: () -> Unit,
) {
    var nickDraft by remember(player.identityId) { mutableStateOf(player.gameNickname) }
    val needsGameIdentity = player.identityId.isBlank()
    var serverDraft by remember(player.identityId) {
        mutableStateOf(
            if (player.serverNumber >= 1) player.serverNumber.toString() else "",
        )
    }
    LaunchedEffect(player.gameNickname, player.serverNumber, player.identityId) {
        nickDraft = player.gameNickname
        serverDraft =
            if (player.serverNumber >= 1) player.serverNumber.toString() else ""
    }

    val serverNum = serverDraft.trim().toIntOrNull()
    val nickOk = nickDraft.trim().length in 2..32
    val serverOk = serverNum != null && serverNum in 1..9999
    val identityChanged = nickOk && serverOk && (
        needsGameIdentity ||
            nickDraft.trim() != player.gameNickname ||
            serverNum != player.serverNumber
        )

    val statusLabel = when (player.membershipStatus) {
        "pending" -> stringResource(R.string.admin_status_pending)
        "removed" -> stringResource(R.string.admin_status_removed)
        else -> stringResource(R.string.admin_status_active)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            when {
                formatServerLabel(player.serverNumber) != null ->
                    "${formatServerLabel(player.serverNumber)} ${player.gameNickname}"
                needsGameIdentity ->
                    stringResource(R.string.admin_players_no_server_title, player.gameNickname)
                else -> player.gameNickname
            },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        if (needsGameIdentity) {
            Text(
                stringResource(R.string.admin_players_no_server_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            stringResource(R.string.admin_players_account, player.email),
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            stringResource(R.string.admin_field_status, statusLabel),
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            stringResource(
                R.string.admin_field_role,
                accountRoleLabel(player.accountRole),
            ),
            style = MaterialTheme.typography.bodySmall,
        )
        val teamLine = player.playerTeamTag?.let { tag ->
            val prefix = teamTagWithServerPrefix(tag, player.serverNumber)
            "$prefix ${player.playerTeamDisplayName.orEmpty()}".trim()
        }
        if (!teamLine.isNullOrBlank()) {
            Text(
                stringResource(R.string.admin_game_servers_team, teamLine),
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            Text(
                stringResource(R.string.admin_players_no_team),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        HorizontalDivider()
        Text(
            stringResource(R.string.admin_players_game_section),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        OutlinedTextField(
            value = serverDraft,
            onValueChange = { serverDraft = it.filter { c -> c.isDigit() }.take(4) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(R.string.profile_field_server)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        OutlinedTextField(
            value = nickDraft,
            onValueChange = { nickDraft = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(R.string.admin_players_game_nick)) },
        )
        Button(
            onClick = {
                if (serverNum != null) {
                    onSaveGameIdentity(nickDraft.trim(), serverNum)
                    onDismiss()
                }
            },
            enabled = identityChanged,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.admin_players_save_game))
        }

        HorizontalDivider()
        Text(
            stringResource(R.string.admin_players_moderation_section),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        when (player.membershipStatus) {
            "pending" -> Button(onClick = onApprove, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.admin_btn_approve))
            }
            "active" -> OutlinedButton(onClick = onRemoveFromTeam, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.admin_btn_remove_team))
            }
            "removed" -> OutlinedButton(onClick = onRestorePending, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.admin_btn_mark_pending))
            }
        }
        Text(stringResource(R.string.admin_role_change), style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            com.lastasylum.alliance.data.auth.AccountRoles.ALL.forEach { r ->
                OutlinedButton(onClick = { onSetRole(r) }) {
                    Text(accountRoleLabel(r))
                }
            }
        }
        if (player.userId != currentUserId) {
            TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.admin_delete_account),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
