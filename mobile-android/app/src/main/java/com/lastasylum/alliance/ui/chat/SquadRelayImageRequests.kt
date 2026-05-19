package com.lastasylum.alliance.ui.chat

import android.content.Context
import coil.request.ImageRequest
import coil.size.Size
import com.lastasylum.alliance.di.AppContainer

/**
 * Единые [ImageRequest] с лимитом decode size — меньше RAM/лагов в чате и оверлее.
 */
object SquadRelayImageRequests {
    private const val CHAT_THUMB_PX = 512
    private const val CHAT_BUBBLE_MAX_PX = 720
    private const val AVATAR_PX = 128
    private const val STRIP_THUMB_PX = 320

    fun chatThumbnail(context: Context, url: String): ImageRequest =
        sizedAuthed(context, url, CHAT_THUMB_PX, CHAT_THUMB_PX, crossfade = true)

    fun chatBubbleImage(context: Context, url: String): ImageRequest =
        sizedAuthed(context, url, CHAT_BUBBLE_MAX_PX, CHAT_BUBBLE_MAX_PX, crossfade = true)

    fun chatAvatar(context: Context, url: String): ImageRequest =
        sizedAuthed(context, url, AVATAR_PX, AVATAR_PX, crossfade = true)

    fun overlayStripThumb(context: Context, url: String): ImageRequest =
        sizedAuthed(context, url, STRIP_THUMB_PX, STRIP_THUMB_PX, crossfade = false)

    private fun sizedAuthed(
        context: Context,
        url: String,
        widthPx: Int,
        heightPx: Int,
        crossfade: Boolean,
    ): ImageRequest {
        val builder = ImageRequest.Builder(context)
            .data(url)
            .size(Size(widthPx, heightPx))
            .apply {
                if (!crossfade) crossfade(false)
                val token = AppContainer.from(context).tokenStore.getAccessToken()
                if (!token.isNullOrBlank()) {
                    addHeader("Authorization", "Bearer $token")
                }
            }
        if (crossfade) {
            builder.crossfade(180)
        }
        return builder.build()
    }
}

fun chatAuthedImageRequest(context: Context, url: String): ImageRequest =
    SquadRelayImageRequests.chatBubbleImage(context, url)
