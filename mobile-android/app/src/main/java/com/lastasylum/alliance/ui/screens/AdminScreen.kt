package com.lastasylum.alliance.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.admin.AdminTeamMemberDto
import com.lastasylum.alliance.data.admin.AdminUserOnServerDto
import com.lastasylum.alliance.data.admin.AllianceAdminDto
import com.lastasylum.alliance.data.admin.PlayerTeamAdminDto
import com.lastasylum.alliance.ui.util.formatServerLabel
import com.lastasylum.alliance.ui.util.teamTagWithServerPrefix
import com.lastasylum.alliance.data.chat.ChatRoomDto
import com.lastasylum.alliance.data.users.TeamMemberDto
import com.lastasylum.alliance.ui.admin.AdminRoute
import com.lastasylum.alliance.ui.admin.AdminUiState
import com.lastasylum.alliance.ui.admin.toTeamMemberDto
import com.lastasylum.alliance.ui.util.formatPresenceTimestampRu
import com.lastasylum.alliance.ui.theme.SquadRelayDimens
import com.lastasylum.alliance.ui.theme.SquadRelaySurfaces

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AdminScreen(
    currentUserId: String,
    state: AdminUiState,
    onNavigateBack: () -> Unit,
    onOpenRoute: (AdminRoute) -> Unit,
    onOpenPlayerTeam: (PlayerTeamAdminDto) -> Unit,
    onTeamSearchChange: (String) -> Unit,
    onUserSearchChange: (String) -> Unit,
    onRefreshOverview: () -> Unit,
    onRefreshPlayerTeams: () -> Unit,
    onRefreshTeamMembers: (String) -> Unit,
    onRefreshUsersWithoutTeam: () -> Unit,
    onRefreshAlliances: () -> Unit,
    onAllianceOverlayChange: (String, Boolean) -> Unit,
    onOpenStickerSettings: (String) -> Unit,
    onCloseStickerSettings: () -> Unit,
    onToggleStickerAllianceRole: (String, Boolean) -> Unit,
    onSaveStickerAccess: () -> Unit,
    onClearStickerAccessError: () -> Unit,
    onRefreshRooms: () -> Unit,
    onCreateRoom: (String) -> Unit,
    onRenameRoom: (String, String) -> Unit,
    onDeleteRoom: (String) -> Unit,
    onApprove: (String) -> Unit,
    onRemoveFromTeam: (String) -> Unit,
    onRestorePending: (String) -> Unit,
    onSetRole: (String, String) -> Unit,
    onRename: (String, String) -> Unit,
    onDeleteUser: (String) -> Unit,
    onClearActionError: () -> Unit,
    onClearRoomError: () -> Unit,
    onDismissSnack: () -> Unit,
    onGameServerFilter: (Int?) -> Unit = {},
    onGameServerSearchChange: (String) -> Unit = {},
    onRefreshGameServers: () -> Unit = {},
    onUpdateGameIdentity: (userId: String, identityId: String, gameNickname: String) -> Unit = { _, _, _ -> },
) {
    val context = LocalContext.current
    var selectedMember by remember { mutableStateOf<TeamMemberDto?>(null) }
    var removeFromTeamTarget by remember { mutableStateOf<TeamMemberDto?>(null) }
    var deleteUserTarget by remember { mutableStateOf<TeamMemberDto?>(null) }
    var deleteRoomTarget by remember { mutableStateOf<ChatRoomDto?>(null) }
    var renameRoomTarget by remember { mutableStateOf<ChatRoomDto?>(null) }
    var renameDraft by remember { mutableStateOf("") }
    var newRoomTitle by remember { mutableStateOf("") }

    val title = when (val route = state.route) {
        AdminRoute.Hub -> stringResource(R.string.admin_screen_title)
        AdminRoute.PlayerTeams -> stringResource(R.string.admin_hub_teams)
        is AdminRoute.PlayerTeamDetail -> route.title
        AdminRoute.UsersWithoutTeam -> stringResource(R.string.admin_hub_users_without_team)
        AdminRoute.ChatRouting -> stringResource(R.string.admin_hub_chat_routing)
        AdminRoute.ChatRooms -> stringResource(R.string.admin_rooms_title)
        AdminRoute.GameServers -> stringResource(R.string.admin_hub_game_servers)
    }
    val showBack = state.route != AdminRoute.Hub

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        AdminTopBar(
            title = title,
            showBack = showBack,
            onBack = onNavigateBack,
            onRefresh = when (val route = state.route) {
                AdminRoute.Hub -> onRefreshOverview
                AdminRoute.PlayerTeams -> onRefreshPlayerTeams
                is AdminRoute.PlayerTeamDetail -> {
                    { onRefreshTeamMembers(route.teamId) }
                }
                AdminRoute.UsersWithoutTeam -> onRefreshUsersWithoutTeam
                AdminRoute.ChatRouting -> onRefreshAlliances
                AdminRoute.ChatRooms -> onRefreshRooms
                AdminRoute.GameServers -> onRefreshGameServers
            },
            refreshing = state.overviewLoading || state.playerTeamsLoading ||
                state.teamMembersLoading || state.usersWithoutTeamLoading ||
                state.alliancesLoading || state.roomsLoading ||
                state.gameServersLoading || state.usersOnServersLoading,
        )

        state.snackMessage?.let { msg ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SquadRelayDimens.contentPaddingHorizontal, vertical = 4.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.medium,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(msg, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismissSnack) {
                        Text(stringResource(R.string.admin_dismiss_error))
                    }
                }
            }
        }

        state.actionError?.let { err ->
            AdminErrorBanner(err, onDismiss = onClearActionError)
        }

        when (state.route) {
            AdminRoute.Hub -> AdminHubContent(
                state = state,
                onOpenRoute = onOpenRoute,
            )
            AdminRoute.PlayerTeams -> AdminPlayerTeamsContent(
                state = state,
                onSearchChange = onTeamSearchChange,
                onTeamClick = onOpenPlayerTeam,
            )
            is AdminRoute.PlayerTeamDetail -> AdminTeamMembersContent(
                state = state,
                onMemberClick = { m ->
                    selectedMember = m.toTeamMemberDto(state.selectedTeam)
                },
            )
            AdminRoute.UsersWithoutTeam -> AdminUsersWithoutTeamContent(
                state = state,
                onSearchChange = onUserSearchChange,
                onUserClick = { selectedMember = it },
            )
            AdminRoute.ChatRouting -> AdminChatRoutingContent(
                state = state,
                onOverlayChange = onAllianceOverlayChange,
                onStickerClick = onOpenStickerSettings,
                context = context,
            )
            AdminRoute.GameServers -> AdminGameServersContent(
                state = state,
                onFilter = onGameServerFilter,
                onSearchChange = onGameServerSearchChange,
                onUpdateGameIdentity = onUpdateGameIdentity,
            )
            AdminRoute.ChatRooms -> AdminChatRoomsContent(
                state = state,
                newRoomTitle = newRoomTitle,
                onNewRoomTitleChange = { newRoomTitle = it },
                onCreateRoom = {
                    onCreateRoom(newRoomTitle)
                    newRoomTitle = ""
                },
                onRenameRoom = { room ->
                    renameRoomTarget = room
                    renameDraft = room.title
                },
                onDeleteRoom = { deleteRoomTarget = it },
                onClearRoomError = onClearRoomError,
            )
        }
    }

    selectedMember?.let { member ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { selectedMember = null },
            sheetState = sheetState,
        ) {
            AdminUserActionsSheet(
                member = member,
                currentUserId = currentUserId,
                onDismiss = { selectedMember = null },
                onApprove = { onApprove(member.id); selectedMember = null },
                onRemoveFromTeam = {
                    removeFromTeamTarget = member
                    selectedMember = null
                },
                onRestorePending = { onRestorePending(member.id); selectedMember = null },
                onSetRole = { role -> onSetRole(member.id, role) },
                onRename = { name -> onRename(member.id, name) },
                onDelete = { deleteUserTarget = member; selectedMember = null },
            )
        }
    }

    if (state.stickerAllianceCode != null) {
        AlertDialog(
            onDismissRequest = onCloseStickerSettings,
            containerColor = SquadRelaySurfaces.dialogColor(),
            title = {
                Text(
                    stringResource(R.string.admin_sticker_section_title) + " · " + state.stickerAllianceCode,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.admin_sticker_roles_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state.stickerAccessLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                    state.stickerAccessError?.let { err ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                err,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = onClearStickerAccessError) {
                                Text(stringResource(R.string.admin_dismiss_error))
                            }
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("R2", "R3", "R4", "R5").forEach { role ->
                            FilterChip(
                                selected = state.stickerRolesZlobyaka.contains(role),
                                onClick = {
                                    onToggleStickerAllianceRole(role, !state.stickerRolesZlobyaka.contains(role))
                                },
                                label = { Text(role) },
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = onSaveStickerAccess, enabled = !state.stickerAccessLoading) {
                    Text(stringResource(R.string.admin_sticker_save))
                }
            },
            dismissButton = {
                TextButton(onClick = onCloseStickerSettings) {
                    Text(stringResource(R.string.admin_delete_cancel))
                }
            },
        )
    }

    removeFromTeamTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { removeFromTeamTarget = null },
            containerColor = SquadRelaySurfaces.dialogColor(),
            title = { Text(stringResource(R.string.admin_remove_team_confirm_title)) },
            text = { Text(stringResource(R.string.admin_remove_team_confirm_body, target.username)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRemoveFromTeam(target.id)
                        removeFromTeamTarget = null
                    },
                ) {
                    Text(
                        stringResource(R.string.admin_btn_remove_team),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { removeFromTeamTarget = null }) {
                    Text(stringResource(R.string.admin_delete_cancel))
                }
            },
        )
    }

    deleteUserTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteUserTarget = null },
            containerColor = SquadRelaySurfaces.dialogColor(),
            title = { Text(stringResource(R.string.admin_delete_title)) },
            text = { Text(stringResource(R.string.admin_delete_body, target.username)) },
            confirmButton = {
                TextButton(onClick = { onDeleteUser(target.id); deleteUserTarget = null }) {
                    Text(stringResource(R.string.admin_delete_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteUserTarget = null }) {
                    Text(stringResource(R.string.admin_delete_cancel))
                }
            },
        )
    }

    renameRoomTarget?.let { room ->
        AlertDialog(
            onDismissRequest = { renameRoomTarget = null },
            containerColor = SquadRelaySurfaces.dialogColor(),
            title = { Text(stringResource(R.string.admin_rooms_rename)) },
            text = {
                OutlinedTextField(
                    value = renameDraft,
                    onValueChange = { renameDraft = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.admin_room_new_title)) },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRenameRoom(room.id, renameDraft)
                        renameRoomTarget = null
                    },
                    enabled = renameDraft.isNotBlank(),
                ) { Text(stringResource(R.string.admin_save_nickname)) }
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
            containerColor = SquadRelaySurfaces.dialogColor(),
            title = { Text(stringResource(R.string.admin_rooms_delete_title)) },
            text = { Text(stringResource(R.string.admin_rooms_delete_body, room.title)) },
            confirmButton = {
                TextButton(onClick = { onDeleteRoom(room.id); deleteRoomTarget = null }) {
                    Text(stringResource(R.string.admin_delete_confirm), color = MaterialTheme.colorScheme.error)
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
private fun AdminTopBar(
    title: String,
    showBack: Boolean,
    onBack: () -> Unit,
    onRefresh: (() -> Unit)?,
    refreshing: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showBack) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
            }
        } else {
            Box(Modifier.size(48.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (onRefresh != null) {
            if (refreshing) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .size(22.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                TextButton(onClick = onRefresh, modifier = Modifier.padding(end = 4.dp)) {
                    Text(stringResource(R.string.admin_refresh))
                }
            }
        }
    }
}

@Composable
private fun AdminHubContent(
    state: AdminUiState,
    onOpenRoute: (AdminRoute) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(
            horizontal = SquadRelayDimens.contentPaddingHorizontal,
            vertical = SquadRelayDimens.itemGap,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                stringResource(R.string.admin_hub_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.overviewError?.let { AdminErrorBanner(it) {} }
        }
        item {
            AdminHubTile(
                icon = { Icon(Icons.Outlined.Groups, null, tint = MaterialTheme.colorScheme.primary) },
                title = stringResource(R.string.admin_hub_teams),
                subtitle = stringResource(R.string.admin_hub_teams_sub, state.playerTeamCount),
                onClick = { onOpenRoute(AdminRoute.PlayerTeams) },
            )
        }
        item {
            AdminHubTile(
                icon = { Icon(Icons.Outlined.PersonOff, null, tint = MaterialTheme.colorScheme.primary) },
                title = stringResource(R.string.admin_hub_users_without_team),
                subtitle = stringResource(R.string.admin_hub_users_without_team_sub, state.usersWithoutTeamCount),
                onClick = { onOpenRoute(AdminRoute.UsersWithoutTeam) },
            )
        }
        item {
            AdminHubTile(
                icon = { Icon(Icons.Outlined.Route, null, tint = MaterialTheme.colorScheme.primary) },
                title = stringResource(R.string.admin_hub_chat_routing),
                subtitle = stringResource(R.string.admin_hub_chat_routing_sub),
                onClick = { onOpenRoute(AdminRoute.ChatRouting) },
            )
        }
        item {
            AdminHubTile(
                icon = { Icon(Icons.AutoMirrored.Outlined.Chat, null, tint = MaterialTheme.colorScheme.primary) },
                title = stringResource(R.string.admin_rooms_title),
                subtitle = stringResource(R.string.admin_hub_rooms_sub),
                onClick = { onOpenRoute(AdminRoute.ChatRooms) },
            )
        }
        item {
            AdminHubTile(
                icon = { Icon(Icons.Outlined.Groups, null, tint = MaterialTheme.colorScheme.primary) },
                title = stringResource(R.string.admin_hub_game_servers),
                subtitle = stringResource(R.string.admin_hub_game_servers_sub),
                onClick = { onOpenRoute(AdminRoute.GameServers) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdminGameServersContent(
    state: AdminUiState,
    onFilter: (Int?) -> Unit,
    onSearchChange: (String) -> Unit,
    onUpdateGameIdentity: (userId: String, identityId: String, gameNickname: String) -> Unit,
) {
    val scroll = rememberScrollState()
    var editTarget by remember { mutableStateOf<AdminUserOnServerDto?>(null) }
    var editDraft by remember { mutableStateOf("") }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = SquadRelayDimens.contentPaddingHorizontal, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        state.gameServersError?.let { AdminErrorBanner(it) {} }
        state.usersOnServersError?.let { AdminErrorBanner(it) {} }
        OutlinedTextField(
            value = state.gameServerSearchQuery,
            onValueChange = onSearchChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(R.string.admin_game_servers_search)) },
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.gameServerFilter == null,
                onClick = { onFilter(null) },
                label = { Text(stringResource(R.string.admin_game_servers_filter_all)) },
            )
            state.gameServers.forEach { s ->
                FilterChip(
                    selected = state.gameServerFilter == s.serverNumber,
                    onClick = { onFilter(s.serverNumber) },
                    label = {
                        Text(
                            stringResource(
                                R.string.admin_game_servers_filter,
                                s.serverNumber,
                                s.userCount,
                            ),
                        )
                    },
                )
            }
        }
        if (state.usersOnServersLoading && state.usersOnServers.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(strokeWidth = 2.dp)
            }
        } else if (state.usersOnServers.isEmpty()) {
            Text(
                stringResource(R.string.admin_game_servers_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            state.usersOnServers.forEach { row ->
                Surface(
                    onClick = {
                        editTarget = row
                        editDraft = row.gameNickname
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = SquadRelaySurfaces.panelColor(0.42f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            stringResource(
                                R.string.admin_game_servers_row,
                                com.lastasylum.alliance.ui.util.formatServerLabel(row.serverNumber)
                                    ?: "#${row.serverNumber}",
                                row.gameNickname,
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            row.email,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            stringResource(R.string.admin_game_servers_account, row.accountUsername),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        val team = row.playerTeamTag?.let { tag ->
                            val prefix = teamTagWithServerPrefix(tag, row.serverNumber)
                            "$prefix ${row.playerTeamDisplayName.orEmpty()}".trim()
                        } ?: row.playerTeamDisplayName
                        if (!team.isNullOrBlank()) {
                            Text(
                                stringResource(R.string.admin_game_servers_team, team),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        if (row.isActiveIdentity) {
                            Text(
                                stringResource(R.string.admin_game_servers_active),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }

    editTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { editTarget = null },
            containerColor = SquadRelaySurfaces.dialogColor(),
            title = { Text(stringResource(R.string.admin_game_servers_edit_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        formatServerLabel(target.serverNumber) ?: "#${target.serverNumber}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    OutlinedTextField(
                        value = editDraft,
                        onValueChange = { editDraft = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.admin_game_servers_edit_hint)) },
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onUpdateGameIdentity(target.userId, target.identityId, editDraft)
                        editTarget = null
                    },
                    enabled = editDraft.trim().length >= 2,
                ) { Text(stringResource(R.string.admin_save_nickname)) }
            },
            dismissButton = {
                TextButton(onClick = { editTarget = null }) {
                    Text(stringResource(R.string.admin_delete_cancel))
                }
            },
        )
    }
}

@Composable
private fun AdminHubTile(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = SquadRelaySurfaces.panelColor(0.48f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            icon()
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AdminPlayerTeamsContent(
    state: AdminUiState,
    onSearchChange: (String) -> Unit,
    onTeamClick: (PlayerTeamAdminDto) -> Unit,
) {
    val q = state.teamSearchQuery.trim().lowercase()
    val filtered = remember(state.playerTeams, q) {
        if (q.isEmpty()) state.playerTeams
        else state.playerTeams.filter {
            it.displayName.lowercase().contains(q) ||
                it.tag.lowercase().contains(q) ||
                it.leaderUsername.lowercase().contains(q) ||
                it.chatRoutingSummary.lowercase().contains(q)
        }
    }
    LazyColumn(
        contentPadding = PaddingValues(
            horizontal = SquadRelayDimens.contentPaddingHorizontal,
            vertical = 8.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            OutlinedTextField(
                value = state.teamSearchQuery,
                onValueChange = onSearchChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.admin_teams_search_hint)) },
            )
            state.playerTeamsError?.let { AdminErrorBanner(it) {} }
        }
        if (filtered.isEmpty() && !state.playerTeamsLoading) {
            item {
                Text(
                    stringResource(R.string.admin_teams_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
        }
        items(filtered, key = { it.id }) { team ->
            AdminTeamListRow(team = team, onClick = { onTeamClick(team) })
        }
    }
}

@Composable
private fun AdminTeamListRow(
    team: PlayerTeamAdminDto,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = SquadRelaySurfaces.panelColor(0.4f),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "${teamTagWithServerPrefix(team.tag.uppercase(), team.leaderServerNumber)} ${team.displayName}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    stringResource(R.string.admin_team_members_count, team.memberCount),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                stringResource(R.string.admin_team_leader, team.leaderUsername),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.admin_team_routing, team.chatRoutingSummary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun AdminTeamMembersContent(
    state: AdminUiState,
    onMemberClick: (AdminTeamMemberDto) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(
            horizontal = SquadRelayDimens.contentPaddingHorizontal,
            vertical = 8.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            state.teamMembersError?.let { AdminErrorBanner(it) {} }
            state.selectedTeam?.let { team ->
                Text(
                    stringResource(R.string.admin_team_routing, team.chatRoutingSummary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (state.teamMembers.isEmpty() && !state.teamMembersLoading) {
            item {
                Text(
                    stringResource(R.string.admin_members_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
        }
        items(state.teamMembers, key = { it.userId }) { member ->
            AdminMemberListRow(member = member, onClick = { onMemberClick(member) })
        }
    }
}

@Composable
private fun AdminUsersWithoutTeamContent(
    state: AdminUiState,
    onSearchChange: (String) -> Unit,
    onUserClick: (TeamMemberDto) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(
            horizontal = SquadRelayDimens.contentPaddingHorizontal,
            vertical = 8.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            OutlinedTextField(
                value = state.userSearchQuery,
                onValueChange = onSearchChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.admin_members_search_hint)) },
            )
            state.usersWithoutTeamError?.let { AdminErrorBanner(it) {} }
        }
        if (state.usersWithoutTeam.isEmpty() && !state.usersWithoutTeamLoading) {
            item {
                Text(
                    stringResource(R.string.admin_users_without_team_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
        }
        items(state.usersWithoutTeam, key = { it.id }) { user ->
            AdminMemberListRow(
                username = user.username,
                subtitle = "${user.role} · ${user.allianceName} · ${user.email}",
                onClick = { onUserClick(user) },
            )
        }
    }
}

@Composable
private fun AdminMemberListRow(
    member: AdminTeamMemberDto,
    onClick: () -> Unit,
) {
    val serverLabel = formatServerLabel(member.serverNumber)
    val nickLine = if (member.gameNickname.isNotBlank() && serverLabel != null) {
        "$serverLabel · ${member.gameNickname}"
    } else {
        member.gameNickname.ifBlank { member.username }
    }
    AdminMemberListRow(
        username = nickLine + if (member.isLeader) " ★" else "",
        subtitle = buildString {
            append(member.teamRole)
            append(" · ")
            append(member.allianceRole)
            append(" · ")
            append(member.allianceName)
            if (member.accountUsername.isNotBlank() && member.accountUsername != member.gameNickname) {
                append(" · ")
                append(member.accountUsername)
            }
        },
        onClick = onClick,
    )
}

@Composable
private fun AdminMemberListRow(
    username: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = SquadRelaySurfaces.panelColor(0.38f),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(username, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AdminChatRoutingContent(
    state: AdminUiState,
    onOverlayChange: (String, Boolean) -> Unit,
    onStickerClick: (String) -> Unit,
    context: Context,
) {
    LazyColumn(
        contentPadding = PaddingValues(
            horizontal = SquadRelayDimens.contentPaddingHorizontal,
            vertical = 8.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                stringResource(R.string.admin_chat_routing_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.alliancesError?.let { AdminErrorBanner(it) {} }
        }
        items(state.alliances, key = { it.publicId }) { row ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = SquadRelaySurfaces.panelColor(0.4f),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(row.allianceCode, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        row.publicId,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(R.string.admin_team_members_count, row.memberCount),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.admin_overlay_switch), style = MaterialTheme.typography.bodySmall)
                        Switch(checked = row.overlayEnabled, onCheckedChange = { onOverlayChange(row.publicId, it) })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("id", row.publicId))
                        }) { Text(stringResource(R.string.admin_copy_team_id)) }
                        TextButton(onClick = { onStickerClick(row.allianceCode) }) {
                            Text(stringResource(R.string.admin_sticker_short))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminChatRoomsContent(
    state: AdminUiState,
    newRoomTitle: String,
    onNewRoomTitleChange: (String) -> Unit,
    onCreateRoom: () -> Unit,
    onRenameRoom: (ChatRoomDto) -> Unit,
    onDeleteRoom: (ChatRoomDto) -> Unit,
    onClearRoomError: () -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(
            horizontal = SquadRelayDimens.contentPaddingHorizontal,
            vertical = 8.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(stringResource(R.string.admin_rooms_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = newRoomTitle,
                    onValueChange = onNewRoomTitleChange,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text(stringResource(R.string.admin_rooms_new_hint)) },
                )
                Button(onClick = onCreateRoom, enabled = newRoomTitle.isNotBlank()) {
                    Text(stringResource(R.string.admin_rooms_create))
                }
            }
            state.roomError?.let { AdminErrorBanner(it, onDismiss = onClearRoomError) }
        }
        items(state.rooms, key = { it.id }) { room ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(room.title, style = MaterialTheme.typography.bodyMedium)
                    Text(room.allianceId ?: "—", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { onRenameRoom(room) }) { Text(stringResource(R.string.admin_rooms_rename)) }
                TextButton(onClick = { onDeleteRoom(room) }) {
                    Text(stringResource(R.string.admin_rooms_delete), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdminUserActionsSheet(
    member: TeamMemberDto,
    currentUserId: String,
    onDismiss: () -> Unit,
    onApprove: () -> Unit,
    onRemoveFromTeam: () -> Unit,
    onRestorePending: () -> Unit,
    onSetRole: (String) -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
) {
    var draftName by remember(member.id) { mutableStateOf(member.username) }
    LaunchedEffect(member.username) { draftName = member.username }
    val statusLabel = when (member.membershipStatus) {
        "pending" -> stringResource(R.string.admin_status_pending)
        "removed" -> stringResource(R.string.admin_status_removed)
        else -> stringResource(R.string.admin_status_active)
    }
    val unknownTime = stringResource(R.string.admin_presence_time_unknown)
    val lastAppLine = stringResource(
        R.string.admin_field_last_app_active,
        formatPresenceTimestampRu(member.lastAppActiveAt).ifBlank { unknownTime },
    )
    val lastOverlayLine = stringResource(
        R.string.admin_field_last_overlay_ingame,
        formatPresenceTimestampRu(member.lastPresenceAt).ifBlank { unknownTime },
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(member.username, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(member.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.admin_field_status, statusLabel), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.admin_field_role, member.role), style = MaterialTheme.typography.bodySmall)
        Text(
            lastAppLine,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            lastOverlayLine,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        member.teamTag?.let {
            Text("[$it] ${member.teamDisplayName.orEmpty()}", style = MaterialTheme.typography.bodySmall)
        }
        HorizontalDivider()
        when (member.membershipStatus) {
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
            listOf("R2", "R3", "R4", "R5").forEach { r ->
                OutlinedButton(onClick = { onSetRole(r) }) { Text(r) }
            }
        }
        OutlinedTextField(
            value = draftName,
            onValueChange = { draftName = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(R.string.admin_new_nickname)) },
        )
        Button(
            onClick = { onRename(draftName); onDismiss() },
            enabled = draftName.length >= 3 && draftName != member.username,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.admin_save_nickname)) }
        if (member.id != currentUserId) {
            TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.admin_delete_account), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun AdminErrorBanner(message: String, onDismiss: (() -> Unit)? = null) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(message, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            onDismiss?.let { TextButton(onClick = it) { Text(stringResource(R.string.admin_dismiss_error)) } }
        }
    }
}
