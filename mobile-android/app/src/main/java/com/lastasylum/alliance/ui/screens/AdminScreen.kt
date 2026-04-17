package com.lastasylum.alliance.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.users.TeamMemberDto
import com.lastasylum.alliance.ui.admin.AdminUiState

@Composable
fun AdminScreen(
    contentPadding: PaddingValues,
    currentUserId: String,
    state: AdminUiState,
    onRefresh: () -> Unit,
    onApprove: (String) -> Unit,
    onRemoveFromTeam: (String) -> Unit,
    onRestorePending: (String) -> Unit,
    onSetRole: (String, String) -> Unit,
    onRename: (String, String) -> Unit,
    onDeleteUser: (String) -> Unit,
    onDismissError: () -> Unit,
) {
    var deleteTarget by remember { mutableStateOf<TeamMemberDto?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.admin_title),
                style = MaterialTheme.typography.headlineSmall,
            )
        }
        item {
            Text(
                text = stringResource(R.string.admin_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onRefresh, enabled = !state.isLoading) {
                    Text(stringResource(R.string.admin_refresh))
                }
            }
        }
        item {
            if (state.isLoading && state.members.isEmpty()) {
                CircularProgressIndicator()
            }
        }
        item {
            if (!state.error.isNullOrBlank()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(text = state.error!!, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onDismissError) {
                        Text(stringResource(R.string.admin_dismiss_error))
                    }
                }
            }
        }
        item {
            if (!state.snackMessage.isNullOrBlank()) {
                Text(
                    text = state.snackMessage!!,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        items(state.members, key = { it.id }) { member ->
            AdminMemberCard(
                member = member,
                currentUserId = currentUserId,
                onApprove = { onApprove(member.id) },
                onRemoveFromTeam = { onRemoveFromTeam(member.id) },
                onRestorePending = { onRestorePending(member.id) },
                onSetRole = { role -> onSetRole(member.id, role) },
                onRename = { name -> onRename(member.id, name) },
                onDeleteClick = { deleteTarget = member },
            )
        }
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.admin_delete_title)) },
            text = {
                Text(
                    stringResource(R.string.admin_delete_body, target.username),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteUser(target.id)
                        deleteTarget = null
                    },
                ) {
                    Text(stringResource(R.string.admin_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.admin_delete_cancel))
                }
            },
        )
    }
}

@Composable
private fun AdminMemberCard(
    member: TeamMemberDto,
    currentUserId: String,
    onApprove: () -> Unit,
    onRemoveFromTeam: () -> Unit,
    onRestorePending: () -> Unit,
    onSetRole: (String) -> Unit,
    onRename: (String) -> Unit,
    onDeleteClick: () -> Unit,
) {
    var draftName by remember(member.id) { mutableStateOf(member.username) }
    LaunchedEffect(member.username) {
        draftName = member.username
    }

    val statusLabel = when (member.membershipStatus) {
        "pending" -> stringResource(R.string.admin_status_pending)
        "removed" -> stringResource(R.string.admin_status_removed)
        else -> stringResource(R.string.admin_status_active)
    }
    val isSelf = member.id == currentUserId

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = member.username,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = member.email,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
            Text(
                text = stringResource(R.string.admin_field_role, member.role),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = stringResource(R.string.admin_field_status, statusLabel),
                style = MaterialTheme.typography.bodySmall,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                if (member.membershipStatus == "pending") {
                    Button(onClick = onApprove, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.admin_btn_approve))
                    }
                }
                if (member.membershipStatus == "active") {
                    OutlinedButton(onClick = onRemoveFromTeam, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.admin_btn_remove_team))
                    }
                }
                if (member.membershipStatus == "removed") {
                    OutlinedButton(onClick = onRestorePending, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.admin_btn_mark_pending))
                    }
                }
            }

            Text(
                text = stringResource(R.string.admin_role_change),
                style = MaterialTheme.typography.labelLarge,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                listOf("R2", "R3", "R4", "R5").forEach { r ->
                    OutlinedButton(
                        onClick = { onSetRole(r) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(r)
                    }
                }
            }

            Text(
                text = stringResource(R.string.admin_rename),
                style = MaterialTheme.typography.labelLarge,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = draftName,
                    onValueChange = { draftName = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text(stringResource(R.string.admin_new_nickname)) },
                )
                Button(
                    onClick = { onRename(draftName) },
                    enabled = draftName.length >= 3 && draftName != member.username,
                ) {
                    Text(stringResource(R.string.admin_save_nickname))
                }
            }

            if (!isSelf) {
                TextButton(onClick = onDeleteClick) {
                    Text(
                        stringResource(R.string.admin_delete_account),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}
