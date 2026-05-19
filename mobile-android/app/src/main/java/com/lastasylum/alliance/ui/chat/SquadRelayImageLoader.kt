package com.lastasylum.alliance.ui.chat

import android.content.Context
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.lastasylum.alliance.di.AppContainer
import com.lastasylum.alliance.data.network.AuthInterceptor
import okhttp3.OkHttpClient

/**
 * Coil [ImageLoader] with Bearer auth via [AuthInterceptor] on a shared OkHttp client.
 * Registered as the app singleton through [com.lastasylum.alliance.SquadRelayApplication].
 */
object SquadRelayImageLoader {
    fun create(context: Context): ImageLoader {
        val appContext = context.applicationContext
        val tokenStore = AppContainer.from(appContext).tokenStore
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(tokenStore))
            .build()
        return ImageLoader.Builder(appContext)
            .okHttpClient(client)
            .allowHardware(true)
            .memoryCache {
                MemoryCache.Builder(appContext)
                    .maxSizePercent(0.12)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(appContext.cacheDir.resolve("coil_chat_images"))
                    .maxSizePercent(0.03)
                    .build()
            }
            .crossfade(false)
            .build()
    }
}
