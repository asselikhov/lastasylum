package com.lastasylum.alliance.data.network

import retrofit2.http.GET

data class AndroidUpdateResponse(
    val versionCode: Int,
    val downloadUrl: String? = null,
)

/**
 * Latest patched game APK descriptor from `GET /mobile/game-patch` (JWT-protected).
 * [downloadUrl] is a short-lived signed GitHub asset URL; download immediately.
 */
data class GamePatchInfo(
    val available: Boolean = false,
    val gameVersion: String? = null,
    val downloadUrl: String? = null,
    val sha256: String? = null,
    val sizeBytes: Long? = null,
    val bridgeVersion: String? = null,
)

interface MobileApi {
    @GET("mobile/android-update")
    suspend fun getAndroidUpdate(): AndroidUpdateResponse
}
