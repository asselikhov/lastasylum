package com.lastasylum.alliance.data.network

import retrofit2.http.GET

data class AndroidUpdateResponse(
    val versionCode: Int,
    val downloadUrl: String? = null,
)

interface MobileApi {
    @GET("mobile/android-update")
    suspend fun getAndroidUpdate(): AndroidUpdateResponse
}
