package com.lastasylum.alliance.push

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import androidx.compose.ui.graphics.toArgb
import com.lastasylum.alliance.ui.theme.roleAccentColor
import com.lastasylum.alliance.ui.theme.roleOnAccentColor
import com.lastasylum.alliance.ui.util.telegramAvatarUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Large notification icon: round avatar + squad rank chip (as in chat). */
object PushNotificationSenderAvatar {
    private const val OUTPUT_PX = 128

    suspend fun loadLargeIcon(
        context: Context,
        telegramUsername: String?,
        squadRole: String?,
        fallbackName: String?,
    ): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val avatar = loadAvatarBitmap(context, telegramUsername, fallbackName)
            composeWithRank(avatar, squadRole, fallbackName)
        }.getOrNull()
    }

    private suspend fun loadAvatarBitmap(
        context: Context,
        telegramUsername: String?,
        fallbackName: String?,
    ): Bitmap? {
        val url = telegramAvatarUrl(telegramUsername)
        if (!url.isNullOrBlank()) {
            val loader = ImageLoader(context.applicationContext)
            val request = ImageRequest.Builder(context.applicationContext)
                .data(url)
                .size(OUTPUT_PX, OUTPUT_PX)
                .allowHardware(false)
                .build()
            val result = loader.execute(request)
            if (result is SuccessResult) {
                return result.image.toBitmap().scale(OUTPUT_PX, OUTPUT_PX)
            }
        }
        return initialsBitmap(fallbackName)
    }

    private fun initialsBitmap(fallbackName: String?): Bitmap {
        val bmp = createBitmap(OUTPUT_PX, OUTPUT_PX)
        val canvas = Canvas(bmp)
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2A3444.toInt() }
        canvas.drawCircle(OUTPUT_PX / 2f, OUTPUT_PX / 2f, OUTPUT_PX / 2f, bg)
        val initial = fallbackName?.trim()?.firstOrNull { it.isLetterOrDigit() }
            ?.uppercaseChar()
            ?.toString()
            ?: "?"
        val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFC8D4E4.toInt()
            textSize = OUTPUT_PX * 0.42f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val y = OUTPUT_PX / 2f - (text.descent() + text.ascent()) / 2f
        canvas.drawText(initial, OUTPUT_PX / 2f, y, text)
        return bmp
    }

    private fun composeWithRank(
        avatar: Bitmap?,
        squadRole: String?,
        fallbackName: String?,
    ): Bitmap {
        val base = avatar ?: initialsBitmap(fallbackName)
        val out = createBitmap(OUTPUT_PX, OUTPUT_PX)
        val canvas = Canvas(out)
        val clip = Path().apply {
            addCircle(OUTPUT_PX / 2f, OUTPUT_PX / 2f, OUTPUT_PX / 2f - 2f, Path.Direction.CW)
        }
        canvas.save()
        canvas.clipPath(clip)
        canvas.drawBitmap(base, 0f, 0f, null)
        canvas.restore()
        val role = squadRole?.trim()?.uppercase().orEmpty()
        if (role.isNotBlank()) {
            drawRankChip(canvas, role, OUTPUT_PX)
        }
        return out
    }

    private fun drawRankChip(canvas: Canvas, role: String, sizePx: Int) {
        val accent = roleAccentColor(role)
        val onAccent = roleOnAccentColor(role)
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = onAccent.toArgb()
            textSize = sizePx * 0.16f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val padH = sizePx * 0.08f
        val padV = sizePx * 0.03f
        val textW = textPaint.measureText(role)
        val chipW = textW + padH * 2f
        val chipH = textPaint.textSize + padV * 2f
        val left = (sizePx - chipW) / 2f
        val top = sizePx - chipH - sizePx * 0.02f
        val rect = RectF(left, top, left + chipW, top + chipH)
        val radius = chipH / 2f
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accent.copy(alpha = 0.22f).toArgb()
        }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = sizePx * 0.008f
            color = accent.copy(alpha = 0.4f).toArgb()
        }
        canvas.drawRoundRect(rect, radius, radius, fill)
        canvas.drawRoundRect(rect, radius, radius, stroke)
        val textY = rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(role, rect.centerX(), textY, textPaint)
    }
}
