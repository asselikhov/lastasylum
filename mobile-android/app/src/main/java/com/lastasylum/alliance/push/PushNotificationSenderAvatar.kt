package com.lastasylum.alliance.push

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.text.TextPaint
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import coil3.imageLoader
import coil3.request.ErrorResult
import coil3.request.SuccessResult
import coil3.toBitmap
import com.lastasylum.alliance.ui.chat.SquadRelayImageRequests
import com.lastasylum.alliance.ui.util.resolvedProfileAvatarUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Large notification icon: transparent canvas, round avatar, squad rank chip on the bottom edge
 * (same layout as overlay «Участники онлайн» / [com.lastasylum.alliance.ui.chat.SquadRankChipOnAvatar]).
 */
object PushNotificationSenderAvatar {
    private const val OUTPUT_PX = 128
    /** ~48dp avatar in a 128px icon (overlay online panel). */
    private const val AVATAR_DIAMETER_PX = 88
    /** Chip sits [CHIP_OFFSET_RATIO] below avatar bottom (6dp on 38dp in chat). */
    private const val CHIP_OFFSET_RATIO = 6f / 38f
    private const val OVERLAY_AVATAR_FILL = 0xFF1E2A3A.toInt()
    private const val OVERLAY_INITIAL_COLOR = 0xFF94A8C0.toInt()

    suspend fun loadLargeIcon(
        context: Context,
        avatarRelativeUrl: String?,
        squadRole: String?,
        fallbackName: String?,
    ): Bitmap = withContext(Dispatchers.IO) {
        val avatar = loadAvatarBitmapWithRetry(context, avatarRelativeUrl, fallbackName)
        composeWithRank(avatar, squadRole, fallbackName)
    }

    /** Sync initials + rank chip for notify-first path (no network). */
    fun placeholderLargeIcon(
        squadRole: String?,
        fallbackName: String?,
    ): Bitmap = composeWithRank(null, squadRole, fallbackName)

    private suspend fun loadAvatarBitmapWithRetry(
        context: Context,
        avatarRelativeUrl: String?,
        fallbackName: String?,
    ): Bitmap? {
        val url = resolvedProfileAvatarUrl(avatarRelativeUrl)
        if (url.isNullOrBlank()) {
            return null
        }
        val appContext = context.applicationContext
        val loader = appContext.imageLoader
        repeat(2) { attempt ->
            val request = SquadRelayImageRequests.chatAvatar(appContext, url)
            when (val result = loader.execute(request)) {
                is SuccessResult -> {
                    return result.image.toBitmap().scale(AVATAR_DIAMETER_PX, AVATAR_DIAMETER_PX)
                }
                is ErrorResult -> {
                    if (attempt == 0) {
                        kotlinx.coroutines.delay(280)
                    }
                }
            }
        }
        return null
    }

    private fun initialsBitmap(fallbackName: String?): Bitmap {
        val bmp = createBitmap(AVATAR_DIAMETER_PX, AVATAR_DIAMETER_PX)
        val canvas = Canvas(bmp)
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = OVERLAY_AVATAR_FILL }
        canvas.drawCircle(
            AVATAR_DIAMETER_PX / 2f,
            AVATAR_DIAMETER_PX / 2f,
            AVATAR_DIAMETER_PX / 2f,
            bg,
        )
        val initial = fallbackName?.trim()?.firstOrNull { it.isLetterOrDigit() }
            ?.uppercaseChar()
            ?.toString()
            ?: "?"
        val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = OVERLAY_INITIAL_COLOR
            textSize = AVATAR_DIAMETER_PX * 0.42f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val y = AVATAR_DIAMETER_PX / 2f - (text.descent() + text.ascent()) / 2f
        canvas.drawText(initial, AVATAR_DIAMETER_PX / 2f, y, text)
        return bmp
    }

    internal fun composeWithRank(
        avatar: Bitmap?,
        squadRole: String?,
        fallbackName: String?,
    ): Bitmap {
        val base = avatar ?: initialsBitmap(fallbackName)
        val out = createBitmap(OUTPUT_PX, OUTPUT_PX)
        val canvas = Canvas(out)
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        val chipOffsetPx = AVATAR_DIAMETER_PX * CHIP_OFFSET_RATIO
        val contentHeight = AVATAR_DIAMETER_PX + chipOffsetPx
        val avatarTop = ((OUTPUT_PX - contentHeight) / 2f).coerceAtLeast(0f)
        val avatarLeft = (OUTPUT_PX - AVATAR_DIAMETER_PX) / 2f
        val centerX = avatarLeft + AVATAR_DIAMETER_PX / 2f
        val centerY = avatarTop + AVATAR_DIAMETER_PX / 2f
        val radius = AVATAR_DIAMETER_PX / 2f

        val clip = Path().apply {
            addCircle(centerX, centerY, radius, Path.Direction.CW)
        }
        canvas.save()
        canvas.clipPath(clip)
        canvas.drawBitmap(base, avatarLeft, avatarTop, null)
        canvas.restore()

        val role = squadRole?.trim()?.uppercase().orEmpty()
        if (role.isNotBlank()) {
            SquadRankChipCanvas.drawOnAvatarBottom(
                canvas = canvas,
                role = role,
                avatarSizePx = AVATAR_DIAMETER_PX,
                avatarLeftPx = avatarLeft,
                avatarTopPx = avatarTop,
            )
        }
        return out
    }
}
