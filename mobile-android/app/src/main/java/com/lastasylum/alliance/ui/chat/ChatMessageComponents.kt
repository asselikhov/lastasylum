package com.lastasylum.alliance.ui.chat

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.lastasylum.alliance.R
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lastasylum.alliance.ui.util.formatServerLabel
import com.lastasylum.alliance.ui.theme.ChatTelegramTeamTagBg
import com.lastasylum.alliance.ui.theme.ChatTelegramTeamTagFg
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.lastasylum.alliance.data.chat.ChatMessage
import com.lastasylum.alliance.data.chat.ChatReaction
import com.lastasylum.alliance.data.chat.chatImageAttachments
import com.lastasylum.alliance.data.chat.hasVisibleText
import com.lastasylum.alliance.di.AppContainer
import com.lastasylum.alliance.ui.theme.ChatTelegramIncomingBubble
import com.lastasylum.alliance.ui.theme.ChatTelegramOutgoingBubble

@Composable
fun ChatSenderAvatar(
    telegramUrl: String?,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    fallbackName: String? = null,
) {
    val ring = MaterialTheme.colorScheme.outlineVariant
    val fill = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f)
    val trimmed = fallbackName?.trim().orEmpty()
    val initialChar = trimmed.firstOrNull { it.isLetterOrDigit() }
        ?: trimmed.firstOrNull()
    val initial = initialChar?.uppercaseChar()?.toString() ?: "?"
    val ctx = LocalContext.current
    Surface(
        modifier = modifier.size(size),
        shape = CircleShape,
        color = fill,
        border = BorderStroke(1.dp, ring),
        tonalElevation = 0.dp,
        shadowElevation = 1.dp,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = initial,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!telegramUrl.isNullOrBlank()) {
                AsyncImage(
                    model = SquadRelayImageRequests.chatAvatar(ctx, telegramUrl),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            }
        }
    }
}

@Composable
private fun ChatServerNumberBadge(
    serverNumber: Int,
    isMine: Boolean,
    modifier: Modifier = Modifier,
) {
    val label = formatServerLabel(serverNumber) ?: return
    val fg = if (isMine) {
        Color.White.copy(alpha = 0.96f)
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.92f)
    }
    val bg = if (isMine) {
        Color.White.copy(alpha = 0.16f)
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(7.dp),
        color = bg,
        border = BorderStroke(1.dp, fg.copy(alpha = 0.28f)),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.4.sp,
            ),
            color = fg,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun ChatTeamTagBadge(
    teamTag: String,
    isMine: Boolean,
    modifier: Modifier = Modifier,
) {
    val raw = teamTag.trim().removePrefix("[").removeSuffix("]").trim()
    if (raw.isEmpty()) return
    val bg = if (isMine) {
        ChatTelegramTeamTagBg.copy(alpha = 0.88f)
    } else {
        ChatTelegramTeamTagBg
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(7.dp),
        color = bg,
        border = BorderStroke(1.dp, ChatTelegramTeamTagFg.copy(alpha = 0.22f)),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Text(
            text = "[$raw]",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = ChatTelegramTeamTagFg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
        )
    }
}

/**
 * Заголовок входящего пузыря: бейдж сервера, тег команды, ник и роль.
 */
@Composable
fun ChatBubbleAuthorHeader(
    teamTag: String?,
    nickname: String,
    nicknameColor: Color,
    tagBracketColor: Color,
    senderRole: String,
    serverNumber: Int? = null,
    isMine: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            val server = serverNumber?.takeIf { it >= 1 }
            if (server != null) {
                ChatServerNumberBadge(serverNumber = server, isMine = isMine)
            }
            teamTag?.trim()?.takeIf { it.isNotEmpty() }?.let { tag ->
                ChatTeamTagBadge(teamTag = tag, isMine = isMine)
            }
            Text(
                text = nickname.trim().ifBlank { "—" },
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.1.sp,
                ),
                color = nicknameColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = true),
            )
        }
        if (senderRole.isNotBlank()) {
            Spacer(modifier = Modifier.width(6.dp))
            RoleBadge(role = senderRole)
        }
    }
}

/** Текст сообщения на всю ширину капли; время и галочки — отдельной строкой справа. */
@Composable
fun ChatMessageBodyText(
    text: String,
    onBubble: Color,
    timeLabel: String,
    isMine: Boolean,
    isChainBottom: Boolean,
    messageId: String?,
    otherReadUptoMessageId: String?,
    timeMuted: Color,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.width(maxWidth)) {
            Text(
                text = text,
                style = textStyle,
                color = onBubble,
                modifier = Modifier.fillMaxWidth(),
            )
            if (timeLabel.isNotBlank() || (isMine && isChainBottom)) {
                ChatMessageTimeWithReadStatus(
                    time = timeLabel,
                    isMine = isMine,
                    isChainBottom = isChainBottom,
                    messageId = messageId,
                    otherReadUptoMessageId = otherReadUptoMessageId,
                    timeColor = timeMuted,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 4.dp),
                )
            }
        }
    }
}

/** Подпись под медиа в одном пузыре (как в Telegram): отдельная полоса + время справа снизу. */
@Composable
fun TelegramImageCaptionBar(
    caption: String,
    formattedTime: String,
    captionBarBg: Color,
    onBubble: Color,
    timeMuted: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(captionBarBg)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = caption,
            style = MaterialTheme.typography.bodyMedium,
            color = onBubble,
            modifier = Modifier.weight(1f),
        )
        if (formattedTime.isNotBlank()) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = formattedTime,
                style = MaterialTheme.typography.labelSmall,
                color = timeMuted,
                modifier = Modifier.padding(bottom = 1.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TelegramLikeAttachmentsGrid(
    urls: List<String>,
    contentDescription: String,
    onOpen: (Int) -> Unit,
    modifier: Modifier = Modifier,
    roundTileCorners: Boolean = true,
    bottomRound: Boolean = true,
    enabled: Boolean = true,
    onLongPress: (() -> Unit)? = null,
) {
    if (urls.isEmpty()) return
    // Telegram-like albums: show up to 6 tiles; for more, overlay +N on the last one.
    val maxShown = 6
    val shown = urls.take(maxShown)
    val extra = (urls.size - shown.size).coerceAtLeast(0)
    val corner = 12.dp
    val gap = 4.dp

    fun tileShapeFor(shownCount: Int, tileIndex: Int): RoundedCornerShape {
        val r = corner
        fun tl() = if (roundTileCorners) r else 0.dp
        fun tr() = if (roundTileCorners) r else 0.dp
        fun bl() = if (roundTileCorners && bottomRound) r else 0.dp
        fun br() = if (roundTileCorners && bottomRound) r else 0.dp
        return when (shownCount) {
            1 -> RoundedCornerShape(topStart = tl(), topEnd = tr(), bottomStart = bl(), bottomEnd = br())
            2 -> when (tileIndex) {
                0 -> RoundedCornerShape(topStart = tl(), topEnd = 0.dp, bottomStart = bl(), bottomEnd = 0.dp)
                else -> RoundedCornerShape(topStart = 0.dp, topEnd = tr(), bottomStart = 0.dp, bottomEnd = br())
            }
            3 -> when (tileIndex) {
                0 -> RoundedCornerShape(topStart = tl(), topEnd = 0.dp, bottomStart = bl(), bottomEnd = 0.dp)
                1 -> RoundedCornerShape(topStart = 0.dp, topEnd = tr(), bottomStart = 0.dp, bottomEnd = 0.dp)
                else -> RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp, bottomStart = 0.dp, bottomEnd = br())
            }
            4 -> when (tileIndex) {
                0 -> RoundedCornerShape(topStart = tl(), topEnd = 0.dp, bottomStart = 0.dp, bottomEnd = 0.dp)
                1 -> RoundedCornerShape(topStart = 0.dp, topEnd = tr(), bottomStart = 0.dp, bottomEnd = 0.dp)
                2 -> RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp, bottomStart = bl(), bottomEnd = 0.dp)
                else -> RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp, bottomStart = 0.dp, bottomEnd = br())
            }
            5, 6 -> {
                // 5 = 2 + 3; 6 = 3 + 3
                val topCols = if (shownCount == 5) 2 else 3
                val topCount = topCols
                val row = if (tileIndex < topCount) 0 else 1
                val col = if (row == 0) tileIndex else tileIndex - topCount
                val cols = if (row == 0) topCols else 3
                val isTL = row == 0 && col == 0
                val isTR = row == 0 && col == cols - 1
                val isBL = row == 1 && col == 0
                val isBR = row == 1 && col == cols - 1
                RoundedCornerShape(
                    topStart = if (isTL) tl() else 0.dp,
                    topEnd = if (isTR) tr() else 0.dp,
                    bottomStart = if (isBL) bl() else 0.dp,
                    bottomEnd = if (isBR) br() else 0.dp,
                )
            }
            else -> RoundedCornerShape(tl(), tr(), bl(), br())
        }
    }

    @Composable
    fun tile(idx: Int, modifier: Modifier) {
        val u = shown.getOrNull(idx) ?: return
        Box(
            modifier = modifier
                .clip(tileShapeFor(shown.size, idx))
                .then(
                    if (enabled) {
                        val semanticsMod = Modifier.semantics {
                            this.contentDescription = contentDescription
                            role = Role.Button
                        }
                        if (onLongPress != null) {
                            semanticsMod.combinedClickable(
                                onClick = { onOpen(idx) },
                                onLongClick = onLongPress,
                            )
                        } else {
                            semanticsMod.clickable { onOpen(idx) }
                        }
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            val ctx = LocalContext.current
            AsyncImage(
                model = SquadRelayImageRequests.chatThumbnail(ctx, u),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            if (extra > 0 && idx == shown.lastIndex) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.38f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "+$extra",
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }

    Column(modifier = modifier) {
        when (shown.size) {
            1 -> tile(0, Modifier.fillMaxWidth().heightIn(max = 260.dp))
            2 -> Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp),
                horizontalArrangement = Arrangement.spacedBy(gap),
            ) {
                tile(0, Modifier.weight(1f).fillMaxHeight())
                tile(1, Modifier.weight(1f).fillMaxHeight())
            }
            3 -> Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                horizontalArrangement = Arrangement.spacedBy(gap),
            ) {
                tile(0, Modifier.weight(1.3f).fillMaxHeight())
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(gap),
                ) {
                    tile(1, Modifier.weight(1f).fillMaxWidth())
                    tile(2, Modifier.weight(1f).fillMaxWidth())
                }
            }
            4 -> Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp),
                verticalArrangement = Arrangement.spacedBy(gap),
            ) {
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(gap),
                ) {
                    tile(0, Modifier.weight(1f).fillMaxHeight())
                    tile(1, Modifier.weight(1f).fillMaxHeight())
                }
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(gap),
                ) {
                    tile(2, Modifier.weight(1f).fillMaxHeight())
                    tile(3, Modifier.weight(1f).fillMaxHeight())
                }
            }
            5 -> Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp),
                verticalArrangement = Arrangement.spacedBy(gap),
            ) {
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(gap),
                ) {
                    tile(0, Modifier.weight(1f).fillMaxHeight())
                    tile(1, Modifier.weight(1f).fillMaxHeight())
                }
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(gap),
                ) {
                    tile(2, Modifier.weight(1f).fillMaxHeight())
                    tile(3, Modifier.weight(1f).fillMaxHeight())
                    tile(4, Modifier.weight(1f).fillMaxHeight())
                }
            }
            else -> Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp),
                verticalArrangement = Arrangement.spacedBy(gap),
            ) {
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(gap),
                ) {
                    tile(0, Modifier.weight(1f).fillMaxHeight())
                    tile(1, Modifier.weight(1f).fillMaxHeight())
                    tile(2, Modifier.weight(1f).fillMaxHeight())
                }
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(gap),
                ) {
                    tile(3, Modifier.weight(1f).fillMaxHeight())
                    tile(4, Modifier.weight(1f).fillMaxHeight())
                    tile(5, Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
    }
}

/** Helper for media+caption inside a bubble (used by screens and overlays). */
@Composable
fun ChatBubbleAttachmentsWithCaption(
    message: ChatMessage,
    isMine: Boolean,
    onBubble: Color,
    timeMuted: Color,
    formattedTime: String,
    onOpenRemoteImages: (List<String>, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val messageImageTapLabel = LocalContext.current.getString(R.string.cd_chat_message_image)
    val imageAttachments = message.chatImageAttachments()
    if (imageAttachments.isEmpty()) return
    val fullResolvedUrls = imageAttachments.map { resolvedChatAttachmentImageUrl(it.url) }
    val captionBarBg = if (isMine) {
        lerp(ChatTelegramOutgoingBubble, Color.Black, 0.18f)
    } else {
        lerp(ChatTelegramIncomingBubble, Color.Black, 0.24f)
    }
    val hasCaption = message.hasVisibleText()
    Column(modifier = modifier) {
        Box(modifier = Modifier.fillMaxWidth()) {
            TelegramLikeAttachmentsGrid(
                urls = fullResolvedUrls,
                contentDescription = messageImageTapLabel,
                onOpen = { idx -> onOpenRemoteImages(fullResolvedUrls, idx) },
                modifier = Modifier.fillMaxWidth(),
                roundTileCorners = false,
                bottomRound = !hasCaption,
            )
            if (!hasCaption && formattedTime.isNotBlank()) {
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
                        text = formattedTime,
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
                formattedTime = formattedTime,
                captionBarBg = captionBarBg,
                onBubble = onBubble,
                timeMuted = timeMuted,
            )
        }
    }
}

@Composable
fun ChatMessageReactionsRow(
    reactions: List<ChatReaction>,
    onReactionToggle: ((emoji: String) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val visible = reactions.filter { it.count > 0 }
    if (visible.isEmpty()) return
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        visible.forEach { r ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (r.reactedByMe) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                },
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                modifier = if (onReactionToggle != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onReactionToggle(r.emoji) },
                    )
                } else {
                    Modifier
                },
            ) {
                Text(
                    text = "${r.emoji} ${r.count}",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (r.reactedByMe) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}

fun resolvedChatAttachmentImageUrl(raw: String): String =
    if (raw.startsWith("http", ignoreCase = true)) raw.trim()
    else com.lastasylum.alliance.BuildConfig.API_BASE_URL.trimEnd('/') + "/" + raw.trimStart('/')

@Composable
fun ChatFileAttachmentCard(
    attachment: com.lastasylum.alliance.data.chat.ChatAttachment,
    isMine: Boolean,
    onDownload: () -> Unit,
    isDownloading: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val title = attachment.filename?.takeIf { it.isNotBlank() }
        ?: context.getString(R.string.chat_copy_apk_placeholder)
    val sizeLabel = formatChatFileSize(attachment.size)
    val cardBg = if (isMine) {
        lerp(ChatTelegramOutgoingBubble, Color.Black, 0.12f)
    } else {
        lerp(ChatTelegramIncomingBubble, Color.Black, 0.08f)
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = cardBg,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (sizeLabel.isNotBlank()) {
                Text(
                    text = sizeLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(
                onClick = onDownload,
                enabled = !isDownloading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isDownloading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(stringResource(R.string.chat_apk_download))
                }
            }
        }
    }
}

