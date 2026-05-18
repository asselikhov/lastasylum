package com.lastasylum.alliance.di

import android.content.Context
import com.lastasylum.alliance.data.admin.AdminRepository
import com.lastasylum.alliance.data.auth.AuthRepository
import com.lastasylum.alliance.data.auth.TokenStore
import com.lastasylum.alliance.data.chat.ChatRepository
import com.lastasylum.alliance.data.chat.ChatRoomPreferences
import com.lastasylum.alliance.data.chat.ChatRoomsRepository
import com.lastasylum.alliance.data.chat.ChatSocketManager
import com.lastasylum.alliance.data.network.NetworkModule
import com.lastasylum.alliance.data.settings.OnboardingPreferences
import com.lastasylum.alliance.data.settings.UserSettingsPreferences
import com.lastasylum.alliance.data.teams.TeamForumSocketManager
import com.lastasylum.alliance.data.teams.TeamsRepository
import com.lastasylum.alliance.data.users.UsersRepository
import com.lastasylum.alliance.data.voice.VoiceChatSession
import com.lastasylum.alliance.data.voice.VoiceSocketManager

class AppContainer private constructor(context: Context) {
    private val appContext = context.applicationContext

    /** Encrypted prefs + KeyStore: создаём лениво; прогрев — в [SquadRelayApplication] на IO. */
    val tokenStore: TokenStore by lazy { TokenStore(appContext) }

    val chatRoomPreferences: ChatRoomPreferences = ChatRoomPreferences(appContext)
    val userSettingsPreferences: UserSettingsPreferences = UserSettingsPreferences(appContext)
    val onboardingPreferences: OnboardingPreferences = OnboardingPreferences(appContext)

    private val chatSocketManager = ChatSocketManager()
    private val voiceSocketManager = VoiceSocketManager()
    private val teamForumSocketManager = TeamForumSocketManager()

    @Volatile
    private var chatRepositoryInstance: ChatRepository? = null

    /** Set by [com.lastasylum.alliance.overlay.CombatOverlayService] while overlay voice is active. */
    @Volatile
    var overlayVoiceSession: VoiceChatSession? = null

    private val authorizedClients by lazy {
        NetworkModule.createAuthorizedClients(tokenStore) {
            chatRepositoryInstance?.onAccessTokenRefreshed()
            overlayVoiceSession?.onAccessTokenRefreshed()
            teamForumSocketManager.reconnectWithFreshToken()
        }
    }

    val chatRepository: ChatRepository
        get() {
            chatRepositoryInstance?.let { return it }
            return synchronized(this) {
                chatRepositoryInstance ?: ChatRepository(
                    chatApi = authorizedClients.chatApi,
                    tokenStore = tokenStore,
                    socketManager = chatSocketManager,
                    chatRoomPreferences = chatRoomPreferences,
                ).also { chatRepositoryInstance = it }
            }
        }

    val authRepository: AuthRepository by lazy {
        AuthRepository(
            authApi = NetworkModule.authApi,
            authorizedAuthApi = authorizedClients.authorizedAuthApi,
            tokenStore = tokenStore,
            chatRoomPreferences = chatRoomPreferences,
        )
    }

    val chatRoomsRepository: ChatRoomsRepository by lazy {
        ChatRoomsRepository(chatApi = authorizedClients.chatApi)
    }

    val usersRepository: UsersRepository by lazy {
        UsersRepository(usersApi = authorizedClients.usersApi)
    }

    val adminRepository: AdminRepository by lazy {
        AdminRepository(adminApi = authorizedClients.adminApi)
    }

    val teamsRepository: TeamsRepository by lazy {
        TeamsRepository(teamsApi = authorizedClients.teamsApi)
    }

    val teamForumSocket: TeamForumSocketManager get() = teamForumSocketManager

    val voiceSocket: VoiceSocketManager get() = voiceSocketManager

    fun newVoiceChatSession(
        onStateChanged: (micOn: Boolean, soundOn: Boolean) -> Unit,
        onMicForegroundChanged: (micActive: Boolean) -> Unit,
        onActiveSpeakersChanged: (count: Int) -> Unit = {},
    ): VoiceChatSession = VoiceChatSession(
        context = appContext,
        tokenStore = tokenStore,
        userSettings = userSettingsPreferences,
        socketManager = voiceSocketManager,
        onStateChanged = onStateChanged,
        onMicForegroundChanged = onMicForegroundChanged,
        onActiveSpeakersChanged = onActiveSpeakersChanged,
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
