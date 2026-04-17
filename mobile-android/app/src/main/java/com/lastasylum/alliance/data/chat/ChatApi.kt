package com.lastasylum.alliance.data.chat

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface ChatApi {
    @GET("chat/messages")
    suspend fun getMessages(@Query("allianceId") allianceId: String = "OBZHORY"): List<ChatMessage>

    @POST("chat/messages")
    suspend fun sendMessage(@Body request: SendMessageRequest): ChatMessage
}
