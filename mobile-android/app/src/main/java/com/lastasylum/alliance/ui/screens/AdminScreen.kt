package com.lastasylum.alliance.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.admin.AllianceAdminDto
import com.lastasylum.alliance.data.chat.ChatRoomDto
import com.lastasylum.alliance.data.users.TeamMemberDto
import com.lastasylum.alliance.ui.admin.AdminUiState
import com.lastasylum.alliance.ui.theme.SquadRelayDimens

private fun copyTeamId(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("team_id", text))
}

@Composable
fun AdminScreen(
    currentUserId: String,
    state: AdminUiState,
    onRefresh: () -> Unit,
    onRefreshAlliances: () -> Unit,
    onClearAlliancesError: () -> Unit,
    onSetFilterAlliance: (String?) -> Unit,
    onMemberSearchChange: (String) -> Unit,
    onAllianceOverlayChange: (String, Boolean) -> Unit,
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
    val context = LocalContext.current
    val chipScroll = rememberScrollState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        contentPadding = PaddingValues(
            horizontal = SquadRelayDimens.contentPaddingHorizontal,
            vertical = SquadRelayDimens.screenTopPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(SquadRelayDimens.sectionGap),
    ) {
        item {
            Text(
                text = stringResource(R.string.admin_screen_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        item {
            adminSectionCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.admin_section_teams),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    OutlinedButton(
                        onClick = onRefreshAlliances,
                        enabled = !state.alliancesLoading,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.semantics {
                            contentDescription = context.getString(R.string.admin_cd_refresh_teams)
                        },
                    ) {
                        Text(stringResource(R.string.admin_refresh))
                    }
                }
                if (state.alliancesLoading && state.alliances.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                state.alliancesError?.takeIf { it.isNotBlank() }?.let { err ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = err,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        TextButton(onClick = onClearAlliancesError) {
                            Text(stringResource(R.string.admin_dismiss_error))
                        }
                    }
                }
                if (!state.alliancesLoading && state.alliances.isEmpty() && state.alliancesError.isNullOrBlank()) {
                    Text(
                        text = stringResource(R.string.admin_members_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    state.alliances.forEachIndexed { index, row ->
                        if (index > 0) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 4.dp),
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                        }
                        AdminAllianceRow(
                            row = row,
                            onCopyId = { copyTeamId(context, row.publicId) },
                            onOverlayChange = { enabled ->
                                onAllianceOverlayChange(row.publicId, enabled)
                            },
                        )
                    }
                }
            }
        }

        item {
            adminSectionCard {
                Text(
                    text = stringResource(R.string.admin_section_members),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(chipScroll),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilterChip(
                        selected = state.filterAllianceCode == null,
                        onClick = { onSetFilterAlliance(null) },
                        label = { Text(stringResource(R.string.admin_filter_all_teams)) },
                    )
                    state.alliances.forEach { a ->
                        FilterChip(
                            selected = state.filterAllianceCode == a.allianceCode,
                            onClick = {
                                onSetFilterAlliance(
                                    if (state.filterAllianceCode == a.allianceCode) null else a.allianceCode,
                                )
                            },
                            label = {
                                Text(
                                    text = a.allianceCode,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                        )
                    }
                }
                OutlinedTextField(
                    value = state.memberSearchQuery,
                    onValueChange = onMemberSearchChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    label = { Text(stringResource(R.string.admin_members_search_hint)) },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.admin_members_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(
                        onClick = onRefresh,
                        enabled = !state.isLoading,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.semantics {
                            contentDescription = context.getString(R.string.admin_cd_refresh_members)
                        },
                    ) {
                        Text(stringResource(R.string.admin_refresh))
                    }
                    if (state.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .size(22.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }

        item {
            adminSectionCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.admin_rooms_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    OutlinedButton(
                        onClick = onRefreshRooms,
                        enabled = !state.roomsLoading,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.semantics {
                            contentDescription = context.getString(R.string.admin_cd_refresh_rooms)
                        },
                    ) {
                        Text(stringResource(R.string.admin_refresh))
                    }
                }
                Text(
                    text = stringResource(R.string.admin_rooms_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        shape = MaterialTheme.shapes.medium,
                        label = { Text(stringResource(R.string.admin_rooms_new_hint)) },
                    )
                    Button(
                        onClick = {
                            onCreateRoom(newRoomTitle)
                            newRoomTitle = ""
                        },
                        enabled = newRoomTitle.isNotBlank(),
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Text(stringResource(R.string.admin_rooms_create))
                    }
                }
                if (state.roomsLoading && state.rooms.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                state.roomError?.takeIf { it.isNotBlank() }?.let { roomErrorText ->
                    Text(
                        text = roomErrorText,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                state.roomSnack?.takeIf { it.isNotBlank() }?.let { roomSnackText ->
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = roomSnackText,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onClearRoomSnack) {
                            Text(stringResource(R.string.admin_dismiss_error))
                        }
                    }
                }
                if (!state.roomsLoading && state.rooms.isEmpty()) {
                    Text(
                        text = stringResource(R.string.admin_rooms_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    state.rooms.forEach { room ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
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
                                TextButton(
                                    onClick = {
                                        renameRoomTarget = room
                                        renameDraft = room.title
                                    },
                                    modifier = Modifier.semantics {
                                        contentDescription = context.getString(
                                            R.string.admin_cd_rename_room,
                                            room.title,
                                        )
                                    },
                                ) {
                                    Text(stringResource(R.string.admin_rooms_rename))
                                }
                                TextButton(
                                    onClick = { deleteRoomTarget = room },
                                    modifier = Modifier.semantics {
                                        contentDescription = context.getString(
                                            R.string.admin_cd_delete_room,
                                            room.title,
                                        )
                                    },
                                ) {
                                    Text(
                                        stringResource(R.string.admin_rooms_delete),
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            when {
                state.isLoading && state.members.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                !state.isLoading && state.members.isEmpty() && state.error.isNullOrBlank() -> {
                    Text(
                        text = stringResource(R.string.admin_members_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                    )
                }
            }
        }
        item {
            val membersErrorText = state.error
            if (!membersErrorText.isNullOrBlank()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = membersErrorText,
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
            val snackMessageText = state.snackMessage
            if (!snackMessageText.isNullOrBlank()) {
                Text(
                    text = snackMessageText,
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
                    shape = MaterialTheme.shapes.medium,
                    label = { Text(stringResource(R.string.admin_room_new_title)) },
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

@Composable
private fun adminSectionCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(SquadRelayDimens.cardInnerPadding),
            verticalArrangement = Arrangement.spacedBy(SquadRelayDimens.itemGap),
            content = content,
        )
    }
}

@Composable
private fun AdminAllianceRow(
    row: AllianceAdminDto,
    onCopyId: () -> Unit,
    onOverlayChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.admin_team_internal_code),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = row.allianceCode,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.admin_team_public_id),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
            Text(
                text = row.publicId,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.admin_team_members_count, row.memberCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
            TextButton(onClick = onCopyId, modifier = Modifier.padding(top = 2.dp)) {
                Text(stringResource(R.string.admin_copy_team_id))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.admin_overlay_switch),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Switch(
                checked = row.overlayEnabled,
                onCheckedChange = onOverlayChange,
            )
        }
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
    val context = LocalContext.current

    Surface(
        shape = MaterialTheme.shapes.large,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(SquadRelayDimens.cardInnerPadding),
            verticalArrangement = Arrangement.spacedBy(SquadRelayDimens.itemGap),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(SquadRelayDimens.itemGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = member.username,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = member.role,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = member.email,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
            val teamId = member.alliancePublicId?.takeIf { it.isNotBlank() }
            if (teamId != null) {
                Text(
                    text = stringResource(R.string.admin_team_public_id) + ": " + teamId,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = stringResource(R.string.admin_field_status, statusLabel),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                if (member.membershipStatus == "pending") {
                    Button(
                        onClick = onApprove,
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Text(stringResource(R.string.admin_btn_approve))
                    }
                }
                if (member.membershipStatus == "active") {
                    OutlinedButton(
                        onClick = onRemoveFromTeam,
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Text(stringResource(R.string.admin_btn_remove_team))
                    }
                }
                if (member.membershipStatus == "removed") {
                    OutlinedButton(
                        onClick = onRestorePending,
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Text(stringResource(R.string.admin_btn_mark_pending))
                    }
                }
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf("R2", "R3", "R4", "R5").forEach { r ->
                    OutlinedButton(
                        onClick = { onSetRole(r) },
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.semantics {
                            contentDescription = context.getString(R.string.admin_cd_set_role, r)
                        },
                    ) {
                        Text(r)
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = draftName,
                    onValueChange = { draftName = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    label = { Text(stringResource(R.string.admin_new_nickname)) },
                )
                Button(
                    onClick = { onRename(draftName) },
                    enabled = draftName.length >= 3 && draftName != member.username,
                    shape = MaterialTheme.shapes.large,
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
