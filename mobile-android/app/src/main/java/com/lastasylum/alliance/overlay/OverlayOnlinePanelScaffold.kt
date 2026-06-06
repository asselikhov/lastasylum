package com.lastasylum.alliance.overlay

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
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
    searchQuery: String,
    activeFilterChip: OverlayOnlineFilterChip,
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
    onMemberLongClick: (OverlayOnlineMemberUiModel) -> Unit,
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
) {
    val tokens = OverlayOnlineMemberTokens
    val totalVisible = displaySections.sumOf { it.items.size }

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
                OnlinePanelSkeleton(
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
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(SquadRelayDimens.contentPaddingHorizontal),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.overlay_online_empty),
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
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = SquadRelayDimens.contentPaddingHorizontal,
                            end = SquadRelayDimens.contentPaddingHorizontal,
                            bottom = 16.dp,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(tokens.gridSpacing),
                        verticalArrangement = Arrangement.spacedBy(tokens.gridSpacing),
                    ) {
                        displaySections.forEach { section ->
                            items(
                                items = section.items,
                                key = { "${section.kind}_${it.userId}" },
                            ) { member ->
                                OverlayOnlineMemberGridCell(
                                    member = member,
                                    selfLabel = selfLabel,
                                    voiceSelfUserId = voiceSelfUserId,
                                    voiceLocalMicOn = voiceLocalMicOn,
                                    voiceLocalSoundOn = voiceLocalSoundOn,
                                    voicePeers = voicePeers,
                                    hasLocalVoiceSession = hasLocalVoiceSession,
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

private val OnlinePanelSectionSpacing = 8.dp

@Composable
private fun OnlinePanelFilterSearchRow(
    activeChip: OverlayOnlineFilterChip,
    searchQuery: String,
    onFilterChip: (OverlayOnlineFilterChip) -> Unit,
    onSearchQuery: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OverlayHudFilterSearchRow(
        selectedFilter = activeChip,
        filterOptions = OverlayOnlineFilterChip.entries,
        filterLabelFor = { chip -> stringResource(filterLabelRes(chip)) },
        onFilterSelect = onFilterChip,
        searchQuery = searchQuery,
        onSearchQuery = onSearchQuery,
        searchHint = stringResource(R.string.overlay_online_search_hint),
        searchClearContentDescription = stringResource(R.string.team_members_search_clear_cd),
        modifier = modifier,
    )
}

private fun filterLabelRes(chip: OverlayOnlineFilterChip): Int = when (chip) {
    OverlayOnlineFilterChip.All -> R.string.overlay_online_filter_all
    OverlayOnlineFilterChip.IngameOnly -> R.string.overlay_online_filter_ingame
    OverlayOnlineFilterChip.WithMic -> R.string.overlay_online_filter_with_mic
    OverlayOnlineFilterChip.RecentOnly -> R.string.overlay_online_filter_recent
}

@Composable
private fun OnlinePanelSkeleton(modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        CircularProgressIndicator(strokeWidth = 2.dp)
    }
}
