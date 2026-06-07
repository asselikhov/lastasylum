package com.lastasylum.alliance.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.lastasylum.alliance.ui.util.profileAvatarImageRequestOrChatFallback

/**
 * Round member avatar with presence dot on the top-left border (not clipped).
 */
@Composable
fun OverlayOnlineMemberAvatar(
    username: String,
    avatarRelativeUrl: String?,
    inGameNow: Boolean,
    freshness: PresenceFreshness,
    modifier: Modifier = Modifier,
    outerSize: Dp = OverlayOnlineMemberTokens.avatarOuter,
) {
    val tokens = OverlayOnlineMemberTokens
    val ctx = LocalContext.current
    val avatarModel = avatarRelativeUrl?.trim()?.takeIf { it.isNotEmpty() }
        ?.let { profileAvatarImageRequestOrChatFallback(ctx, it) }
    val letter = username.trim().take(1).uppercase().ifBlank { "?" }
    val ringColor = when {
        inGameNow && freshness == PresenceFreshness.StaleSoon -> tokens.borderStaleSoon
        inGameNow -> tokens.livePulse
        else -> Color.Transparent
    }
    val dotColor = when {
        inGameNow && freshness == PresenceFreshness.Fresh -> tokens.livePulse
        inGameNow && freshness == PresenceFreshness.StaleSoon -> tokens.borderStaleSoon
        else -> tokens.mutedColor
    }
    val dotSize = tokens.presenceDotSize
    val dotOverlap = tokens.presenceDotOverlap
    Box(
        modifier = modifier
            .padding(top = dotOverlap, start = dotOverlap)
            .size(outerSize),
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .then(
                    if (inGameNow) {
                        Modifier
                            .clip(CircleShape)
                            .background(ringColor.copy(alpha = 0.28f))
                            .padding(tokens.avatarRingWidth)
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(Color(0xFF1E2A3A)),
                contentAlignment = Alignment.Center,
            ) {
                if (avatarModel != null) {
                    AsyncImage(
                        model = avatarModel,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Text(
                        text = letter,
                        style = tokens.titleStyle.copy(color = tokens.metaColor),
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = -dotOverlap, y = -dotOverlap)
                .size(dotSize)
                .clip(CircleShape)
                .background(dotColor)
                .border(1.dp, Color(0xFF10141E), CircleShape),
        )
    }
}
