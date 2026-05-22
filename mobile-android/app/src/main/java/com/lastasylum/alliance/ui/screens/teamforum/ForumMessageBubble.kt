package com.lastasylum.alliance.ui.screens.teamforum

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.chat.stickers.ZlobyakaStickerPack
import com.lastasylum.alliance.data.chat.ChatAttachment
import com.lastasylum.alliance.data.teams.TeamForumMessageDto
import com.lastasylum.alliance.ui.chat.ChatBubbleAuthorHeader
import com.lastasylum.alliance.ui.chat.ChatMessageBodyText
import com.lastasylum.alliance.ui.chat.ChatFileAttachmentCard
import com.lastasylum.alliance.ui.chat.ChatIncomingAvatarEndPad
import com.lastasylum.alliance.ui.chat.ChatIncomingAvatarSize
import com.lastasylum.alliance.ui.chat.chatBubbleWidth
import com.lastasylum.alliance.ui.chat.ChatSenderAvatar
import com.lastasylum.alliance.ui.chat.TelegramImageCaptionBar
import com.lastasylum.alliance.ui.chat.TelegramLikeAttachmentsGrid
import com.lastasylum.alliance.ui.chat.chatBubbleShapeIncoming
import com.lastasylum.alliance.ui.chat.chatBubbleShapeOutgoing
import com.lastasylum.alliance.ui.chat.ForumMessageClusterFlags
import com.lastasylum.alliance.ui.chat.replyPreviewText
import com.lastasylum.alliance.data.chat.chatSenderDisplayWithTag
import com.lastasylum.alliance.ui.chat.resolvedChatAttachmentImageUrl
import com.lastasylum.alliance.ui.theme.SquadRelayDimens
import com.lastasylum.alliance.ui.theme.roleAccentColor
import com.lastasylum.alliance.ui.util.telegramAvatarUrl
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.platform.LocalDensity

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ForumMessageBubble(
    message: TeamForumMessageDto,
    cluster: ForumMessageClusterFlags?,
    isMine: Boolean,
    canDelete: Boolean,
    inSelectionMode: Boolean,
    isSelected: Boolean,
    highlighted: Boolean,
    onOpenImages: (List<String>, Int) -> Unit,
    onJumpToMessage: (String) -> Unit,
    onBeginSelection: () -> Unit,
    onToggleSelection: () -> Unit,
    onSwipeReply: () -> Unit,
    onOpenActions: () -> Unit,
    downloadingForumFileUrl: String? = null,
    onDownloadForumFile: (TeamForumMessageDto) -> Unit = {},
) {
    val deleted = !message.deletedAt.isNullOrBlank() &&
        !message.deletedAt.equals("null", ignoreCase = true)
    val ctx = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val layoutDirection = LocalLayoutDirection.current
    val density = LocalDensity.current
    val swipePx = remember(density) { with(density) { 56.dp.toPx() } }

    val clusterTop = cluster?.topSpacing ?: 8.dp
    val showClusterHeader = cluster?.showHeader ?: true
    val isChainBottom = cluster?.isChainBottom ?: true
    val tightClusterTop = cluster?.tightInnerTop ?: false

    val stickerStem = if (!deleted) ZlobyakaStickerPack.parseStem(message.text) else null
    val rawImagePaths = remember(message.id, message.imageRelativeUrls, message.imageRelativeUrl) {
        mergedForumImagePaths(message)
    }
    val resolvedImageUrls = remember(rawImagePaths) {
        rawImagePaths.map { resolvedChatAttachmentImageUrl(it) }
    }
    val floatingSticker = stickerStem != null && message.replyTo == null && !deleted
    val scheme = MaterialTheme.colorScheme
    val bubbleBg = if (isMine) {
        lerp(scheme.primary, scheme.surface, 0.28f).copy(alpha = 0.82f)
    } else {
        scheme.surface.copy(alpha = 0.52f)
    }
    val onBubble = if (isMine) Color.White else scheme.onSurface
    val timeMuted = if (isMine) Color.White.copy(alpha = 0.72f) else scheme.onSurfaceVariant.copy(alpha = 0.85f)
    val bubbleBorder = BorderStroke(
        1.dp,
        if (isMine) Color.White.copy(alpha = 0.14f) else scheme.outline.copy(alpha = 0.2f),
    )
    val senderAccent = roleAccentColor(message.senderRole)
    val messageImageTapLabel = stringResource(R.string.cd_chat_message_image)
    val nickname = message.senderUsername.trim()
    val displayName = nickname.ifBlank { "—" }
    val telegramUrl = telegramAvatarUrl(message.senderTelegramUsername)

    val timeStr = com.lastasylum.alliance.ui.chat.formatChatTime(message.createdAt)
    val timeLabel = remember(timeStr, message.editedAt, message.createdAt) {
        if (timeStr.isBlank()) {
            ""
        } else if (
            com.lastasylum.alliance.ui.chat.chatMessageShowsEditedLabel(
                message.editedAt,
                message.createdAt,
            )
        ) {
            "$timeStr · ${ctx.getString(R.string.chat_edited)}"
        } else {
            timeStr
        }
    }

    val bubbleShape = if (isMine) {
        chatBubbleShapeOutgoing(isChainBottom)
    } else {
        chatBubbleShapeIncoming(isChainBottom)
    }

    val captionBarBg = if (isMine) {
        lerp(bubbleBg, Color.Black, 0.15f)
    } else {
        lerp(bubbleBg, Color.Black, 0.22f)
    }

    val swipeModifier = if (!deleted) {
        Modifier.pointerInput(message.id, layoutDirection, swipePx) {
            var accX = 0f
            detectHorizontalDragGestures(
                onDragEnd = {
                    val fired = kotlin.math.abs(accX) > swipePx
                    if (fired) {
                        val towardReply = if (layoutDirection == LayoutDirection.Rtl) {
                            accX < 0
                        } else {
                            accX > 0
                        }
                        if (towardReply) onSwipeReply()
                    }
                    accX = 0f
                },
                onHorizontalDrag = { change, dragAmount ->
                    accX += dragAmount
                    change.consume()
                },
            )
        }
    } else {
        Modifier
    }

    val bubbleClickModifier = Modifier
        .semantics(mergeDescendants = true) {
            contentDescription = "${message.senderUsername}: ${message.text}"
            role = Role.Button
        }
        .combinedClickable(
            onClick = {
                if (inSelectionMode && canDelete) onToggleSelection()
            },
            onLongClick = {
                if (deleted) return@combinedClickable
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                if (canDelete) {
                    if (inSelectionMode) onToggleSelection() else onBeginSelection()
                } else {
                    onOpenActions()
                }
            },
        )

    val baseBg = if (floatingSticker) Color.Transparent else bubbleBg
    val selectionTint = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    val highlightTint = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    val finalBg = when {
        isSelected -> lerp(baseBg, selectionTint, 0.65f)
        highlighted -> lerp(baseBg, highlightTint, 0.55f)
        else -> baseBg
    }

    val bubblePadH = if (stickerStem != null) 8.dp else 12.dp
    val bubblePadBottom = if (stickerStem != null) 8.dp else 10.dp
    val bubblePadTop = when {
        tightClusterTop -> if (stickerStem != null) 5.dp else 6.dp
        stickerStem != null -> 8.dp
        else -> 10.dp
    }

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val maxBubble = minOf(
            maxWidth * com.lastasylum.alliance.ui.chat.ChatBubbleMaxWidthFraction,
            com.lastasylum.alliance.ui.chat.ChatBubbleMaxWidthCap,
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = clusterTop),
            verticalAlignment = Alignment.Bottom,
        ) {
            if (isMine) {
                Spacer(Modifier.weight(1f))
            }
            if (!isMine && inSelectionMode && canDelete) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelection() },
                )
            }
            if (!isMine && !deleted) {
                ChatSenderAvatar(
                    telegramUrl = telegramUrl,
                    size = ChatIncomingAvatarSize,
                    modifier = Modifier.padding(end = ChatIncomingAvatarEndPad),
                    fallbackName = displayName,
                )
            }
            if (isMine && inSelectionMode && canDelete) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelection() },
                )
            }

            when {
                deleted -> {
                    Surface(
                        modifier = Modifier
                            .chatBubbleWidth(
                                maxBubble = maxBubble,
                                expandToMax = true,
                            )
                            .then(bubbleClickModifier),
                        color = finalBg,
                        shape = bubbleShape,
                        tonalElevation = 0.dp,
                        shadowElevation = 2.dp,
                        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.28f)),
                    ) {
                        Column(
                            Modifier
                                .padding(horizontal = bubblePadH)
                                .padding(top = bubblePadTop, bottom = bubblePadBottom)
                                .then(swipeModifier),
                        ) {
                            Text(
                                text = stringResource(R.string.team_forum_message_deleted),
                                style = MaterialTheme.typography.bodyMedium,
                                color = timeMuted,
                            )
                            if (timeLabel.isNotBlank()) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                ) {
                                    Text(
                                        text = timeLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = timeMuted,
                                    )
                                }
                            }
                        }
                    }
                }
                floatingSticker -> {
                    val floatMod = Modifier
                        .widthIn(max = 232.dp)
                        .then(bubbleClickModifier)
                        .then(swipeModifier)
                    Column(modifier = floatMod) {
                        if (!isMine && showClusterHeader) {
                            ChatBubbleAuthorHeader(
                                teamTag = message.senderTeamTag,
                                serverNumber = message.senderServerNumber,
                                nickname = nickname.ifBlank { "—" },
                                nicknameColor = senderAccent,
                                senderRole = message.senderRole,
                                isMine = isMine,
                            )
                        }
                        message.forwardedFrom?.let { fwd ->
                            Text(
                                text = stringResource(
                                    R.string.chat_forwarded_from,
                                    chatSenderDisplayWithTag(
                                        fwd.senderTeamTag,
                                        fwd.senderUsername,
                                        fwd.senderServerNumber,
                                    ),
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = onBubble.copy(alpha = 0.78f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = if (!isMine && showClusterHeader) 2.dp else 0.dp)
                                .clip(bubbleShape),
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(ctx)
                                    .data(ZlobyakaStickerPack.assetUriForStem(stickerStem!!))
                                    .size(384)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = stringResource(R.string.cd_chat_sticker),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 200.dp),
                                contentScale = ContentScale.Fit,
                            )
                            if (timeLabel.isNotBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color.Black.copy(alpha = 0.45f),
                                    tonalElevation = 0.dp,
                                    shadowElevation = 0.dp,
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(6.dp),
                                ) {
                                    Text(
                                        text = timeLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                else -> {
                    Surface(
                        modifier = Modifier
                            .chatBubbleWidth(
                                maxBubble = maxBubble,
                                expandToMax = stickerStem == null,
                                compactMax = if (stickerStem != null) {
                                    minOf(maxBubble, 280.dp)
                                } else {
                                    maxBubble
                                },
                            )
                            .then(bubbleClickModifier),
                        color = finalBg,
                        shape = bubbleShape,
                        tonalElevation = 0.dp,
                        shadowElevation = if (isMine) 4.dp else 3.dp,
                        border = bubbleBorder,
                    ) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = bubblePadH)
                                .padding(top = bubblePadTop, bottom = bubblePadBottom)
                                .then(swipeModifier),
                            verticalArrangement = Arrangement.spacedBy(SquadRelayDimens.itemGap),
                        ) {
                            message.forwardedFrom?.let { fwd ->
                                Text(
                                    text = stringResource(
                                        R.string.chat_forwarded_from,
                                        chatSenderDisplayWithTag(
                                        fwd.senderTeamTag,
                                        fwd.senderUsername,
                                        fwd.senderServerNumber,
                                    ),
                                    ),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = onBubble.copy(alpha = 0.78f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (!isMine && showClusterHeader) {
                                ChatBubbleAuthorHeader(
                                    teamTag = message.senderTeamTag,
                                    serverNumber = message.senderServerNumber,
                                    nickname = nickname.ifBlank { "—" },
                                    nicknameColor = senderAccent,
                                    senderRole = message.senderRole,
                                    isMine = isMine,
                                )
                            }

                            message.replyTo?.let { rp ->
                                val rid = message.replyToMessageId
                                if (!rid.isNullOrBlank()) {
                                    val replyInteraction = remember(message.id, rid) {
                                        MutableInteractionSource()
                                    }
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable(
                                                interactionSource = replyInteraction,
                                                indication = ripple(bounded = true),
                                                onClick = { onJumpToMessage(rid) },
                                            ),
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isMine) {
                                            Color.White.copy(alpha = 0.12f)
                                        } else {
                                            scheme.onSurface.copy(alpha = 0.1f)
                                        },
                                        tonalElevation = 0.dp,
                                        shadowElevation = 0.dp,
                                    ) {
                                        Column(
                                            Modifier.padding(
                                                horizontal = SquadRelayDimens.itemGap,
                                                vertical = SquadRelayDimens.headerSubtitleGap + 2.dp,
                                            ),
                                        ) {
                                            Text(
                                                text = chatSenderDisplayWithTag(
                                                    rp.senderTeamTag,
                                                    rp.senderUsername,
                                                    rp.senderServerNumber,
                                                ),
                                                style = MaterialTheme.typography.labelMedium.copy(
                                                    fontWeight = FontWeight.SemiBold,
                                                ),
                                                color = if (isMine) {
                                                    Color.White.copy(alpha = 0.92f)
                                                } else {
                                                    scheme.onSurface.copy(alpha = 0.95f)
                                                },
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                text = replyPreviewText(rp.text),
                                                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                                                color = if (isMine) {
                                                    Color.White.copy(alpha = 0.78f)
                                                } else {
                                                    scheme.onSurface.copy(alpha = 0.72f)
                                                },
                                                maxLines = 4,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                }
                            }

                            when {
                                !message.fileRelativeUrl.isNullOrBlank() && !deleted -> {
                                    val fileUrl = message.fileRelativeUrl.trim()
                                    val fileAtt = ChatAttachment(
                                        url = fileUrl,
                                        kind = "file",
                                        filename = message.fileFilename,
                                        mimeType = "application/vnd.android.package-archive",
                                        size = null,
                                    )
                                    Column(
                                        Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        ChatFileAttachmentCard(
                                            attachment = fileAtt,
                                            isMine = isMine,
                                            onDownload = { onDownloadForumFile(message) },
                                            isDownloading = downloadingForumFileUrl == fileUrl,
                                        )
                                        if (message.text.isNotBlank()) {
                                            ChatMessageBodyText(
                                                text = message.text,
                                                onBubble = onBubble,
                                                timeLabel = timeLabel,
                                                isMine = isMine,
                                                isChainBottom = isChainBottom,
                                                messageId = message.id,
                                                otherReadUptoMessageId = null,
                                                timeMuted = timeMuted,
                                            )
                                        } else if (timeLabel.isNotBlank()) {
                                            Text(
                                                text = timeLabel,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = timeMuted,
                                            )
                                        }
                                    }
                                }
                                rawImagePaths.isNotEmpty() -> {
                                    val hasCaption = message.text.isNotBlank()
                                    Column(
                                        Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(0.dp),
                                    ) {
                                        Box(Modifier.fillMaxWidth()) {
                                            TelegramLikeAttachmentsGrid(
                                                urls = resolvedImageUrls,
                                                contentDescription = messageImageTapLabel,
                                                onOpen = { idx -> onOpenImages(resolvedImageUrls, idx) },
                                                modifier = Modifier.fillMaxWidth(),
                                                roundTileCorners = false,
                                                bottomRound = !hasCaption,
                                            )
                                            if (!hasCaption && timeLabel.isNotBlank()) {
                                                Surface(
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = Color.Black.copy(alpha = 0.45f),
                                                    tonalElevation = 0.dp,
                                                    shadowElevation = 0.dp,
                                                    modifier = Modifier
                                                        .align(Alignment.BottomEnd)
                                                        .padding(6.dp),
                                                ) {
                                                    Text(
                                                        text = timeLabel,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = Color.White,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                                    )
                                                }
                                            }
                                        }
                                        if (hasCaption) {
                                            TelegramImageCaptionBar(
                                                caption = message.text.trimEnd(),
                                                formattedTime = timeLabel,
                                                captionBarBg = captionBarBg,
                                                onBubble = onBubble,
                                                timeMuted = timeMuted,
                                                captionExpandKey = message.id,
                                            )
                                        }
                                    }
                                }
                                else -> {
                                    ChatMessageBodyText(
                                        text = message.text,
                                        onBubble = onBubble,
                                        timeLabel = timeLabel,
                                        isMine = isMine,
                                        isChainBottom = isChainBottom,
                                        messageId = message.id,
                                        otherReadUptoMessageId = null,
                                        timeMuted = timeMuted,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (!isMine) {
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

private fun mergedForumImagePaths(message: TeamForumMessageDto): List<String> {
    val list = message.imageRelativeUrls.toMutableList()
    val single = message.imageRelativeUrl?.trim()?.takeIf { it.isNotBlank() }
    if (single != null && list.none { it == single }) {
        list.add(0, single)
    }
    return list.filter { it.isNotBlank() }.distinct()
}
