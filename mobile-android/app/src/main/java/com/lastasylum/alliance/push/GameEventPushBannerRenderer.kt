package com.lastasylum.alliance.push

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import com.lastasylum.alliance.R
import com.lastasylum.alliance.gameevents.GameEventCategory
import com.lastasylum.alliance.gameevents.GameEventCatalog

/** Gradient header bitmap for expanded game-event push (category-colored). */
object GameEventPushBannerRenderer {
    private const val WIDTH = 1024
    private const val HEIGHT = 280

    fun createBanner(
        context: Context,
        category: GameEventCategory,
        teamDisplayName: String,
        attentionPrefix: String,
        attentionSuffix: String,
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
        drawWatermark(canvas, context, category, palette)
        drawAttentionHeadline(
            canvas = canvas,
            context = context,
            teamDisplayName = teamDisplayName,
            attentionPrefix = attentionPrefix,
            attentionSuffix = attentionSuffix,
            palette = palette,
        )
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

    private fun drawWatermark(
        canvas: Canvas,
        context: Context,
        category: GameEventCategory,
        palette: CategoryPalette,
    ) {
        val label = when (category) {
            GameEventCategory.HQ -> context.getString(R.string.game_event_push_watermark_hq)
            GameEventCategory.PVE -> "PvE"
            GameEventCategory.PVP -> "PvP"
        }
        val x = WIDTH * 0.58f
        val y = HEIGHT * 0.78f
        val textSize = 216f
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.watermark
            alpha = 42
            this.textSize = textSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.watermark
            alpha = 56
            this.textSize = textSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(label, x, y, stroke)
        canvas.drawText(label, x, y, fill)
    }

    private fun drawAttentionHeadline(
        canvas: Canvas,
        context: Context,
        teamDisplayName: String,
        attentionPrefix: String,
        attentionSuffix: String,
        palette: CategoryPalette,
    ) {
        val inter = ResourcesCompat.getFont(context, R.font.inter)
            ?: Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        val teamName = teamDisplayName.trim().ifBlank { "—" }
        val attentionWord = attentionPrefix.trim().ifEmpty { "Внимание" }
        val exclamation = attentionSuffix.trim().ifEmpty { "!" }
        val space = " "

        var textSize = 52f
        val maxWidth = WIDTH * 0.88f
        val left = WIDTH * 0.06f

        val prefixPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.pillText
            alpha = 235
            typeface = Typeface.create(inter, Typeface.NORMAL)
        }
        val teamPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.pillStroke
            typeface = Typeface.create(inter, Typeface.BOLD)
            setShadowLayer(6f, 0f, 2f, 0x66000000)
        }
        val suffixPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.pillText
            alpha = 235
            typeface = Typeface.create(inter, Typeface.NORMAL)
        }

        fun totalWidth(size: Float): Float {
            prefixPaint.textSize = size
            teamPaint.textSize = size * 1.08f
            suffixPaint.textSize = size
            return prefixPaint.measureText(attentionWord) +
                prefixPaint.measureText(space) +
                teamPaint.measureText(teamName) +
                suffixPaint.measureText(space) +
                suffixPaint.measureText(exclamation)
        }

        while (totalWidth(textSize) > maxWidth && textSize > 34f) {
            textSize -= 2f
        }
        prefixPaint.textSize = textSize
        teamPaint.textSize = textSize * 1.08f
        suffixPaint.textSize = textSize

        val baseline = HEIGHT * 0.34f
        var x = left
        fun drawSegment(text: String, paint: Paint) {
            canvas.drawText(text, x, baseline, paint)
            x += paint.measureText(text)
        }
        drawSegment(attentionWord, prefixPaint)
        drawSegment(space, prefixPaint)
        drawSegment(teamName, teamPaint)
        drawSegment(space, suffixPaint)
        drawSegment(exclamation, suffixPaint)
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
