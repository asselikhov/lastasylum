package com.lastasylum.alliance.overlay

import android.graphics.Rect
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.chat.ChatMessage
import com.lastasylum.alliance.data.chat.chatImageAttachments
import com.lastasylum.alliance.data.chat.chatSenderDisplayWithTag
import com.lastasylum.alliance.data.chat.stickers.StickerPacks
import com.lastasylum.alliance.ui.chat.ChatSenderAvatar
import com.lastasylum.alliance.ui.chat.MapLinkedMessageText
import com.lastasylum.alliance.ui.chat.RoleBadge
import com.lastasylum.alliance.ui.chat.chatMessageIsClusterChainBottomOldestFirst
import com.lastasylum.alliance.ui.chat.chatMessageShowsClusterHeaderOldestFirst
import com.lastasylum.alliance.ui.chat.TelegramLikeAttachmentsGrid
import com.lastasylum.alliance.ui.chat.formatChatTime
import com.lastasylum.alliance.ui.chat.resolvedChatAttachmentImageUrl
import com.lastasylum.alliance.ui.theme.ChatTelegramIncomingBubble
import com.lastasylum.alliance.ui.theme.ChatTelegramIncomingOnBubble
import com.lastasylum.alliance.ui.theme.ChatTelegramOutgoingBubble
import com.lastasylum.alliance.ui.theme.ChatTelegramOutgoingOnBubble
import com.lastasylum.alliance.ui.theme.ChatTelegramTimeMuted
import com.lastasylum.alliance.ui.theme.ChatTelegramTimeMutedIncoming
import com.lastasylum.alliance.ui.theme.roleAccentColor
import com.lastasylum.alliance.ui.util.telegramAvatarUrl
import java.time.Instant
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

@OptIn(FlowPreview::class)
@Composable
fun OverlayChatStrip(
    messages: List<ChatMessage>,
    selfUserId: String?,
    lightStrip: Boolean = false,
    onDismissMessage: (ChatMessage) -> Unit = {},
    onDismissRegionsChanged: (List<Rect>) -> Unit = {},
    onNoticeClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val keep = remember { mutableStateListOf<ChatMessage>() }
    val leaving = remember { mutableStateMapOf<String, Boolean>() }
    val dismissRegions = remember { mutableStateMapOf<String, Rect>() }
    val latestMessages by rememberUpdatedState(messages)
    val latestSelfId by rememberUpdatedState(selfUserId)
    val stripScroll = rememberLazyListState()
    var accentEnterKey by remember { mutableStateOf<String?>(null) }

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
            .debounce(120)
            .collect { rects -> onDismissRegionsChanged(rects) }
    }

    val cfg = LocalConfiguration.current
    val stripMaxHeight = remember(cfg.screenHeightDp) {
        (cfg.screenHeightDp * 0.56f).dp.coerceAtLeast(220.dp)
    }

    LaunchedEffect(latestMessages) {
        val keysBefore = keep.map { keyOf(it) }.toSet()

        latestMessages.forEach { m ->
            val key = keyOf(m)
            leaving.remove(key)
            val i = keep.indexOfFirst { keyOf(it) == key }
            if (i >= 0) {
                keep[i] = m
                if (!lightStrip) accentEnterKey = key
            } else {
                keep.add(m)
            }
        }
        val currentKeys = latestMessages.map { keyOf(it) }.toSet()
        val removed = keep.filter { keyOf(it) !in currentKeys }
        removed.forEach { m -> leaving[keyOf(m)] = true }
        if (removed.isNotEmpty()) {
            delay(220)
            keep.removeAll { keyOf(it) !in currentKeys }
            removed.forEach { m -> leaving.remove(keyOf(m)) }
        }
        val orderIndex = latestMessages.mapIndexed { index, msg -> keyOf(msg) to index }.toMap()
        keep.sortBy { orderIndex[keyOf(it)] ?: Int.MAX_VALUE }

        val keysAfter = latestMessages.map { keyOf(it) }.toSet()
        val newKeys = keysAfter - keysBefore
        // Только одно новое сообщение — «премиум» анимация входа; при пакетной подгрузке истории не дёргаем всю колонку.
        if (!lightStrip && newKeys.size == 1) {
            accentEnterKey = newKeys.first()
            delay(260)
            accentEnterKey = null
        }
    }

    val compactStickers = keep.size > 5
    val stripBurstMode = lightStrip || keep.size > 2
    val visibleMessages = remember(keep, leaving.toMap()) {
        keep.filter { leaving[keyOf(it)] != true }
    }

    LazyColumn(
        state = stripScroll,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = stripMaxHeight),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (visibleMessages.isNotEmpty()) {
            item(key = "strip_hdr") {
                OverlayStripBatchHeader(firstMessage = visibleMessages.first())
            }
        }
        items(
            count = visibleMessages.size,
            key = { visibleMessages[it].let { m -> keyOf(m) } },
        ) { index ->
            val msg = visibleMessages[index]
            val key = keyOf(msg)
            val isMine = !latestSelfId.isNullOrBlank() && msg.senderId == latestSelfId
            val isChainBottom = chatMessageIsClusterChainBottomOldestFirst(visibleMessages, index)
            val showClusterHeader = chatMessageShowsClusterHeaderOldestFirst(visibleMessages, index)
            val fancyEnter = !lightStrip && !stripBurstMode && accentEnterKey != null && key == accentEnterKey
            val enterTransition = if (lightStrip || stripBurstMode) {
                fadeIn(animationSpec = tween(24))
            } else if (fancyEnter) {
                fadeIn(animationSpec = tween(90)) +
                    scaleIn(initialScale = 0.97f, animationSpec = tween(120))
            } else {
                fadeIn(animationSpec = tween(40))
            }
            AnimatedVisibility(
                visible = true,
                enter = enterTransition,
            ) {
                OverlayChatStripMessage(
                    msg = msg,
                    messageKey = key,
                    isMine = isMine,
                    showAvatar = !isMine && isChainBottom,
                    showSenderHeader = showClusterHeader,
                    compactStickers = compactStickers,
                    lightStrip = lightStrip || stripBurstMode,
                    onNoticeClick = onNoticeClick,
                    onDismiss = { onDismissMessage(msg) },
                    onReportDismissBounds = { mk, rect ->
                        if (latestMessages.none { keyOf(it) == mk }) return@OverlayChatStripMessage
                        if (rect.isEmpty) return@OverlayChatStripMessage
                        val prev = dismissRegions[mk]
                        if (prev != null &&
                            kotlin.math.abs(prev.left - rect.left) < 6 &&
                            kotlin.math.abs(prev.top - rect.top) < 6 &&
                            kotlin.math.abs(prev.right - rect.right) < 6 &&
                            kotlin.math.abs(prev.bottom - rect.bottom) < 6
                        ) {
                            return@OverlayChatStripMessage
                        }
                        dismissRegions[mk] = rect
                    },
                    onClearDismissRegion = { mk ->
                        dismissRegions.remove(mk)
                    },
                )
            }
        }
    }
}

@Composable
private fun OverlayStripBatchHeader(firstMessage: ChatMessage) {
    val context = LocalContext.current
    val clock = remember(firstMessage.createdAt) { formatChatTime(firstMessage.createdAt) }
    val label = remember(firstMessage.createdAt, clock) {
        if (clock.isBlank()) {
            context.getString(R.string.overlay_strip_live_now)
        } else {
            val instant = OverlayChatTime.parseInstant(firstMessage.createdAt)
            val recent = instant != null &&
                abs(Instant.now().epochSecond - instant.epochSecond) < 150L
            if (recent) {
                context.getString(R.string.overlay_strip_live_now)
            } else {
                context.getString(R.string.overlay_strip_messages_since, clock)
            }
        }
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 10.dp),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.88f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
        )
    }
}

@Composable
private fun OverlayChatStripMessage(
    msg: ChatMessage,
    messageKey: String,
    isMine: Boolean,
    showAvatar: Boolean,
    showSenderHeader: Boolean,
    compactStickers: Boolean,
    lightStrip: Boolean,
    onNoticeClick: (String) -> Unit,
    onDismiss: () -> Unit,
    onReportDismissBounds: (String, Rect) -> Unit,
    onClearDismissRegion: (String) -> Unit,
) {
    val noticeId = msg._id
    val isNotice = OverlayStripNoticeIds.isNotice(noticeId)
    val noticeClickable = OverlayStripNoticeIds.isClickable(noticeId)
    val bubbleBg = if (isMine) ChatTelegramOutgoingBubble else ChatTelegramIncomingBubble
    val onBubble = if (isMine) ChatTelegramOutgoingOnBubble else ChatTelegramIncomingOnBubble
    val timeMuted = if (isMine) ChatTelegramTimeMuted else ChatTelegramTimeMutedIncoming
    val time = remember(msg.createdAt) { formatChatTime(msg.createdAt) }
    val images = remember(msg.attachments, lightStrip) {
        if (lightStrip) {
            emptyList()
        } else {
            msg.chatImageAttachments()
        }
    }
    val imageUrls = remember(images) { images.map { resolvedChatAttachmentImageUrl(it.url) } }
    val stickerStem = remember(msg.text, lightStrip) {
        if (lightStrip) null else StickerPacks.stemForMessage(msg.text)
    }
    val hasSticker = !lightStrip && stickerStem != null
    val hasText = msg.text.isNotBlank() && (!hasSticker || lightStrip)
    val displayName = remember(msg.senderTeamTag, msg.senderUsername) {
        chatSenderDisplayWithTag(
            msg.senderTeamTag,
            msg.senderUsername,
            msg.senderServerNumber,
        ).trim().ifBlank { "—" }
    }
    val avatarUrl = remember(msg.senderTelegramUsername) {
        telegramAvatarUrl(msg.senderTelegramUsername)
    }
    val role = remember(msg.senderRole) { msg.senderRole.trim() }
    val border = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    val shape = RoundedCornerShape(16.dp)
    val gradientBrush = remember(bubbleBg, isMine) {
        val top = lerp(bubbleBg, Color.White, if (isMine) 0.10f else 0.07f)
        val bottom = lerp(bubbleBg, Color.Black, if (isMine) 0.16f else 0.20f)
        Brush.verticalGradient(listOf(top, bottom))
    }
    val stickerCap = if (compactStickers) 72.dp else 96.dp
    val stickerImg = if (compactStickers) 58.dp else 76.dp
    val avatarSize = if (compactStickers) 28.dp else 30.dp
    val dismissCd = stringResource(R.string.overlay_chat_dismiss_cd)

    DisposableEffect(messageKey) {
        onDispose { onClearDismissRegion(messageKey) }
    }

    Surface(
        shape = shape,
        color = bubbleBg,
        border = BorderStroke(1.dp, border),
        tonalElevation = 0.dp,
        shadowElevation = if (lightStrip) 0.dp else 3.dp,
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
                    .padding(
                        start = 10.dp,
                        end = 28.dp,
                        top = if (lightStrip) 6.dp else 8.dp,
                        bottom = if (lightStrip) 6.dp else 8.dp,
                    ),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    when {
                        showAvatar && lightStrip -> {
                            OverlayLightStripAvatar(
                                fallbackName = msg.senderUsername,
                                size = avatarSize,
                            )
                        }
                        showAvatar -> {
                            ChatSenderAvatar(
                                telegramUrl = avatarUrl,
                                size = avatarSize,
                                fallbackName = msg.senderUsername,
                            )
                        }
                        !isMine -> {
                            Spacer(
                                modifier = Modifier.size(
                                    width = avatarSize,
                                    height = avatarSize,
                                ),
                            )
                        }
                    }
                    if (time.isNotBlank() && showAvatar) {
                        Text(
                            text = time,
                            style = MaterialTheme.typography.labelSmall,
                            color = timeMuted,
                            maxLines = 1,
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (showSenderHeader) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = displayName,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.SemiBold,
                                ),
                                color = roleAccentColor(role),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (role.isNotBlank() && !lightStrip) {
                                RoleBadge(role = role)
                            }
                        }
                    }

                    if (hasSticker && stickerStem != null) {
                        val ctx = androidx.compose.ui.platform.LocalContext.current
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = lerp(bubbleBg, Color.Black, if (isMine) 0.08f else 0.12f),
                            tonalElevation = 0.dp,
                            shadowElevation = 0.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = stickerCap),
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(if (compactStickers) 6.dp else 8.dp)
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
                                        .size(stickerImg)
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
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (hasText) {
                        val body = msg.text.trimEnd()
                        val linkColor = if (isMine) {
                            Color(0xFF8FD3FF)
                        } else {
                            Color(0xFF5EB3F6)
                        }
                        MapLinkedMessageText(
                            text = body,
                            style = MaterialTheme.typography.bodyMedium,
                            color = onBubble,
                            fadeBaseColor = bubbleBg,
                            linkColor = linkColor,
                            maxLines = if (compactStickers) 2 else 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 3.dp, end = 3.dp)
                    .size(32.dp)
                    .onGloballyPositioned { coords ->
                        val b = coords.boundsInRoot()
                        onReportDismissBounds(
                            messageKey,
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
                        onClickLabel = dismissCd,
                        onClick = onDismiss,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "✕",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                )
            }
        }
    }
}

@Composable
private fun OverlayLightStripAvatar(
    fallbackName: String,
    size: androidx.compose.ui.unit.Dp,
) {
    val letter = fallbackName.trim().take(1).uppercase().ifBlank { "?" }
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = letter,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
