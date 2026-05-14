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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
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
import com.lastasylum.alliance.data.chat.chatSenderDisplayWithTag
import com.lastasylum.alliance.data.chat.stickers.ZlobyakaStickerPack
import com.lastasylum.alliance.ui.chat.ChatSenderAvatar
import com.lastasylum.alliance.ui.chat.RoleBadge
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

@Composable
fun OverlayChatStrip(
    messages: List<ChatMessage>,
    selfUserId: String?,
    onDismissMessage: (ChatMessage) -> Unit = {},
    onDismissRegionsChanged: (List<Rect>) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val keep = remember { mutableStateListOf<ChatMessage>() }
    val leaving = remember { mutableStateMapOf<String, Boolean>() }
    val dismissRegions = remember { mutableStateMapOf<String, Rect>() }
    val latestMessages by rememberUpdatedState(messages)
    val latestSelfId by rememberUpdatedState(selfUserId)
    val stripScroll = rememberScrollState()
    var accentEnterKey by remember { mutableStateOf<String?>(null) }

    fun keyOf(msg: ChatMessage): String =
        msg._id?.takeIf { it.isNotBlank() } ?: msg.stableKey()

    // Синхронно с реальным списком сообщений: иначе один layout после удаления из preview
    // снова регистрирует rect по карточке, которая ещё в keep, а LaunchedEffect ещё не вызвал retainAll.
    SideEffect {
        val valid = latestMessages.map { keyOf(it) }.toSet()
        val stale = dismissRegions.keys.filter { it !in valid }
        if (stale.isNotEmpty()) {
            stale.forEach { dismissRegions.remove(it) }
            onDismissRegionsChanged(dismissRegions.values.toList())
        }
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
            if (i >= 0) keep[i] = m else keep.add(m)
        }
        val currentKeys = latestMessages.map { keyOf(it) }.toSet()
        val removed = keep.filter { keyOf(it) !in currentKeys }
        removed.forEach { m -> leaving[keyOf(m)] = true }
        if (removed.isNotEmpty()) {
            delay(220)
            keep.removeAll { keyOf(it) !in currentKeys }
            removed.forEach { m -> leaving.remove(keyOf(m)) }
        }
        val order = latestMessages.map { keyOf(it) }
        keep.sortBy { order.indexOf(keyOf(it)).let { idx -> if (idx < 0) Int.MAX_VALUE else idx } }

        val keysAfter = latestMessages.map { keyOf(it) }.toSet()
        val newKeys = keysAfter - keysBefore
        // Только одно новое сообщение — «премиум» анимация входа; при пакетной подгрузке истории не дёргаем всю колонку.
        if (newKeys.size == 1) {
            accentEnterKey = newKeys.first()
            delay(260)
            accentEnterKey = null
        }
    }

    val compactStickers = keep.size > 5

    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = stripMaxHeight)
            .verticalScroll(stripScroll),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (keep.isNotEmpty()) {
            OverlayStripBatchHeader(firstMessage = keep.first())
        }
        keep.forEach { msg ->
            val key = keyOf(msg)
            val isMine = !latestSelfId.isNullOrBlank() && msg.senderId == latestSelfId
            val isLeaving = leaving[key] == true
            val fancyEnter = accentEnterKey != null && key == accentEnterKey
            val enterTransition = if (fancyEnter) {
                fadeIn(animationSpec = tween(110)) +
                    scaleIn(initialScale = 0.96f, animationSpec = tween(170))
            } else {
                fadeIn(animationSpec = tween(55))
            }
            AnimatedVisibility(
                visible = !isLeaving,
                enter = enterTransition,
                exit = fadeOut(animationSpec = tween(150)) +
                    slideOutVertically(animationSpec = tween(170)) { it / 4 } +
                    scaleOut(targetScale = 0.98f, animationSpec = tween(170)),
            ) {
                OverlayChatStripMessage(
                    msg = msg,
                    messageKey = key,
                    isMine = isMine,
                    compactStickers = compactStickers,
                    onDismiss = { onDismissMessage(msg) },
                    onReportDismissBounds = { mk, rect ->
                        // During AnimatedVisibility exit the row is still composed; positioning callbacks
                        // would re-register rects after LaunchedEffect cleared them — touches then hit
                        // "dismiss" zones with no real target and block the game.
                        if (leaving[mk] == true) return@OverlayChatStripMessage
                        if (latestMessages.none { keyOf(it) == mk }) return@OverlayChatStripMessage
                        dismissRegions[mk] = rect
                        onDismissRegionsChanged(dismissRegions.values.toList())
                    },
                    onClearDismissRegion = { mk ->
                        dismissRegions.remove(mk)
                        onDismissRegionsChanged(dismissRegions.values.toList())
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
    compactStickers: Boolean,
    onDismiss: () -> Unit,
    onReportDismissBounds: (String, Rect) -> Unit,
    onClearDismissRegion: (String) -> Unit,
) {
    val bubbleBg = if (isMine) ChatTelegramOutgoingBubble else ChatTelegramIncomingBubble
    val onBubble = if (isMine) ChatTelegramOutgoingOnBubble else ChatTelegramIncomingOnBubble
    val timeMuted = if (isMine) ChatTelegramTimeMuted else ChatTelegramTimeMutedIncoming
    val time = remember(msg.createdAt) { formatChatTime(msg.createdAt) }
    val images = remember(msg.attachments) {
        msg.attachments.filter { it.kind == "image" && it.url.isNotBlank() }
    }
    val imageUrls = remember(images) { images.map { resolvedChatAttachmentImageUrl(it.url) } }
    val stickerStem = remember(msg.text) { ZlobyakaStickerPack.parseStem(msg.text) }
    val hasSticker = stickerStem != null
    val hasText = !hasSticker && msg.text.isNotBlank()
    val displayName = remember(msg.senderTeamTag, msg.senderUsername) {
        chatSenderDisplayWithTag(msg.senderTeamTag, msg.senderUsername).trim().ifBlank { "—" }
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
    val dismissCd = stringResource(R.string.overlay_chat_dismiss_cd)

    DisposableEffect(messageKey) {
        onDispose { onClearDismissRegion(messageKey) }
    }

    Surface(
        shape = shape,
        color = Color.Transparent,
        border = BorderStroke(1.dp, border),
        tonalElevation = 0.dp,
        shadowElevation = 3.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(brush = gradientBrush, shape = shape),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 28.dp, top = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    ChatSenderAvatar(
                        telegramUrl = avatarUrl,
                        size = if (compactStickers) 28.dp else 30.dp,
                        fallbackName = msg.senderUsername,
                    )
                    if (time.isNotBlank()) {
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = roleAccentColor(role),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (role.isNotBlank()) {
                            RoleBadge(role = role)
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
                                    model = ImageRequest.Builder(ctx)
                                        .data(ZlobyakaStickerPack.assetUriForStem(stickerStem))
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
                        Text(
                            text = msg.text.trimEnd(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = onBubble,
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
