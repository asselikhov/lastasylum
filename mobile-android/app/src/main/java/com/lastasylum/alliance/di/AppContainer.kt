package com.lastasylum.alliance.di

import android.content.Context
import com.lastasylum.alliance.data.auth.AuthRepository
import com.lastasylum.alliance.data.auth.TokenStore
import com.lastasylum.alliance.data.chat.ChatRepository
import com.lastasylum.alliance.data.chat.ChatRoomPreferences
import com.lastasylum.alliance.data.chat.ChatRoomsRepository
import com.lastasylum.alliance.data.chat.ChatSocketManager
import com.lastasylum.alliance.data.network.NetworkModule
import com.lastasylum.alliance.data.users.UsersRepository

class AppContainer private constructor(context: Context) {
    private val appContext = context.applicationContext
    val tokenStore: TokenStore = TokenStore(appContext)
    val chatRoomPreferences: ChatRoomPreferences = ChatRoomPreferences(appContext)
    private val authorizedClients = NetworkModule.createAuthorizedClients(tokenStore)

    val authRepository: AuthRepository = AuthRepository(
        authApi = NetworkModule.authApi,
        authorizedAuthApi = authorizedClients.authorizedAuthApi,
        tokenStore = tokenStore,
        chatRoomPreferences = chatRoomPreferences,
    )

    val chatRepository: ChatRepository = ChatRepository(
        chatApi = authorizedClients.chatApi,
        tokenStore = tokenStore,
        socketManager = ChatSocketManager(),
        chatRoomPreferences = chatRoomPreferences,
    )

    val chatRoomsRepository: ChatRoomsRepository = ChatRoomsRepository(
        chatApi = authorizedClients.chatApi,
    )

    val usersRepository: UsersRepository = UsersRepository(
        usersApi = authorizedClients.usersApi,
    )

    companion object {
        @Volatile
        private var instance: AppContainer? = null

        fun from(context: Context): AppContainer {
            return instance ?: synchronized(this) {
                instance ?: AppContainer(context).also { instance = it }
            }
        }
    }
}
