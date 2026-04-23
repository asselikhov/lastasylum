package com.lastasylum.alliance.overlay

import android.content.Context
import coil.request.ImageRequest
import com.lastasylum.alliance.BuildConfig
import com.lastasylum.alliance.di.AppContainer

fun resolvedChatAttachmentImageUrl(raw: String): String =
    if (raw.startsWith("http", ignoreCase = true)) raw.trim()
    else BuildConfig.API_BASE_URL.trimEnd('/') + "/" + raw.trimStart('/')

fun overlayAuthedImageRequest(
    context: Context,
    url: String,
    configure: ImageRequest.Builder.() -> Unit = {},
): ImageRequest =
    ImageRequest.Builder(context)
        .data(url)
        .apply {
            val token = AppContainer.from(context).tokenStore.getAccessToken()
            if (!token.isNullOrBlank()) {
                addHeader("Authorization", "Bearer $token")
            }
        }
        .apply(configure)
        .crossfade(true)
        .build()
