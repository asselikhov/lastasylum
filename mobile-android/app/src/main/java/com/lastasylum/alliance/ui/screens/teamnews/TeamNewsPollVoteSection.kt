package com.lastasylum.alliance.ui.screens.teamnews

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.teams.TeamNewsPollDetailDto
import com.lastasylum.alliance.data.teams.TeamNewsPollVoteDto
import com.lastasylum.alliance.overlay.LocalOverlayUiMode
import com.lastasylum.alliance.overlay.LocalShowOverlayPollVotersSheet
import com.lastasylum.alliance.overlay.OverlayAwareBottomSheet
import com.lastasylum.alliance.overlay.OverlayChatInteractionHold
import com.lastasylum.alliance.overlay.OverlayPollVotersRequest
import com.lastasylum.alliance.ui.chat.ChatSenderAvatar
import com.lastasylum.alliance.ui.components.team.JournalVoteButton
import com.lastasylum.alliance.ui.components.team.TeamPollQuestionHeader
import com.lastasylum.alliance.ui.components.team.TeamPollVoteOptionSurface
import com.lastasylum.alliance.ui.theme.SquadRelaySurfaces

internal fun pollVotersForOption(
    votes: List<TeamNewsPollVoteDto>,
    optionId: String,
): List<TeamNewsPollVoteDto> = votes.filter { it.optionId == optionId }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TeamNewsPollVotersReveal(
    optionText: String,
    voters: List<TeamNewsPollVoteDto>,
    modifier: Modifier = Modifier,
) {
    val overlayUi = LocalOverlayUiMode.current
    val showOverlaySheet = LocalShowOverlayPollVotersSheet.current
    var showSheet by remember { mutableStateOf(false) }
    val count = voters.size
    if (count == 0) {
        Text(
            text = stringResource(R.string.team_news_poll_voters_empty),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )
        return
    }
    TextButton(
        onClick = {
            if (overlayUi && showOverlaySheet != null) {
                showOverlaySheet(
                    OverlayPollVotersRequest(
                        optionText = optionText,
                        voters = voters,
                    ),
                )
            } else {
                OverlayChatInteractionHold.prepareOverlayModalInteraction(overlayUi)
                showSheet = true
            }
        },
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 2.dp),
    ) {
        Text(
            text = stringResource(R.string.team_news_poll_show_voters, count),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    if (showSheet && !overlayUi) {
        OverlayAwareBottomSheet(
            onDismissRequest = { showSheet = false },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.team_news_poll_voters_sheet_title, optionText),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                TeamNewsPollVoterChips(voters = voters)
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TeamNewsPollVoterChips(
    voters: List<TeamNewsPollVoteDto>,
    modifier: Modifier = Modifier,
) {
    if (voters.isEmpty()) {
        Text(
            text = stringResource(R.string.team_news_poll_voters_empty),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )
        return
    }
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        voters.forEach { vote ->
            val label = vote.username?.trim().orEmpty().ifBlank { "?" }
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = SquadRelaySurfaces.panelColor(),
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                ),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ChatSenderAvatar(
                        avatarRelativeUrl = vote.avatarRelativeUrl,
                        size = 22.dp,
                        fallbackName = label,
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
internal fun TeamNewsPollVoteBlock(
    poll: TeamNewsPollDetailDto,
    voteBusy: Boolean,
    onVote: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val totalVotes = poll.tallies.sumOf { it.count }
    val tallyByOption = remember(poll.tallies) {
        poll.tallies.associate { it.optionId to it.count }
    }
    val votersByOption = remember(poll.votes) {
        poll.votes.groupBy { it.optionId }
    }
    var selected by remember(poll.myVoteOptionId, poll.options) {
        mutableStateOf(poll.myVoteOptionId)
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TeamPollQuestionHeader(question = poll.question, totalVotes = totalVotes)
        poll.options.forEach { opt ->
            val cnt = tallyByOption[opt.id] ?: 0
            val share = if (totalVotes > 0) cnt.toFloat() / totalVotes else 0f
            val optionVoters = votersByOption[opt.id].orEmpty()
            val isSelected = selected == opt.id
            TeamPollVoteOptionSurface(
                text = opt.text,
                voteCount = cnt,
                share = share,
                selected = isSelected,
                onSelect = { selected = opt.id },
                contentBelow = {
                    TeamNewsPollVotersReveal(
                        optionText = opt.text,
                        voters = optionVoters,
                    )
                },
            )
        }
        JournalVoteButton(
            label = if (poll.myVoteOptionId == null) {
                stringResource(R.string.team_news_vote)
            } else {
                stringResource(R.string.team_news_change_vote)
            },
            enabled = !voteBusy && selected != null,
            loading = voteBusy,
            onClick = {
                val oid = selected ?: return@JournalVoteButton
                onVote(oid)
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
