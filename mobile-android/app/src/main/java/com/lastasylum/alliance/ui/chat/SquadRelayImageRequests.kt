package com.lastasylum.alliance.ui.chat

import android.content.Context
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.size.Size
/**
 * Единые [ImageRequest] с лимитом decode size — меньше RAM/лагов в чате и оверлее.
 */
object SquadRelayImageRequests {
    private const val CHAT_THUMB_PX = 512
    /** Smaller decode for attachment tiles inside scrolling message lists. */
    private const val CHAT_LIST_THUMB_PX = 384
    private const val CHAT_BUBBLE_MAX_PX = 720
    private const val AVATAR_PX = 128
    private const val STRIP_THUMB_PX = 320

    fun chatThumbnail(context: Context, url: String): ImageRequest =
        sizedAuthed(context, url, CHAT_THUMB_PX, CHAT_THUMB_PX, animateCrossfade = true)

    /** Lazy list tiles: no crossfade animation while flinging. */
    fun chatThumbnailInList(context: Context, url: String): ImageRequest =
        sizedAuthed(context, url, CHAT_LIST_THUMB_PX, CHAT_LIST_THUMB_PX, animateCrossfade = false)

    fun chatBubbleImage(context: Context, url: String): ImageRequest =
        sizedAuthed(context, url, CHAT_BUBBLE_MAX_PX, CHAT_BUBBLE_MAX_PX, animateCrossfade = true)

    fun chatAvatar(context: Context, url: String): ImageRequest =
        sizedAuthed(context, url, AVATAR_PX, AVATAR_PX, animateCrossfade = true)

    fun overlayStripThumb(context: Context, url: String): ImageRequest =
        sizedAuthed(context, url, STRIP_THUMB_PX, STRIP_THUMB_PX, animateCrossfade = false)

    /** Превью content:// / FileProvider в композере (оверлей и приложение). */
    fun localUriPreview(context: Context, uri: android.net.Uri): ImageRequest =
        ImageRequest.Builder(context.applicationContext)
            .data(uri)
            .size(CHAT_THUMB_PX, CHAT_THUMB_PX)
            .allowHardware(false)
            .crossfade(false)
            .build()

    private fun sizedAuthed(
        context: Context,
        url: String,
        widthPx: Int,
        heightPx: Int,
        animateCrossfade: Boolean,
    ): ImageRequest {
        val appContext = context.applicationContext
        val builder = ImageRequest.Builder(appContext)
            .data(url)
            .allowHardware(false)
            .size(Size(widthPx, heightPx))
        if (animateCrossfade) {
            builder.crossfade(180)
        } else {
            builder.crossfade(false)
        }
        return builder.build()
    }
}

fun chatAuthedImageRequest(context: Context, url: String): ImageRequest =
    SquadRelayImageRequests.chatBubbleImage(context, url)
