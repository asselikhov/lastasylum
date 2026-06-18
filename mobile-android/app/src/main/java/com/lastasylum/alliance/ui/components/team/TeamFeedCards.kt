package com.lastasylum.alliance.ui.components.team

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.teams.TeamNewsListItemDto
import com.lastasylum.alliance.ui.components.premium.FeedCardHero
import com.lastasylum.alliance.ui.components.premium.FeedCardMetaRow
import com.lastasylum.alliance.ui.components.premium.FeedCardUnreadTonalBadge
import com.lastasylum.alliance.ui.components.team.FeedAnimationTier
import com.lastasylum.alliance.ui.screens.teamnews.teamNewsAuthedImageRequest
import com.lastasylum.alliance.ui.util.formatTeamFeedDateRu

@Composable
fun TeamPollQuestionHeader(
    question: String,
    totalVotes: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        JournalPollBadge(
            votesLabel = if (totalVotes > 0) {
                stringResource(R.string.team_news_votes_count, totalVotes)
            } else {
                null
            },
        )
        Text(
            text = question,
            style = PremiumJournalFeedTokens.headlineStyle,
        )
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
    JournalPollOptionTile(
        text = text,
        share = share,
        voteCount = voteCount,
        selected = selected,
        modifier = modifier,
        interactive = true,
        onSelect = onSelect,
        showVoteCount = false,
        contentBelow = contentBelow,
    )
}

@Composable
fun JournalVoteButton(
    label: String,
    enabled: Boolean,
    loading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                if (enabled) {
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xFF38BDF8).copy(alpha = 0.28f),
                            Color(0xFF818CF8).copy(alpha = 0.24f),
                        ),
                    )
                } else {
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xFF2A3444).copy(alpha = 0.6f),
                            Color(0xFF2A3444).copy(alpha = 0.5f),
                        ),
                    )
                },
            )
            .border(
                1.dp,
                if (enabled) {
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xFF38BDF8).copy(alpha = 0.55f),
                            Color(0xFF818CF8).copy(alpha = 0.45f),
                        ),
                    )
                } else {
                    Brush.linearGradient(
                        listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.04f)),
                    )
                },
                shape,
            )
            .then(
                if (enabled && !loading) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            androidx.compose.material3.CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = PremiumJournalFeedTokens.titleColor,
            )
        } else {
            Text(
                text = label,
                style = PremiumJournalFeedTokens.optionLabelStyle,
                color = if (enabled) PremiumJournalFeedTokens.titleColor else PremiumJournalFeedTokens.metaColor,
            )
        }
    }
}

@Composable
fun TeamNewsFeedCard(
    item: TeamNewsListItemDto,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isUnread: Boolean = false,
) {
    val formattedCreatedAt = remember(item.createdAt) { formatTeamFeedDateRu(item.createdAt) }
    val hasHero = !item.firstImageRelativeUrl.isNullOrBlank()
    val pollQuestion = item.pollQuestion?.trim().orEmpty()
    val pollOptions = item.pollOptions.orEmpty()
    val showPollPreview = item.hasPoll && pollQuestion.isNotEmpty() && pollOptions.size >= 2
    val pollOnly = item.pollOnly
    val totalVotes = item.pollTallies.sumOf { it.count }
    val context = LocalContext.current
    val heroRequest = remember(item.id, item.firstImageRelativeUrl) {
        teamNewsAuthedImageRequest(context, item.firstImageRelativeUrl)
    }
    val unreadBadge = stringResource(R.string.team_news_unread_badge)
    val cardDesc = remember(item.id, item.title, isUnread, formattedCreatedAt, unreadBadge) {
        buildString {
            append(item.title)
            if (isUnread) append(", $unreadBadge")
            append(", $formattedCreatedAt")
        }
    }
    val journalVariant = when {
        pollOnly -> JournalFeedVariant.PollOnly
        showPollPreview -> JournalFeedVariant.Poll
        isUnread -> JournalFeedVariant.UnreadNews
        else -> JournalFeedVariant.News
    }

    PremiumJournalFeedShell(
        onClick = onClick,
        modifier = modifier.semantics { contentDescription = cardDesc },
        variant = journalVariant,
        isUnread = isUnread,
        animationTier = if (isUnread) FeedAnimationTier.Full else FeedAnimationTier.Off,
        contentTopPadding = PremiumJournalFeedTokens.contentPaddingTop(hasHero, journalVariant),
        topContent = {
            if (hasHero) {
                FeedCardHero(
                    imageRequest = heroRequest,
                    title = item.title,
                    contentDescription = item.title,
                    topOverlay = {
                        if (isUnread) FeedCardUnreadTonalBadge(label = unreadBadge)
                    },
                )
                HorizontalDivider(
                    thickness = 1.dp,
                    color = Color.White.copy(alpha = 0.06f),
                )
            }
        },
        content = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(PremiumJournalFeedTokens.sectionGap),
            ) {
                if (showPollPreview) {
                    val displayTitle = when {
                        pollOnly -> pollQuestion
                        !hasHero -> item.title
                        else -> ""
                    }
                    if (displayTitle.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (!hasHero && isUnread) FeedCardUnreadTonalBadge(label = unreadBadge)
                            Text(
                                text = displayTitle,
                                style = PremiumJournalFeedTokens.titleStyle,
                                maxLines = if (pollOnly) 3 else 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                        }
                    }
                    JournalPollBadge(
                        votesLabel = if (totalVotes > 0) {
                            stringResource(R.string.team_news_votes_count, totalVotes)
                        } else {
                            null
                        },
                    )
                    JournalPollPreviewBlock(
                        options = pollOptions,
                        tallies = item.pollTallies,
                        myVoteOptionId = item.myVoteOptionId,
                        maxOptions = 2,
                    )
                } else {
                    if (!hasHero) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (isUnread) FeedCardUnreadTonalBadge(label = unreadBadge)
                            Text(
                                text = item.title,
                                style = PremiumJournalFeedTokens.titleStyle,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    if (item.excerpt.isNotBlank()) {
                        Text(
                            text = item.excerpt,
                            style = PremiumJournalFeedTokens.excerptStyle,
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
                    color = Color.White.copy(alpha = 0.06f),
                )
                FeedCardMetaRow(
                    username = item.authorUsername,
                    avatarRelativeUrl = item.authorAvatarRelativeUrl,
                    trailingMeta = formattedCreatedAt,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = PremiumJournalFeedTokens.accentRailWidth + 10.dp,
                            end = PremiumJournalFeedTokens.cardPaddingH,
                            top = 8.dp,
                            bottom = 8.dp,
                        ),
                )
            }
        },
    )
}
