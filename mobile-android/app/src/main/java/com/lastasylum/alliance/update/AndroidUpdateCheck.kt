package com.lastasylum.alliance.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.lastasylum.alliance.BuildConfig
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.network.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/** Returns download URL if server reports a newer [versionCode] and URL is non-empty. */
suspend fun fetchNewerApkDownloadUrl(): String? = runCatching {
    val info = NetworkModule.mobileApi.getAndroidUpdate()
    val url = info.downloadUrl?.trim().orEmpty()
    if (info.versionCode > BuildConfig.VERSION_CODE && url.isNotEmpty()) {
        url
    } else {
        null
    }
}.getOrNull()

fun Context.openApkDownload(url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    if (this !is android.app.Activity) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    startActivity(intent)
}

fun Context.toastNoUpdateAvailable() {
    val c = applicationContext
    Toast.makeText(
        c,
        c.getString(R.string.profile_update_none),
        Toast.LENGTH_SHORT,
    ).show()
}

fun Context.toastUpdateCheckFailed() {
    val c = applicationContext
    Toast.makeText(
        c,
        c.getString(R.string.profile_update_error),
        Toast.LENGTH_SHORT,
    ).show()
}

/** Скачивает публичный APK (GitHub Releases и т.п.) и открывает установщик. */
suspend fun downloadAndInstallAppUpdate(
    context: Context,
    downloadUrl: String,
): Result<Unit> = withContext(Dispatchers.IO) {
    runCatching {
        val appContext = context.applicationContext
        val url = downloadUrl.trim()
        val safeName = Uri.parse(url).lastPathSegment
            ?.takeIf { it.endsWith(".apk", ignoreCase = true) }
            ?: "SquadRelay-update.apk"
        val dir = File(appContext.cacheDir, "apk_updates").apply { mkdirs() }
        val outFile = File(dir, safeName.replace(Regex("[^\\w.\\-]+"), "_"))
        val client = OkHttpClient.Builder().build()
        val request = Request.Builder().url(url).get().build()
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
