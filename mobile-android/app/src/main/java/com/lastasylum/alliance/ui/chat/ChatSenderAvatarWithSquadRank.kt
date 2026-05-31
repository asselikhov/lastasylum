package com.lastasylum.alliance.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lastasylum.alliance.R
import com.lastasylum.alliance.ui.theme.roleAccentColor

/**
 * Аватар с чипом ранга на нижней границе — как в оверлее «Участники онлайн».
 */
@Composable
fun ChatSenderAvatarWithSquadRank(
    telegramUrl: String?,
    squadRole: String,
    modifier: Modifier = Modifier,
    size: Dp = ChatIncomingAvatarSize,
    fallbackName: String? = null,
) {
    val role = squadRole.trim().uppercase()
    val roleColor = roleAccentColor(role)
    val roleCd = if (role.isNotBlank()) {
        stringResource(R.string.overlay_member_squad_rank_cd, role)
    } else {
        null
    }
    Box(
        modifier = modifier,
        contentAlignment = Alignment.BottomCenter,
    ) {
        ChatSenderAvatar(
            telegramUrl = telegramUrl,
            size = size,
            fallbackName = fallbackName,
        )
        if (role.isNotBlank()) {
            SquadRankChipOnAvatar(
                role = role,
                color = roleColor,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = 6.dp)
                    .then(
                        if (roleCd != null) {
                            Modifier.semantics { contentDescription = roleCd }
                        } else {
                            Modifier
                        },
                    ),
            )
        }
    }
}

@Composable
fun SquadRankChipOnAvatar(
    role: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val chipRadius = 10.dp
    Text(
        text = role,
        style = TextStyle(
            fontWeight = FontWeight.SemiBold,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            color = color,
        ),
        modifier = modifier
            .clip(RoundedCornerShape(chipRadius))
            .background(color.copy(alpha = 0.14f))
            .border(0.5.dp, color.copy(alpha = 0.35f), RoundedCornerShape(chipRadius))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        maxLines = 1,
    )
}
