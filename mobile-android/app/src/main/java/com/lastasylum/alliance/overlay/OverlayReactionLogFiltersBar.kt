package com.lastasylum.alliance.overlay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.chat.OverlayReactionLogFilter
import com.lastasylum.alliance.data.chat.OverlayReactionLogScopeFilter
import com.lastasylum.alliance.ui.components.CompactSearchBar
import com.lastasylum.alliance.ui.theme.SquadRelayDimens

@Composable
fun OverlayReactionLogFiltersBar(
    directionFilter: OverlayReactionLogFilter,
    onDirectionFilter: (OverlayReactionLogFilter) -> Unit,
    scopeFilter: OverlayReactionLogScopeFilter,
    onScopeFilter: (OverlayReactionLogScopeFilter) -> Unit,
    searchQuery: String,
    onSearchQuery: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = SquadRelayDimens.contentPaddingHorizontal,
                vertical = OverlayHudFilterFields.SectionVerticalPadding,
            ),
        verticalArrangement = Arrangement.spacedBy(OverlayHudFilterFields.SectionVerticalSpacing),
    ) {
        CompactSearchBar(
            query = searchQuery,
            onQueryChange = onSearchQuery,
            hint = stringResource(R.string.overlay_notifications_search_hint),
            clearContentDescription = stringResource(R.string.team_members_search_clear_cd),
        )
        OverlayHudFilterChipRow {
            OverlayReactionLogFilter.entries.forEach { option ->
                OverlayHudFilterChip(
                    label = directionFilterLabel(option),
                    selected = directionFilter == option,
                    onClick = { onDirectionFilter(option) },
                )
            }
        }
        OverlayHudFilterChipRow {
            OverlayReactionLogScopeFilter.entries.forEach { option ->
                OverlayHudFilterChip(
                    label = scopeFilterLabel(option),
                    selected = scopeFilter == option,
                    onClick = { onScopeFilter(option) },
                )
            }
        }
    }
}

@Composable
private fun directionFilterLabel(option: OverlayReactionLogFilter): String = when (option) {
    OverlayReactionLogFilter.All -> stringResource(R.string.overlay_notifications_filter_all)
    OverlayReactionLogFilter.Incoming -> stringResource(R.string.overlay_notifications_filter_incoming)
    OverlayReactionLogFilter.Outgoing -> stringResource(R.string.overlay_notifications_filter_outgoing)
}

@Composable
private fun scopeFilterLabel(option: OverlayReactionLogScopeFilter): String = when (option) {
    OverlayReactionLogScopeFilter.All -> stringResource(R.string.overlay_notifications_scope_all)
    OverlayReactionLogScopeFilter.Personal ->
        stringResource(R.string.overlay_reaction_burst_caption_private)
    OverlayReactionLogScopeFilter.Broadcast ->
        stringResource(R.string.overlay_reaction_burst_caption_broadcast)
    OverlayReactionLogScopeFilter.Reply ->
        stringResource(R.string.overlay_notifications_reply_scope)
}
