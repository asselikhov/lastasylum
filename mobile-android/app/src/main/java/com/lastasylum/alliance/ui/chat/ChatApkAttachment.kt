package com.lastasylum.alliance.ui.chat

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.core.content.FileProvider
import com.lastasylum.alliance.BuildConfig
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.chat.ChatAttachment
import com.lastasylum.alliance.di.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.Locale

fun resolvedChatAttachmentUrl(raw: String): String =
    if (raw.startsWith("http", ignoreCase = true)) raw.trim()
    else BuildConfig.API_BASE_URL.trimEnd('/') + "/" + raw.trimStart('/')

fun chatAttachmentFileId(url: String): String? {
    val path = url.trim().removePrefix("/")
    val prefix = "chat/attachments/"
    if (!path.startsWith(prefix, ignoreCase = true)) return null
    return path.removePrefix(prefix).substringBefore('/').takeIf { it.isNotBlank() }
}

fun isChatApkAttachment(attachment: ChatAttachment): Boolean {
    if (attachment.kind == "file") return true
    val mime = attachment.mimeType?.lowercase(Locale.US).orEmpty()
    if (mime.contains("package-archive")) return true
    val name = attachment.filename?.lowercase(Locale.US).orEmpty()
    return name.endsWith(".apk")
}

fun formatChatFileSize(bytes: Long?): String {
    if (bytes == null || bytes <= 0L) return ""
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 0.95) {
        String.format(Locale.getDefault(), "%.1f MB", mb)
    } else {
        val kb = bytes / 1024.0
        String.format(Locale.getDefault(), "%.0f KB", kb)
    }
}

suspend fun downloadAndInstallChatApk(
    context: Context,
    attachment: ChatAttachment,
): Result<Unit> = withContext(Dispatchers.IO) {
    runCatching {
        val appContext = context.applicationContext
        val token = AppContainer.from(appContext).tokenStore.getAccessToken()
            ?: error(appContext.getString(R.string.chat_apk_download_auth))
        val url = resolvedChatAttachmentUrl(attachment.url)
        val safeName = attachment.filename
            ?.takeIf { it.endsWith(".apk", ignoreCase = true) }
            ?: "squadrelay-update.apk"
        val dir = File(appContext.cacheDir, "apk_updates").apply { mkdirs() }
        val outFile = File(dir, safeName.replace(Regex("[^\\w.\\-]+"), "_"))
        val client = OkHttpClient.Builder().build()
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error(appContext.getString(R.string.chat_apk_download_failed))
            }
            val body = response.body ?: error(appContext.getString(R.string.chat_apk_download_failed))
            outFile.outputStream().use { out -> body.byteStream().copyTo(out) }
        }
        if (outFile.length() <= 0L) {
            error(appContext.getString(R.string.chat_apk_download_failed))
        }
        withContext(Dispatchers.Main) {
            launchApkInstaller(appContext, outFile)
        }
    }
}

private fun launchApkInstaller(context: Context, apkFile: File) {
    val uri: Uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        apkFile,
    )
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (context !is android.app.Activity) {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
    runCatching { context.startActivity(intent) }
        .onFailure {
            Toast.makeText(
                context,
                context.getString(R.string.chat_apk_install_failed),
                Toast.LENGTH_LONG,
            ).show()
        }
}

fun queryDisplayName(context: Context, uri: Uri): String {
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (idx >= 0 && cursor.moveToFirst()) {
            return cursor.getString(idx)?.trim().orEmpty()
        }
    }
    return uri.lastPathSegment?.substringAfterLast('/')?.trim().orEmpty()
}
