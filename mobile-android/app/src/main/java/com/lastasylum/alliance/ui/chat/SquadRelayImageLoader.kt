package com.lastasylum.alliance.ui.chat

import android.content.Context
import android.os.Build
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.serviceLoaderEnabled
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import okio.Path.Companion.toPath

/**
 * Coil [ImageLoader] with Bearer auth via [AuthInterceptor] on a shared OkHttp client.
 * Registered as the app singleton through [com.lastasylum.alliance.SquadRelayApplication].
 */
object SquadRelayImageLoader {
    @OptIn(ExperimentalCoroutinesApi::class)
    fun create(context: Context): ImageLoader {
        val appContext = context.applicationContext
        val client = authenticatedOkHttpClient(appContext)
        return ImageLoader.Builder(appContext)
            .serviceLoaderEnabled(false)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = client))
                if (Build.VERSION.SDK_INT >= 28) {
                    add(AnimatedImageDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            // TYPE_APPLICATION_OVERLAY не рисует hardware bitmap — иначе фото в оверлей-чате пустые.
            .allowHardware(false)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(appContext, 0.12)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(appContext.cacheDir.resolve("coil_chat_images").absolutePath.toPath())
                    .maxSizePercent(0.03)
                    .build()
            }
            .crossfade(false)
            .coroutineContext(Dispatchers.IO.limitedParallelism(6))
            .build()
    }
}
