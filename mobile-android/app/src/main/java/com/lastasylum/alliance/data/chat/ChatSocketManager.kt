package com.lastasylum.alliance.data.chat

import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject

class ChatSocketManager {
    private var socket: Socket? = null

    fun connect(
        baseUrl: String,
        accessToken: String,
        onNewMessage: (ChatMessage) -> Unit,
    ) {
        if (socket?.connected() == true) return

        val options = IO.Options.builder()
            .setPath("/socket.io/")
            .setAuth(mapOf("token" to accessToken))
            .build()
        socket = IO.socket(baseUrl.trimEnd('/') + "/chat", options).apply {
            on(Socket.EVENT_CONNECT) {
                emit("room:join", JSONObject().put("allianceId", "OBZHORY"))
            }
            on("message:new") { args ->
                val payload = args.firstOrNull() as? JSONObject ?: return@on
                val message = ChatMessage(
                    _id = payload.optString("_id"),
                    allianceId = payload.optString("allianceId", "OBZHORY"),
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

    fun sendMessage(text: String, allianceId: String = "OBZHORY") {
        socket?.emit(
            "message:send",
            JSONObject()
                .put("allianceId", allianceId)
                .put("text", text),
        )
    }

    fun disconnect() {
        socket?.disconnect()
        socket?.off()
        socket = null
    }
}
