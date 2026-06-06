package com.lastasylum.alliance.ui.components.team

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.ui.theme.premium.PremiumColors

/** Cyan ambient shimmer for unread journal/news cards. */
fun DrawScope.drawJournalUnreadWave(
    drift: Float,
    flicker: Float,
    lite: Boolean = false,
) {
    val w = size.width
    val corner = FeedCardDesignTokens.compactInnerCornerRadius.toPx()
    val shift = drift * w * 0.55f
    val scale = if (lite) 0.65f else 1f
    drawRoundRect(
        brush = Brush.horizontalGradient(
            colors = listOf(
                Color.Transparent,
                PremiumColors.accentCyan.copy(alpha = (0.10f + flicker * 0.14f) * scale),
                PremiumColors.accentCyanBright.copy(alpha = (0.20f + flicker * 0.16f) * scale),
                PremiumColors.accentPurple.copy(alpha = (0.08f + flicker * 0.10f) * scale),
                Color.Transparent,
            ),
            startX = -shift,
            endX = w * 1.4f - shift,
        ),
        topLeft = Offset(0f, 0f),
        size = Size(w, if (lite) 2.dp.toPx() else 3.dp.toPx()),
        cornerRadius = CornerRadius(corner, corner),
    )
    if (!lite) {
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    PremiumColors.accentCyan.copy(alpha = 0.04f + flicker * 0.06f),
                    Color.Transparent,
                ),
                endY = size.height * 0.35f,
            ),
            size = size,
            cornerRadius = CornerRadius(corner),
        )
    }
}
