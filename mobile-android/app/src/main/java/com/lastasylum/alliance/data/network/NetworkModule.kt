package com.lastasylum.alliance.data.network

import com.lastasylum.alliance.BuildConfig
import com.lastasylum.alliance.data.auth.AuthApi
import com.lastasylum.alliance.data.auth.TokenStore
import com.lastasylum.alliance.data.chat.ChatApi
import com.lastasylum.alliance.data.stt.SttApi
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object NetworkModule {
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    private val publicClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .addInterceptor(loggingInterceptor)
        .build()

    private val publicRetrofit: Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(publicClient)
        .addConverterFactory(MoshiConverterFactory.create())
        .build()

    val authApi: AuthApi = publicRetrofit.create(AuthApi::class.java)

    fun createAuthorizedClients(tokenStore: TokenStore): AuthorizedClients {
        val authorizedClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .addInterceptor(AuthInterceptor(tokenStore))
            .authenticator(TokenAuthenticator(tokenStore, authApi))
            .addInterceptor(loggingInterceptor)
            .build()

        val authorizedRetrofit = Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(authorizedClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()

        return AuthorizedClients(
            chatApi = authorizedRetrofit.create(ChatApi::class.java),
            sttApi = authorizedRetrofit.create(SttApi::class.java),
        )
    }
}

data class AuthorizedClients(
    val chatApi: ChatApi,
    val sttApi: SttApi,
)
}
