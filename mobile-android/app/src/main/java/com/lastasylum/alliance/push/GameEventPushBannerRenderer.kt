package com.lastasylum.alliance.push

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.graphics.createBitmap
import com.lastasylum.alliance.gameevents.GameEventCategory
import com.lastasylum.alliance.gameevents.GameEventCatalog

/** Gradient header bitmap for expanded game-event push (category-colored). */
object GameEventPushBannerRenderer {
    private const val WIDTH = 1024
    private const val HEIGHT = 280

    fun createBanner(
        category: GameEventCategory,
        categoryLabel: String,
    ): Bitmap {
        val palette = paletteFor(category)
        val bitmap = createBitmap(WIDTH, HEIGHT)
        val canvas = Canvas(bitmap)
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f,
                0f,
                WIDTH.toFloat(),
                HEIGHT.toFloat(),
                palette.gradientColors,
                palette.gradientStops,
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), bg)
        drawAccentGlow(canvas, palette.glowColor)
        drawCategoryPill(canvas, categoryLabel, palette)
        drawWatermark(canvas, category, palette)
        return bitmap
    }

    private fun drawAccentGlow(canvas: Canvas, glowColor: Int) {
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = glowColor
            alpha = 48
        }
        canvas.drawCircle(WIDTH * 0.82f, HEIGHT * 0.22f, HEIGHT * 0.55f, glow)
        canvas.drawCircle(WIDTH * 0.12f, HEIGHT * 0.78f, HEIGHT * 0.38f, glow)
    }

    private fun drawCategoryPill(
        canvas: Canvas,
        label: String,
        palette: CategoryPalette,
    ) {
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.pillText
            textSize = 44f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val padH = 36f
        val padV = 22f
        val textW = textPaint.measureText(label)
        val boxW = textW + padH * 2f
        val boxH = 44f + padV * 2f
        val left = 40f
        val top = 36f
        val rect = RectF(left, top, left + boxW, top + boxH)
        val path = Path().apply { addRoundRect(rect, 22f, 22f, Path.Direction.CW) }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.pillBg
            alpha = 210
        }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = palette.pillStroke
            alpha = 180
        }
        canvas.drawPath(path, fill)
        canvas.drawPath(path, stroke)
        val textY = rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(label, rect.left + padH, textY, textPaint)
    }

    private fun drawWatermark(
        canvas: Canvas,
        category: GameEventCategory,
        palette: CategoryPalette,
    ) {
        val short = when (category) {
            GameEventCategory.HQ -> "HQ"
            GameEventCategory.PVE -> "PvE"
            GameEventCategory.PVP -> "PvP"
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.watermark
            alpha = 38
            textSize = 200f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(short, WIDTH * 0.58f, HEIGHT * 0.78f, paint)
    }

    fun accentColor(category: GameEventCategory): Int =
        GameEventCatalog.notificationColor(category)

    private fun paletteFor(category: GameEventCategory): CategoryPalette = when (category) {
        GameEventCategory.HQ -> CategoryPalette(
            gradientColors = intArrayOf(
                0xFFFFD54F.toInt(),
                0xFFFFB300.toInt(),
                0xFFB86B00.toInt(),
                0xFF3D2E08.toInt(),
            ),
            gradientStops = floatArrayOf(0f, 0.35f, 0.72f, 1f),
            glowColor = 0xFFFFE082.toInt(),
            pillBg = 0xFF2A1F08.toInt(),
            pillStroke = 0xFFFFD54F.toInt(),
            pillText = 0xFFFFF8E1.toInt(),
            watermark = 0xFFFFB300.toInt(),
        )
        GameEventCategory.PVE -> CategoryPalette(
            gradientColors = intArrayOf(
                0xFF82B1FF.toInt(),
                0xFF3D5AFE.toInt(),
                0xFF1A237E.toInt(),
                0xFF0A1020.toInt(),
            ),
            gradientStops = floatArrayOf(0f, 0.38f, 0.75f, 1f),
            glowColor = 0xFF448AFF.toInt(),
            pillBg = 0xFF0D1528.toInt(),
            pillStroke = 0xFF82B1FF.toInt(),
            pillText = 0xFFE8F0FF.toInt(),
            watermark = 0xFF3D5AFE.toInt(),
        )
        GameEventCategory.PVP -> CategoryPalette(
            gradientColors = intArrayOf(
                0xFFFF8A80.toInt(),
                0xFFE53935.toInt(),
                0xFFB71C1C.toInt(),
                0xFF1A0808.toInt(),
            ),
            gradientStops = floatArrayOf(0f, 0.38f, 0.72f, 1f),
            glowColor = 0xFFFF5252.toInt(),
            pillBg = 0xFF240A0A.toInt(),
            pillStroke = 0xFFFF8A80.toInt(),
            pillText = 0xFFFFEBEE.toInt(),
            watermark = 0xFFE53935.toInt(),
        )
    }

    private data class CategoryPalette(
        val gradientColors: IntArray,
        val gradientStops: FloatArray,
        val glowColor: Int,
        val pillBg: Int,
        val pillStroke: Int,
        val pillText: Int,
        val watermark: Int,
    )
}
