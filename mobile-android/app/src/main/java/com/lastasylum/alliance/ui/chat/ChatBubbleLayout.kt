package com.lastasylum.alliance.ui.chat

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Доля ширины строки под пузырь (как в мессенджерах: не на весь экран, но широко). */
const val ChatBubbleMaxWidthFraction = 0.88f

/** Оверлей-чат: шире пузырь под текст на всю «каплю». */
const val ChatOverlayBubbleMaxWidthFraction = 0.94f

val ChatBubbleMaxWidthCap = 360.dp

val ChatOverlayBubbleMaxWidthCap = 400.dp

private val SelectionCheckboxReserve = 52.dp

/**
 * Строка сообщения: считает [maxBubbleWidth] с учётом аватара и чекбокса,
 * для исходящих — [Spacer] с weight, чтобы пузырь прижимался к краю.
 */
@Composable
fun ChatMessageBubbleRow(
    isMine: Boolean,
    clusterTopSpacing: Dp,
    inSelectionMode: Boolean,
    canDelete: Boolean,
    showIncomingAvatar: Boolean,
    /** Keeps bubble indent when avatar is hidden mid-stack (Telegram grouping). */
    reserveIncomingAvatarSpace: Boolean = false,
    leadingAvatar: @Composable () -> Unit,
    selectionControl: @Composable RowScope.() -> Unit,
    bubbleWidthFraction: Float = ChatBubbleMaxWidthFraction,
    bubbleWidthCap: Dp = ChatBubbleMaxWidthCap,
    bubble: @Composable (maxBubbleWidth: Dp) -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = clusterTopSpacing),
    ) {
        val checkboxReserve =
            if (inSelectionMode && canDelete) SelectionCheckboxReserve else 0.dp
        val avatarReserve =
            if (!isMine && (showIncomingAvatar || reserveIncomingAvatarSpace)) {
                ChatIncomingAvatarSize + ChatIncomingAvatarEndPad
            } else {
                0.dp
            }
        val maxBubble = minOf(
            (maxWidth - checkboxReserve - avatarReserve) * bubbleWidthFraction,
            bubbleWidthCap,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
        ) {
            if (isMine) {
                selectionControl()
                Spacer(modifier = Modifier.weight(1f, fill = true))
            } else {
                selectionControl()
                when {
                    showIncomingAvatar -> leadingAvatar()
                    reserveIncomingAvatarSpace -> {
                        Spacer(
                            modifier = Modifier.width(
                                ChatIncomingAvatarSize + ChatIncomingAvatarEndPad,
                            ),
                        )
                    }
                }
            }
            bubble(maxBubble)
        }
    }
}

/**
 * Ширина колонки пузыря. Для текста — [widthIn] до [maxBubble], чтобы длинные строки
 * заполняли каплю; [fillMaxWidth] на Surface внутри даёт Text полную ширину.
 */
fun Modifier.chatBubbleWidth(
    maxBubble: Dp,
    expandToMax: Boolean,
    compactMax: Dp = maxBubble,
): Modifier = if (expandToMax) {
    widthIn(max = maxBubble)
} else {
    widthIn(max = compactMax)
}

/** Surface пузыря растягивается на ширину колонки (иначе сжимается по min intrinsic Text). */
fun Modifier.chatBubbleSurfaceWidth(expandToMax: Boolean): Modifier =
    if (expandToMax) fillMaxWidth() else this

fun chatBubbleExpandsToRowWidth(
    floatingSticker: Boolean,
    floatingImages: Boolean,
    stickerStem: String?,
): Boolean = !floatingSticker && !floatingImages && stickerStem == null
