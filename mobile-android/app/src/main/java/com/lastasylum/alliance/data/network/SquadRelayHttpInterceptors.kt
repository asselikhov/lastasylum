package com.lastasylum.alliance.data.network

import android.util.Log
import com.lastasylum.alliance.BuildConfig
import okhttp3.Interceptor
import java.io.IOException

internal fun userAgentInterceptor(): Interceptor =
    Interceptor { chain ->
        val req = chain.request()
        val next = req.newBuilder()
            .header(
                "User-Agent",
                "SquadRelay-Android/${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            )
            .build()
        chain.proceed(next)
    }

/** В DEBUG пишет в Logcat URL и тип ошибки — по нему видно, IPv6, TLS или неверный хост. */
internal fun debugNetworkFailureInterceptor(): Interceptor =
    Interceptor { chain ->
        val req = chain.request()
        if (!BuildConfig.DEBUG) return@Interceptor chain.proceed(req)
        try {
            val res = chain.proceed(req)
            if (!res.isSuccessful) {
                Log.w(TAG, "${req.method} ${req.url} -> HTTP ${res.code}")
            }
            res
        } catch (e: IOException) {
            Log.e(TAG, "${req.method} ${req.url} failed: ${e.javaClass.simpleName}: ${e.message}")
            throw e
        }
    }

private const val TAG = "SquadRelayHttp"
