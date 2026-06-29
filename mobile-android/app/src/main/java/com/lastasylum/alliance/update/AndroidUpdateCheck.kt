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

sealed class AppUpdateCheckResult {
    data class Available(val downloadUrl: String) : AppUpdateCheckResult()
    data object UpToDate : AppUpdateCheckResult()
    data object Failed : AppUpdateCheckResult()
}

/** Distinguishes network/API failure from «already on latest build». */
suspend fun checkAppUpdate(): AppUpdateCheckResult = runCatching {
    val info = NetworkModule.mobileApi.getAndroidUpdate()
    val url = info.downloadUrl?.trim().orEmpty()
    if (info.versionCode > BuildConfig.VERSION_CODE && url.isNotEmpty()) {
        AppUpdateCheckResult.Available(url)
    } else {
        AppUpdateCheckResult.UpToDate
    }
}.getOrElse { AppUpdateCheckResult.Failed }

/** Returns download URL if server reports a newer [versionCode] and URL is non-empty. */
suspend fun fetchNewerApkDownloadUrl(): String? = when (val result = checkAppUpdate()) {
    is AppUpdateCheckResult.Available -> result.downloadUrl
    else -> null
}

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

/**
 * Скачивает публичный APK (GitHub Releases и т.п.) в кэш и возвращает файл.
 *
 * Установку (запуск системного установщика) намеренно НЕ делаем здесь: запуск Activity
 * установщика из фонового сервиса блокируется ограничениями Background Activity Launch на
 * многих прошивках (MIUI/EMUI/ColorOS и т.п.), причём без исключения — окно просто не
 * появляется. Поэтому установку запускает foreground-[com.lastasylum.alliance.MainActivity].
 */
suspend fun downloadAppUpdateApk(
    context: Context,
    downloadUrl: String,
): Result<File> = withContext(Dispatchers.IO) {
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
        outFile
    }
}

/**
 * Открывает системный установщик для уже скачанного APK. Должно вызываться из foreground
 * (например, Activity), иначе на части прошивок запуск молча блокируется.
 *
 * @return false, если запустить установщик не удалось (например, нет разрешения «установка из
 * этого источника» или фоновый запуск заблокирован).
 */
fun Context.installDownloadedApk(apkFile: File): Boolean {
    val uri: Uri = FileProvider.getUriForFile(
        this,
        "${packageName}.fileprovider",
        apkFile,
    )
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (this@installDownloadedApk !is android.app.Activity) {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
    return runCatching { startActivity(intent) }
        .onFailure {
            Toast.makeText(
                this,
                getString(R.string.chat_apk_install_failed),
                Toast.LENGTH_LONG,
            ).show()
        }
        .isSuccess
}
