package com.lastasylum.alliance.data.chat

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import okhttp3.MultipartBody
import okhttp3.RequestBody

interface ChatApi {
    @GET("chat/rooms")
    suspend fun listRooms(): List<ChatRoomDto>

    @POST("chat/rooms")
    suspend fun createRoom(@Body body: CreateChatRoomRequest): ChatRoomDto

    @PATCH("chat/rooms/{roomId}")
    suspend fun updateRoom(
        @Path("roomId") roomId: String,
        @Body body: UpdateChatRoomRequest,
    ): ChatRoomDto

    @DELETE("chat/rooms/{roomId}")
    suspend fun deleteRoom(@Path("roomId") roomId: String)

    @GET("chat/messages")
    suspend fun getMessages(
        @Query("roomId") roomId: String,
        @Query("before") before: String? = null,
        @Query("limit") limit: Int? = null,
    ): List<ChatMessage>

    @POST("chat/messages")
    suspend fun sendMessage(@Body request: SendMessageRequest): ChatMessage

    @Multipart
    @POST("chat/attachments")
    suspend fun uploadAttachment(
        @Part file: MultipartBody.Part,
        @Part("roomId") roomId: RequestBody,
    ): UploadChatAttachmentResponse

    @DELETE("chat/messages/{messageId}")
    suspend fun deleteMessage(@Path("messageId") messageId: String): ChatMessageDeleteResult
}
