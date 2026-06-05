package com.lastasylum.alliance.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.lastasylum.alliance.R

private val ContextMenuIconTint = Color(0xFFB8B8B8)
private val ContextMenuLabelColor = Color.White
private val ContextMenuSurfaceColor = Color(0xFF2F2F2F)

data class MessageContextMenuActions(
    val onReply: () -> Unit,
    val onCopy: () -> Unit,
    val onPin: (() -> Unit)? = null,
    val onUnpin: (() -> Unit)? = null,
    val onEdit: (() -> Unit)? = null,
    val onReact: (String) -> Unit,
    val onViewImages: (() -> Unit)? = null,
    val onSaveToGallery: (() -> Unit)? = null,
    val onGoToMap: (() -> Unit)? = null,
)

@Composable
fun MessageContextMenuPopup(
    showReactions: Boolean,
    canCopy: Boolean,
    canPin: Boolean,
    isPinned: Boolean,
    pinActionsEnabled: Boolean,
    mayEdit: Boolean,
    hasImages: Boolean,
    hasMapCoordinate: Boolean,
    onDismiss: () -> Unit,
    actions: MessageContextMenuActions,
) {
    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
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
                color = ContextMenuSurfaceColor.copy(alpha = 0.96f),
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
            ) {
                CompositionLocalProvider(LocalContentColor provides ContextMenuLabelColor) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        MessageSheetActionRow(
                            icon = Icons.AutoMirrored.Outlined.Reply,
                            label = stringResource(R.string.chat_action_reply),
                            iconTint = ContextMenuIconTint,
                            labelColor = ContextMenuLabelColor,
                            onClick = {
                                actions.onReply()
                                onDismiss()
                            },
                        )
                        if (canCopy) {
                            MessageSheetActionRow(
                                icon = Icons.Outlined.ContentCopy,
                                label = stringResource(R.string.chat_action_copy),
                                iconTint = ContextMenuIconTint,
                                labelColor = ContextMenuLabelColor,
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
                                iconTint = ContextMenuIconTint,
                                labelColor = ContextMenuLabelColor,
                                onClick = {
                                    actions.onViewImages?.invoke()
                                    onDismiss()
                                },
                            )
                            MessageSheetActionRow(
                                icon = Icons.Outlined.SaveAlt,
                                label = stringResource(R.string.chat_action_save_to_gallery),
                                iconTint = ContextMenuIconTint,
                                labelColor = ContextMenuLabelColor,
                                onClick = {
                                    actions.onSaveToGallery?.invoke()
                                    onDismiss()
                                },
                            )
                        }
                        if (canPin && !isPinned) {
                            MessageSheetActionRow(
                                icon = Icons.Outlined.PushPin,
                                label = stringResource(R.string.chat_action_pin),
                                iconTint = ContextMenuIconTint,
                                labelColor = ContextMenuLabelColor,
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
                                label = stringResource(R.string.chat_action_unpin),
                                iconTint = ContextMenuIconTint,
                                labelColor = ContextMenuLabelColor,
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
                                iconTint = ContextMenuIconTint,
                                labelColor = ContextMenuLabelColor,
                                onClick = {
                                    actions.onEdit?.invoke()
                                    onDismiss()
                                },
                            )
                        }
                        if (hasMapCoordinate) {
                            MessageSheetActionRow(
                                icon = Icons.Outlined.Place,
                                label = stringResource(R.string.chat_action_go_to_map),
                                iconTint = ContextMenuIconTint,
                                labelColor = ContextMenuLabelColor,
                                onClick = {
                                    actions.onGoToMap?.invoke()
                                    onDismiss()
                                },
                            )
                        }
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
        color = ContextMenuSurfaceColor.copy(alpha = 0.96f),
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
                    color = Color.White,
                    modifier = Modifier
                        .clickable { onReact(emoji) }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}
