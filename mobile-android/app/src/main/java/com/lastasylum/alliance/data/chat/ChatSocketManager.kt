package com.lastasylum.alliance.data.chat

import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject

class ChatSocketManager {
    private var socket: Socket? = null
    private var subscribedRoomId: String? = null

    fun connect(
        baseUrl: String,
        accessToken: String,
        roomId: String,
        onNewMessage: (ChatMessage) -> Unit,
    ) {
        if (socket?.connected() == true && subscribedRoomId == roomId) return

        disconnect()
        subscribedRoomId = roomId

        val options = IO.Options.builder()
            .setPath("/socket.io/")
            .setAuth(mapOf("token" to accessToken))
            .build()
        socket = IO.socket(baseUrl.trimEnd('/') + "/chat", options).apply {
            on(Socket.EVENT_CONNECT) {
                emit(
                    "room:join",
                    JSONObject().put("roomId", roomId),
                )
            }
            on("message:new") { args ->
                val payload = args.firstOrNull() as? JSONObject ?: return@on
                val msgRoom = payload.optString("roomId", "")
                if (msgRoom.isNotBlank() && msgRoom != roomId) return@on
                val message = ChatMessage(
                    _id = payload.optString("_id").takeIf { it.isNotBlank() },
                    allianceId = payload.optString(
                        "allianceId",
                        AllianceDefaults.DEFAULT_ALLIANCE_ID,
                    ),
                    roomId = msgRoom,
                    senderId = payload.optString("senderId"),
                    senderUsername = payload.optString("senderUsername"),
                    senderRole = payload.optString("senderRole"),
                    text = payload.optString("text"),
                    createdAt = payload.optString("createdAt"),
                )
                onNewMessage(message)
            }
            connect()
        }
    }

    fun sendMessage(text: String, roomId: String) {
        socket?.emit(
            "message:send",
            JSONObject()
                .put("roomId", roomId)
                .put("text", text),
        )
    }

    fun disconnect() {
        socket?.disconnect()
        socket?.off()
        socket = null
        subscribedRoomId = null
    }
}
