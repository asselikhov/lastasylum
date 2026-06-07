package com.lastasylum.alliance.data.chat

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ChatSyncStateDto(
    val historyClearedAt: String? = null,
)
