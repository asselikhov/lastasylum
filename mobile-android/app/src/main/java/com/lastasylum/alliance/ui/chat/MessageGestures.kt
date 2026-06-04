package com.lastasylum.alliance.ui.chat

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.lastasylum.alliance.overlay.OverlayChatInteractionHold

data class MessageActionOpenRequest(
    val messageId: String,
    val anchorBounds: Rect,
)

fun handleMessageTapForActions(
    messageId: String?,
    anchorBounds: Rect,
    inSelectionMode: Boolean,
    canDelete: Boolean,
    overlayUi: Boolean,
    onOpenActions: (MessageActionOpenRequest) -> Unit,
    onToggleSelection: (String) -> Unit,
) {
    if (messageId.isNullOrBlank()) return
    when {
        inSelectionMode && canDelete -> onToggleSelection(messageId)
        !inSelectionMode -> {
            if (overlayUi) {
                OverlayChatInteractionHold.prepareOverlayModalInteraction(true)
            }
            onOpenActions(
                MessageActionOpenRequest(
                    messageId = messageId,
                    anchorBounds = anchorBounds,
                ),
            )
        }
    }
}

fun handleMessageLongPressForSelection(
    messageId: String?,
    canDelete: Boolean,
    inSelectionMode: Boolean,
    haptics: HapticFeedback,
    onBeginSelection: (String) -> Unit,
    onToggleSelection: (String) -> Unit,
) {
    if (messageId.isNullOrBlank()) return
    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    when {
        inSelectionMode && canDelete -> onToggleSelection(messageId)
        canDelete -> onBeginSelection(messageId)
    }
}
