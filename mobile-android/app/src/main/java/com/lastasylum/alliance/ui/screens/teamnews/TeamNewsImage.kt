package com.lastasylum.alliance.ui.screens.teamnews

import android.content.Context
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.lastasylum.alliance.BuildConfig

fun resolvedTeamNewsImageUrl(raw: String?): String? {
    val s = raw?.trim().orEmpty()
    if (s.isEmpty()) return null
    if (s.startsWith("http", ignoreCase = true)) return s
    return BuildConfig.API_BASE_URL.trimEnd('/') + "/" + s.trimStart('/')
}

/** Auth headers come from [com.lastasylum.alliance.ui.chat.SquadRelayImageLoader] OkHttp interceptor. */
fun teamNewsAuthedImageRequest(context: Context, rawPath: String?): ImageRequest? {
    val url = resolvedTeamNewsImageUrl(rawPath) ?: return null
    return ImageRequest.Builder(context)
        .data(url)
        .crossfade(true)
        .build()
}
