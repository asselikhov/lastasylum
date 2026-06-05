package com.lastasylum.alliance.ui.chat

import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import coil3.imageLoader
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.ByteArrayOutputStream

data class ChatGallerySaveResult(
    val savedCount: Int,
    val failedCount: Int,
) {
    val totalRequested: Int get() = savedCount + failedCount
}

suspend fun saveChatImagesToGallery(
    context: Context,
    imageUrls: List<String>,
): ChatGallerySaveResult = withContext(Dispatchers.IO) {
    val urls = imageUrls.map { it.trim() }.filter { it.isNotBlank() }
    if (urls.isEmpty()) return@withContext ChatGallerySaveResult(0, 0)

    val appContext = context.applicationContext
    if (!canWriteImagesToGallery(appContext)) {
        return@withContext ChatGallerySaveResult(0, urls.size)
    }

    val client = authenticatedOkHttpClient(appContext)
    val imageLoader = appContext.imageLoader
    var saved = 0
    var failed = 0
    val timestamp = System.currentTimeMillis()

    for ((index, url) in urls.withIndex()) {
        val payload = downloadImagePayload(appContext, client, imageLoader, url)
        if (payload == null) {
            failed++
            continue
        }
        if (writeImageToGallery(appContext, payload.bytes, payload.mime, timestamp, index)) {
            saved++
        } else {
            failed++
        }
    }
    ChatGallerySaveResult(saved, failed)
}

private fun canWriteImagesToGallery(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return true
    return ContextCompat.checkSelfPermission(
        context,
        android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
    ) == PackageManager.PERMISSION_GRANTED
}

private data class ImagePayload(val bytes: ByteArray, val mime: String)

private suspend fun downloadImagePayload(
    appContext: Context,
    client: okhttp3.OkHttpClient,
    imageLoader: coil3.ImageLoader,
    url: String,
): ImagePayload? {
    downloadImageBytesViaHttp(client, url)?.let { return it }
    return downloadImageBytesViaCoil(appContext, imageLoader, url)
}

private fun downloadImageBytesViaHttp(
    client: okhttp3.OkHttpClient,
    url: String,
): ImagePayload? = runCatching {
    client.newCall(Request.Builder().url(url).build()).execute().use { response ->
        if (!response.isSuccessful) return@use null
        val body = response.body ?: return@use null
        val bytes = body.bytes()
        val mime = response.header("Content-Type")
            ?.substringBefore(";")
            ?.trim()
            ?.takeIf { it.startsWith("image/", ignoreCase = true) }
            ?: sniffImageMime(bytes)
            ?: "image/jpeg"
        ImagePayload(bytes, mime)
    }
}.getOrNull()

private suspend fun downloadImageBytesViaCoil(
    appContext: Context,
    imageLoader: coil3.ImageLoader,
    url: String,
): ImagePayload? = runCatching {
    val request = ImageRequest.Builder(appContext)
        .data(url)
        .allowHardware(false)
        .build()
    when (val result = imageLoader.execute(request)) {
        is SuccessResult -> {
            val bitmap = result.image.toBitmap()
            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, baos)
            ImagePayload(baos.toByteArray(), "image/jpeg")
        }
        is ErrorResult -> null
    }
}.getOrNull()

private fun writeImageToGallery(
    appContext: Context,
    bytes: ByteArray,
    mime: String,
    timestamp: Long,
    index: Int,
): Boolean {
    val ext = when {
        mime.contains("png", ignoreCase = true) -> "png"
        mime.contains("gif", ignoreCase = true) -> "gif"
        mime.contains("webp", ignoreCase = true) -> "webp"
        else -> "jpg"
    }
    val displayName = "squadrelay_${timestamp}_$index.$ext"
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
        put(MediaStore.Images.Media.MIME_TYPE, mime)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SquadRelay")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
    }
    val resolver = appContext.contentResolver
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
    try {
        resolver.openOutputStream(uri)?.use { out ->
            out.write(bytes)
        } ?: run {
            resolver.delete(uri, null, null)
            return false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            if (resolver.update(uri, values, null, null) <= 0) {
                resolver.delete(uri, null, null)
                return false
            }
        }
        return true
    } catch (_: Exception) {
        resolver.delete(uri, null, null)
        return false
    }
}

internal fun sniffImageMime(bytes: ByteArray): String? {
    if (bytes.size < 2) return null
    if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) return "image/jpeg"
    if (bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte()) return "image/png"
    if (bytes[0] == 'G'.code.toByte() && bytes[1] == 'I'.code.toByte()) return "image/gif"
    if (bytes.size >= 12 && bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte()) return "image/webp"
    return null
}
