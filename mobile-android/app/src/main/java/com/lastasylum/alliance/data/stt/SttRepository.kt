package com.lastasylum.alliance.data.stt

import java.io.File
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody

class SttRepository(
    private val sttApi: SttApi,
) {
    suspend fun transcribe(audioFile: File): Result<String> {
        if (!audioFile.exists()) {
            return Result.failure(IllegalArgumentException("Audio file not found"))
        }

        return runCatching {
            val requestBody = audioFile.asRequestBody("audio/mp4".toMediaTypeOrNull())
            val part = MultipartBody.Part.createFormData("audio", audioFile.name, requestBody)
            sttApi.transcribe(part).text
        }
    }
}
