package com.lastasylum.alliance.ui.components.team

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ModeEditOutline
import androidx.compose.material.icons.outlined.Poll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.lastasylum.alliance.R
import com.lastasylum.alliance.ui.chat.ChatSenderAvatar
import com.lastasylum.alliance.ui.util.telegramAvatarUrl
import com.lastasylum.alliance.data.teams.TeamNewsListItemDto
import com.lastasylum.alliance.data.teams.TeamNewsPollOptionDto
import com.lastasylum.alliance.data.teams.TeamNewsPollTallyDto
import com.lastasylum.alliance.ui.screens.teamnews.teamNewsAuthedImageRequest
import com.lastasylum.alliance.ui.components.premium.FeedCardHero
import com.lastasylum.alliance.ui.components.premium.FeedCardMetaRow
import com.lastasylum.alliance.ui.components.premium.FeedCardPollHeaderStrip
import com.lastasylum.alliance.ui.components.premium.FeedCardTypePill
import com.lastasylum.alliance.ui.components.premium.FeedCardUnreadDot
import com.lastasylum.alliance.ui.components.premium.PremiumFeedCardShell
import com.lastasylum.alliance.ui.components.premium.PremiumProgressBar
import com.lastasylum.alliance.ui.theme.premium.PremiumColors
import com.lastasylum.alliance.ui.theme.premium.PremiumSurfaces
import com.lastasylum.alliance.ui.theme.SquadRelayAtmosphericPurple
import com.lastasylum.alliance.ui.theme.SquadRelayAtmosphericSky
import com.lastasylum.alliance.ui.theme.SquadRelayPrimary
import com.lastasylum.alliance.ui.theme.SquadRelaySecondary
import com.lastasylum.alliance.ui.theme.SquadRelaySurfaces
import com.lastasylum.alliance.ui.util.formatTeamFeedDateRu
import kotlin.math.roundToInt

private val cardShape = TeamFeedCardTokens.cardShape
private val innerShape = RoundedCornerShape(16.dp)
private fun glassBorder(alpha: Float = 0.14f) =
    BorderStroke(1.dp, Color.White.copy(alpha = alpha))

@Composable
private fun PollBadge(
    modifier: Modifier = Modifier,
    votesLabel: String? = null,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = SquadRelayPrimary.copy(alpha = 0.18f),
            border = BorderStroke(1.dp, SquadRelayPrimary.copy(alpha = 0.35f)),
        ) {
            Row(
                Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    Icons.Outlined.Poll,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = SquadRelayPrimary.copy(alpha = 0.95f),
                )
                Text(
                    text = stringResource(R.string.team_news_poll_badge),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = SquadRelayPrimary.copy(alpha = 0.95f),
                )
            }
        }
        votesLabel?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PollResultOptionRow(
    text: String,
    voteCount: Int,
    share: Float,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val pct = if (share > 0f) (share * 100f).roundToInt() else 0
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = scheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (pct > 0) "$pct%" else "—",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = if (selected) SquadRelayPrimary else scheme.onSurfaceVariant,
            )
        }
        PremiumProgressBar(
            progress = share.coerceIn(0f, 1f),
            modifier = Modifier.fillMaxWidth(),
            barHeight = 6.dp,
        )
        Text(
            text = stringResource(R.string.team_news_poll_option_votes, voteCount),
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant.copy(alpha = 0.85f),
        )
    }
}

@Composable
fun TeamPollQuestionHeader(
    question: String,
    totalVotes: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PollBadge(
            votesLabel = if (totalVotes > 0) {
                stringResource(R.string.team_news_votes_count, totalVotes)
            } else {
                null
            },
        )
        Text(
            text = question,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 28.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun leadingPollOptions(
    options: List<TeamNewsPollOptionDto>,
    tallies: List<TeamNewsPollTallyDto>,
    max: Int,
): List<TeamNewsPollOptionDto> {
    if (options.size <= max) return options
    return options
        .sortedByDescending { opt -> tallies.find { it.optionId == opt.id }?.count ?: 0 }
        .take(max)
}

@Composable
fun TeamPollPreviewBlock(
    question: String,
    options: List<TeamNewsPollOptionDto>,
    tallies: List<TeamNewsPollTallyDto>,
    myVoteOptionId: String?,
    modifier: Modifier = Modifier,
    maxOptions: Int = Int.MAX_VALUE,
    compact: Boolean = false,
    showHeader: Boolean = true,
) {
    val totalVotes = tallies.sumOf { it.count }
    val previewOptions = if (maxOptions < Int.MAX_VALUE) {
        leadingPollOptions(options, tallies, maxOptions)
    } else {
        options
    }
    val remaining = (options.size - previewOptions.size).coerceAtLeast(0)
    val votesLabel = if (totalVotes > 0) {
        stringResource(R.string.team_news_votes_count, totalVotes)
    } else {
        null
    }

    val body = @Composable {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 14.dp),
        ) {
            if (showHeader) {
                PollBadge(votesLabel = votesLabel)
            }
            Text(
                text = question,
                style = if (compact) {
                    MaterialTheme.typography.headlineSmall
                } else {
                    MaterialTheme.typography.titleMedium
                },
                fontWeight = FontWeight.SemiBold,
                lineHeight = if (compact) 26.sp else 24.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = if (compact) 3 else Int.MAX_VALUE,
                overflow = TextOverflow.Ellipsis,
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                previewOptions.forEach { opt ->
                    val cnt = tallies.find { it.optionId == opt.id }?.count ?: 0
                    val share = if (totalVotes > 0) cnt.toFloat() / totalVotes else 0f
                    val selected = myVoteOptionId == opt.id
                    Surface(
                        shape = innerShape,
                        color = if (selected) {
                            PremiumColors.accentPurple.copy(alpha = 0.10f)
                        } else {
                            SquadRelaySurfaces.subtleColor(0.32f)
                        },
                        border = BorderStroke(
                            1.dp,
                            if (selected) {
                                PremiumColors.accentPurple.copy(alpha = 0.45f)
                            } else {
                                Color.White.copy(alpha = 0.06f)
                            },
                        ),
                    ) {
                        PollResultOptionRow(
                            text = opt.text,
                            voteCount = cnt,
                            share = share,
                            selected = selected,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        )
                    }
                }
                if (remaining > 0) {
                    Text(
                        text = "+$remaining",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (compact && totalVotes > 0) {
                Text(
                    text = stringResource(R.string.team_news_votes_count, totalVotes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (compact) {
                    Modifier.padding(top = 4.dp)
                } else {
                    Modifier
                },
            ),
    ) {
        if (compact) {
            HorizontalDivider(
                thickness = 1.dp,
                color = Color.White.copy(alpha = FeedCardDesignTokens.footerDividerAlpha),
            )
        }
        Box(
            Modifier.padding(
                top = if (compact) 12.dp else 0.dp,
            ),
        ) {
            body()
        }
    }
}

@Composable
fun TeamPollVoteOptionSurface(
    text: String,
    voteCount: Int,
    share: Float,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    contentBelow: @Composable (() -> Unit)? = null,
) {
    Surface(
        onClick = onSelect,
        shape = innerShape,
        color = if (selected) {
            SquadRelayPrimary.copy(alpha = 0.12f)
        } else {
            SquadRelaySurfaces.subtleColor(0.36f)
        },
        border = BorderStroke(
            1.dp,
            if (selected) SquadRelayPrimary.copy(alpha = 0.48f) else Color.White.copy(alpha = 0.07f),
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 14.dp, top = 8.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                RadioButton(selected = selected, onClick = onSelect)
                PollResultOptionRow(
                    text = text,
                    voteCount = voteCount,
                    share = share,
                    selected = selected,
                    modifier = Modifier.weight(1f),
                )
            }
            contentBelow?.let { below ->
                Box(Modifier.padding(start = 48.dp)) { below() }
            }
        }
    }
}

@Composable
fun TeamNewsFeedCard(
    item: TeamNewsListItemDto,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    showEdit: Boolean,
    modifier: Modifier = Modifier,
    isUnread: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val formattedCreatedAt = remember(item.createdAt) { formatTeamFeedDateRu(item.createdAt) }
    val hasHero = !item.firstImageRelativeUrl.isNullOrBlank()
    val pollQuestion = item.pollQuestion?.trim().orEmpty()
    val pollOptions = item.pollOptions.orEmpty()
    val showPollPreview = item.hasPoll && pollQuestion.isNotEmpty() && pollOptions.size >= 2
    val pollOnly = item.pollOnly
    val totalVotes = item.pollTallies.sumOf { it.count }
    val pollVotesLabel = if (totalVotes > 0) {
        stringResource(R.string.team_news_votes_count, totalVotes)
    } else {
        null
    }
    val context = LocalContext.current
    val heroRequest = remember(item.firstImageRelativeUrl, context) {
        teamNewsAuthedImageRequest(context, item.firstImageRelativeUrl)
    }
    val pollBadge = stringResource(R.string.team_news_poll_badge)
    val cardDesc = buildString {
        append(item.title)
        if (isUnread) append(", ${stringResource(R.string.team_news_unread_badge)}")
        append(", $formattedCreatedAt")
    }

    PremiumFeedCardShell(
        onClick = onClick,
        modifier = modifier.semantics { contentDescription = cardDesc },
        variant = if (pollOnly) FeedCardVariant.PollOnly else FeedCardVariant.News,
        isUnread = isUnread,
        listMode = true,
        contentPadding = PaddingValues(0.dp),
        topContent = {
            when {
                showPollPreview && pollOnly && !hasHero -> {
                    FeedCardPollHeaderStrip(
                        pollLabel = pollBadge,
                        votesLabel = pollVotesLabel,
                    )
                }
                showPollPreview && !hasHero && !pollOnly -> {
                    FeedCardPollHeaderStrip(pollLabel = pollBadge, votesLabel = null)
                }
                hasHero -> {
                    FeedCardHero(
                        imageRequest = heroRequest,
                        title = item.title,
                        contentDescription = item.title,
                        topOverlay = {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (isUnread) FeedCardUnreadDot()
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color.Black.copy(alpha = 0.38f),
                                    border = glassBorder(0.12f),
                                ) {
                                    Text(
                                        text = formattedCreatedAt,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.9f),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    )
                                }
                                if (item.hasPoll) {
                                    FeedCardTypePill(
                                        label = pollBadge,
                                        icon = Icons.Outlined.Poll,
                                    )
                                }
                            }
                        },
                    )
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = Color.White.copy(alpha = 0.06f),
                    )
                }
            }
        },
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(FeedCardDesignTokens.contentPadding),
                verticalArrangement = Arrangement.spacedBy(FeedCardDesignTokens.sectionGap),
            ) {
                if (showPollPreview) {
                    TeamPollPreviewBlock(
                        question = pollQuestion,
                        options = pollOptions,
                        tallies = item.pollTallies,
                        myVoteOptionId = item.myVoteOptionId,
                        maxOptions = 2,
                        compact = hasHero,
                        showHeader = false,
                    )
                } else {
                    if (!hasHero) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (isUnread) FeedCardUnreadDot()
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                                lineHeight = 26.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    if (item.excerpt.isNotBlank()) {
                        Text(
                            text = item.excerpt,
                            style = MaterialTheme.typography.bodyMedium,
                            lineHeight = 22.sp,
                            color = scheme.onSurfaceVariant,
                            maxLines = if (hasHero) 2 else 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        },
        footer = {
            Column(Modifier.fillMaxWidth()) {
                HorizontalDivider(
                    thickness = 1.dp,
                    color = Color.White.copy(alpha = FeedCardDesignTokens.footerDividerAlpha),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = FeedCardDesignTokens.contentPadding, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FeedCardMetaRow(
                        username = item.authorUsername,
                        telegramUsername = item.authorTelegramUsername,
                        trailingMeta = if (hasHero) "" else formattedCreatedAt,
                        modifier = Modifier.weight(1f),
                    )
                    if (showEdit) {
                        IconButton(onClick = onEdit, modifier = Modifier.size(40.dp)) {
                            Icon(
                                Icons.Outlined.ModeEditOutline,
                                contentDescription = stringResource(R.string.team_news_edit),
                                tint = scheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
    )
}

