package com.lastasylum.alliance.data.stt

import okhttp3.MultipartBody
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

data class SttResponse(
    val text: String,
)

interface SttApi {
    @Multipart
    @POST("stt/transcribe")
    suspend fun transcribe(@Part audio: MultipartBody.Part): SttResponse
}
