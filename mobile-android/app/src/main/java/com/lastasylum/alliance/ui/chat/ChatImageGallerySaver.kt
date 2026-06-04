package com.lastasylum.alliance.ui.chat

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import com.lastasylum.alliance.data.network.AuthInterceptor
import com.lastasylum.alliance.di.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

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
    val tokenStore = AppContainer.from(appContext).tokenStore
    val client = OkHttpClient.Builder()
        .addInterceptor(AuthInterceptor(tokenStore))
        .build()
    var saved = 0
    var failed = 0
    val timestamp = System.currentTimeMillis()

    for ((index, url) in urls.withIndex()) {
        try {
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) {
                    failed++
                    return@use
                }
                val body = response.body ?: run {
                    failed++
                    return@use
                }
                val bytes = body.bytes()
                val mime = response.header("Content-Type")
                    ?.substringBefore(";")
                    ?.trim()
                    ?.takeIf { it.startsWith("image/", ignoreCase = true) }
                    ?: sniffImageMime(bytes)
                    ?: "image/jpeg"
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
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: run {
                        failed++
                        return@use
                    }
                resolver.openOutputStream(uri)?.use { out ->
                    out.write(bytes)
                } ?: run {
                    failed++
                    resolver.delete(uri, null, null)
                    return@use
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                }
                saved++
            }
        } catch (_: Exception) {
            failed++
        }
    }
    ChatGallerySaveResult(saved, failed)
}

private fun sniffImageMime(bytes: ByteArray): String? {
    if (bytes.size < 12) return null
    if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) return "image/jpeg"
    if (bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte()) return "image/png"
    if (bytes[0] == 'G'.code.toByte() && bytes[1] == 'I'.code.toByte()) return "image/gif"
    if (bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte()) return "image/webp"
    return null
}
