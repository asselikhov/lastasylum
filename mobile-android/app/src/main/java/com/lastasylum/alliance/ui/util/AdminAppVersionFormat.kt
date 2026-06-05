package com.lastasylum.alliance.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.lastasylum.alliance.R

fun formatAdminAppVersion(
    versionName: String?,
    versionCode: Int?,
): String? {
    val name = versionName?.trim().orEmpty()
    val code = versionCode
    if (name.isEmpty() || code == null || code < 0) return null
    return "$name ($code)"
}

@Composable
fun adminAppVersionLine(versionName: String?, versionCode: Int?): String {
    val name = versionName?.trim().orEmpty()
    val code = versionCode
    return if (name.isNotEmpty() && code != null && code >= 0) {
        stringResource(R.string.admin_app_version, name, code)
    } else {
        stringResource(R.string.admin_app_version_unknown)
    }
}
