package com.lastasylum.alliance.overlay

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lastasylum.alliance.ui.components.team.ForumTopicCardTokens
import com.lastasylum.alliance.ui.theme.premium.PremiumColors

object OverlayOnlineMemberTokens {
    val cellShape = RoundedCornerShape(14.dp)
    val cellMinHeight = 120.dp
    val avatarOuter = 48.dp
    val avatarInner = 44.dp
    val avatarRingWidth = 2.dp
    val gridSpacing = 10.dp
    val gridPaddingH = 14.dp
    val chipRadius = 10.dp

    val glassFill = Color(0xFF141C28).copy(alpha = 0.92f)
    val glassFillStale = Color(0xFF121820).copy(alpha = 0.88f)
    val borderDefault = Color(0x334A7CFF)
    val borderLive = PremiumColors.accentCyan.copy(alpha = 0.45f)
    val borderStaleSoon = Color(0xFFFFB74D).copy(alpha = 0.55f)
    val borderRecent = Color(0x334A5E72)

    val titleColor = Color(0xFFF4F7FF)
    val metaColor = Color(0xFF94A8C0)
    val mutedColor = Color(0xFF7A8FA6)
    val livePulse = PremiumColors.liveIndicator

    val InterFamily = ForumTopicCardTokens.InterFamily

    val titleStyle = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        color = titleColor,
    )

    val metaStyle = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        color = metaColor,
    )

    val chipStyle = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.sp,
        lineHeight = 12.sp,
    )
}
