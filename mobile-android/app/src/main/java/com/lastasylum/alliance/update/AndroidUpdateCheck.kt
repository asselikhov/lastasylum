package com.lastasylum.alliance.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.lastasylum.alliance.BuildConfig
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.network.NetworkModule

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
