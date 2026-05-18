package com.lastasylum.alliance.ui.screens.teamforum

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.teams.TeamForumMessageDto
import com.lastasylum.alliance.di.AppContainer
import com.lastasylum.alliance.ui.chat.formatChatFileSize
import com.lastasylum.alliance.ui.chat.resolvedChatAttachmentUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.Locale

fun isForumApkMessage(message: TeamForumMessageDto): Boolean {
    val url = message.fileRelativeUrl?.trim().orEmpty()
    if (url.isBlank()) return false
    val name = message.fileFilename?.lowercase(Locale.US).orEmpty()
    return name.endsWith(".apk") || url.contains(".apk", ignoreCase = true)
}

fun resolvedForumFileUrl(raw: String): String = resolvedChatAttachmentUrl(raw)

suspend fun downloadAndInstallForumApk(
    context: Context,
    fileRelativeUrl: String,
    filename: String?,
): Result<Unit> = withContext(Dispatchers.IO) {
    runCatching {
        val appContext = context.applicationContext
        val token = AppContainer.from(appContext).tokenStore.getAccessToken()
            ?: error(appContext.getString(R.string.chat_apk_download_auth))
        val url = resolvedForumFileUrl(fileRelativeUrl)
        val safeName = filename
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
            launchForumApkInstaller(appContext, outFile)
        }
    }
}

private fun launchForumApkInstaller(context: Context, apkFile: File) {
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

fun formatForumFileSize(bytes: Long?): String = formatChatFileSize(bytes)
