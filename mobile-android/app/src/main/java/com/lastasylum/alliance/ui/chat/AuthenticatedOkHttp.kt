package com.lastasylum.alliance.ui.chat

import android.content.Context
import com.lastasylum.alliance.data.network.AuthInterceptor
import com.lastasylum.alliance.di.AppContainer
import okhttp3.OkHttpClient

private val clientCache = java.util.concurrent.ConcurrentHashMap<String, OkHttpClient>()

/** Shared OkHttp client with Bearer auth for chat attachments (Coil + gallery save). */
fun authenticatedOkHttpClient(context: Context): OkHttpClient {
    val appContext = context.applicationContext
    val key = appContext.packageName
    return clientCache.getOrPut(key) {
        val tokenStore = AppContainer.from(appContext).tokenStore
        OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(tokenStore))
            .build()
    }
}
