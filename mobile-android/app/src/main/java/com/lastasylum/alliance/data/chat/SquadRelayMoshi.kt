package com.lastasylum.alliance.data.chat

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

object SquadRelayMoshi {
    fun build(): Moshi =
        Moshi.Builder()
            .add(PinnedMessagePreviewDtoJsonAdapter.FACTORY)
            .addLast(KotlinJsonAdapterFactory())
            .build()
}
