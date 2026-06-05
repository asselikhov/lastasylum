package com.lastasylum.alliance.ui.screens.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.admin.AdminTeamMemberDto
import com.lastasylum.alliance.data.admin.PlayerTeamAdminDto
import com.lastasylum.alliance.data.chat.ChatMessage
import com.lastasylum.alliance.data.chat.ChatRoomDto
import com.lastasylum.alliance.data.teams.TeamForumMessageDto
import com.lastasylum.alliance.data.teams.TeamForumTopicDto
import com.lastasylum.alliance.data.teams.TeamNewsListItemDto
import com.lastasylum.alliance.ui.admin.AdminTeamDetailTab
import com.lastasylum.alliance.ui.admin.AdminUiState
import com.lastasylum.alliance.ui.admin.toAdminPlayerRow
import com.lastasylum.alliance.ui.components.team.ForumTopicCardTokens
import com.lastasylum.alliance.ui.components.team.ForumTopicFeedCard
import com.lastasylum.alliance.ui.theme.SquadRelayDimens
import com.lastasylum.alliance.ui.theme.SquadRelaySurfaces
import com.lastasylum.alliance.ui.util.adminAppVersionLine
import com.lastasylum.alliance.ui.util.chatSenderDisplayLine
import com.lastasylum.alliance.ui.util.formatForumTopicTimeRu
import com.lastasylum.alliance.ui.util.formatIsoDateShortRu
import com.lastasylum.alliance.ui.util.formatIsoDateTimeRu
import com.lastasylum.alliance.ui.util.formatServerLabel
import com.lastasylum.alliance.ui.util.teamTagWithServerPrefix

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminTeamDetailContent(
    state: AdminUiState,
    onEditTeam: () -> Unit,
    onTabChange: (AdminTeamDetailTab) -> Unit,
    onMemberClick: (AdminTeamMemberDto) -> Unit,
    onChatRoomClick: (ChatRoomDto) -> Unit,
    onForumTopicClick: (TeamForumTopicDto) -> Unit,
) {
    val team = state.selectedTeam
    Column(Modifier.fillMaxSize()) {
        team?.let { t ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = SquadRelayDimens.contentPaddingHorizontal,
                        vertical = 8.dp,
                    ),
                shape = MaterialTheme.shapes.medium,
                color = SquadRelaySurfaces.panelColor(0.45f),
            ) {
                RowHeader(team = t, onEditTeam = onEditTeam)
            }
        }
        val tabs = AdminTeamDetailTab.entries
        PrimaryTabRow(
            selectedTabIndex = tabs.indexOf(state.teamDetailTab).coerceAtLeast(0),
            modifier = Modifier.fillMaxWidth(),
        ) {
            tabs.forEach { tab ->
                Tab(
                    selected = state.teamDetailTab == tab,
                    onClick = { onTabChange(tab) },
                    text = {
                        Text(
                            when (tab) {
                                AdminTeamDetailTab.MEMBERS ->
                                    stringResource(R.string.admin_team_tab_members)
                                AdminTeamDetailTab.CHAT_ROOMS ->
                                    stringResource(R.string.admin_team_tab_chat)
                                AdminTeamDetailTab.NEWS ->
                                    stringResource(R.string.admin_team_tab_news)
                                AdminTeamDetailTab.FORUM ->
                                    stringResource(R.string.admin_team_tab_forum)
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
            }
        }
        when (state.teamDetailTab) {
            AdminTeamDetailTab.MEMBERS -> AdminTeamMembersTab(
                state = state,
                onMemberClick = onMemberClick,
            )
            AdminTeamDetailTab.CHAT_ROOMS -> AdminTeamChatRoomsTab(
                state = state,
                onRoomClick = onChatRoomClick,
            )
            AdminTeamDetailTab.NEWS -> AdminTeamNewsTab(state = state)
            AdminTeamDetailTab.FORUM -> AdminTeamForumTab(
                state = state,
                onTopicClick = onForumTopicClick,
            )
        }
    }
}

@Composable
private fun RowHeader(
    team: PlayerTeamAdminDto,
    onEditTeam: () -> Unit,
) {
    androidx.compose.foundation.layout.Row(
        Modifier
            .fillMaxWidth()
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "${teamTagWithServerPrefix(team.tag.uppercase(), team.leaderServerNumber)} ${team.displayName}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringResource(R.string.admin_team_leader, team.leaderUsername),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (team.serverNumbers.isNotEmpty()) {
                Text(
                    team.serverNumbers.joinToString(", ") { formatServerLabel(it) ?: "#$it" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        IconButton(onClick = onEditTeam) {
            Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.admin_team_edit_cd))
        }
    }
}

@Composable
private fun AdminTeamMembersTab(
    state: AdminUiState,
    onMemberClick: (AdminTeamMemberDto) -> Unit,
) {
    if (state.teamMembersLoading && state.teamMembers.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(strokeWidth = 2.dp)
        }
        return
    }
    state.teamMembersError?.let {
        Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
    }
    LazyColumn(
        contentPadding = PaddingValues(horizontal = SquadRelayDimens.contentPaddingHorizontal, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (state.teamMembers.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.admin_members_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
        }
        items(state.teamMembers, key = { it.userId }) { member ->
            val serverLabel = formatServerLabel(member.serverNumber)
            val line = if (serverLabel != null) "$serverLabel · ${member.gameNickname}" else member.gameNickname
            Surface(
                onClick = { onMemberClick(member) },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = SquadRelaySurfaces.panelColor(0.38f),
            ) {
                androidx.compose.foundation.layout.Row(
                    Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(line + if (member.isLeader) " ★" else "", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "${member.teamRole} · ${member.email}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            adminAppVersionLine(member.appVersionName, member.appVersionCode),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                }
            }
        }
    }
}

@Composable
private fun AdminTeamChatRoomsTab(
    state: AdminUiState,
    onRoomClick: (ChatRoomDto) -> Unit,
) {
    TabLoadingList(
        loading = state.teamChatRoomsLoading,
        empty = state.teamChatRooms.isEmpty(),
        emptyText = stringResource(R.string.admin_team_chat_empty),
        error = state.teamChatRoomsError,
    ) {
        items(state.teamChatRooms, key = { it.id }) { room ->
            Surface(
                onClick = { onRoomClick(room) },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = SquadRelaySurfaces.panelColor(0.38f),
            ) {
                androidx.compose.foundation.layout.Row(
                    Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(room.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                }
            }
        }
    }
}

@Composable
private fun AdminTeamNewsTab(state: AdminUiState) {
    TabLoadingList(
        loading = state.teamNewsLoading,
        empty = state.teamNews.isEmpty(),
        emptyText = stringResource(R.string.admin_team_news_empty),
        error = state.teamNewsError,
    ) {
        items(state.teamNews, key = { it.id }) { item ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = SquadRelaySurfaces.panelColor(0.38f),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(item.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(item.excerpt, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${item.authorUsername} · ${formatIsoDateShortRu(item.createdAt)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun AdminTeamForumTab(
    state: AdminUiState,
    onTopicClick: (TeamForumTopicDto) -> Unit,
) {
    TabLoadingList(
        loading = state.teamForumLoading,
        empty = state.teamForumTopics.isEmpty(),
        emptyText = stringResource(R.string.admin_team_forum_empty),
        error = state.teamForumError,
        itemSpacing = ForumTopicCardTokens.listSpacing,
    ) {
        itemsIndexed(state.teamForumTopics, key = { _, topic -> topic.id }) { index, topic ->
            val messageMeta = topic.lastMessageAt?.let { formatForumTopicTimeRu(it) }
                ?: formatForumTopicTimeRu(topic.createdAt)
            ForumTopicFeedCard(
                topic = topic,
                listIndex = index,
                messageMeta = messageMeta,
                displayUnreadCount = topic.unreadCount,
                onClick = { onTopicClick(topic) },
            )
        }
    }
}

@Composable
private fun TabLoadingList(
    loading: Boolean,
    empty: Boolean,
    emptyText: String,
    error: String?,
    itemSpacing: androidx.compose.ui.unit.Dp = 8.dp,
    content: LazyListScope.() -> Unit,
) {
    if (loading && empty) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(strokeWidth = 2.dp)
        }
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(horizontal = SquadRelayDimens.contentPaddingHorizontal, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(itemSpacing),
    ) {
        error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        if (empty) {
            item {
                Text(emptyText, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
            }
        }
        content()
    }
}

@Composable
fun AdminChatRoomViewerContent(state: AdminUiState) {
    if (state.chatRoomMessagesLoading && state.chatRoomMessages.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(strokeWidth = 2.dp)
        }
        return
    }
    state.chatRoomMessagesError?.let {
        Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
    }
    LazyColumn(
        contentPadding = PaddingValues(horizontal = SquadRelayDimens.contentPaddingHorizontal, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(state.chatRoomMessages, key = { it._id ?: it.text }) { msg ->
            AdminChatMessageCard(msg)
        }
    }
}

@Composable
private fun AdminChatMessageCard(message: ChatMessage) {
    val sender = chatSenderDisplayLine(message.senderTeamTag, message.senderUsername, message.senderServerNumber)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = SquadRelaySurfaces.panelColor(0.35f),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(sender, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            if (message.text.isNotBlank()) {
                Text(message.text, style = MaterialTheme.typography.bodyMedium)
            }
            message.createdAt?.let {
                Text(
                    formatIsoDateTimeRu(it),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun AdminForumTopicViewerContent(state: AdminUiState) {
    if (state.forumTopicMessagesLoading && state.forumTopicMessages.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(strokeWidth = 2.dp)
        }
        return
    }
    state.forumTopicMessagesError?.let {
        Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
    }
    LazyColumn(
        contentPadding = PaddingValues(horizontal = SquadRelayDimens.contentPaddingHorizontal, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(state.forumTopicMessages, key = { it.id }) { msg ->
            AdminForumMessageCard(msg)
        }
    }
}

@Composable
private fun AdminForumMessageCard(message: TeamForumMessageDto) {
    val sender = chatSenderDisplayLine(message.senderTeamTag, message.senderUsername, message.senderServerNumber)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = SquadRelaySurfaces.panelColor(0.35f),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(sender, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            if (message.text.isNotBlank()) {
                Text(message.text, style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                formatIsoDateTimeRu(message.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
