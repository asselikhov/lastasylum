package com.lastasylum.alliance.game

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.FileProvider
import com.lastasylum.alliance.BuildConfig
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.chat.SquadRelayMoshi
import com.lastasylum.alliance.data.network.GamePatchInfo
import com.lastasylum.alliance.di.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest

/**
 * One-button game patch: asks the backend ([BuildConfig.API_BASE_URL] + `mobile/game-patch`,
 * JWT) for the latest patched APK descriptor, downloads it from the short-lived signed GitHub
 * URL, verifies the sha256, then drives the system installer.
 *
 * The patched APK is signed with the patch keystore (not Google's), so the first switch from a
 * stock install needs an uninstall ([requestUninstallStockGame]); re-patches between game
 * versions keep the same signature and install over the previous patch directly.
 */
object GamePatchInstaller {
    private const val PATCH_ENDPOINT = "mobile/game-patch"
    private const val CACHE_DIR = "game_patch"

    sealed interface Prepare {
        /** Patch downloaded and integrity-verified; ready to install. */
        data class Ready(val apk: File, val gameVersion: String?) : Prepare
        /** Backend reports no patch is currently published. */
        data object Unavailable : Prepare
        /** User-facing error; [messageRes] is a localized string resource. */
        data class Failed(val messageRes: Int) : Prepare
    }

    /** Fetch descriptor, download the APK and verify its sha256. Runs off the main thread. */
    suspend fun prepareLatestPatch(context: Context): Prepare = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val token = AppContainer.from(appContext).tokenStore.getAccessToken()
            ?: return@withContext Prepare.Failed(R.string.game_patch_error_auth)

        val info = runCatching { fetchPatchInfo(token) }.getOrNull()
            ?: return@withContext Prepare.Failed(R.string.game_patch_error_network)
        val url = info.downloadUrl?.trim().orEmpty()
        if (!info.available || url.isEmpty()) {
            return@withContext Prepare.Unavailable
        }

        val apk = runCatching { download(appContext, url) }.getOrNull()
            ?: return@withContext Prepare.Failed(R.string.game_patch_error_download)
        if (apk.length() <= 0L) {
            apk.delete()
            return@withContext Prepare.Failed(R.string.game_patch_error_download)
        }

        val expected = info.sha256?.trim()?.lowercase().orEmpty()
        if (expected.isNotEmpty() && sha256Hex(apk) != expected) {
            apk.delete()
            return@withContext Prepare.Failed(R.string.game_patch_error_integrity)
        }
        Prepare.Ready(apk, info.gameVersion)
    }

    private fun fetchPatchInfo(token: String): GamePatchInfo? {
        val url = BuildConfig.API_BASE_URL.trimEnd('/') + "/" + PATCH_ENDPOINT
        val client = OkHttpClient.Builder().build()
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            return SquadRelayMoshi.build()
                .adapter(GamePatchInfo::class.java)
                .fromJson(body)
        }
    }

    private fun download(context: Context, url: String): File {
        val dir = File(context.cacheDir, CACHE_DIR).apply { mkdirs() }
        val outFile = File(dir, "game-patched.apk")
        if (outFile.exists()) outFile.delete()
        val client = OkHttpClient.Builder().build()
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("download failed: ${response.code}")
            val body = response.body ?: error("empty body")
            outFile.outputStream().use { out -> body.byteStream().copyTo(out) }
        }
        return outFile
    }

    private fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** Whether a stock (non-patched) build of the target game is installed. */
    fun isStockGameInstalled(context: Context): Boolean {
        val appContext = context.applicationContext
        val status = GameMapPatchStatus.read(appContext, gamePackages(appContext))
        return status.state == GameMapPatchStatus.State.PATCH_NOT_INSTALLED
    }

    /** Launch the system uninstall dialog for the installed game (needed before first patch). */
    fun requestUninstallStockGame(context: Context): Boolean {
        val appContext = context.applicationContext
        val pkg = installedGamePackage(appContext) ?: return false
        val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")).apply {
            if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    /** Hand the verified APK to the system installer (single universal APK). */
    fun installPrepared(context: Context, apk: File): Boolean {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    private fun gamePackages(context: Context): List<String> =
        GameDeepLinkNavigator.targetPackages(context)

    private fun installedGamePackage(context: Context): String? {
        val pm = context.packageManager
        return gamePackages(context).firstOrNull { pkg ->
            runCatching { pm.getPackageInfo(pkg, 0) }.isSuccess
        }
    }
}
