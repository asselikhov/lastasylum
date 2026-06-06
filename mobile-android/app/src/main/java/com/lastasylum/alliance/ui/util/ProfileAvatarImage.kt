package com.lastasylum.alliance.ui.util

import android.content.Context
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Size
import com.lastasylum.alliance.BuildConfig
import com.lastasylum.alliance.ui.chat.SquadRelayImageRequests

fun resolvedProfileAvatarUrl(raw: String?): String? {
    val s = raw?.trim().orEmpty()
    if (s.isEmpty()) return null
    if (s.startsWith("http", ignoreCase = true)) return s
    return BuildConfig.API_BASE_URL.trimEnd('/') + "/" + s.trimStart('/')
}

/** Auth headers come from [com.lastasylum.alliance.ui.chat.SquadRelayImageLoader] OkHttp interceptor. */
fun profileAvatarImageRequest(context: Context, rawPath: String?): ImageRequest? {
    val url = resolvedProfileAvatarUrl(rawPath) ?: return null
    return ImageRequest.Builder(context)
        .data(url)
        .size(Size(128, 128))
        .crossfade(true)
        .build()
}

fun profileAvatarImageRequestOrChatFallback(
    context: Context,
    rawPath: String?,
): ImageRequest? {
    val url = resolvedProfileAvatarUrl(rawPath) ?: return null
    return SquadRelayImageRequests.chatAvatar(context, url)
}
