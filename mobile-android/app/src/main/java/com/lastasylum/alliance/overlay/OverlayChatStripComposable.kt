package com.lastasylum.alliance.overlay

import android.graphics.Rect
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.chat.ChatMessage
import com.lastasylum.alliance.data.chat.chatImageAttachments
import com.lastasylum.alliance.data.chat.chatSenderDisplayWithTag
import com.lastasylum.alliance.data.chat.stickers.StickerPacks
import com.lastasylum.alliance.ui.chat.ChatSenderAvatarWithSquadRank
import com.lastasylum.alliance.ui.chat.MapLinkedMessageText
import com.lastasylum.alliance.ui.chat.isAtReverseChatBottom
import com.lastasylum.alliance.ui.chat.scrollReverseChatRevealLatest
import com.lastasylum.alliance.ui.chat.TelegramLikeAttachmentsGrid
import com.lastasylum.alliance.ui.chat.formatChatTime
import com.lastasylum.alliance.ui.chat.resolvedChatAttachmentImageUrl
import com.lastasylum.alliance.ui.theme.ChatTelegramIncomingBubble
import com.lastasylum.alliance.ui.theme.ChatTelegramIncomingOnBubble
import com.lastasylum.alliance.ui.theme.ChatTelegramTimeMutedIncoming
import com.lastasylum.alliance.ui.theme.roleAccentColor
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private const val STRIP_EXIT_ANIM_MS = 120L
private const val STRIP_ENTER_ANIM_MS = 100
private const val STRIP_CARD_ESTIMATE_DP = 120

@OptIn(FlowPreview::class)
@Composable
fun OverlayChatStrip(
    messages: List<ChatMessage>,
    @Suppress("UNUSED_PARAMETER") selfUserId: String?,
    @Suppress("UNUSED_PARAMETER") lightStrip: Boolean = false,
    onDismissMessage: (ChatMessage) -> Unit = {},
    onDismissRegionsChanged: (List<Rect>) -> Unit = {},
    onNoticeClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val keep = remember { mutableStateListOf<ChatMessage>() }
    val leaving = remember { mutableStateMapOf<String, Boolean>() }
    val dismissRegions = remember { mutableStateMapOf<String, Rect>() }
    val latestMessages by rememberUpdatedState(messages)
    val stripScroll = rememberLazyListState()
    val atStripBottom by remember(stripScroll) {
        derivedStateOf { stripScroll.isAtReverseChatBottom() }
    }

    fun keyOf(msg: ChatMessage): String =
        msg._id?.takeIf { it.isNotBlank() } ?: msg.stableKey()

    LaunchedEffect(latestMessages) {
        val valid = latestMessages.map { keyOf(it) }.toSet()
        val stale = dismissRegions.keys.filter { it !in valid }
        if (stale.isNotEmpty()) {
            stale.forEach { dismissRegions.remove(it) }
        }
    }

    LaunchedEffect(Unit) {
        snapshotFlow { dismissRegions.values.toList() }
            .debounce(48)
            .collect { rects -> onDismissRegionsChanged(rects) }
    }

    LaunchedEffect(stripScroll) {
        snapshotFlow { stripScroll.isScrollInProgress }
            .debounce(80)
            .collect { scrolling ->
                if (!scrolling && dismissRegions.isNotEmpty()) {
                    onDismissRegionsChanged(dismissRegions.values.toList())
                }
            }
    }

    val stripMaxHeight = remember {
        (STRIP_CARD_ESTIMATE_DP * OverlayChatStripBuffer.DEFAULT_MAX_PREVIEW +
            8 * (OverlayChatStripBuffer.DEFAULT_MAX_PREVIEW - 1)).dp
    }

    LaunchedEffect(latestMessages) {
        latestMessages.forEach { m ->
            val key = keyOf(m)
            leaving.remove(key)
            val i = keep.indexOfFirst { keyOf(it) == key }
            if (i >= 0) {
                keep[i] = m
            } else {
                keep.add(m)
            }
        }
        val currentKeys = latestMessages.map { keyOf(it) }.toSet()
        val removed = keep.filter { keyOf(it) !in currentKeys }
        if (removed.isNotEmpty()) {
            removed.forEach { m -> leaving[keyOf(m)] = true }
            delay(STRIP_EXIT_ANIM_MS)
            keep.removeAll { keyOf(it) !in currentKeys }
            removed.forEach { m -> leaving.remove(keyOf(m)) }
        }
        val orderIndex = latestMessages.mapIndexed { index, msg -> keyOf(msg) to index }.toMap()
        keep.sortBy { orderIndex[keyOf(it)] ?: Int.MAX_VALUE }
        if (keep.size > OverlayChatStripBuffer.DEFAULT_MAX_PREVIEW) {
            val overflow = keep.size - OverlayChatStripBuffer.DEFAULT_MAX_PREVIEW
            val trimmed = keep.take(overflow)
            trimmed.forEach { m -> leaving[keyOf(m)] = true }
            delay(STRIP_EXIT_ANIM_MS)
            val trimKeys = trimmed.map { keyOf(it) }.toSet()
            keep.removeAll { keyOf(it) in trimKeys }
            trimmed.forEach { m -> leaving.remove(keyOf(m)) }
        }
    }

    val leavingCount = leaving.size
    val displayMessages = remember(keep.size, leavingCount) {
        keep.asReversed()
    }

    LaunchedEffect(latestMessages, atStripBottom) {
        if (!atStripBottom || displayMessages.isEmpty()) return@LaunchedEffect
        runCatching {
            stripScroll.scrollReverseChatRevealLatest(animate = false, adjustViewport = false)
        }
    }

    LazyColumn(
        state = stripScroll,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = stripMaxHeight),
        reverseLayout = true,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(
            count = displayMessages.size,
            key = { displayMessages[it].let { m -> keyOf(m) } },
            contentType = { idx ->
                if (OverlayStripNoticeIds.isNotice(displayMessages[idx]._id)) 0 else 1
            },
        ) { index ->
            val msg = displayMessages[index]
            val key = keyOf(msg)
            val isVisible = leaving[key] != true

            fun reportBounds(mk: String, rect: Rect) {
                if (latestMessages.none { keyOf(it) == mk }) return
                if (rect.isEmpty()) return
                dismissRegions[mk] = rect
            }
            fun clearRegion(mk: String) {
                dismissRegions.remove(mk)
            }

            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(animationSpec = tween(STRIP_ENTER_ANIM_MS)) +
                    slideInVertically(initialOffsetY = { it / 3 }, animationSpec = tween(STRIP_ENTER_ANIM_MS)) +
                    scaleIn(initialScale = 0.96f, animationSpec = tween(STRIP_ENTER_ANIM_MS)),
                exit = fadeOut(animationSpec = tween(STRIP_EXIT_ANIM_MS.toInt())) +
                    slideOutVertically(targetOffsetY = { -it / 4 }, animationSpec = tween(STRIP_EXIT_ANIM_MS.toInt())) +
                    scaleOut(targetScale = 0.98f, animationSpec = tween(STRIP_EXIT_ANIM_MS.toInt())),
            ) {
                OverlayChatStripMessage(
                    msg = msg,
                    messageKey = key,
                    onNoticeClick = onNoticeClick,
                    onDismiss = {
                        leaving[key] = true
                        onDismissMessage(msg)
                    },
                    onReportDismissBounds = { mk, rect -> reportBounds(mk, rect) },
                    onClearDismissRegion = { mk -> clearRegion(mk) },
                )
            }
        }
    }
}

@Composable
private fun OverlayChatStripMessage(
    msg: ChatMessage,
    messageKey: String,
    onNoticeClick: (String) -> Unit,
    onDismiss: () -> Unit,
    onReportDismissBounds: (String, Rect) -> Unit,
    onClearDismissRegion: (String) -> Unit,
) {
    val noticeId = msg._id
    val isNotice = OverlayStripNoticeIds.isNotice(noticeId)
    val noticeClickable = OverlayStripNoticeIds.isClickable(noticeId)
    val bubbleBg = ChatTelegramIncomingBubble
    val onBubble = ChatTelegramIncomingOnBubble
    val timeMuted = ChatTelegramTimeMutedIncoming
    val time = remember(msg.createdAt) { formatChatTime(msg.createdAt) }
    val images = remember(msg.attachments) { msg.chatImageAttachments() }
    val imageUrls = remember(images) { images.map { resolvedChatAttachmentImageUrl(it.url) } }
    val stickerStem = remember(msg.text) { StickerPacks.stemForMessage(msg.text) }
    val hasSticker = stickerStem != null
    val hasText = msg.text.isNotBlank() && !hasSticker
    val displayName = remember(msg.senderTeamTag, msg.senderUsername) {
        chatSenderDisplayWithTag(
            msg.senderTeamTag,
            msg.senderUsername,
            msg.senderServerNumber,
        ).trim().ifBlank { "—" }
    }
    val avatarRelativeUrl = remember(msg.senderAvatarRelativeUrl) {
        msg.senderAvatarRelativeUrl?.trim()?.takeIf { it.isNotEmpty() }
    }
    val role = remember(msg.senderRole) { msg.senderRole.trim() }
    val border = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    val shape = RoundedCornerShape(16.dp)
    val gradientBrush = remember(bubbleBg) {
        val top = lerp(bubbleBg, Color.White, 0.07f)
        val bottom = lerp(bubbleBg, Color.Black, 0.20f)
        Brush.verticalGradient(listOf(top, bottom))
    }
    val avatarSize = 30.dp
    val dismissCd = stringResource(R.string.overlay_chat_dismiss_cd)

    DisposableEffect(messageKey) {
        onDispose { onClearDismissRegion(messageKey) }
    }

    Surface(
        shape = shape,
        color = bubbleBg,
        border = BorderStroke(1.dp, border),
        tonalElevation = 0.dp,
        shadowElevation = 3.dp,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isNotice && noticeClickable && noticeId != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClick = { onNoticeClick(noticeId) },
                    )
                } else {
                    Modifier
                },
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(brush = gradientBrush, shape = shape),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                ChatSenderAvatarWithSquadRank(
                    avatarRelativeUrl = avatarRelativeUrl,
                    squadRole = role,
                    size = avatarSize,
                    fallbackName = msg.senderUsername,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = roleAccentColor(role),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    if (hasSticker) {
                        val ctx = androidx.compose.ui.platform.LocalContext.current
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = lerp(bubbleBg, Color.Black, 0.12f),
                            tonalElevation = 0.dp,
                            shadowElevation = 0.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 96.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(8.dp)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center,
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(ctx.applicationContext)
                                        .data(StickerPacks.assetUriForMessage(msg.text))
                                        .allowHardware(false)
                                        .crossfade(true)
                                        .size(256)
                                        .build(),
                                    contentDescription = stringResource(R.string.cd_chat_sticker),
                                    modifier = Modifier
                                        .size(76.dp)
                                        .clip(RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Fit,
                                )
                            }
                        }
                    }
                    if (imageUrls.isNotEmpty()) {
                        TelegramLikeAttachmentsGrid(
                            urls = imageUrls,
                            contentDescription = "chat attachment",
                            onOpen = {},
                            roundTileCorners = true,
                            bottomRound = true,
                            enabled = false,
                            inMessageList = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (hasText) {
                        val body = msg.text.trimEnd()
                        MapLinkedMessageText(
                            text = body,
                            style = MaterialTheme.typography.bodyMedium,
                            color = onBubble,
                            fadeBaseColor = bubbleBg,
                            linkColor = Color(0xFF5EB3F6),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (time.isNotBlank()) {
                        Text(
                            text = time,
                            style = MaterialTheme.typography.labelSmall,
                            color = timeMuted,
                            maxLines = 1,
                            modifier = Modifier.align(Alignment.End),
                        )
                    }
                }
            }
            OverlayStripDismissButton(
                contentDescription = dismissCd,
                onDismiss = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 4.dp, end = 4.dp),
                onReportBounds = { rect -> onReportDismissBounds(messageKey, rect) },
            )
        }
    }
}

@Composable
private fun OverlayStripDismissButton(
    contentDescription: String,
    onDismiss: () -> Unit,
    onReportBounds: (Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(8.dp)
    val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    val bg = MaterialTheme.colorScheme.surface.copy(alpha = 0.62f)
    val iconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f)
    Box(
        modifier = modifier
            .size(28.dp)
            .clip(shape)
            .background(bg)
            .border(0.5.dp, borderColor, shape)
            .onGloballyPositioned { coords ->
                val b = coords.boundsInRoot()
                onReportBounds(
                    Rect(
                        b.left.roundToInt(),
                        b.top.roundToInt(),
                        b.right.roundToInt(),
                        b.bottom.roundToInt(),
                    ),
                )
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClickLabel = contentDescription,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "✕",
            style = MaterialTheme.typography.labelLarge,
            color = iconColor,
        )
    }
}
