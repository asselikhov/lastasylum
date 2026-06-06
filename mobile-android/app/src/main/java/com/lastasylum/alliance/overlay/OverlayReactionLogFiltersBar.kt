package com.lastasylum.alliance.overlay

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.chat.OverlayReactionLogFilter
import com.lastasylum.alliance.ui.theme.SquadRelayDimens

@Composable
fun OverlayReactionLogFiltersBar(
    directionFilter: OverlayReactionLogFilter,
    onDirectionFilter: (OverlayReactionLogFilter) -> Unit,
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
    ) {
        OverlayHudFilterSearchRow(
            selectedFilter = directionFilter,
            filterOptions = OverlayReactionLogFilter.entries,
            filterLabelFor = { option -> directionFilterLabel(option) },
            onFilterSelect = onDirectionFilter,
            searchQuery = searchQuery,
            onSearchQuery = onSearchQuery,
            searchHint = stringResource(R.string.overlay_notifications_search_hint),
            searchClearContentDescription = stringResource(R.string.team_members_search_clear_cd),
        )
    }
}

@Composable
private fun directionFilterLabel(option: OverlayReactionLogFilter): String =
    when (option) {
        OverlayReactionLogFilter.All ->
            stringResource(R.string.overlay_notifications_filter_all)
        OverlayReactionLogFilter.Incoming ->
            stringResource(R.string.overlay_notifications_filter_incoming)
        OverlayReactionLogFilter.Outgoing ->
            stringResource(R.string.overlay_notifications_filter_outgoing)
    }
