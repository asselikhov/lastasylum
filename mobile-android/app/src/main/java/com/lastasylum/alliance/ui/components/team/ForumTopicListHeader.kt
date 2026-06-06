package com.lastasylum.alliance.ui.components.team

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.teams.TeamForumTopicDto
import com.lastasylum.alliance.ui.components.CompactSearchBar

fun filterForumTopics(
    topics: List<TeamForumTopicDto>,
    query: String,
): List<TeamForumTopicDto> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return topics
    return topics.filter { topic -> topic.title.lowercase().contains(q) }
}

@Composable
fun ForumTopicListHeader(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    CompactSearchBar(
        query = searchQuery,
        onQueryChange = onSearchQueryChange,
        hint = stringResource(R.string.team_forum_search_hint),
        clearContentDescription = stringResource(R.string.team_members_search_clear_cd),
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = com.lastasylum.alliance.ui.theme.SquadRelayDimens.contentPaddingHorizontal,
                vertical = 6.dp,
            ),
    )
}
