package com.lastasylum.alliance.overlay

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.R
import com.lastasylum.alliance.ui.theme.SquadRelayDimens
import com.lastasylum.alliance.ui.util.OVERLAY_ONLINE_PANEL_POLL_MS

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun OverlayOnlinePanelScaffold(
    displaySections: List<OverlayOnlinePresenceSection>,
    voiceFlagsByUserId: Map<String, VoiceMemberFlags>,
    ingameCount: Int,
    recentCount: Int,
    searchQuery: String,
    activeFilterChip: OverlayOnlineFilterChip,
    recentSectionCollapsed: Boolean,
    realtimeMode: Boolean,
    loading: Boolean,
    refreshing: Boolean,
    error: String?,
    selfLabel: String,
    onSearchQuery: (String) -> Unit,
    onFilterChip: (OverlayOnlineFilterChip) -> Unit,
    onToggleRecentSection: () -> Unit,
    onRefresh: () -> Unit,
    onMemberLongClick: (OverlayOnlineMemberUiModel) -> Unit,
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
) {
    val tokens = OverlayOnlineMemberTokens
    val totalVisible = displaySections.sumOf { it.items.size }
    val pollLabelSec = (OVERLAY_ONLINE_PANEL_POLL_MS / 1000).toInt()

    Column(modifier = modifier.fillMaxSize()) {
        topBar()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SquadRelayDimens.contentPaddingHorizontal),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RealtimeStatusChip(
                realtimeMode = realtimeMode,
                pollSeconds = pollLabelSec,
            )
            FilterChipRow(
                activeChip = activeFilterChip,
                onFilterChip = onFilterChip,
            )
            SummaryChipRow(
                ingameCount = ingameCount,
                recentCount = recentCount,
                recentCollapsed = recentSectionCollapsed,
                onToggleRecent = onToggleRecentSection,
            )
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQuery,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = {
                    Text(
                        stringResource(R.string.overlay_online_search_hint),
                        color = tokens.mutedColor,
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = tokens.mutedColor,
                        modifier = Modifier.size(18.dp),
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = tokens.titleColor,
                    unfocusedTextColor = tokens.titleColor,
                    focusedBorderColor = tokens.borderLive.copy(alpha = 0.5f),
                    unfocusedBorderColor = tokens.borderDefault,
                    cursorColor = tokens.borderLive,
                    focusedContainerColor = Color(0xFF1A2836),
                    unfocusedContainerColor = Color(0xFF141C28),
                ),
                shape = RoundedCornerShape(10.dp),
                textStyle = MaterialTheme.typography.labelSmall,
            )
        }
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
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
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
                            top = 4.dp,
                            bottom = 16.dp,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(tokens.gridSpacing),
                        verticalArrangement = Arrangement.spacedBy(tokens.gridSpacing),
                    ) {
                        displaySections.forEach { section ->
                            if (section.items.isEmpty()) return@forEach
                            item(key = "hdr_${section.kind}", span = { GridItemSpan(2) }) {
                                SectionHeader(section.kind)
                            }
                            items(
                                items = section.items,
                                key = { it.userId },
                            ) { member ->
                                val flags = voiceFlagsByUserId[member.userId]
                                OverlayOnlineMemberGridCell(
                                    member = member,
                                    micOn = flags?.micOn == true,
                                    soundOn = flags?.soundOn == true,
                                    selfLabel = selfLabel,
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

@Composable
private fun RealtimeStatusChip(realtimeMode: Boolean, pollSeconds: Int) {
    val tokens = OverlayOnlineMemberTokens
    val label = if (realtimeMode) {
        stringResource(R.string.overlay_online_realtime_live)
    } else {
        stringResource(R.string.overlay_online_realtime_poll, pollSeconds)
    }
    Text(
        text = label,
        style = tokens.metaStyle.copy(
            color = if (realtimeMode) tokens.livePulse else tokens.mutedColor,
            fontWeight = FontWeight.SemiBold,
        ),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (realtimeMode) tokens.livePulse.copy(alpha = 0.12f)
                else Color(0xFF1A2836),
            )
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
private fun FilterChipRow(
    activeChip: OverlayOnlineFilterChip,
    onFilterChip: (OverlayOnlineFilterChip) -> Unit,
) {
    val scroll = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scroll),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        FilterChipOption(
            label = stringResource(R.string.overlay_online_filter_all),
            selected = activeChip == OverlayOnlineFilterChip.All,
            onClick = { onFilterChip(OverlayOnlineFilterChip.All) },
        )
        FilterChipOption(
            label = stringResource(R.string.overlay_online_filter_ingame),
            selected = activeChip == OverlayOnlineFilterChip.IngameOnly,
            onClick = { onFilterChip(OverlayOnlineFilterChip.IngameOnly) },
        )
        FilterChipOption(
            label = stringResource(R.string.overlay_online_filter_with_mic),
            selected = activeChip == OverlayOnlineFilterChip.WithMic,
            onClick = { onFilterChip(OverlayOnlineFilterChip.WithMic) },
        )
        FilterChipOption(
            label = stringResource(R.string.overlay_online_filter_recent),
            selected = activeChip == OverlayOnlineFilterChip.RecentOnly,
            onClick = { onFilterChip(OverlayOnlineFilterChip.RecentOnly) },
        )
    }
}

@Composable
private fun SummaryChipRow(
    ingameCount: Int,
    recentCount: Int,
    recentCollapsed: Boolean,
    onToggleRecent: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        FilterChipOption(
            label = stringResource(R.string.overlay_online_summary_ingame, ingameCount),
            selected = false,
            onClick = {},
            enabled = false,
        )
        FilterChipOption(
            label = if (recentCollapsed) {
                stringResource(R.string.overlay_online_recent_expand, recentCount)
            } else {
                stringResource(R.string.overlay_online_recent_collapse, recentCount)
            },
            selected = !recentCollapsed,
            onClick = onToggleRecent,
        )
    }
}

@Composable
private fun FilterChipOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Color(0xFF2A4558),
            containerColor = Color(0xFF1A2836),
            labelColor = Color(0xFFE8F4FF),
            selectedLabelColor = Color(0xFFE8F4FF),
        ),
        modifier = Modifier.heightIn(min = 32.dp),
    )
}

@Composable
private fun SectionHeader(kind: PresenceSectionKind) {
    val text = when (kind) {
        PresenceSectionKind.Ingame -> stringResource(R.string.overlay_online_section_ingame)
        PresenceSectionKind.Recent -> stringResource(R.string.overlay_online_section_recent)
    }
    val color = when (kind) {
        PresenceSectionKind.Ingame -> MaterialTheme.colorScheme.primary
        PresenceSectionKind.Recent -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = color,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
    )
}

@Composable
private fun OnlinePanelSkeleton(modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        CircularProgressIndicator(strokeWidth = 2.dp)
    }
}
