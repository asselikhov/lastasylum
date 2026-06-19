package com.lastasylum.alliance.ui.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.chat.PinnedMessagePreviewDto
import com.lastasylum.alliance.data.chat.chatSenderDisplayWithTag
import com.lastasylum.alliance.ui.theme.premium.PremiumSurfaces

enum class PinnedBarVariant {
    Default,
    Forum,
}

@Composable
fun pinnedPreviewLabel(
    preview: PinnedMessagePreviewDto,
    messageDeleted: Boolean = false,
    messageUnavailable: Boolean = false,
): String {
    if (messageDeleted) return stringResource(R.string.chat_pinned_preview_deleted)
    if (messageUnavailable) return stringResource(R.string.chat_pinned_preview_unavailable)
    return when {
        preview.isSticker -> stringResource(R.string.chat_pinned_preview_sticker)
        preview.hasImage && preview.text.isBlank() ->
            stringResource(R.string.chat_pinned_preview_photo)
        preview.text.isNotBlank() -> preview.text
        preview.hasImage -> stringResource(R.string.chat_pinned_preview_photo)
        else -> stringResource(R.string.chat_sheet_preview_empty)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PinnedMessageBar(
    preview: PinnedMessagePreviewDto,
    canUnpin: Boolean,
    onTap: () -> Unit,
    onUnpin: () -> Unit,
    modifier: Modifier = Modifier,
    historyCount: Int = 0,
    messageDeleted: Boolean = false,
    messageUnavailable: Boolean = false,
    thumbnailUrl: String? = null,
    pinnedMetaLine: String? = null,
    onLongPress: (() -> Unit)? = null,
    variant: PinnedBarVariant = PinnedBarVariant.Default,
) {
    AnimatedContent(
        targetState = preview.id,
        modifier = modifier,
        transitionSpec = {
            fadeIn(tween(180)) + slideInVertically(tween(200)) { it / 4 } togetherWith
                fadeOut(tween(120)) + slideOutVertically(tween(140)) { -it / 6 }
        },
        label = "pinnedBarPreview",
    ) { _ ->
        PinnedMessageBarContent(
            preview = preview,
            canUnpin = canUnpin,
            onTap = onTap,
            onUnpin = onUnpin,
            historyCount = historyCount,
            messageDeleted = messageDeleted,
            messageUnavailable = messageUnavailable,
            thumbnailUrl = thumbnailUrl,
            pinnedMetaLine = pinnedMetaLine,
            onLongPress = onLongPress,
            variant = variant,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PinnedMessageBarContent(
    preview: PinnedMessagePreviewDto,
    canUnpin: Boolean,
    onTap: () -> Unit,
    onUnpin: () -> Unit,
    historyCount: Int,
    messageDeleted: Boolean,
    messageUnavailable: Boolean,
    thumbnailUrl: String?,
    pinnedMetaLine: String?,
    onLongPress: (() -> Unit)?,
    variant: PinnedBarVariant,
) {
    val senderLine = chatSenderDisplayWithTag(
        preview.senderTeamTag,
        preview.senderUsername,
        preview.senderServerNumber,
    )
    val bodyLine = pinnedPreviewLabel(preview, messageDeleted, messageUnavailable)
    val forumStyle = variant == PinnedBarVariant.Forum
    val shape = RoundedCornerShape(if (forumStyle) 12.dp else 0.dp)
    val verticalPad = if (forumStyle) 7.dp else 8.dp
    val thumbSize = if (forumStyle) 36.dp else 32.dp
  Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = shape,
            color = if (forumStyle) {
                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
            tonalElevation = if (forumStyle) 2.dp else 1.dp,
            shadowElevation = if (forumStyle) 4.dp else 0.dp,
            border = if (forumStyle) {
                BorderStroke(1.dp, PremiumSurfaces.borderColor(0.16f))
            } else {
                null
            },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .combinedClickable(
                        onClick = onTap,
                        onLongClick = onLongPress ?: if (canUnpin) onUnpin else null,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .width(if (forumStyle) 4.dp else 3.dp)
                        .fillMaxHeight()
                        .background(
                            if (forumStyle) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.92f)
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                        ),
                )
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp, vertical = verticalPad),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (!thumbnailUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = thumbnailUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(thumbSize)
                                .clip(RoundedCornerShape(if (forumStyle) 8.dp else 6.dp)),
                            contentScale = ContentScale.Crop,
                        )
                    }
                    Icon(
                        imageVector = Icons.Outlined.PushPin,
                        contentDescription = stringResource(R.string.chat_pinned_bar_cd),
                        modifier = Modifier.size(if (forumStyle) 18.dp else 20.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            if (senderLine.isNotBlank()) {
                                Text(
                                    text = senderLine,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                    ),
                                    color = if (forumStyle) {
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f)
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                            }
                            if (historyCount > 1) {
                                Text(
                                    text = historyCount.coerceAtMost(99).toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary)
                                        .padding(horizontal = 5.dp, vertical = 1.dp),
                                )
                            }
                        }
                        Text(
                            text = bodyLine,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = if (forumStyle) 1 else 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!pinnedMetaLine.isNullOrBlank()) {
                            Text(
                                text = pinnedMetaLine,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (canUnpin) {
                        IconButton(
                            onClick = onUnpin,
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.chat_action_unpin),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        if (!forumStyle) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        }
    }
}
