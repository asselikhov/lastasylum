package com.lastasylum.alliance.ui.chat

import android.content.Context
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.lastasylum.alliance.data.auth.TokenStore
import com.lastasylum.alliance.data.network.AuthInterceptor
import okhttp3.OkHttpClient

/**
 * Shared Coil loader with Bearer auth — avoids per-[AsyncImage] token reads.
 */
object SquadRelayImageLoader {
    @Volatile
    private var instance: ImageLoader? = null

    fun get(context: Context, tokenStore: TokenStore): ImageLoader {
        return instance ?: synchronized(this) {
            instance ?: build(context.applicationContext, tokenStore).also { instance = it }
        }
    }

    fun invalidate() {
        synchronized(this) {
            instance?.shutdown()
            instance = null
        }
    }

    private fun build(context: Context, tokenStore: TokenStore): ImageLoader {
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(tokenStore))
            .build()
        return ImageLoader.Builder(context)
            .okHttpClient(client)
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizePercent(0.15)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("coil_chat_images"))
                    .maxSizePercent(0.02)
                    .build()
            }
            .crossfade(false)
            .build()
    }
}
