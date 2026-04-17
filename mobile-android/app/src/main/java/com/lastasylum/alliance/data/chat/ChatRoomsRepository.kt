package com.lastasylum.alliance.data.chat

class ChatRoomsRepository(
    private val chatApi: ChatApi,
) {
    suspend fun listRooms(): Result<List<ChatRoomDto>> = runCatching { chatApi.listRooms() }

    suspend fun createRoom(title: String, sortOrder: Int? = null): Result<ChatRoomDto> =
        runCatching { chatApi.createRoom(CreateChatRoomRequest(title = title, sortOrder = sortOrder)) }

    suspend fun updateRoom(
        roomId: String,
        title: String? = null,
        sortOrder: Int? = null,
        archived: Boolean? = null,
    ): Result<ChatRoomDto> =
        runCatching {
            chatApi.updateRoom(
                roomId,
                UpdateChatRoomRequest(title = title, sortOrder = sortOrder, archived = archived),
            )
        }

    suspend fun deleteRoom(roomId: String): Result<Unit> =
        runCatching { chatApi.deleteRoom(roomId) }
}
