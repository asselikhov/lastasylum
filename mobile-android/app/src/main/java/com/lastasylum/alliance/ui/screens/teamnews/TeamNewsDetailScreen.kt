package com.lastasylum.alliance.ui.screens.teamnews

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.ModeEditOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.teams.TeamNewsDetailDto
import com.lastasylum.alliance.data.teams.TeamNewsPollDetailDto
import com.lastasylum.alliance.ui.components.team.JournalFeedVariant
import com.lastasylum.alliance.ui.components.team.PremiumJournalFeedShell
import com.lastasylum.alliance.ui.components.team.PremiumJournalFeedTokens
import com.lastasylum.alliance.ui.util.formatTeamFeedDateRu
import com.lastasylum.alliance.ui.util.sanitizePublicDisplayName

private val detailHeroShape = RoundedCornerShape(20.dp)
private val pageHorizontalPad = 16.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TeamNewsDetailTopBar(
    title: String,
    canEdit: Boolean,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent,
        ),
        title = {
            Text(
                title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.team_news_cd_back),
                )
            }
        },
        actions = {
            if (canEdit) {
                IconButton(onClick = onEdit, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Outlined.ModeEditOutline,
                        contentDescription = stringResource(R.string.team_news_edit),
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.team_news_delete),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
    )
}

@Composable
internal fun TeamNewsDetailScrollContent(
    detail: TeamNewsDetailDto,
    voteBusy: Boolean,
    onVote: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val hero: String? = detail.imageRelativeUrls.firstOrNull() ?: detail.firstImageRelativeUrl
    val heroRequest = remember(hero, context) {
        teamNewsAuthedImageRequest(context, hero)
    }
    val showArticleBody = detail.body.trim().isNotEmpty() || hero != null
    val galleryPaths = remember(detail.imageRelativeUrls, detail.firstImageRelativeUrl) {
        val base = if (detail.imageRelativeUrls.isNotEmpty()) {
            detail.imageRelativeUrls
        } else {
            detail.firstImageRelativeUrl?.let { listOf(it) } ?: emptyList()
        }
        if (base.size <= 1) emptyList() else base.drop(1)
    }
    val poll = detail.poll
    val pollOnly = poll != null && !showArticleBody
    val authorMetaLine = remember(detail.authorUsername, detail.createdAt) {
        "${sanitizePublicDisplayName(detail.authorUsername)} · ${formatTeamFeedDateRu(detail.createdAt)}"
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        heroRequest?.let { imgReq ->
            item(key = "news_detail_hero") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (pollOnly) 200.dp else 220.dp)
                        .padding(horizontal = pageHorizontalPad, vertical = 12.dp)
                        .clip(detailHeroShape)
                        .border(
                            1.dp,
                            Brush.linearGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.22f),
                                    com.lastasylum.alliance.ui.theme.premium.PremiumColors.accentCyan.copy(alpha = 0.18f),
                                ),
                            ),
                            detailHeroShape,
                        ),
                ) {
                    AsyncImage(
                        model = imgReq,
                        contentDescription = detail.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.65f),
                                    ),
                                ),
                            ),
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = detail.title,
                            style = PremiumJournalFeedTokens.titleStyle,
                            color = PremiumJournalFeedTokens.metaOnHero,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = authorMetaLine,
                            style = PremiumJournalFeedTokens.metaStyle,
                            color = PremiumJournalFeedTokens.metaOnHero.copy(alpha = 0.88f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        if (showArticleBody && !pollOnly) {
            item(key = "news_detail_body") {
                PremiumJournalFeedShell(
                    onClick = null,
                    variant = JournalFeedVariant.News,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = pageHorizontalPad, vertical = 8.dp),
                    content = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (hero == null) {
                            Text(
                                detail.title,
                                style = PremiumJournalFeedTokens.headlineStyle,
                            )
                            Text(
                                authorMetaLine,
                                style = PremiumJournalFeedTokens.metaStyle,
                            )
                        }
                        if (detail.body.trim().isNotEmpty()) {
                            Text(
                                detail.body,
                                style = PremiumJournalFeedTokens.excerptStyle.copy(
                                    fontSize = 16.sp,
                                    lineHeight = 24.sp,
                                ),
                            )
                        }
                    }
                    },
                )
            }
        } else if (poll == null && hero == null) {
            item(key = "news_detail_meta") {
                Text(
                    authorMetaLine,
                    style = PremiumJournalFeedTokens.metaStyle,
                    modifier = Modifier.padding(horizontal = pageHorizontalPad, vertical = 8.dp),
                )
            }
        }

        if (galleryPaths.isNotEmpty()) {
            item(key = "news_detail_gallery_title") {
                Text(
                    stringResource(R.string.team_news_gallery_label),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = pageHorizontalPad, vertical = 8.dp),
                )
            }
            item(key = "news_detail_gallery_row") {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(horizontal = pageHorizontalPad),
                ) {
                    items(galleryPaths, key = { it }) { rawPath ->
                        val galleryReq = remember(rawPath, context) {
                            teamNewsAuthedImageRequest(context, rawPath)
                        }
                        galleryReq?.let { imgReq ->
                            AsyncImage(
                                model = imgReq,
                                contentDescription = stringResource(R.string.team_news_gallery_image_cd),
                                modifier = Modifier
                                    .width(152.dp)
                                    .height(108.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .border(
                                        1.dp,
                                        Color.White.copy(alpha = 0.08f),
                                        RoundedCornerShape(14.dp),
                                    ),
                                contentScale = ContentScale.Crop,
                            )
                        }
                    }
                }
            }
        }

        poll?.let { pollDto ->
            item(key = "news_detail_poll_${detail.updatedAt}") {
                TeamNewsDetailPollCard(
                    poll = pollDto,
                    voteBusy = voteBusy,
                    onVote = onVote,
                    pollOnly = pollOnly,
                    modifier = Modifier.padding(
                        horizontal = pageHorizontalPad,
                        vertical = if (showArticleBody) 8.dp else 12.dp,
                    ),
                )
            }
        }
    }
}

@Composable
internal fun TeamNewsDetailPollCard(
    poll: TeamNewsPollDetailDto,
    voteBusy: Boolean,
    onVote: (String) -> Unit,
    pollOnly: Boolean,
    modifier: Modifier = Modifier,
) {
    PremiumJournalFeedShell(
        onClick = null,
        variant = if (pollOnly) JournalFeedVariant.PollOnly else JournalFeedVariant.Poll,
        modifier = modifier.fillMaxWidth(),
        content = {
            TeamNewsPollVoteBlock(
                poll = poll,
                voteBusy = voteBusy,
                onVote = onVote,
            )
        },
    )
}
