package com.lastasylum.alliance.ui.components.team

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.teams.TeamForumTopicDto
import com.lastasylum.alliance.ui.components.CompactSearchBar
import com.lastasylum.alliance.ui.util.parseIsoInstant
import java.time.Instant
import java.time.temporal.ChronoUnit

enum class ForumTopicListFilter {
    All,
    Unread,
    Recent,
}

fun filterForumTopics(
    topics: List<TeamForumTopicDto>,
    query: String,
    filter: ForumTopicListFilter,
    unreadAt: (TeamForumTopicDto) -> Int,
): List<TeamForumTopicDto> {
    val q = query.trim().lowercase()
    val now = Instant.now()
    return topics.filter { topic ->
        val matchesQuery = q.isEmpty() || topic.title.lowercase().contains(q)
        val matchesFilter = when (filter) {
            ForumTopicListFilter.All -> true
            ForumTopicListFilter.Unread -> unreadAt(topic) > 0
            ForumTopicListFilter.Recent -> {
                val instant = parseIsoInstant(topic.lastMessageAt ?: topic.createdAt)
                instant != null && ChronoUnit.DAYS.between(instant, now) <= 7
            }
        }
        matchesQuery && matchesFilter
    }
}

@Composable
fun ForumTopicListHeader(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    activeFilter: ForumTopicListFilter,
    onFilterChange: (ForumTopicListFilter) -> Unit,
    modifier: Modifier = Modifier,
    showSearch: Boolean = true,
    compactFilters: Boolean = false,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = com.lastasylum.alliance.ui.theme.SquadRelayDimens.contentPaddingHorizontal,
                vertical = 6.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (showSearch) {
            CompactSearchBar(
                query = searchQuery,
                onQueryChange = onSearchQueryChange,
                hint = stringResource(R.string.team_forum_search_hint),
                clearContentDescription = stringResource(R.string.team_members_search_clear_cd),
            )
        }
        val filters = if (compactFilters) {
            listOf(ForumTopicListFilter.All, ForumTopicListFilter.Unread)
        } else {
            ForumTopicListFilter.entries
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            filters.forEach { chip ->
                val selected = activeFilter == chip
                FilterChip(
                    selected = selected,
                    onClick = { onFilterChange(chip) },
                    label = {
                        Text(
                            text = when (chip) {
                                ForumTopicListFilter.All -> stringResource(R.string.team_forum_filter_all)
                                ForumTopicListFilter.Unread -> stringResource(R.string.team_forum_filter_unread)
                                ForumTopicListFilter.Recent -> stringResource(R.string.team_forum_filter_recent)
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                        selectedLabelColor = MaterialTheme.colorScheme.primary,
                    ),
                )
            }
        }
    }
}
