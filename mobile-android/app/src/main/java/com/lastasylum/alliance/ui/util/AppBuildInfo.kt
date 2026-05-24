package com.lastasylum.alliance.ui.util

import android.content.Context
import com.lastasylum.alliance.BuildConfig
import com.lastasylum.alliance.R
import java.time.Instant

/** User-visible build labels (Settings, login footer). */
object AppBuildInfo {
    fun versionWithCommit(): String =
        "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · ${BuildConfig.GIT_COMMIT}"

    fun buildTimeFormatted(): String =
        formatIsoDateTimeRu(Instant.ofEpochMilli(BuildConfig.BUILD_TIME_MS).toString())

    /** Same format as the login screen: «Сборка … (N) · дата». */
    fun authStyleBuildFooter(context: Context): String =
        context.getString(
            R.string.auth_build_footer,
            "${BuildConfig.VERSION_NAME} · ${BuildConfig.GIT_COMMIT}",
            BuildConfig.VERSION_CODE,
            buildTimeFormatted(),
        )
}
