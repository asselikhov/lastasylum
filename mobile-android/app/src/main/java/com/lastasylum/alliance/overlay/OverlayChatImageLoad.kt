package com.lastasylum.alliance.overlay

import android.content.Context
import coil.request.ImageRequest
import com.lastasylum.alliance.BuildConfig
fun resolvedChatAttachmentImageUrl(raw: String): String =
    if (raw.startsWith("http", ignoreCase = true)) raw.trim()
    else BuildConfig.API_BASE_URL.trimEnd('/') + "/" + raw.trimStart('/')

fun overlayAuthedImageRequest(
    context: Context,
    url: String,
    configure: ImageRequest.Builder.() -> Unit = {},
): ImageRequest =
    ImageRequest.Builder(context.applicationContext)
        .data(url)
        .allowHardware(false)
        .apply(configure)
        .crossfade(false)
        .build()
