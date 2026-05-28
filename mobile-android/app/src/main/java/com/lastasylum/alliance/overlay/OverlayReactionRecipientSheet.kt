package com.lastasylum.alliance.overlay

import android.content.Context
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.airbnb.lottie.LottieAnimationView
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.teams.PlayerTeamMemberDto
import com.lastasylum.alliance.data.teams.TeamsRepository
import com.lastasylum.alliance.data.users.UsersRepository
import com.lastasylum.alliance.ui.components.premium.PremiumFeedCardShell
import com.lastasylum.alliance.ui.components.team.FeedCardVariant
import com.lastasylum.alliance.ui.util.formatOverlayPresenceAgeRu
import com.lastasylum.alliance.ui.components.team.TeamMemberPresenceCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val OverlaySheetBgTop = Color(0xF2141C2A)
private val OverlaySheetBgBottom = Color(0xEE0C1018)
private val OverlaySheetStroke = Color(0x3D4A62AA)
private val OverlayGoldAccent = Color(0xFFFFB74D)
private val OverlayMuted = Color(0xFF9AB0C4D8)

internal suspend fun loadOverlayIngameReactionRecipients(
    usersRepository: UsersRepository,
    teamsRepository: TeamsRepository,
): Result<List<PlayerTeamMemberDto>> =
    withContext(Dispatchers.IO) {
        runCatching {
            val ctx = OverlayTeamContextCache.load(
                usersRepository = usersRepository,
                teamsRepository = teamsRepository,
            ).getOrThrow()
            val self = ctx.currentUserId
            OverlayTeamPresenceCache.load(
                teamId = ctx.teamId,
                teamsRepository = teamsRepository,
            ).getOrThrow()
                .ingame
                .filter { it.userId != self }
        }
    }

@Composable
fun OverlayReactionRecipientSheet(
    reactionId: String,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
    onSendToUserIds: (List<String>) -> Unit,
    onSendBroadcastAll: (memberCount: Int) -> Unit,
    loadMembers: suspend () -> Result<List<PlayerTeamMemberDto>>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var members by remember { mutableStateOf<List<PlayerTeamMemberDto>>(emptyList()) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        loading = true
        error = null
        loadMembers()
            .onSuccess { members = it }
            .onFailure { e ->
                error = when (e.message) {
                    "no_team" -> context.getString(R.string.overlay_reactions_no_team)
                    else ->
                        e.message?.takeIf { it.isNotBlank() }
                            ?: context.getString(
                                R.string.overlay_history_send_failed,
                                e.javaClass.simpleName,
                            )
                }
            }
        loading = false
    }

    val filteredMembers by remember(members, searchQuery) {
        derivedStateOf {
            val q = searchQuery.trim()
            if (q.isEmpty()) {
                members
            } else {
                members.filter { it.username.contains(q, ignoreCase = true) }
            }
        }
    }

    val allFilteredSelected by remember(filteredMembers, selectedIds) {
        derivedStateOf {
            filteredMembers.isNotEmpty() &&
                filteredMembers.all { it.userId in selectedIds }
        }
    }

    val selectedCount = selectedIds.size
    val maxSheetHeight = (LocalConfiguration.current.screenHeightDp * 0.85f).dp
    val listMaxHeight = (maxSheetHeight - 300.dp).coerceAtLeast(140.dp)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = maxSheetHeight)
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, OverlaySheetStroke, RoundedCornerShape(16.dp)),
        color = Color.Transparent,
        shadowElevation = 10.dp,
        tonalElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(OverlaySheetBgTop, OverlaySheetBgBottom),
                    ),
                ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
            ) {
                RecipientSheetHeader(onBack = onBack, onDismiss = onDismiss)

                OverlayReactionRecipientPreview(
                    reactionId = reactionId,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                )

                Text(
                    text = stringResource(R.string.overlay_reactions_recipient_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = OverlayMuted,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )

                when {
                    loading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 120.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                strokeWidth = 2.5.dp,
                            )
                        }
                    }
                    error != null -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 120.dp)
                                .padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = error.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFFF8A80),
                            )
                        }
                    }
                    members.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 120.dp)
                                .padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.overlay_reactions_none_ingame),
                                style = MaterialTheme.typography.bodyMedium,
                                color = OverlayMuted,
                            )
                        }
                    }
                    else -> {
                        RecipientToolbar(
                            showSearch = members.size > 6,
                            searchQuery = searchQuery,
                            onSearchChange = { searchQuery = it },
                            allSelected = allFilteredSelected,
                            hasFiltered = filteredMembers.isNotEmpty(),
                            onToggleSelectAll = {
                                selectedIds = if (allFilteredSelected) {
                                    selectedIds - filteredMembers.map { it.userId }.toSet()
                                } else {
                                    selectedIds + filteredMembers.map { it.userId }
                                }
                            },
                        )

                        BroadcastAllRow(
                            memberCount = members.size,
                            onClick = { onSendBroadcastAll(members.size) },
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        )

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = listMaxHeight),
                            contentPadding = PaddingValues(
                                start = 14.dp,
                                end = 14.dp,
                                top = 4.dp,
                                bottom = 8.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            if (filteredMembers.isEmpty()) {
                                item(key = "search_empty") {
                                    Text(
                                        text = stringResource(R.string.overlay_reactions_none_ingame),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = OverlayMuted,
                                        modifier = Modifier.padding(8.dp),
                                    )
                                }
                            } else {
                                items(filteredMembers, key = { it.userId }) { member ->
                                    RecipientMemberRow(
                                        member = member,
                                        selected = member.userId in selectedIds,
                                        onToggle = {
                                            selectedIds = if (member.userId in selectedIds) {
                                                selectedIds - member.userId
                                            } else {
                                                selectedIds + member.userId
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                RecipientSendBar(
                    selectedCount = selectedCount,
                    enabled = selectedCount > 0 && !loading && error == null,
                    onSend = { onSendToUserIds(selectedIds.toList()) },
                )
            }
        }
    }
}

@Composable
private fun RecipientSheetHeader(
    onBack: () -> Unit,
    onDismiss: () -> Unit,
) {
    val backCd = stringResource(R.string.overlay_reactions_back_cd)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.semantics {
                contentDescription = backCd
            },
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                tint = Color(0x99A8B4CC),
            )
        }
        Text(
            text = stringResource(R.string.overlay_reactions_recipient_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFF4F7FF),
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDismiss) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.overlay_online_close_cd),
                tint = Color(0x99A8B4CC),
            )
        }
    }
}

@Composable
private fun RecipientToolbar(
    showSearch: Boolean,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    allSelected: Boolean,
    hasFiltered: Boolean,
    onToggleSelectAll: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (showSearch) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = {
                    Text(
                        stringResource(R.string.overlay_reactions_search_hint),
                        color = OverlayMuted,
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = OverlayMuted,
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color(0xFFF4F7FF),
                    unfocusedTextColor = Color(0xFFF4F7FF),
                    focusedBorderColor = Color(0x775A9AB8),
                    unfocusedBorderColor = Color(0x354A5E72),
                    cursorColor = Color(0xFF8FAEFF),
                    focusedContainerColor = Color(0xFF1A2836),
                    unfocusedContainerColor = Color(0xFF141C28),
                ),
                shape = RoundedCornerShape(12.dp),
            )
        }
        if (hasFiltered) {
            FilterChip(
                selected = allSelected,
                onClick = onToggleSelectAll,
                label = {
                    Text(
                        text = stringResource(
                            if (allSelected) {
                                R.string.overlay_reactions_clear_selection
                            } else {
                                R.string.overlay_reactions_select_all
                            },
                        ),
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFF2A4558),
                    containerColor = Color(0xFF1A2836),
                    labelColor = Color(0xFFE8F4FF),
                    selectedLabelColor = Color(0xFFE8F4FF),
                ),
            )
        }
    }
}

@Composable
private fun BroadcastAllRow(
    memberCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PremiumFeedCardShell(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        variant = FeedCardVariant.Member,
        showLiveAccent = false,
        listMode = true,
        accentColor = OverlayGoldAccent,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.overlay_reactions_send_all),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = OverlayGoldAccent,
                )
                Text(
                    text = stringResource(R.string.overlay_reactions_send_all_subtitle, memberCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = OverlayMuted,
                )
                Text(
                    text = stringResource(R.string.overlay_reactions_send_all_broadcast_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = OverlayMuted.copy(alpha = 0.85f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
    )
}

@Composable
private fun RecipientMemberRow(
    member: PlayerTeamMemberDto,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    val squadRole = member.teamRole.trim().uppercase().ifBlank { "R1" }
    val rowCd = stringResource(
        if (selected) R.string.overlay_reactions_member_selected_cd else R.string.overlay_reactions_member_row_cd,
        member.username,
        squadRole,
    )
    val shape = RoundedCornerShape(14.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .semantics { contentDescription = rowCd }
            .then(
                if (selected) {
                    Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.75f), shape)
                } else {
                    Modifier
                },
            )
            .clip(shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggle,
            ),
    ) {
        TeamMemberPresenceCard(
            username = member.username,
            telegramUsername = member.telegramUsername,
            squadRole = squadRole,
            displayName = member.username,
            presenceSubtitle = stringResource(R.string.team_member_in_game_cd),
            inGameNow = true,
            showIngameAvatarRing = true,
            micOn = false,
            soundOn = false,
            showVoiceBadges = false,
            trailingContent = {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onToggle() },
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.primary,
                        uncheckedColor = OverlayMuted,
                        checkmarkColor = Color.White,
                    ),
                )
            },
        )
    }
}

@Composable
private fun RecipientSendBar(
    selectedCount: Int,
    enabled: Boolean,
    onSend: () -> Unit,
) {
    HorizontalDivider(color = OverlaySheetStroke.copy(alpha = 0.6f))
    Button(
        onClick = onSend,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .heightIn(min = 48.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF3D5AFE),
            disabledContainerColor = Color(0xFF2A3544),
            contentColor = Color(0xFFF8FAFF),
            disabledContentColor = OverlayMuted,
        ),
    ) {
        Text(
            text = stringResource(R.string.overlay_reactions_send_selected, selectedCount),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun OverlayReactionRecipientPreview(
    reactionId: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val previewCd = stringResource(R.string.overlay_reactions_recipient_preview_cd)
    val textPayload = remember(reactionId) { decodeTextReactionId(reactionId) }
    val reaction = remember(reactionId) { overlayQuickReactionById(context, reactionId) }

    Surface(
        modifier = modifier
            .semantics { contentDescription = previewCd },
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF1A2434),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x334A62AA)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (textPayload != null) {
                val metrics = remember(context, density) {
                    OverlayReactionBurstLayout.metrics(context) { dpValue ->
                        (dpValue * density.density).toInt()
                    }
                }
                val maxTextWidthPx = remember(metrics, density) {
                    OverlayReactionBurstLayout.textMessageMaxWidthPx(
                        metrics,
                        (148 * density.density).toInt(),
                    )
                }
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 72.dp),
                    factory = { ctx ->
                        OverlayReactionTextBurstUi.createMessageTextView(
                            ctx,
                            textPayload,
                            maxTextWidthPx,
                        )
                    },
                )
            } else {
                AndroidView(
                    modifier = Modifier.size(112.dp),
                    factory = { ctx ->
                        val icon = createOverlayReactionTileIcon(
                            ctx,
                            reaction,
                            playAnimatedPreview = true,
                        )
                        FrameLayout(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                            addView(
                                icon,
                                FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    android.view.Gravity.CENTER,
                                ),
                            )
                            tag = icon
                        }
                    },
                    update = { host ->
                        val icon = host.tag as? ImageView ?: return@AndroidView
                        resumeOverlayReactionTilePreview(icon)
                    },
                    onRelease = { host ->
                        (host.tag as? ImageView)?.let { stopOverlayReactionTileAnimation(it) }
                    },
                )
            }
        }
    }
}
