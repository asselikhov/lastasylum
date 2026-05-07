package com.lastasylum.alliance.ui.screens.teamnews

import android.content.Context
import coil.request.ImageRequest
import com.lastasylum.alliance.BuildConfig
import com.lastasylum.alliance.di.AppContainer

fun resolvedTeamNewsImageUrl(raw: String?): String? {
    val s = raw?.trim().orEmpty()
    if (s.isEmpty()) return null
    if (s.startsWith("http", ignoreCase = true)) return s
    return BuildConfig.API_BASE_URL.trimEnd('/') + "/" + s.trimStart('/')
}

fun teamNewsAuthedImageRequest(context: Context, rawPath: String?): ImageRequest? {
    val url = resolvedTeamNewsImageUrl(rawPath) ?: return null
    return ImageRequest.Builder(context)
        .data(url)
        .apply {
            val token = AppContainer.from(context).tokenStore.getAccessToken()
            if (!token.isNullOrBlank()) {
                addHeader("Authorization", "Bearer $token")
            }
        }
        .crossfade(true)
        .build()
}
