package com.lastasylum.alliance.ui.chat

import android.content.Context
import android.net.Uri
import com.lastasylum.alliance.overlay.OverlayPickedImages

/** Stable FileProvider URIs for compose preview and upload (picker grants may expire). */
fun stabilizeComposerImageUris(context: Context, uris: List<Uri>): List<Uri> {
    if (uris.isEmpty()) return emptyList()
    val copied = OverlayPickedImages.copyToCache(context, uris)
    return if (copied.isNotEmpty()) copied else uris
}
