package com.lastasylum.alliance.push

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import androidx.compose.ui.graphics.toArgb
import com.lastasylum.alliance.ui.theme.roleAccentColor

/** Canvas rank chip matching [com.lastasylum.alliance.ui.chat.SquadRankChipOnAvatar]. */
internal object SquadRankChipCanvas {
    /** 10sp on 38dp avatar ≈ 26% of avatar diameter. */
    const val TEXT_SIZE_RATIO = 0.263f
    /** Bottom offset 6dp on 38dp avatar (Compose [offset] on chip). */
    private const val BOTTOM_OFFSET_RATIO = 6f / 38f
    private const val FILL_ALPHA = 0.14f
    private const val STROKE_ALPHA = 0.35f
    private const val H_PAD_RATIO = 6f / 38f
    const val V_PAD_RATIO = 2f / 38f

    fun drawOnAvatarBottom(
        canvas: Canvas,
        role: String,
        avatarSizePx: Int,
        avatarLeftPx: Float = 0f,
        avatarTopPx: Float = 0f,
    ) {
        val normalizedRole = role.trim().uppercase()
        if (normalizedRole.isEmpty()) return
        val accent = roleAccentColor(normalizedRole)
        val textSize = avatarSizePx * TEXT_SIZE_RATIO
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accent.toArgb()
            this.textSize = textSize
            typeface = Typeface.create(Typeface.DEFAULT, 600, false)
            textAlign = Paint.Align.CENTER
        }
        val padH = avatarSizePx * H_PAD_RATIO
        val padV = avatarSizePx * V_PAD_RATIO
        val textW = textPaint.measureText(normalizedRole)
        val chipW = textW + padH * 2f
        val chipH = textPaint.textSize + padV * 2f
        val left = avatarLeftPx + (avatarSizePx - chipW) / 2f
        val bottomOffset = avatarSizePx * BOTTOM_OFFSET_RATIO
        val top = avatarTopPx + avatarSizePx + bottomOffset - chipH
        val rect = RectF(left, top, left + chipW, top + chipH)
        val radius = chipH / 2f
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accent.copy(alpha = FILL_ALPHA).toArgb()
        }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = avatarSizePx * (0.5f / 38f).coerceAtLeast(1f)
            color = accent.copy(alpha = STROKE_ALPHA).toArgb()
        }
        canvas.drawRoundRect(rect, radius, radius, fill)
        canvas.drawRoundRect(rect, radius, radius, stroke)
        val textY = rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(normalizedRole, rect.centerX(), textY, textPaint)
    }
}
