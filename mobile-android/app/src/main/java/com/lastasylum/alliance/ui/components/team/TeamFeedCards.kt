package com.lastasylum.alliance.ui.components.team

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Forum
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.lastasylum.alliance.R
import com.lastasylum.alliance.ui.chat.ChatSenderAvatar
import com.lastasylum.alliance.ui.util.telegramAvatarUrl
import com.lastasylum.alliance.data.teams.TeamForumTopicDto
import com.lastasylum.alliance.data.teams.TeamNewsListItemDto
import com.lastasylum.alliance.data.teams.TeamNewsPollOptionDto
import com.lastasylum.alliance.data.teams.TeamNewsPollTallyDto
import com.lastasylum.alliance.ui.screens.teamnews.teamNewsAuthedImageRequest
import com.lastasylum.alliance.ui.theme.SquadRelayAtmosphericPurple
import com.lastasylum.alliance.ui.theme.SquadRelayAtmosphericSky
import com.lastasylum.alliance.ui.theme.SquadRelayPrimary
import com.lastasylum.alliance.ui.theme.SquadRelaySecondary
import com.lastasylum.alliance.ui.theme.SquadRelaySurfaces
import com.lastasylum.alliance.ui.util.formatTeamFeedDateRu
import kotlin.math.roundToInt

private val cardShape = RoundedCornerShape(22.dp)
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
    val barColors = if (selected) {
        listOf(SquadRelayPrimary, SquadRelayAtmosphericSky)
    } else {
        listOf(
            scheme.primary.copy(alpha = 0.55f),
            scheme.tertiary.copy(alpha = 0.45f),
        )
    }
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
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(scheme.surfaceVariant.copy(alpha = 0.45f)),
        ) {
            if (share > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(share.coerceIn(0.02f, 1f))
                        .background(Brush.horizontalGradient(barColors)),
                )
            }
        }
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

@Composable
fun TeamPollPreviewBlock(
    question: String,
    options: List<TeamNewsPollOptionDto>,
    tallies: List<TeamNewsPollTallyDto>,
    myVoteOptionId: String?,
    modifier: Modifier = Modifier,
) {
    val totalVotes = tallies.sumOf { it.count }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
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
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 24.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            options.forEach { opt ->
                val cnt = tallies.find { it.optionId == opt.id }?.count ?: 0
                val share = if (totalVotes > 0) cnt.toFloat() / totalVotes else 0f
                Surface(
                    shape = innerShape,
                    color = if (myVoteOptionId == opt.id) {
                        SquadRelayPrimary.copy(alpha = 0.10f)
                    } else {
                        SquadRelaySurfaces.subtleColor(0.32f)
                    },
                    border = BorderStroke(
                        1.dp,
                        if (myVoteOptionId == opt.id) {
                            SquadRelayPrimary.copy(alpha = 0.42f)
                        } else {
                            Color.White.copy(alpha = 0.06f)
                        },
                    ),
                ) {
                    PollResultOptionRow(
                        text = opt.text,
                        voteCount = cnt,
                        share = share,
                        selected = myVoteOptionId == opt.id,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    )
                }
            }
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
private fun AuthorChip(username: String, telegramUsername: String? = null) {
    val avatarUrl = telegramAvatarUrl(telegramUsername)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (avatarUrl != null) {
            ChatSenderAvatar(
                telegramUrl = avatarUrl,
                size = 28.dp,
                fallbackName = username,
            )
        } else {
            val initial = username.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(SquadRelayPrimary.copy(0.85f), SquadRelaySecondary.copy(0.75f)),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = initial,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
        }
        Text(
            text = username,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
    val hasHero = !item.firstImageRelativeUrl.isNullOrBlank()
    val pollQuestion = item.pollQuestion?.trim().orEmpty()
    val pollOptions = item.pollOptions.orEmpty()
    val showPollPreview = item.hasPoll && pollQuestion.isNotEmpty() && pollOptions.size >= 2
    val pollOnly = item.pollOnly

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = if (isUnread) 1.5.dp else 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (isUnread) 0.2f else 0.12f),
                        if (isUnread) {
                            Color(0xFFFF5252).copy(alpha = 0.45f)
                        } else {
                            SquadRelayPrimary.copy(alpha = 0.22f)
                        },
                        Color.White.copy(alpha = 0.06f),
                    ),
                ),
                shape = cardShape,
            ),
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = SquadRelaySurfaces.panelColor(0.56f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp, pressedElevation = 2.dp),
    ) {
        Column {
            if (showPollPreview && !hasHero) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    SquadRelayPrimary.copy(alpha = 0.28f),
                                    SquadRelaySecondary.copy(alpha = 0.14f),
                                    Color.Transparent,
                                ),
                            ),
                        ),
                ) {
                    Row(
                        Modifier
                            .align(Alignment.CenterStart)
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Poll,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(22.dp),
                        )
                        Text(
                            text = if (pollOnly) {
                                stringResource(R.string.team_news_poll_badge)
                            } else {
                                stringResource(R.string.team_news_poll_badge)
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.92f),
                        )
                    }
                }
            }

            if (hasHero) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(172.dp),
                ) {
                    item.firstImageRelativeUrl?.let { raw ->
                        teamNewsAuthedImageRequest(LocalContext.current, raw)?.let { req ->
                            AsyncImage(
                                model = req,
                                contentDescription = item.title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.55f),
                                        Color.Black.copy(alpha = 0.78f),
                                    ),
                                ),
                            ),
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (isUnread) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0xFFFF5252),
                                ) {
                                    Text(
                                        text = stringResource(R.string.team_news_unread_badge),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                    )
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color.Black.copy(alpha = 0.38f),
                                border = glassBorder(0.12f),
                            ) {
                                Text(
                                    text = formatTeamFeedDateRu(item.createdAt),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.9f),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
                            if (item.hasPoll) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = SquadRelayPrimary.copy(alpha = 0.45f),
                                ) {
                                    Text(
                                        text = stringResource(R.string.team_news_poll_badge),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    )
                                }
                            }
                        }
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                HorizontalDivider(
                    thickness = 1.dp,
                    color = Color.White.copy(alpha = 0.06f),
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (showPollPreview) {
                    TeamPollPreviewBlock(
                        question = pollQuestion,
                        options = pollOptions,
                        tallies = item.pollTallies,
                        myVoteOptionId = item.myVoteOptionId,
                    )
                } else {
                    if (!hasHero) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 26.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        AuthorChip(item.authorUsername, item.authorTelegramUsername)
                        if (isUnread && !hasHero) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFFFF5252),
                            ) {
                                Text(
                                    text = stringResource(R.string.team_news_unread_badge),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                )
                            }
                        }
                        if (!hasHero) {
                            Text(
                                text = formatTeamFeedDateRu(item.createdAt),
                                style = MaterialTheme.typography.labelSmall,
                                color = scheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
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
        }
    }
}

private data class ForumAccent(val start: Color, val end: Color)

private fun forumAccentForIndex(index: Int): ForumAccent {
    val palette = listOf(
        ForumAccent(SquadRelayPrimary, SquadRelayAtmosphericSky),
        ForumAccent(SquadRelaySecondary, Color(0xFF818CF8)),
        ForumAccent(Color(0xFF34D399), SquadRelaySecondary),
        ForumAccent(Color(0xFFF472B6), SquadRelayPrimary),
    )
    return palette[index % palette.size]
}

@Composable
fun ForumTopicFeedCard(
    topic: TeamForumTopicDto,
    listIndex: Int,
    messageMeta: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    menu: @Composable () -> Unit = {},
) {
    val accent = forumAccentForIndex(listIndex)
    val scheme = MaterialTheme.colorScheme
    val creatorAvatarUrl = telegramAvatarUrl(topic.createdByTelegramUsername)

    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = cardShape,
        color = SquadRelaySurfaces.panelColor(0.54f),
        border = glassBorder(0.10f),
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (creatorAvatarUrl != null) {
                ChatSenderAvatar(
                    telegramUrl = creatorAvatarUrl,
                    size = 48.dp,
                    fallbackName = topic.title,
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Brush.linearGradient(listOf(accent.start, accent.end))),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Forum,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.95f),
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = topic.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 22.sp,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (topic.messageCount > 0) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = accent.start.copy(alpha = 0.16f),
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.team_forum_messages_pill,
                                    topic.messageCount,
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = accent.start.copy(alpha = 0.95f),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                    if (topic.unreadCount > 0) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFFF5252),
                        ) {
                            Text(
                                text = if (topic.unreadCount > 99) {
                                    "99+"
                                } else {
                                    topic.unreadCount.toString()
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            )
                        }
                    }
                    Text(
                        text = messageMeta,
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                menu()
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
