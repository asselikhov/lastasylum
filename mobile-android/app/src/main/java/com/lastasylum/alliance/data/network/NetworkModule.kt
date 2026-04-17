package com.lastasylum.alliance.data.network

import com.lastasylum.alliance.BuildConfig
import com.lastasylum.alliance.data.auth.AuthApi
import com.lastasylum.alliance.data.auth.TokenStore
import com.lastasylum.alliance.data.chat.ChatApi
import com.lastasylum.alliance.data.users.UsersApi
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object NetworkModule {
    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private fun loggingInterceptorOrNull(): HttpLoggingInterceptor? {
        if (!BuildConfig.DEBUG) return null
        return HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
    }

    private fun OkHttpClient.Builder.applySquadRelayNetworkDefaults(): OkHttpClient.Builder {
        // HTTP/2 + IPv6 на части LTE дают «тихие» таймауты; HTTP/1.1 + приоритет IPv4 стабильнее.
        protocols(listOf(Protocol.HTTP_1_1))
        dns(PreferIpv4Dns)
        connectTimeout(45, TimeUnit.SECONDS)
        readTimeout(120, TimeUnit.SECONDS)
        writeTimeout(45, TimeUnit.SECONDS)
        addInterceptor(userAgentInterceptor())
        if (BuildConfig.DEBUG) {
            addInterceptor(debugNetworkFailureInterceptor())
        }
        loggingInterceptorOrNull()?.let { addInterceptor(it) }
        return this
    }

    private val publicClient = OkHttpClient.Builder()
        .applySquadRelayNetworkDefaults()
        .build()

    private val publicRetrofit: Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(publicClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    val authApi: AuthApi = publicRetrofit.create(AuthApi::class.java)

    val mobileApi: MobileApi = publicRetrofit.create(MobileApi::class.java)

    fun createAuthorizedClients(
        tokenStore: TokenStore,
        onAccessTokenRefreshed: () -> Unit = {},
    ): AuthorizedClients {
        val authorizedClient = OkHttpClient.Builder()
            .applySquadRelayNetworkDefaults()
            .addInterceptor(AuthInterceptor(tokenStore))
            .authenticator(
                TokenAuthenticator(tokenStore, authApi, onAccessTokenRefreshed),
            )
            .build()

        val authorizedRetrofit = Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(authorizedClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

        return AuthorizedClients(
            chatApi = authorizedRetrofit.create(ChatApi::class.java),
            authorizedAuthApi = authorizedRetrofit.create(AuthApi::class.java),
            usersApi = authorizedRetrofit.create(UsersApi::class.java),
        )
    }
}

data class AuthorizedClients(
    val chatApi: ChatApi,
    /** Same routes as public [authApi], but sends Bearer token — required for [AuthApi.logout]. */
    val authorizedAuthApi: AuthApi,
    val usersApi: UsersApi,
)
