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
import com.lastasylum.alliance.ui.admin.AdminPlayersSegment
import com.lastasylum.alliance.ui.admin.AdminRoute
import com.lastasylum.alliance.ui.admin.AdminUiState
import com.lastasylum.alliance.ui.admin.toAdminPlayerRow
import com.lastasylum.alliance.ui.admin.AdminTeamDetailTab
import com.lastasylum.alliance.ui.screens.admin.AdminChatRoomViewerContent
import com.lastasylum.alliance.ui.screens.admin.AdminForumTopicViewerContent
import com.lastasylum.alliance.ui.screens.admin.AdminPlayerManageSheet
import com.lastasylum.alliance.ui.screens.admin.AdminStickerAccessSheet
import com.lastasylum.alliance.ui.screens.admin.AdminTeamBrandingDialog
import com.lastasylum.alliance.ui.screens.admin.AdminTeamDetailContent
import androidx.compose.material.icons.outlined.Edit
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
    onPlayersSearchChange: (String) -> Unit,
    onPlayersSegmentChange: (AdminPlayersSegment) -> Unit,
    onPlayersServerFilter: (Int?) -> Unit,
    onTeamsServerFilter: (Int?) -> Unit,
    onTeamDetailTabChange: (AdminTeamDetailTab) -> Unit,
    onOpenChatRoom: (String, com.lastasylum.alliance.data.chat.ChatRoomDto) -> Unit,
    onOpenForumTopic: (String, com.lastasylum.alliance.data.teams.TeamForumTopicDto) -> Unit,
    onRefreshOverview: () -> Unit,
    onRefreshPlayerTeams: () -> Unit,
    onRefreshTeamDetail: (String) -> Unit,
    onRefreshPlayers: () -> Unit,
    onRefreshAlliances: () -> Unit,
    onAllianceOverlayChange: (String, Boolean) -> Unit,
    onOpenStickerSettings: (String) -> Unit,
    onCloseStickerSettings: () -> Unit,
    onSelectStickerPack: (String) -> Unit,
    onStickerMemberSearchChange: (String) -> Unit,
    onToggleStickerAllianceRole: (String, String, Boolean) -> Unit,
    onToggleStickerUserGrant: (String, String, Boolean) -> Unit,
    onSaveStickerAccess: () -> Unit,
    onClearStickerAccessError: () -> Unit,
    onOpenPlayerStickerEditor: (String, String) -> Unit,
    onTogglePlayerStickerPack: (String, Boolean) -> Unit,
    onSavePlayerStickerAccess: () -> Unit,
    onApprove: (String) -> Unit,
    onRemoveFromTeam: (String) -> Unit,
    onRestorePending: (String) -> Unit,
    onSetRole: (String, String) -> Unit,
    onDeleteUser: (String) -> Unit,
    onClearActionError: () -> Unit,
    onDismissSnack: () -> Unit,
    onUpdateGameIdentity: (userId: String, identityId: String, gameNickname: String, serverNumber: Int) -> Unit = { _, _, _, _ -> },
    onUpdatePlayerTeam: (teamId: String, tag: String, displayName: String) -> Unit = { _, _, _ -> },
    onLoadMorePlayers: () -> Unit = {},
    onLoadMorePlayerTeams: () -> Unit = {},
) {
    val context = LocalContext.current
    var selectedPlayer by remember { mutableStateOf<AdminUserOnServerDto?>(null) }
    var selectedPlayerAllianceCode by remember { mutableStateOf<String?>(null) }
    var removeFromTeamUserId by remember { mutableStateOf<String?>(null) }
    var deleteUserId by remember { mutableStateOf<String?>(null) }
    var editTeamTarget by remember { mutableStateOf<PlayerTeamAdminDto?>(null) }
    val title = when (val route = state.route) {
        AdminRoute.Hub -> stringResource(R.string.admin_screen_title)
        AdminRoute.PlayerTeams -> stringResource(R.string.admin_hub_teams)
        is AdminRoute.PlayerTeamDetail -> route.title
        is AdminRoute.ChatRoomViewer -> route.roomTitle
        is AdminRoute.ForumTopicViewer -> route.topicTitle
        AdminRoute.Players -> stringResource(R.string.admin_hub_players)
        AdminRoute.ChatRouting -> stringResource(R.string.admin_hub_chat_routing)
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
                    { onRefreshTeamDetail(route.teamId) }
                }
                is AdminRoute.ChatRoomViewer -> null
                is AdminRoute.ForumTopicViewer -> null
                AdminRoute.Players -> onRefreshPlayers
                AdminRoute.ChatRouting -> onRefreshAlliances
            },
            refreshing = state.overviewLoading || state.playerTeamsLoading ||
                state.teamMembersLoading || state.teamChatRoomsLoading ||
                state.teamNewsLoading || state.teamForumLoading ||
                state.chatRoomMessagesLoading || state.forumTopicMessagesLoading ||
                state.alliancesLoading ||
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
                onServerFilter = onTeamsServerFilter,
                onTeamClick = onOpenPlayerTeam,
                onLoadMore = onLoadMorePlayerTeams,
            )
            is AdminRoute.PlayerTeamDetail -> AdminTeamDetailContent(
                state = state,
                onEditTeam = { editTeamTarget = state.selectedTeam },
                onTabChange = onTeamDetailTabChange,
                onMemberClick = { m ->
                    selectedPlayerAllianceCode = m.allianceName.trim().takeIf { it.isNotEmpty() }
                    m.toAdminPlayerRow(state.selectedTeam)?.let { selectedPlayer = it }
                },
                onChatRoomClick = { room ->
                    state.selectedTeam?.let { onOpenChatRoom(it.id, room) }
                },
                onForumTopicClick = { topic ->
                    state.selectedTeam?.let { onOpenForumTopic(it.id, topic) }
                },
            )
            is AdminRoute.ChatRoomViewer -> AdminChatRoomViewerContent(state = state)
            is AdminRoute.ForumTopicViewer -> AdminForumTopicViewerContent(state = state)
            AdminRoute.ChatRouting -> AdminChatRoutingContent(
                state = state,
                onOverlayChange = onAllianceOverlayChange,
                onStickerClick = onOpenStickerSettings,
                context = context,
            )
            AdminRoute.Players -> AdminPlayersContent(
                state = state,
                onSegmentChange = onPlayersSegmentChange,
                onServerFilter = onPlayersServerFilter,
                onSearchChange = onPlayersSearchChange,
                onPlayerClick = {
                    selectedPlayerAllianceCode = state.stickerAllianceCode
                        ?: state.alliances.firstOrNull()?.allianceCode
                    selectedPlayer = it
                },
                onLoadMore = onLoadMorePlayers,
            )
        }
    }

    selectedPlayer?.let { player ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = {
                selectedPlayer = null
                selectedPlayerAllianceCode = null
            },
            sheetState = sheetState,
        ) {
            AdminPlayerManageSheet(
                player = player,
                currentUserId = currentUserId,
                state = state,
                allianceCode = selectedPlayerAllianceCode
                    ?: state.stickerAllianceCode
                    ?: state.alliances.firstOrNull()?.allianceCode,
                onDismiss = {
                    selectedPlayer = null
                    selectedPlayerAllianceCode = null
                },
                onOpenStickerEditor = { alliance, userId ->
                    onOpenPlayerStickerEditor(alliance, userId)
                },
                onTogglePlayerStickerPack = onTogglePlayerStickerPack,
                onSavePlayerStickerAccess = onSavePlayerStickerAccess,
                onSaveGameIdentity = { nick, server ->
                    onUpdateGameIdentity(player.userId, player.identityId, nick, server)
                },
                onApprove = { onApprove(player.userId); selectedPlayer = null },
                onRemoveFromTeam = {
                    removeFromTeamUserId = player.userId
                    selectedPlayer = null
                },
                onRestorePending = { onRestorePending(player.userId); selectedPlayer = null },
                onSetRole = { role -> onSetRole(player.userId, role) },
                onDelete = { deleteUserId = player.userId; selectedPlayer = null },
            )
        }
    }

    editTeamTarget?.let { team ->
        AdminTeamBrandingDialog(
            initialTag = team.tag,
            initialDisplayName = team.displayName,
            onDismiss = { editTeamTarget = null },
            onSave = { tag, name ->
                onUpdatePlayerTeam(team.id, tag, name)
                editTeamTarget = null
            },
        )
    }

    if (state.stickerAllianceCode != null) {
        val stickerSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = onCloseStickerSettings,
            sheetState = stickerSheetState,
        ) {
            AdminStickerAccessSheet(
                state = state,
                onSelectPack = onSelectStickerPack,
                onMemberSearchChange = onStickerMemberSearchChange,
                onToggleRole = onToggleStickerAllianceRole,
                onToggleUser = onToggleStickerUserGrant,
                onSave = onSaveStickerAccess,
                onDismiss = onCloseStickerSettings,
            )
        }
    }

    removeFromTeamUserId?.let { userId ->
        AlertDialog(
            onDismissRequest = { removeFromTeamUserId = null },
            containerColor = SquadRelaySurfaces.dialogColor(),
            title = { Text(stringResource(R.string.admin_remove_team_confirm_title)) },
            text = { Text(stringResource(R.string.admin_remove_team_confirm_body_short)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRemoveFromTeam(userId)
                        removeFromTeamUserId = null
                    },
                ) {
                    Text(
                        stringResource(R.string.admin_btn_remove_team),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { removeFromTeamUserId = null }) {
                    Text(stringResource(R.string.admin_delete_cancel))
                }
            },
        )
    }

    deleteUserId?.let { userId ->
        AlertDialog(
            onDismissRequest = { deleteUserId = null },
            containerColor = SquadRelaySurfaces.dialogColor(),
            title = { Text(stringResource(R.string.admin_delete_title)) },
            text = { Text(stringResource(R.string.admin_delete_body_short)) },
            confirmButton = {
                TextButton(onClick = { onDeleteUser(userId); deleteUserId = null }) {
                    Text(stringResource(R.string.admin_delete_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteUserId = null }) {
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
                title = stringResource(R.string.admin_hub_players),
                subtitle = stringResource(
                    R.string.admin_hub_players_sub,
                    state.usersWithoutTeamCount,
                ),
                onClick = { onOpenRoute(AdminRoute.Players) },
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
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdminPlayersContent(
    state: AdminUiState,
    onSegmentChange: (AdminPlayersSegment) -> Unit,
    onServerFilter: (Int?) -> Unit,
    onSearchChange: (String) -> Unit,
    onPlayerClick: (AdminUserOnServerDto) -> Unit,
    onLoadMore: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = SquadRelayDimens.contentPaddingHorizontal,
            vertical = 8.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                stringResource(R.string.admin_players_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        state.gameServersError?.let { err ->
            item { AdminErrorBanner(err) {} }
        }
        state.usersOnServersError?.let { err ->
            item { AdminErrorBanner(err) {} }
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.playersSegment == AdminPlayersSegment.ALL,
                    onClick = { onSegmentChange(AdminPlayersSegment.ALL) },
                    label = { Text(stringResource(R.string.admin_players_tab_all)) },
                )
                FilterChip(
                    selected = state.playersSegment == AdminPlayersSegment.WITHOUT_TEAM,
                    onClick = { onSegmentChange(AdminPlayersSegment.WITHOUT_TEAM) },
                    label = {
                        Text(
                            stringResource(
                                R.string.admin_players_tab_no_team,
                                state.usersWithoutTeamCount,
                            ),
                        )
                    },
                )
            }
        }
        item {
            OutlinedTextField(
                value = state.playersSearchQuery,
                onValueChange = onSearchChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.admin_game_servers_search)) },
            )
        }
        item {
            AdminServerFilterChips(
                servers = state.gameServers,
                selected = state.gameServerFilter,
                onSelect = onServerFilter,
            )
        }
        if (state.usersOnServersLoading && state.usersOnServers.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(strokeWidth = 2.dp)
                }
            }
        } else if (state.usersOnServers.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.admin_game_servers_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(state.usersOnServers, key = { "${it.userId}_${it.identityId}" }) { row ->
                AdminPlayerListRow(row = row, onClick = { onPlayerClick(row) })
            }
            if (state.usersOnServersHasMore) {
                item {
                    AdminLoadMoreRow(
                        loading = state.usersOnServersLoadingMore,
                        onClick = onLoadMore,
                    )
                }
            }
        }
    }
}

@Composable
private fun AdminPlayerListRow(
    row: AdminUserOnServerDto,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = SquadRelaySurfaces.panelColor(0.42f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(
                    R.string.admin_game_servers_row,
                    formatServerLabel(row.serverNumber)
                        ?: stringResource(R.string.admin_players_server_unset),
                    row.gameNickname,
                ),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringResource(R.string.admin_game_servers_account, row.email),
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

@Composable
private fun AdminLoadMoreRow(
    loading: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(strokeWidth = 2.dp)
        } else {
            OutlinedButton(onClick = onClick) {
                Text(stringResource(R.string.admin_load_more))
            }
        }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdminServerFilterChips(
    servers: List<com.lastasylum.alliance.data.admin.AdminServerSummaryDto>,
    selected: Int?,
    onSelect: (Int?) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text(stringResource(R.string.admin_game_servers_filter_all)) },
        )
        servers.forEach { s ->
            FilterChip(
                selected = selected == s.serverNumber,
                onClick = { onSelect(s.serverNumber) },
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
}

@Composable
private fun AdminPlayerTeamsContent(
    state: AdminUiState,
    onSearchChange: (String) -> Unit,
    onServerFilter: (Int?) -> Unit,
    onTeamClick: (PlayerTeamAdminDto) -> Unit,
    onLoadMore: () -> Unit,
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
            AdminServerFilterChips(
                servers = state.gameServers,
                selected = state.teamsServerFilter,
                onSelect = onServerFilter,
            )
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
        if (state.playerTeamsHasMore && q.isEmpty()) {
            item {
                AdminLoadMoreRow(
                    loading = state.playerTeamsLoadingMore,
                    onClick = onLoadMore,
                )
            }
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
