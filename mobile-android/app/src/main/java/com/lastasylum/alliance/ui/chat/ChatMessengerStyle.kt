package com.lastasylum.alliance.ui.chat

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.ui.theme.ChatTelegramIncomingBubble
import com.lastasylum.alliance.ui.theme.ChatTelegramIncomingOnBubble
import com.lastasylum.alliance.ui.theme.ChatTelegramOutgoingBubble
import com.lastasylum.alliance.ui.theme.ChatTelegramOutgoingOnBubble
import com.lastasylum.alliance.ui.theme.ChatTelegramTimeMuted
import com.lastasylum.alliance.ui.theme.ChatTelegramTimeMutedIncoming

/** Shared Telegram-like messenger visuals for in-app and overlay chat. */
object ChatMessengerStyle {
    const val bubbleWidthFraction = ChatOverlayBubbleMaxWidthFraction
    val bubbleWidthCap = ChatOverlayBubbleMaxWidthCap

    val bubbleHorizontalPad = 14.dp
    val bubbleTopPad = 11.dp
    val bubbleBottomPad = 11.dp
    val stickerHorizontalPad = 8.dp
    val stickerVerticalPad = 8.dp

    val bubbleElevationMine = 2.dp
    val bubbleElevationOther = 2.dp

    fun bubbleBackground(isMine: Boolean, highlighted: Boolean, highlightTint: Color): Color {
        val base = if (isMine) ChatTelegramOutgoingBubble else ChatTelegramIncomingBubble
        return if (highlighted) {
            lerp(base, highlightTint, 0.55f)
        } else {
            base
        }
    }

    fun bubbleContentColor(isMine: Boolean): Color =
        if (isMine) ChatTelegramOutgoingOnBubble else ChatTelegramIncomingOnBubble

    fun timeMutedColor(isMine: Boolean): Color =
        if (isMine) ChatTelegramTimeMuted else ChatTelegramTimeMutedIncoming

    fun bubbleBorderColor(isMine: Boolean, highlighted: Boolean, highlightBorder: Color): Color =
        when {
            highlighted -> highlightBorder
            isMine -> Color.White.copy(alpha = 0.1f)
            else -> Color.White.copy(alpha = 0.06f)
        }

    @Composable
    fun messageTextStyle(typography: Typography): TextStyle = typography.bodyLarge

    fun bubbleHorizontalPadding(stickerStem: String?): Dp =
        if (stickerStem != null) stickerHorizontalPad else bubbleHorizontalPad

    fun bubbleTopPadding(stickerStem: String?, tightClusterTop: Boolean): Dp = when {
        tightClusterTop -> if (stickerStem != null) 5.dp else 6.dp
        stickerStem != null -> stickerVerticalPad
        else -> bubbleTopPad
    }

    fun bubbleBottomPadding(stickerStem: String?): Dp =
        if (stickerStem != null) stickerVerticalPad else bubbleBottomPad
}
