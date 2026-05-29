package com.lastasylum.alliance.overlay

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.material3.TextButton
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
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.teams.PlayerTeamMemberDto
import com.lastasylum.alliance.data.teams.TeamsRepository
import com.lastasylum.alliance.data.users.UsersRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val OverlaySheetBgTop = Color(0xF2141C2A)
private val OverlaySheetBgBottom = Color(0xEE0C1018)
private val OverlaySheetStroke = Color(0x3D4A62AA)
private val OverlayMuted = Color(0xFF9AB0C4D8)
private val OverlayCyan = Color(0xFF38BDF8)

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
    initialSelectedUserIds: Set<String> = emptySet(),
) {
    val context = LocalContext.current
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var members by remember { mutableStateOf<List<PlayerTeamMemberDto>>(emptyList()) }
    var selectedIds by remember(initialSelectedUserIds) {
        mutableStateOf(initialSelectedUserIds)
    }
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
    val sheetHeight = (LocalConfiguration.current.screenHeightDp * 0.62f).dp

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(sheetHeight)
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, OverlaySheetStroke, RoundedCornerShape(16.dp)),
        color = Color.Transparent,
        shadowElevation = 10.dp,
        tonalElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(OverlaySheetBgTop, OverlaySheetBgBottom),
                    ),
                ),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
            ) {
                RecipientSheetHeader(onBack = onBack, onDismiss = onDismiss)

                OverlayReactionRecipientPreview(
                    reactionId = reactionId,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                )

                Text(
                    text = stringResource(R.string.overlay_reactions_recipient_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = OverlayMuted,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                )

                when {
                    loading -> {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
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
                                .weight(1f)
                                .fillMaxWidth()
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
                                .weight(1f)
                                .fillMaxWidth()
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
                        RecipientCompactToolbar(
                            showSearch = members.size > 9,
                            searchQuery = searchQuery,
                            onSearchChange = { searchQuery = it },
                            allSelected = allFilteredSelected,
                            hasFiltered = filteredMembers.isNotEmpty(),
                            memberCount = members.size,
                            onToggleSelectAll = {
                                selectedIds = if (allFilteredSelected) {
                                    selectedIds - filteredMembers.map { it.userId }.toSet()
                                } else {
                                    selectedIds + filteredMembers.map { it.userId }
                                }
                            },
                            onSendBroadcastAll = { onSendBroadcastAll(members.size) },
                        )

                        if (filteredMembers.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = stringResource(R.string.overlay_reactions_none_ingame),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OverlayMuted,
                                )
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentPadding = PaddingValues(
                                    start = 12.dp,
                                    end = 12.dp,
                                    top = 4.dp,
                                    bottom = 4.dp,
                                ),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(filteredMembers, key = { it.userId }) { member ->
                                    OverlayOnlineMemberGridCellFromDto(
                                        member = member,
                                        micOn = false,
                                        soundOn = false,
                                        mode = OverlayOnlineMemberCellMode.Selectable,
                                        selected = member.userId in selectedIds,
                                        onToggleSelect = {
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
            .padding(start = 2.dp, end = 2.dp, top = 2.dp, bottom = 0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .size(40.dp)
                .semantics { contentDescription = backCd },
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                tint = Color(0x99A8B4CC),
            )
        }
        Text(
            text = stringResource(R.string.overlay_reactions_recipient_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFF4F7FF),
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = onDismiss,
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.overlay_online_close_cd),
                tint = Color(0x99A8B4CC),
            )
        }
    }
}

@Composable
private fun RecipientCompactToolbar(
    showSearch: Boolean,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    allSelected: Boolean,
    hasFiltered: Boolean,
    memberCount: Int,
    onToggleSelectAll: () -> Unit,
    onSendBroadcastAll: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
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
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = OverlayMuted,
                        modifier = Modifier.size(18.dp),
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
                shape = RoundedCornerShape(10.dp),
                textStyle = MaterialTheme.typography.labelSmall,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
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
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF2A4558),
                        containerColor = Color(0xFF1A2836),
                        labelColor = Color(0xFFE8F4FF),
                        selectedLabelColor = Color(0xFFE8F4FF),
                    ),
                    modifier = Modifier.heightIn(min = 32.dp),
                )
            }
            TextButton(
                onClick = onSendBroadcastAll,
                modifier = Modifier.heightIn(min = 32.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
            ) {
                Text(
                    text = stringResource(R.string.overlay_reactions_send_all_short, memberCount),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = OverlayCyan,
                )
            }
        }
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
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .heightIn(min = 44.dp),
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
    val playAnimated = reaction.lottieRawRes != null || reaction.gifDrawableRes != null

    Box(
        modifier = modifier
            .semantics { contentDescription = previewCd },
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
                    (120 * density.density).toInt(),
                )
            }
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp, max = 56.dp),
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
                modifier = Modifier.size(60.dp),
                factory = { ctx ->
                    val icon = createOverlayReactionTileIcon(
                        ctx,
                        reaction,
                        playAnimatedPreview = playAnimated,
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
                    if (playAnimated) {
                        resumeOverlayReactionTilePreview(icon)
                    }
                },
                onRelease = { host ->
                    (host.tag as? ImageView)?.let { stopOverlayReactionTileAnimation(it) }
                },
            )
        }
    }
}
