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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.R
import com.lastasylum.alliance.ui.theme.SquadRelayDimens

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun OverlayOnlinePanelScaffold(
    displaySections: List<OverlayOnlinePresenceSection>,
    voiceFlagsByUserId: Map<String, VoiceMemberFlags>,
    searchQuery: String,
    activeFilterChip: OverlayOnlineFilterChip,
    loading: Boolean,
    refreshing: Boolean,
    error: String?,
    selfLabel: String,
    onSearchQuery: (String) -> Unit,
    onFilterChip: (OverlayOnlineFilterChip) -> Unit,
    onRefresh: () -> Unit,
    onMemberLongClick: (OverlayOnlineMemberUiModel) -> Unit,
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    toolbarTrailing: @Composable RowScope.() -> Unit = {},
) {
    val tokens = OverlayOnlineMemberTokens
    val totalVisible = displaySections.sumOf { it.items.size }

    Column(modifier = modifier.fillMaxSize()) {
        topBar()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SquadRelayDimens.contentPaddingHorizontal),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OnlinePanelFilterSearchRow(
                activeChip = activeFilterChip,
                searchQuery = searchQuery,
                onFilterChip = onFilterChip,
                onSearchQuery = onSearchQuery,
                trailing = {
                    IconButton(
                        onClick = onRefresh,
                        enabled = !refreshing,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = stringResource(R.string.overlay_online_refresh_cd),
                            tint = tokens.titleColor,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    toolbarTrailing()
                },
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
                            items(
                                items = section.items,
                                key = { "${section.kind}_${it.userId}" },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OnlinePanelFilterSearchRow(
    activeChip: OverlayOnlineFilterChip,
    searchQuery: String,
    onFilterChip: (OverlayOnlineFilterChip) -> Unit,
    onSearchQuery: (String) -> Unit,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val tokens = OverlayOnlineMemberTokens
    var filterExpanded by remember { mutableStateOf(false) }
    val filterLabel = stringResource(filterLabelRes(activeChip))
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = tokens.titleColor,
        unfocusedTextColor = tokens.titleColor,
        focusedBorderColor = tokens.borderLive.copy(alpha = 0.5f),
        unfocusedBorderColor = tokens.borderDefault,
        cursorColor = tokens.borderLive,
        focusedContainerColor = Color(0xFF1A2836),
        unfocusedContainerColor = Color(0xFF141C28),
    )
    val fieldShape = RoundedCornerShape(10.dp)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ExposedDropdownMenuBox(
            expanded = filterExpanded,
            onExpandedChange = { filterExpanded = it },
            modifier = Modifier.widthIn(min = 118.dp, max = 136.dp),
        ) {
            OutlinedTextField(
                value = filterLabel,
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = filterExpanded)
                },
                colors = fieldColors,
                shape = fieldShape,
                textStyle = MaterialTheme.typography.labelSmall,
            )
            ExposedDropdownMenu(
                expanded = filterExpanded,
                onDismissRequest = { filterExpanded = false },
            ) {
                OverlayOnlineFilterChip.entries.forEach { chip ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(filterLabelRes(chip)),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        },
                        onClick = {
                            filterExpanded = false
                            onFilterChip(chip)
                        },
                    )
                }
            }
        }
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQuery,
            modifier = Modifier.weight(1f),
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
            colors = fieldColors,
            shape = fieldShape,
            textStyle = MaterialTheme.typography.labelSmall,
        )
        trailing()
    }
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
