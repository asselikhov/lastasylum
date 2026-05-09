package com.lastasylum.alliance.ui.screens.teamnews

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
import com.lastasylum.alliance.data.teams.TeamsRepository
import com.lastasylum.alliance.data.teams.UpdateTeamNewsBody
import com.lastasylum.alliance.ui.util.toUserMessageRu
import com.lastasylum.alliance.ui.theme.SquadRelayDimens
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private object TeamNewsRoutes {
    const val LIST = "news_list"
    const val CREATE = "news_create"
    fun detail(id: String) = "news_detail/$id"
    fun edit(id: String) = "news_edit/$id"
}

private fun formatNewsDateRu(iso: String): String =
    runCatching {
        val instant = Instant.parse(iso)
        val fmt = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", java.util.Locale("ru"))
        fmt.format(instant.atZone(ZoneId.systemDefault()))
    }.getOrElse { iso }

private fun pollVotersSummaryLine(
    votes: List<TeamNewsPollVoteDto>,
    optionId: String,
    maxNames: Int = 18,
): String? {
    val names = votes.filter { it.optionId == optionId }
        .map { v ->
            val u = v.username?.trim().orEmpty()
            if (u.isNotEmpty() && u != "—") u else v.userId
        }
        .distinct()
    if (names.isEmpty()) return null
    return if (names.size <= maxNames) {
        names.joinToString(", ")
    } else {
        names.take(maxNames).joinToString(", ") + "…"
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
) {
    val nav = rememberNavController()
    NavHost(
        navController = nav,
        startDestination = TeamNewsRoutes.LIST,
        modifier = modifier,
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
            val id = entry.arguments?.getString("newsId") ?: return@composable
            TeamNewsDetailRoute(
                teamId = teamId,
                newsId = id,
                currentUserId = currentUserId,
                myTeamRole = myTeamRole,
                teamsRepository = teamsRepository,
                onBack = { nav.popBackStack() },
                onEdit = { nav.navigate(TeamNewsRoutes.edit(it)) },
            )
        }
        composable(TeamNewsRoutes.CREATE) {
            TeamNewsEditorRoute(
                teamId = teamId,
                newsId = null,
                teamsRepository = teamsRepository,
                onBack = { nav.popBackStack() },
                onDone = { nav.popBackStack() },
            )
        }
        composable(
            route = "news_edit/{newsId}",
            arguments = listOf(navArgument("newsId") { type = NavType.StringType }),
        ) { entry ->
            val id = entry.arguments?.getString("newsId") ?: return@composable
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
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var newsItems by remember { mutableStateOf<List<TeamNewsListItemDto>>(emptyList()) }
    var loadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(teamId) {
        loading = true
        loadError = null
        teamsRepository.listTeamNews(teamId, null, limit = 40)
            .onSuccess { page ->
                newsItems = page.items
                loading = false
            }
            .onFailure { e ->
                loadError = e.toUserMessageRu(res)
                loading = false
            }
    }

    Scaffold(
        floatingActionButton = {
            if (canPublishNews) {
                FloatingActionButton(
                    onClick = onCreate,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.team_news_new))
                }
            }
        },
    ) { pad ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(pad),
        ) {
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
                                scope.launch {
                                    loading = true
                                    loadError = null
                                    teamsRepository.listTeamNews(teamId, null, limit = 40)
                                        .onSuccess { page ->
                                            newsItems = page.items
                                            loading = false
                                        }
                                        .onFailure { e ->
                                            loadError = e.toUserMessageRu(res)
                                            loading = false
                                        }
                                }
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
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 88.dp, top = 2.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(
                            count = newsItems.size,
                            key = { index -> newsItems[index].id },
                        ) { index ->
                            val row = newsItems[index]
                            TeamNewsCard(
                                item = row,
                                onClick = { onOpenDetail(row.id) },
                                onEdit = {
                                    val isAuthor = row.authorUserId == currentUserId
                                    val isR5 = myTeamRole == "R5"
                                    if (isAuthor || isR5) onEditFromList(row.id)
                                },
                                showEdit = row.authorUserId == currentUserId || myTeamRole == "R5",
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TeamNewsCard(
    item: TeamNewsListItemDto,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    showEdit: Boolean,
) {
    val shape = RoundedCornerShape(20.dp)
    val hasHero = !item.firstImageRelativeUrl.isNullOrBlank()
    val baseBg = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f)
    val ring = MaterialTheme.colorScheme.primary.copy(alpha = 0.42f)
    val ringSoft = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        border = BorderStroke(1.5.dp, ring),
        colors = CardDefaults.outlinedCardColors(containerColor = baseBg),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            if (hasHero) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(172.dp)
                        .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)),
                ) {
                    item.firstImageRelativeUrl?.let { raw ->
                        teamNewsAuthedImageRequest(LocalContext.current, raw)?.let { req ->
                            AsyncImage(
                                model = req,
                                contentDescription = null,
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
                                        Color.Black.copy(alpha = 0.62f),
                                    ),
                                    startY = 40f,
                                ),
                            ),
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            AssistChip(
                                onClick = {},
                                enabled = false,
                                label = {
                                    Text(
                                        text = formatNewsDateRu(item.createdAt),
                                        maxLines = 1,
                                    )
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = Color.Black.copy(alpha = 0.42f),
                                    labelColor = Color.White.copy(alpha = 0.92f),
                                    disabledContainerColor = Color.Black.copy(alpha = 0.42f),
                                    disabledLabelColor = Color.White.copy(alpha = 0.92f),
                                ),
                                border = AssistChipDefaults.assistChipBorder(
                                    enabled = false,
                                    borderColor = Color.White.copy(alpha = 0.14f),
                                ),
                            )
                            if (item.hasPoll) {
                                AssistChip(
                                    onClick = {},
                                    enabled = false,
                                    label = { Text(stringResource(R.string.team_news_poll_badge), maxLines = 1) },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = Color.Black.copy(alpha = 0.42f),
                                        labelColor = Color.White.copy(alpha = 0.92f),
                                        disabledContainerColor = Color.Black.copy(alpha = 0.42f),
                                        disabledLabelColor = Color.White.copy(alpha = 0.92f),
                                    ),
                                    border = AssistChipDefaults.assistChipBorder(
                                        enabled = false,
                                        borderColor = Color.White.copy(alpha = 0.14f),
                                    ),
                                )
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
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f),
                )
            }
            Surface(
                color = if (hasHero) {
                    lerp(baseBg, MaterialTheme.colorScheme.surfaceContainerLow, 0.35f)
                } else {
                    baseBg
                },
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 15.dp, vertical = 13.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (!hasHero) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = item.excerpt,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (hasHero) 2 else 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.85f),
                                border = BorderStroke(1.dp, ringSoft),
                            ) {
                                Text(
                                    text = item.authorUsername,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                )
                            }
                            if (!hasHero) {
                                Text(
                                    text = formatNewsDateRu(item.createdAt),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        if (showEdit) {
                            IconButton(onClick = onEdit) {
                                Icon(
                                    imageVector = Icons.Outlined.ModeEditOutline,
                                    contentDescription = stringResource(R.string.team_news_edit),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
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
) {
    val context = LocalContext.current
    val res = context.resources
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
                loading = false
            }
            .onFailure { e ->
                err = e.toUserMessageRu(res)
                loading = false
            }
    }

    val d = detail
    val canEdit = d != null && (d.authorUserId == currentUserId || myTeamRole == "R5")
    val poll = d?.poll

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(d?.title ?: "") },
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
                        IconButton(onClick = { deleteOpen = true }) {
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
                    val pagePad = 12.dp
                    val hero: String? = d.imageRelativeUrls.firstOrNull() ?: d.firstImageRelativeUrl
                    hero?.let { rawPath: String ->
                        teamNewsAuthedImageRequest(context, rawPath)?.let { imgReq: ImageRequest ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(240.dp)
                                    .padding(horizontal = pagePad, vertical = pagePad)
                                    .clip(RoundedCornerShape(20.dp)),
                            ) {
                                AsyncImage(
                                    model = imgReq,
                                    contentDescription = null,
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
                                        text = "${d.authorUsername} · ${formatNewsDateRu(d.createdAt)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.White.copy(alpha = 0.86f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = pagePad)
                            .padding(bottom = 14.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp,
                    ) {
                        Column(
                            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            if (hero == null) {
                                Text(
                                    d.title,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    "${d.authorUsername} · ${formatNewsDateRu(d.createdAt)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(d.body, style = MaterialTheme.typography.bodyLarge)
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
                                teamNewsAuthedImageRequest(context, rawPath)?.let { imgReq ->
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
                        val p = poll
                        Spacer(Modifier.height(20.dp))
                        key(d.updatedAt) {
                            Card(
                                Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                ),
                                shape = RoundedCornerShape(16.dp),
                            ) {
                                Column(
                                    Modifier.padding(18.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Text(p.question, style = MaterialTheme.typography.titleMedium)
                                    val totalVotes = p.tallies.sumOf { tally -> tally.count }
                                    Text(
                                        stringResource(R.string.team_news_votes_count, totalVotes),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    var selected by remember {
                                        mutableStateOf(p.myVoteOptionId)
                                    }
                                    p.options.forEach { opt: TeamNewsPollOptionDto ->
                                        val cnt =
                                            p.tallies.find { t -> t.optionId == opt.id }?.count ?: 0
                                        val share =
                                            if (totalVotes > 0) cnt.toFloat() / totalVotes else 0f
                                        Row(
                                            Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            RadioButton(
                                                selected = selected == opt.id,
                                                onClick = { selected = opt.id },
                                            )
                                            Column(Modifier.weight(1f)) {
                                                Text(opt.text, style = MaterialTheme.typography.bodyMedium)
                                                Spacer(Modifier.height(6.dp))
                                                LinearProgressIndicator(
                                                    progress = { share },
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(6.dp)
                                                        .clip(RoundedCornerShape(4.dp)),
                                                    color = MaterialTheme.colorScheme.primary,
                                                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                                )
                                                Spacer(Modifier.height(4.dp))
                                                Text(
                                                    stringResource(R.string.team_news_poll_option_votes, cnt),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                                pollVotersSummaryLine(p.votes, opt.id)?.let { line ->
                                                    Text(
                                                        stringResource(R.string.team_news_poll_voters, line),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(Modifier.height(4.dp))
                                    }
                                    Button(
                                        onClick = {
                                            val oid = selected ?: return@Button
                                            voteBusy = true
                                            scope.launch {
                                                teamsRepository.voteTeamNews(teamId, newsId, oid)
                                                    .onSuccess { voted -> detail = voted }
                                                voteBusy = false
                                            }
                                        },
                                        enabled = !voteBusy && selected != null,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        if (voteBusy) {
                                            CircularProgressIndicator(
                                                Modifier.size(20.dp),
                                                strokeWidth = 2.dp,
                                            )
                                        } else {
                                            Text(
                                                if (p.myVoteOptionId == null) {
                                                    stringResource(R.string.team_news_vote)
                                                } else {
                                                    stringResource(R.string.team_news_change_vote)
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (deleteOpen && d != null) {
        AlertDialog(
            onDismissRequest = { deleteOpen = false },
            title = { Text(stringResource(R.string.team_news_delete)) },
            text = { Text(stringResource(R.string.team_news_delete_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            teamsRepository.deleteTeamNews(teamId, d.id)
                                .onSuccess { deleteOpen = false; onBack() }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TeamNewsEditorRoute(
    teamId: String,
    newsId: String?,
    teamsRepository: TeamsRepository,
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val res = context.resources
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    val imageIds = remember { mutableStateListOf<String>() }
    var includePoll by remember { mutableStateOf(false) }
    var pollQuestion by remember { mutableStateOf("") }
    val pollOptions = remember { mutableStateListOf("", "") }
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
                    p.options.forEach { pollOptions.add(it.text) }
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
            val bytes = cr.openInputStream(uri)?.use { it.readBytes() } ?: return@launch
            val name = "upload_${System.currentTimeMillis()}.jpg"
            teamsRepository.uploadTeamNewsImage(teamId, bytes, name, mime)
                .onSuccess { uploaded -> imageIds.add(uploaded.fileId) }
                .onFailure { e -> err = e.toUserMessageRu(res) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { includePoll = !includePoll }) {
                    Text(
                        if (includePoll) stringResource(R.string.team_news_remove_poll)
                        else stringResource(R.string.team_news_add_poll),
                    )
                }
            }
            if (includePoll) {
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
                        onClick = { pollOptions.add("") },
                    ) {
                        Text(stringResource(R.string.team_news_add_option))
                    }
                }
            }
            Button(
                onClick = {
                    if (title.isBlank() || body.isBlank()) {
                        err = res.getString(R.string.team_news_fill_required)
                        return@Button
                    }
                    if (includePoll) {
                        val filled = pollOptions.count { o -> o.isNotBlank() }
                        if (pollQuestion.isBlank() || filled < 2) {
                            err = res.getString(R.string.team_news_poll_invalid)
                            return@Button
                        }
                    }
                    err = null
                    saving = true
                    scope.launch {
                        val pollBody =
                            if (includePoll) {
                                TeamNewsPollCreateBody(
                                    question = pollQuestion.trim(),
                                    optionTexts = pollOptions.map { o -> o.trim() }.filter { o -> o.isNotEmpty() },
                                )
                            } else {
                                null
                            }
                        if (newsId == null) {
                            teamsRepository.createTeamNews(
                                teamId,
                                CreateTeamNewsBody(
                                    title = title.trim(),
                                    body = body.trim(),
                                    imageFileIds = imageIds.toList(),
                                    poll = pollBody,
                                ),
                            )
                                .onSuccess { saving = false; onDone() }
                                .onFailure { e ->
                                    err = e.toUserMessageRu(res)
                                    saving = false
                                }
                        } else {
                            teamsRepository.updateTeamNews(
                                teamId,
                                newsId,
                                UpdateTeamNewsBody(
                                    title = title.trim(),
                                    body = body.trim(),
                                    imageFileIds = imageIds.toList(),
                                    poll = if (includePoll) pollBody else null,
                                    clearPoll = if (!includePoll) true else null,
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
