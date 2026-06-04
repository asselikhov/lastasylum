package com.lastasylum.alliance.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.lastasylum.alliance.R
import com.lastasylum.alliance.ui.theme.SquadRelayDimens
import kotlin.math.roundToInt

data class MessageContextMenuActions(
    val onReply: () -> Unit,
    val onCopy: () -> Unit,
    val onPin: (() -> Unit)? = null,
    val onUnpin: (() -> Unit)? = null,
    val onEdit: (() -> Unit)? = null,
    val onDelete: (() -> Unit)? = null,
    val onReact: (String) -> Unit,
    val onViewImages: (() -> Unit)? = null,
    val onGoToMap: (() -> Unit)? = null,
    val onForward: (() -> Unit)? = null,
    val onPasteToInput: (() -> Unit)? = null,
)

@Composable
fun MessageContextMenuPopup(
    anchorBounds: Rect,
    showReactions: Boolean,
    canCopy: Boolean,
    canPin: Boolean,
    isPinned: Boolean,
    pinActionsEnabled: Boolean,
    mayEdit: Boolean,
    canDelete: Boolean,
    hasImages: Boolean,
    hasMapCoordinate: Boolean,
    canForward: Boolean = true,
    canPasteToInput: Boolean = false,
    onDismiss: () -> Unit,
    actions: MessageContextMenuActions,
) {
    val density = LocalDensity.current
    val screenHeightPx = with(density) {
        LocalConfiguration.current.screenHeightDp.dp.toPx()
    }
    val menuWidthPx = with(density) { 240.dp.toPx() }
    val reactionHeightPx = with(density) { 52.dp.toPx() }
    val menuHeightEstimatePx = with(density) { 280.dp.toPx() }
    val marginPx = with(density) { 8.dp.toPx() }

    val centerX = anchorBounds.left + anchorBounds.width / 2f
    val left = (centerX - menuWidthPx / 2f)
        .coerceIn(marginPx, with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() } - menuWidthPx - marginPx)
    val placeAbove = anchorBounds.top > menuHeightEstimatePx + reactionHeightPx + marginPx * 2
    val top = if (placeAbove) {
        (anchorBounds.top - reactionHeightPx - menuHeightEstimatePx - marginPx)
            .coerceAtLeast(marginPx)
    } else {
        (anchorBounds.bottom + marginPx).coerceAtMost(screenHeightPx - menuHeightEstimatePx - marginPx)
    }

    Popup(
        alignment = Alignment.TopStart,
        offset = IntOffset(left.roundToInt(), top.roundToInt()),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.widthIn(min = 200.dp, max = 280.dp),
        ) {
            if (showReactions) {
                MessageContextReactionStrip(
                    onReact = { emoji ->
                        actions.onReact(emoji)
                        onDismiss()
                    },
                )
            }
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
            ) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    MessageSheetActionRow(
                        icon = Icons.AutoMirrored.Outlined.Reply,
                        label = stringResource(R.string.chat_action_reply),
                        onClick = {
                            actions.onReply()
                            onDismiss()
                        },
                    )
                    if (canCopy) {
                        MessageSheetActionRow(
                            icon = Icons.Outlined.ContentCopy,
                            label = stringResource(R.string.chat_action_copy),
                            onClick = {
                                actions.onCopy()
                                onDismiss()
                            },
                        )
                    }
                    if (hasImages) {
                        MessageSheetActionRow(
                            icon = Icons.Outlined.Image,
                            label = stringResource(R.string.chat_action_view_images),
                            onClick = {
                                actions.onViewImages?.invoke()
                                onDismiss()
                            },
                        )
                    }
                    if (canPin && !isPinned) {
                        MessageSheetActionRow(
                            icon = Icons.Outlined.PushPin,
                            label = stringResource(R.string.forum_action_pin),
                            onClick = {
                                actions.onPin?.invoke()
                                onDismiss()
                            },
                            enabled = pinActionsEnabled,
                        )
                    }
                    if (canPin && isPinned) {
                        MessageSheetActionRow(
                            icon = Icons.Outlined.PushPin,
                            label = stringResource(R.string.forum_action_unpin),
                            onClick = {
                                actions.onUnpin?.invoke()
                                onDismiss()
                            },
                            enabled = pinActionsEnabled,
                        )
                    }
                    if (mayEdit) {
                        MessageSheetActionRow(
                            icon = Icons.Outlined.Edit,
                            label = stringResource(R.string.chat_action_edit),
                            onClick = {
                                actions.onEdit?.invoke()
                            },
                        )
                    }
                    if (canDelete) {
                        MessageSheetActionRow(
                            icon = Icons.Outlined.DeleteOutline,
                            label = stringResource(R.string.chat_action_delete),
                            onClick = {
                                actions.onDelete?.invoke()
                                onDismiss()
                            },
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                    if (hasMapCoordinate) {
                        MessageSheetActionRow(
                            icon = Icons.Outlined.Place,
                            label = stringResource(R.string.chat_action_go_to_map),
                            onClick = {
                                actions.onGoToMap?.invoke()
                                onDismiss()
                            },
                        )
                    }
                    if (canForward) {
                        MessageSheetActionRow(
                            icon = Icons.Outlined.Share,
                            label = stringResource(R.string.chat_action_forward),
                            onClick = {
                                actions.onForward?.invoke()
                                onDismiss()
                            },
                        )
                    }
                    if (canPasteToInput) {
                        MessageSheetActionRow(
                            icon = Icons.Outlined.ContentCopy,
                            label = stringResource(R.string.chat_action_paste_to_input),
                            onClick = {
                                actions.onPasteToInput?.invoke()
                                onDismiss()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MessageContextMenuScrim(onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.32f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onDismiss,
            ),
    )
}

@Composable
private fun MessageContextReactionStrip(onReact: (String) -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        tonalElevation = 4.dp,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChatQuickReactions.defaults.forEach { emoji ->
                Text(
                    text = emoji,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier
                        .clickable { onReact(emoji) }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}
