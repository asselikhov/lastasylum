package com.lastasylum.alliance.overlay

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import com.lastasylum.alliance.data.voice.VoicePeerState
import com.lastasylum.alliance.ui.theme.SquadRelayDimens

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OverlayOnlinePanelScaffold(
    displaySections: List<OverlayOnlinePresenceSection>,
    baseSections: List<OverlayOnlinePresenceSection>,
    searchQuery: String,
    activeFilterChip: OverlayOnlineFilterChip,
    filterCounts: OverlayOnlineFilterCounts,
    loading: Boolean,
    refreshing: Boolean,
    error: String?,
    staleDataHint: String? = null,
    selfLabel: String,
    voiceSelfUserId: String? = null,
    voiceLocalMicOn: Boolean? = null,
    voiceLocalSoundOn: Boolean? = null,
    voicePeers: Map<String, VoicePeerState> = emptyMap(),
    hasLocalVoiceSession: Boolean = false,
    onSearchQuery: (String) -> Unit,
    onFilterChip: (OverlayOnlineFilterChip) -> Unit,
    onRefresh: () -> Unit,
    onMemberClick: (OverlayOnlineMemberUiModel) -> Unit,
    onMemberLongClick: (OverlayOnlineMemberUiModel) -> Unit,
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
) {
    val tokens = OverlayOnlineMemberTokens
    val ingameSection = displaySections.firstOrNull { it.kind == PresenceSectionKind.Ingame }
    val recentSection = displaySections.firstOrNull { it.kind == PresenceSectionKind.Recent }
    val ingameItems = ingameSection?.items.orEmpty()
    val recentItems = recentSection?.items.orEmpty()
    val baseRecentCount = baseSections.firstOrNull { it.kind == PresenceSectionKind.Recent }?.items?.size ?: 0
    val totalVisible = ingameItems.size + recentItems.size

    val defaultRecentExpanded = activeFilterChip == OverlayOnlineFilterChip.RecentOnly ||
        baseRecentCount == 0
    var recentExpanded by remember(activeFilterChip, baseRecentCount) {
        mutableStateOf(defaultRecentExpanded)
    }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(OnlinePanelSectionSpacing),
    ) {
        topBar()
        staleDataHint?.takeIf { it.isNotBlank() }?.let { hint ->
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = SquadRelayDimens.contentPaddingHorizontal),
            )
        }
        OnlinePanelFilterSearchRow(
            activeChip = activeFilterChip,
            searchQuery = searchQuery,
            filterCounts = filterCounts,
            onFilterChip = onFilterChip,
            onSearchQuery = onSearchQuery,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = SquadRelayDimens.contentPaddingHorizontal,
                    vertical = OverlayHudFilterFields.SectionVerticalPadding,
                ),
        )
        when {
            loading && totalVisible == 0 && error == null -> {
                OverlayOnlinePanelShimmer(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
            }
            error != null && totalVisible == 0 -> {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(SquadRelayDimens.contentPaddingHorizontal),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        TextButton(onClick = onRefresh) {
                            Text(stringResource(R.string.overlay_panel_load_retry))
                        }
                    }
                }
            }
            totalVisible == 0 -> {
                val emptyRes = resolveOverlayOnlinePanelEmptyMessageRes(
                    activeFilterChip = activeFilterChip,
                    searchQuery = searchQuery,
                    filterCounts = filterCounts,
                    totalVisible = totalVisible,
                ) ?: R.string.overlay_online_empty
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(SquadRelayDimens.contentPaddingHorizontal),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(emptyRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            else -> {
                PullToRefreshBox(
                    isRefreshing = refreshing,
                    onRefresh = onRefresh,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = SquadRelayDimens.contentPaddingHorizontal,
                            end = SquadRelayDimens.contentPaddingHorizontal,
                            bottom = 16.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(tokens.gridSpacing),
                    ) {
                        if (ingameItems.isNotEmpty()) {
                            stickyHeader(key = "header_ingame") {
                                SectionHeader(
                                    title = stringResource(
                                        R.string.overlay_online_section_ingame_count,
                                        stringResource(R.string.overlay_online_section_ingame),
                                        ingameItems.size,
                                    ),
                                )
                            }
                            items(
                                items = ingameItems.chunked(2),
                                key = { row -> "ingame_${row.joinToString("_") { it.userId }}" },
                            ) { row ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(tokens.gridSpacing),
                                ) {
                                    row.forEach { member ->
                                        OverlayOnlineMemberGridCell(
                                            member = member,
                                            selfLabel = selfLabel,
                                            voiceSelfUserId = voiceSelfUserId,
                                            voiceLocalMicOn = voiceLocalMicOn,
                                            voiceLocalSoundOn = voiceLocalSoundOn,
                                            voicePeers = voicePeers,
                                            hasLocalVoiceSession = hasLocalVoiceSession,
                                            onClick = { onMemberClick(member) },
                                            onLongClick = { onMemberLongClick(member) },
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                    if (row.size == 1) {
                                        Box(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                        if (recentItems.isNotEmpty()) {
                            stickyHeader(key = "header_recent") {
                                RecentSectionHeader(
                                    count = recentItems.size,
                                    expanded = recentExpanded,
                                    onToggle = { recentExpanded = !recentExpanded },
                                )
                            }
                            if (recentExpanded) {
                                items(
                                    items = recentItems,
                                    key = { "recent_${it.userId}" },
                                ) { member ->
                                    OverlayOnlineMemberListRow(
                                        member = member,
                                        selfLabel = selfLabel,
                                        onClick = { onMemberClick(member) },
                                        onLongClick = { onMemberLongClick(member) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
            .padding(vertical = 6.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RecentSectionHeader(
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(
                R.string.overlay_online_section_recent_count,
                stringResource(R.string.overlay_online_section_recent),
                count,
            ),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onToggle) {
            Text(
                text = stringResource(
                    if (expanded) {
                        R.string.overlay_online_recent_collapse
                    } else {
                        R.string.overlay_online_recent_expand
                    },
                ),
            )
        }
    }
}

private val OnlinePanelSectionSpacing = 8.dp

@Composable
private fun OnlinePanelFilterSearchRow(
    activeChip: OverlayOnlineFilterChip,
    searchQuery: String,
    filterCounts: OverlayOnlineFilterCounts,
    onFilterChip: (OverlayOnlineFilterChip) -> Unit,
    onSearchQuery: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OverlayHudFilterSearchRow(
        selectedFilter = activeChip,
        filterOptions = OverlayOnlineFilterChip.entries,
        filterLabelFor = { chip ->
            val count = filterChipCountFor(chip, filterCounts)
            stringResource(filterChipLabelRes(chip), count)
        },
        onFilterSelect = onFilterChip,
        searchQuery = searchQuery,
        onSearchQuery = onSearchQuery,
        searchHint = stringResource(R.string.overlay_online_search_hint),
        searchClearContentDescription = stringResource(R.string.team_members_search_clear_cd),
        modifier = modifier,
    )
}
