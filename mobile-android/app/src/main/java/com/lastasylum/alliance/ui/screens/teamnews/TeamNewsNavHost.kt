package com.lastasylum.alliance.ui.screens.teamnews

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.ModeEditOutline
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import com.lastasylum.alliance.overlay.OverlayAwareAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.lastasylum.alliance.overlay.LocalOverlayUiMode
import com.lastasylum.alliance.overlay.LocalShowOverlayPollVotersSheet
import com.lastasylum.alliance.overlay.OverlayAwareBottomSheet
import com.lastasylum.alliance.overlay.OverlayChatInteractionHold
import com.lastasylum.alliance.overlay.OverlayModalScope
import com.lastasylum.alliance.overlay.OverlayPollVotersRequest
import com.lastasylum.alliance.overlay.OverlayPollVotersSheetHost
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.teams.CreateTeamNewsBody
import com.lastasylum.alliance.data.teams.TeamNewsDetailDto
import com.lastasylum.alliance.data.teams.TeamNewsListItemDto
import com.lastasylum.alliance.data.teams.TeamNewsPollCreateBody
import com.lastasylum.alliance.data.teams.TeamNewsPollOptionDto
import com.lastasylum.alliance.data.teams.TeamNewsPollVoteDto
import com.lastasylum.alliance.data.teams.TeamNewsReadCursorSync
import com.lastasylum.alliance.data.teams.TeamsRepository
import com.lastasylum.alliance.data.teams.UpdateTeamNewsBody
import com.lastasylum.alliance.di.AppContainer
import com.lastasylum.alliance.data.teams.TeamInboxUnread
import com.lastasylum.alliance.overlay.OverlayGameStatusHudRefresh
import com.lastasylum.alliance.ui.util.toUserMessageRu
import com.lastasylum.alliance.ui.util.telegramAvatarUrl
import com.lastasylum.alliance.ui.components.team.TeamNewsFeedCard
import com.lastasylum.alliance.ui.util.formatTeamFeedDateRu
import com.lastasylum.alliance.ui.components.team.TeamPollPreviewBlock
import com.lastasylum.alliance.ui.components.team.TeamPollQuestionHeader
import com.lastasylum.alliance.ui.components.team.TeamPollVoteOptionSurface
import com.lastasylum.alliance.ui.theme.SquadRelayDimens
import com.lastasylum.alliance.ui.theme.SquadRelaySurfaces
import kotlinx.coroutines.launch
import java.time.Instant
private object TeamNewsRoutes {
    const val LIST = "news_list"
    const val CREATE = "news_create"
    fun detail(id: String) = "news_detail/$id"
    fun edit(id: String) = "news_edit/$id"
}

private fun pollVotersForOption(
    votes: List<TeamNewsPollVoteDto>,
    optionId: String,
): List<TeamNewsPollVoteDto> =
    votes.filter { it.optionId == optionId }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TeamNewsPollVotersReveal(
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
                    val avatarUrl = telegramAvatarUrl(vote.telegramUsername)
                    if (avatarUrl != null) {
                        AsyncImage(
                            model = avatarUrl,
                            contentDescription = label,
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = label.take(1).uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TeamNewsPollVoteBlock(
    poll: com.lastasylum.alliance.data.teams.TeamNewsPollDetailDto,
    voteBusy: Boolean,
    onVote: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val totalVotes = poll.tallies.sumOf { it.count }
    var selected by remember(poll.myVoteOptionId, poll.options) {
        mutableStateOf(poll.myVoteOptionId)
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TeamPollQuestionHeader(question = poll.question, totalVotes = totalVotes)
        poll.options.forEach { opt ->
            val cnt = poll.tallies.find { it.optionId == opt.id }?.count ?: 0
            val share = if (totalVotes > 0) cnt.toFloat() / totalVotes else 0f
            val optionVoters = pollVotersForOption(poll.votes, opt.id)
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
        Button(
            onClick = {
                val oid = selected ?: return@Button
                onVote(oid)
            },
            enabled = !voteBusy && selected != null,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
        ) {
            if (voteBusy) {
                CircularProgressIndicator(
                    Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Text(
                    if (poll.myVoteOptionId == null) {
                        stringResource(R.string.team_news_vote)
                    } else {
                        stringResource(R.string.team_news_change_vote)
                    },
                )
            }
        }
    }
}

private enum class TeamNewsEditorMode {
    News,
    PollOnly,
}

@Composable
private fun TeamNewsNavInvalidArgs(onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.team_news_load_error),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
        TextButton(onClick = onBack, modifier = Modifier.padding(top = 12.dp)) {
            Text(stringResource(R.string.team_news_cd_back))
        }
    }
}

@Composable
fun TeamNewsNavHost(
    teamId: String,
    currentUserId: String,
    myTeamRole: String,
    canPublishNews: Boolean,
    teamsRepository: TeamsRepository,
    modifier: Modifier = Modifier,
    onNewsInboxChanged: () -> Unit = {},
) {
    val overlayUi = LocalOverlayUiMode.current
    val nav = rememberNavController()
    val navHost = @Composable {
        NavHost(
            navController = nav,
            startDestination = TeamNewsRoutes.LIST,
            modifier = Modifier.fillMaxSize(),
        ) {
        composable(TeamNewsRoutes.LIST) {
            TeamNewsListRoute(
                teamId = teamId,
                currentUserId = currentUserId,
                myTeamRole = myTeamRole,
                canPublishNews = canPublishNews,
                teamsRepository = teamsRepository,
                onOpenDetail = { nav.navigate(TeamNewsRoutes.detail(it)) },
                onCreate = { nav.navigate(TeamNewsRoutes.CREATE) },
                onEditFromList = { nav.navigate(TeamNewsRoutes.edit(it)) },
            )
        }
        composable(
            route = "news_detail/{newsId}",
            arguments = listOf(navArgument("newsId") { type = NavType.StringType }),
        ) { entry ->
            val id = entry.arguments?.getString("newsId")
            if (id.isNullOrBlank()) {
                TeamNewsNavInvalidArgs(onBack = { nav.popBackStack() })
                return@composable
            }
            TeamNewsDetailRoute(
                teamId = teamId,
                newsId = id,
                currentUserId = currentUserId,
                myTeamRole = myTeamRole,
                teamsRepository = teamsRepository,
                onBack = { nav.popBackStack() },
                onEdit = { nav.navigate(TeamNewsRoutes.edit(it)) },
                onNewsInboxChanged = onNewsInboxChanged,
            )
        }
        composable(TeamNewsRoutes.CREATE) {
            TeamNewsEditorRoute(
                teamId = teamId,
                newsId = null,
                teamsRepository = teamsRepository,
                onBack = { nav.popBackStack() },
                onDone = { nav.popBackStack() },
                onNewsInboxChanged = onNewsInboxChanged,
            )
        }
        composable(
            route = "news_edit/{newsId}",
            arguments = listOf(navArgument("newsId") { type = NavType.StringType }),
        ) { entry ->
            val id = entry.arguments?.getString("newsId")
            if (id.isNullOrBlank()) {
                TeamNewsNavInvalidArgs(onBack = { nav.popBackStack() })
                return@composable
            }
            TeamNewsEditorRoute(
                teamId = teamId,
                newsId = id,
                teamsRepository = teamsRepository,
                onBack = { nav.popBackStack() },
                onDone = { nav.popBackStack() },
            )
        }
        }
    }
    if (overlayUi) {
        OverlayPollVotersSheetHost(modifier = modifier) {
            navHost()
        }
    } else {
        Box(modifier = modifier) {
            navHost()
        }
    }
}

@Composable
private fun TeamNewsListRoute(
    teamId: String,
    currentUserId: String,
    myTeamRole: String,
    canPublishNews: Boolean,
    teamsRepository: TeamsRepository,
    onOpenDetail: (String) -> Unit,
    onCreate: () -> Unit,
    onEditFromList: (String) -> Unit,
) {
    val context = LocalContext.current
    val res = context.resources
    val overlayUi = LocalOverlayUiMode.current
    val scope = rememberCoroutineScope()
    val newsPrefs = remember { AppContainer.from(context).userSettingsPreferences }
    val launchDiskCache = remember { AppContainer.from(context).launchDiskCache }
    val cachedPage = remember(teamId, currentUserId) {
        if (currentUserId.isNotBlank()) {
            launchDiskCache.loadTeamNews(currentUserId, teamId)
        } else {
            null
        }
    }
    var loading by remember { mutableStateOf(cachedPage == null) }
    var newsItems by remember { mutableStateOf(cachedPage?.items ?: emptyList()) }
    var newsNextCursor by remember { mutableStateOf(cachedPage?.nextCursor) }
    var loadingMore by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }

    suspend fun loadNewsPage(cursor: String?, append: Boolean) {
        if (append) loadingMore = true else loading = true
        if (!append) loadError = null
        teamsRepository.listTeamNews(teamId, cursor, limit = 40)
            .onSuccess { page ->
                newsItems = if (append) newsItems + page.items else page.items
                newsNextCursor = page.nextCursor
                if (!append && currentUserId.isNotBlank()) {
                    launchDiskCache.saveTeamNews(currentUserId, teamId, page)
                }
                loading = false
                loadingMore = false
            }
            .onFailure { e ->
                if (!append) loadError = e.toUserMessageRu(res)
                loading = false
                loadingMore = false
            }
    }

    LaunchedEffect(teamId) {
        loadNewsPage(cursor = null, append = false)
    }

    val unreadNewsIds = remember(newsItems, currentUserId) {
        val currentUser = currentUserId.trim()
        val lastSeen = newsPrefs.getLastSeenTeamNewsCreatedAt()
            ?.let { runCatching { Instant.parse(it) }.getOrNull() }
        if (lastSeen == null) {
            newsItems.asSequence()
                .filterNot { item -> currentUser.isNotBlank() && item.authorUserId.trim() == currentUser }
                .map { it.id }
                .toSet()
        } else {
            newsItems.asSequence()
                .filterNot { item -> currentUser.isNotBlank() && item.authorUserId.trim() == currentUser }
                .filter { item ->
                    runCatching { Instant.parse(item.createdAt) }.getOrNull()?.isAfter(lastSeen) == true
                }
                .map { it.id }
                .toSet()
        }
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            when {
                loading && newsItems.isEmpty() -> {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
                loadError != null && newsItems.isEmpty() -> {
                    Column(
                        Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(SquadRelayDimens.itemGap),
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
                        ) {
                            Text(
                                text = loadError ?: "",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                scope.launch { loadNewsPage(cursor = null, append = false) }
                            },
                        ) {
                            Text(stringResource(R.string.team_news_retry))
                        }
                    }
                }
                newsItems.isEmpty() -> {
                    Text(
                        text = stringResource(R.string.team_news_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                else -> {
                    val listTopPad = if (overlayUi) 0.dp else 8.dp
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = SquadRelayDimens.contentPaddingHorizontal,
                            end = SquadRelayDimens.contentPaddingHorizontal,
                            top = listTopPad,
                            bottom = 88.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        items(
                            items = newsItems,
                            key = { it.id },
                        ) { item ->
                            TeamNewsFeedCard(
                                item = item,
                                isUnread = item.id in unreadNewsIds,
                                onClick = { onOpenDetail(item.id) },
                                onEdit = {
                                    val isAuthor = item.authorUserId == currentUserId
                                    val isOfficer =
                                        myTeamRole == "R4" || myTeamRole == "R5"
                                    if (isAuthor || isOfficer) onEditFromList(item.id)
                                },
                                showEdit = item.authorUserId == currentUserId ||
                                    myTeamRole == "R4" || myTeamRole == "R5",
                            )
                        }
                        if (!newsNextCursor.isNullOrBlank()) {
                            item {
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            loadNewsPage(newsNextCursor, append = true)
                                        }
                                    },
                                    enabled = !loadingMore,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        if (loadingMore) {
                                            stringResource(R.string.team_news_loading_more)
                                        } else {
                                            stringResource(R.string.team_news_load_more)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (canPublishNews) {
                FloatingActionButton(
                    onClick = onCreate,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.team_news_new))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TeamNewsDetailRoute(
    teamId: String,
    newsId: String,
    currentUserId: String,
    myTeamRole: String,
    teamsRepository: TeamsRepository,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onNewsInboxChanged: () -> Unit = {},
) {
    val context = LocalContext.current
    val res = context.resources
    val overlayUi = LocalOverlayUiMode.current
    var detail by remember { mutableStateOf<TeamNewsDetailDto?>(null) }
    var loading by remember { mutableStateOf(true) }
    var err by remember { mutableStateOf<String?>(null) }
    var voteBusy by remember { mutableStateOf(false) }
    var deleteOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(teamId, newsId) {
        loading = true
        err = null
        teamsRepository.getTeamNews(teamId, newsId)
            .onSuccess { doc ->
                detail = doc
                val app = AppContainer.from(context)
                TeamNewsReadCursorSync.markNewsSeen(
                    app.teamsRepository,
                    app.userSettingsPreferences,
                    teamId,
                    doc.createdAt,
                )
                onNewsInboxChanged()
                loading = false
            }
            .onFailure { e ->
                err = e.toUserMessageRu(res)
                loading = false
            }
    }

    val d = detail
    val canEdit = d != null && (
        d.authorUserId == currentUserId ||
            myTeamRole == "R4" || myTeamRole == "R5"
        )
    val poll = d?.poll

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                ),
                title = {
                    Text(
                        d?.poll?.question?.takeIf { it.isNotBlank() }
                            ?: d?.title
                            ?: "",
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.team_news_cd_back),
                        )
                    }
                },
                actions = {
                    if (canEdit && d != null) {
                        TextButton(onClick = { onEdit(d.id) }) {
                            Text(stringResource(R.string.team_news_edit))
                        }
                        IconButton(onClick = {
                            OverlayChatInteractionHold.prepareOverlayModalInteraction(overlayUi)
                            deleteOpen = true
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.team_news_delete))
                        }
                    }
                },
            )
        },
    ) { pad ->
        when {
            loading && d == null -> {
                Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            err != null && d == null -> {
                Text(err ?: "", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(pad))
            }
            d != null -> {
                Column(
                    Modifier
                        .padding(pad)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                ) {
                    val pagePad = 14.dp
                    val hero: String? = d.imageRelativeUrls.firstOrNull() ?: d.firstImageRelativeUrl
                    val heroRequest = remember(hero, context) {
                        teamNewsAuthedImageRequest(context, hero)
                    }
                    heroRequest?.let { imgReq: ImageRequest ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(248.dp)
                                    .padding(horizontal = pagePad, vertical = 16.dp)
                                    .clip(RoundedCornerShape(26.dp))
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                                        RoundedCornerShape(26.dp),
                                    ),
                            ) {
                                AsyncImage(
                                    model = imgReq,
                                    contentDescription = d.title,
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
                                                    Color.Black.copy(alpha = 0.58f),
                                                ),
                                            ),
                                        ),
                                )
                                Column(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        text = d.title,
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = "${d.authorUsername} · ${formatTeamFeedDateRu(d.createdAt)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.White.copy(alpha = 0.86f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                    }

                    val showArticleBody = d.body.trim().isNotEmpty() || hero != null
                    if (showArticleBody) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = pagePad)
                                .padding(bottom = 16.dp)
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                                    RoundedCornerShape(26.dp),
                                ),
                            shape = RoundedCornerShape(26.dp),
                        colors = SquadRelaySurfaces.cardColors(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        ) {
                            Row(Modifier.height(IntrinsicSize.Min)) {
                                Box(
                                    modifier = Modifier
                                        .width(5.dp)
                                        .fillMaxHeight()
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(
                                                    MaterialTheme.colorScheme.primary,
                                                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.85f),
                                                ),
                                            ),
                                        ),
                                )
                                Column(
                                    Modifier
                                        .weight(1f)
                                        .padding(horizontal = 18.dp, vertical = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    if (hero == null) {
                                        Text(
                                            d.title,
                                            style = MaterialTheme.typography.headlineSmall,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Text(
                                            "${d.authorUsername} · ${formatTeamFeedDateRu(d.createdAt)}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    if (d.body.trim().isNotEmpty()) {
                                        Text(
                                            d.body,
                                            style = MaterialTheme.typography.bodyLarge,
                                            lineHeight = 24.sp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                }
                            }
                        }
                    } else if (poll == null) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = pagePad, vertical = 8.dp),
                        ) {
                            Text(
                                "${d.authorUsername} · ${formatTeamFeedDateRu(d.createdAt)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    val galleryPaths = remember(d.imageRelativeUrls, d.firstImageRelativeUrl) {
                        val base = if (d.imageRelativeUrls.isNotEmpty()) {
                            d.imageRelativeUrls
                        } else {
                            d.firstImageRelativeUrl?.let { listOf(it) } ?: emptyList()
                        }
                        if (base.size <= 1) emptyList() else base.drop(1)
                    }
                    if (galleryPaths.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.team_news_gallery_label),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = pagePad),
                        )
                        Spacer(Modifier.height(8.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(horizontal = pagePad, vertical = 2.dp),
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
                                            .width(168.dp)
                                            .height(120.dp)
                                            .clip(RoundedCornerShape(12.dp)),
                                        contentScale = ContentScale.Crop,
                                    )
                                }
                            }
                        }
                    }
                    if (poll != null) {
                        Spacer(Modifier.height(if (showArticleBody) 8.dp else 16.dp))
                        key(d.updatedAt) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = pagePad)
                                    .padding(bottom = 20.dp),
                                shape = RoundedCornerShape(22.dp),
                        colors = SquadRelaySurfaces.cardColors(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                                border = BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f),
                                ),
                            ) {
                                TeamNewsPollVoteBlock(
                                    poll = poll,
                                    voteBusy = voteBusy,
                                    onVote = { oid ->
                                        voteBusy = true
                                        scope.launch {
                                            teamsRepository.voteTeamNews(teamId, newsId, oid)
                                                .onSuccess { voted -> detail = voted }
                                                .onFailure { e -> err = e.toUserMessageRu(res) }
                                            voteBusy = false
                                        }
                                    },
                                    modifier = Modifier.padding(18.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (deleteOpen && d != null) {
        OverlayModalScope(preparedByCaller = true) {
        OverlayAwareAlertDialog(
            onDismissRequest = { deleteOpen = false },
            title = { Text(stringResource(R.string.team_news_delete)) },
            text = { Text(stringResource(R.string.team_news_delete_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            teamsRepository.deleteTeamNews(teamId, d.id)
                                .onSuccess { deleteOpen = false; onBack() }
                                .onFailure { e -> err = e.toUserMessageRu(res) }
                        }
                    },
                ) {
                    Text(stringResource(R.string.team_news_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteOpen = false }) {
                    Text(stringResource(R.string.profile_action_cancel))
                }
            },
        )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TeamNewsEditorRoute(
    teamId: String,
    newsId: String?,
    teamsRepository: TeamsRepository,
    onBack: () -> Unit,
    onDone: () -> Unit,
    onNewsInboxChanged: () -> Unit = {},
) {
    val context = LocalContext.current
    val res = context.resources
    val scope = rememberCoroutineScope()
    var editorMode by remember { mutableStateOf(TeamNewsEditorMode.News) }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    val imageIds = remember { mutableStateListOf<String>() }
    var includePoll by remember { mutableStateOf(false) }
    var pollQuestion by remember { mutableStateOf("") }
    val pollOptions = remember { mutableStateListOf("", "") }
    val pollOptionIds = remember { mutableStateListOf<String?>(null, null) }
    var loading by remember { mutableStateOf(newsId != null) }
    var saving by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(teamId, newsId) {
        val nid = newsId ?: return@LaunchedEffect
        loading = true
        teamsRepository.getTeamNews(teamId, nid)
            .onSuccess { d ->
                title = d.title
                body = d.body
                imageIds.clear()
                d.imageRelativeUrls.forEach { rel ->
                    val seg = rel.substringAfterLast('/')
                    if (seg.isNotBlank()) imageIds.add(seg)
                }
                val p = d.poll
                if (p != null) {
                    includePoll = true
                    pollQuestion = p.question
                    pollOptions.clear()
                    pollOptionIds.clear()
                    p.options.forEach { opt ->
                        pollOptions.add(opt.text)
                        pollOptionIds.add(opt.id)
                    }
                    val pollOnlyPost =
                        d.pollOnly ||
                            (d.body.trim().isEmpty() && d.imageRelativeUrls.isEmpty())
                    if (pollOnlyPost) {
                        editorMode = TeamNewsEditorMode.PollOnly
                    }
                }
                loading = false
            }
            .onFailure { e ->
                err = e.toUserMessageRu(res)
                loading = false
            }
    }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val cr = context.contentResolver
            val mime = cr.getType(uri) ?: "image/jpeg"
            val bytes = cr.openInputStream(uri)?.use { it.readBytes() }
                ?: run {
                    err = res.getString(R.string.chat_attachment_read_failed)
                    return@launch
                }
            val name = "upload_${System.currentTimeMillis()}.jpg"
            teamsRepository.uploadTeamNewsImage(teamId, bytes, name, mime)
                .onSuccess { uploaded -> imageIds.add(uploaded.fileId) }
                .onFailure { e -> err = e.toUserMessageRu(res) }
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                ),
                title = {
                    Text(
                        if (newsId == null) stringResource(R.string.team_news_new)
                        else stringResource(R.string.team_news_edit),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !saving) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.team_news_cd_back),
                        )
                    }
                },
            )
        },
    ) { pad ->
        if (loading) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            err?.let { msg ->
                Text(msg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            if (newsId == null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TeamNewsEditorMode.entries.forEach { mode ->
                        val selected = editorMode == mode
                        val label = when (mode) {
                            TeamNewsEditorMode.News -> stringResource(R.string.team_news_editor_mode_news)
                            TeamNewsEditorMode.PollOnly -> stringResource(R.string.team_news_editor_mode_poll)
                        }
                        Surface(
                            onClick = {
                                editorMode = mode
                                if (mode == TeamNewsEditorMode.PollOnly) {
                                    includePoll = true
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                SquadRelaySurfaces.subtleColor(0.45f)
                            },
                            border = if (selected) {
                                null
                            } else {
                                BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                                )
                            },
                        ) {
                            Text(
                                text = label,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }
            }
            if (editorMode == TeamNewsEditorMode.PollOnly) {
                Text(
                    text = stringResource(R.string.team_news_poll_only_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (editorMode == TeamNewsEditorMode.News) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { v -> title = v.take(200) },
                    label = { Text(stringResource(R.string.team_news_title_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = body,
                    onValueChange = { v -> body = v },
                    label = { Text(stringResource(R.string.team_news_body_hint)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                )
                OutlinedButton(onClick = { pickImage.launch("image/*") }) {
                    Text(stringResource(R.string.team_news_pick_image))
                }
                imageIds.forEachIndexed { idx, id ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("• $id", maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        TextButton(onClick = { imageIds.removeAt(idx) }) { Text("×") }
                    }
                }
                if (includePoll) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { includePoll = false }) {
                            Text(stringResource(R.string.team_news_remove_poll))
                        }
                    }
                } else if (newsId == null || editorMode == TeamNewsEditorMode.News) {
                    TextButton(onClick = { includePoll = true }) {
                        Text(stringResource(R.string.team_news_editor_mode_poll))
                    }
                }
            }
            if (editorMode == TeamNewsEditorMode.PollOnly || includePoll) {
                OutlinedTextField(
                    value = pollQuestion,
                    onValueChange = { v -> pollQuestion = v.take(500) },
                    label = { Text(stringResource(R.string.team_news_poll_question_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                pollOptions.forEachIndexed { i, v ->
                    OutlinedTextField(
                        value = v,
                        onValueChange = { nv -> pollOptions[i] = nv.take(200) },
                        label = {
                            Text(stringResource(R.string.team_news_poll_option_hint, i + 1))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (pollOptions.size < 8) {
                    TextButton(
                        onClick = {
                            pollOptions.add("")
                            pollOptionIds.add(null)
                        },
                    ) {
                        Text(stringResource(R.string.team_news_add_option))
                    }
                }
            }
            Button(
                onClick = {
                    val pollOnly = editorMode == TeamNewsEditorMode.PollOnly
                    val wantsPoll = pollOnly || includePoll
                    if (wantsPoll) {
                        val filled = pollOptions.count { o -> o.isNotBlank() }
                        if (pollQuestion.isBlank() || filled < 2) {
                            err = res.getString(R.string.team_news_poll_invalid)
                            return@Button
                        }
                    }
                    if (!pollOnly && (title.isBlank() || body.isBlank())) {
                        err = res.getString(R.string.team_news_fill_required)
                        return@Button
                    }
                    err = null
                    saving = true
                    scope.launch {
                        val pollBody =
                            if (wantsPoll) {
                                val optionPairs = pollOptions.indices.mapNotNull { i ->
                                    val text = pollOptions[i].trim()
                                    if (text.isEmpty()) null else text to pollOptionIds.getOrNull(i)
                                }
                                TeamNewsPollCreateBody(
                                    question = pollQuestion.trim(),
                                    optionTexts = optionPairs.map { it.first },
                                    optionIds = if (newsId != null) {
                                        optionPairs.map { (_, id) -> id.orEmpty() }
                                    } else {
                                        null
                                    },
                                )
                            } else {
                                null
                            }
                        val publishTitle =
                            if (pollOnly) null else title.trim().takeIf { it.isNotEmpty() }
                        val publishBody =
                            if (pollOnly) null else body.trim().takeIf { it.isNotEmpty() }
                        val publishImages = if (pollOnly) emptyList() else imageIds.toList()
                        if (newsId == null) {
                            teamsRepository.createTeamNews(
                                teamId,
                                CreateTeamNewsBody(
                                    title = publishTitle,
                                    body = publishBody,
                                    imageFileIds = publishImages,
                                    poll = pollBody,
                                ),
                            )
                                .onSuccess { created ->
                                    val app = AppContainer.from(context)
                                    TeamNewsReadCursorSync.markNewsSeen(
                                        app.teamsRepository,
                                        app.userSettingsPreferences,
                                        teamId,
                                        created.createdAt,
                                    )
                                    onNewsInboxChanged()
                                    saving = false
                                    onDone()
                                }
                                .onFailure { e ->
                                    err = e.toUserMessageRu(res)
                                    saving = false
                                }
                        } else {
                            teamsRepository.updateTeamNews(
                                teamId,
                                newsId,
                                UpdateTeamNewsBody(
                                    title = if (pollOnly) null else title.trim().ifBlank { null },
                                    body = if (pollOnly) null else body.trim(),
                                    imageFileIds = if (pollOnly) emptyList() else imageIds.toList(),
                                    poll = if (wantsPoll) pollBody else null,
                                    clearPoll = if (!wantsPoll) true else null,
                                ),
                            )
                                .onSuccess { saving = false; onDone() }
                                .onFailure { e ->
                                    err = e.toUserMessageRu(res)
                                    saving = false
                                }
                        }
                    }
                },
                enabled = !saving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (saving) {
                    CircularProgressIndicator(
                        Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(stringResource(R.string.team_news_save))
                }
            }
        }
    }
}
