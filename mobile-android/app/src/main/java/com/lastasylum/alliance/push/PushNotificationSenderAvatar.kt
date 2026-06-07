package com.lastasylum.alliance.push

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
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

/** Large notification icon: round avatar + squad rank chip on bottom edge (as in chat). */
object PushNotificationSenderAvatar {
    private const val OUTPUT_PX = 128
    /** Slightly smaller than canvas so rank chip fits inside the circular mask. */
    private const val AVATAR_PX = 96
    private const val AVATAR_TOP_PX = 8f

    suspend fun loadLargeIcon(
        context: Context,
        avatarRelativeUrl: String?,
        squadRole: String?,
        fallbackName: String?,
    ): Bitmap = withContext(Dispatchers.IO) {
        val avatar = loadAvatarBitmapWithRetry(context, avatarRelativeUrl, fallbackName)
        composeWithRank(avatar, squadRole, fallbackName)
    }

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
                    return result.image.toBitmap().scale(AVATAR_PX, AVATAR_PX)
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
        val bmp = createBitmap(AVATAR_PX, AVATAR_PX)
        val canvas = Canvas(bmp)
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2A3444.toInt() }
        canvas.drawCircle(AVATAR_PX / 2f, AVATAR_PX / 2f, AVATAR_PX / 2f, bg)
        val initial = fallbackName?.trim()?.firstOrNull { it.isLetterOrDigit() }
            ?.uppercaseChar()
            ?.toString()
            ?: "?"
        val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFC8D4E4.toInt()
            textSize = AVATAR_PX * 0.42f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val y = AVATAR_PX / 2f - (text.descent() + text.ascent()) / 2f
        canvas.drawText(initial, AVATAR_PX / 2f, y, text)
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
        val avatarLeft = (OUTPUT_PX - AVATAR_PX) / 2f
        val centerX = avatarLeft + AVATAR_PX / 2f
        val centerY = AVATAR_TOP_PX + AVATAR_PX / 2f
        val radius = AVATAR_PX / 2f - 1f
        val clip = Path().apply {
            addCircle(centerX, centerY, radius, Path.Direction.CW)
        }
        canvas.save()
        canvas.clipPath(clip)
        canvas.drawBitmap(base, avatarLeft, AVATAR_TOP_PX, null)
        canvas.restore()
        val role = squadRole?.trim()?.uppercase().orEmpty()
        if (role.isNotBlank()) {
            SquadRankChipCanvas.drawOnAvatarBottom(
                canvas = canvas,
                role = role,
                avatarSizePx = AVATAR_PX,
                avatarLeftPx = avatarLeft,
                avatarTopPx = AVATAR_TOP_PX,
            )
        }
        return out
    }
}
