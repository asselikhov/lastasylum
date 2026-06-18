package com.lastasylum.alliance.ui.screens.teamnews

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.ModeEditOutline
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.teams.TeamNewsDetailDto
import com.lastasylum.alliance.data.teams.TeamNewsPollDetailDto
import com.lastasylum.alliance.ui.components.premium.FeedCardHero
import com.lastasylum.alliance.ui.components.team.JournalFeedVariant
import com.lastasylum.alliance.ui.components.team.PremiumJournalFeedShell
import com.lastasylum.alliance.ui.components.team.PremiumJournalFeedTokens
import com.lastasylum.alliance.ui.util.formatTeamFeedDateRu
import com.lastasylum.alliance.ui.util.sanitizePublicDisplayName

private val pageHorizontalPad = 16.dp
private val galleryThumbShape = RoundedCornerShape(14.dp)
private val galleryThumbSize = 72.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TeamNewsDetailTopBar(
    title: String,
    canEdit: Boolean,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent,
        ),
        title = {
            Text(
                title,
                maxLines = 1,
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
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.team_news_edit),
                    )
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.team_news_edit)) },
                        onClick = {
                            menuOpen = false
                            onEdit()
                        },
                        leadingIcon = {
                            Icon(Icons.Outlined.ModeEditOutline, contentDescription = null)
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(R.string.team_news_delete),
                                color = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
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
        if (heroRequest != null && hero != null) {
            item(key = "news_detail_hero") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = pageHorizontalPad, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FeedCardHero(
                        imageRequest = heroRequest,
                        title = detail.title,
                        contentDescription = detail.title,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp)),
                    )
                    Text(
                        text = authorMetaLine,
                        style = PremiumJournalFeedTokens.metaStyle,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
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
            item(key = "news_detail_gallery_row") {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(horizontal = pageHorizontalPad, vertical = 8.dp),
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
                                    .size(galleryThumbSize)
                                    .clip(galleryThumbShape)
                                    .border(
                                        1.dp,
                                        Color.White.copy(alpha = 0.08f),
                                        galleryThumbShape,
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
