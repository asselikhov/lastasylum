package com.lastasylum.alliance.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.chat.ChatRoomDto
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
    onRefreshRooms: () -> Unit,
    onCreateRoom: (String) -> Unit,
    onRenameRoom: (String, String) -> Unit,
    onDeleteRoom: (String) -> Unit,
    onClearRoomSnack: () -> Unit,
) {
    var deleteTarget by remember { mutableStateOf<TeamMemberDto?>(null) }
    var deleteRoomTarget by remember { mutableStateOf<ChatRoomDto?>(null) }
    var renameRoomTarget by remember { mutableStateOf<ChatRoomDto?>(null) }
    var renameDraft by remember { mutableStateOf("") }
    var newRoomTitle by remember { mutableStateOf("") }

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
            Text(
                text = stringResource(R.string.admin_rooms_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.admin_rooms_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = newRoomTitle,
                    onValueChange = { newRoomTitle = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text(stringResource(R.string.admin_rooms_new_hint)) },
                )
                Button(
                    onClick = {
                        onCreateRoom(newRoomTitle)
                        newRoomTitle = ""
                    },
                    enabled = newRoomTitle.isNotBlank(),
                ) {
                    Text(stringResource(R.string.admin_rooms_create))
                }
            }
            OutlinedButton(
                onClick = onRefreshRooms,
                enabled = !state.roomsLoading,
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text(stringResource(R.string.admin_refresh))
            }
            if (state.roomsLoading && state.rooms.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.padding(top = 8.dp))
            }
            if (!state.roomError.isNullOrBlank()) {
                Text(
                    text = state.roomError!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (!state.roomSnack.isNullOrBlank()) {
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = state.roomSnack!!,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onClearRoomSnack) {
                        Text(stringResource(R.string.admin_dismiss_error))
                    }
                }
            }
        }
        items(state.rooms, key = { it.id }) { room ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = room.title,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = {
                        renameRoomTarget = room
                        renameDraft = room.title
                    }) {
                        Text(stringResource(R.string.admin_rooms_rename))
                    }
                    TextButton(onClick = { deleteRoomTarget = room }) {
                        Text(
                            stringResource(R.string.admin_rooms_delete),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
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
                    Text(
                        text = state.error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
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
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
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

    renameRoomTarget?.let { room ->
        AlertDialog(
            onDismissRequest = { renameRoomTarget = null },
            title = { Text(stringResource(R.string.admin_rooms_rename)) },
            text = {
                OutlinedTextField(
                    value = renameDraft,
                    onValueChange = { renameDraft = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.admin_new_nickname)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRenameRoom(room.id, renameDraft)
                        renameRoomTarget = null
                    },
                    enabled = renameDraft.isNotBlank(),
                ) {
                    Text(stringResource(R.string.admin_save_nickname))
                }
            },
            dismissButton = {
                TextButton(onClick = { renameRoomTarget = null }) {
                    Text(stringResource(R.string.admin_delete_cancel))
                }
            },
        )
    }

    deleteRoomTarget?.let { room ->
        AlertDialog(
            onDismissRequest = { deleteRoomTarget = null },
            title = { Text(stringResource(R.string.admin_rooms_delete_title)) },
            text = {
                Text(stringResource(R.string.admin_rooms_delete_body, room.title))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteRoom(room.id)
                        deleteRoomTarget = null
                    },
                ) {
                    Text(
                        stringResource(R.string.admin_delete_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteRoomTarget = null }) {
                    Text(stringResource(R.string.admin_delete_cancel))
                }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
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
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = member.email,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
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
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf("R2", "R3", "R4", "R5").forEach { r ->
                    OutlinedButton(onClick = { onSetRole(r) }) {
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
