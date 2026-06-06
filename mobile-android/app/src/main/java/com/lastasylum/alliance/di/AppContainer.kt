package com.lastasylum.alliance.di

import android.content.Context
import com.lastasylum.alliance.data.admin.AdminRepository
import com.lastasylum.alliance.data.cache.LaunchDiskCache
import com.lastasylum.alliance.data.auth.AuthRepository
import com.lastasylum.alliance.data.auth.TokenStore
import com.lastasylum.alliance.data.chat.ChatRepository
import com.lastasylum.alliance.data.chat.outbox.ChatOutbox
import com.lastasylum.alliance.data.chat.store.LaunchDiskCacheImporter
import com.lastasylum.alliance.data.chat.store.MessageStore
import com.lastasylum.alliance.data.chat.store.SquadRelayDatabase
import com.lastasylum.alliance.data.chat.sync.ChatSyncEngine
import com.lastasylum.alliance.data.chat.sync.asSyncGateway
import com.lastasylum.alliance.data.telemetry.DeliveryLatencyTracker
import com.lastasylum.alliance.data.telemetry.DeliveryTelemetryUploader
import com.lastasylum.alliance.data.teams.forum.ForumRepository
import com.lastasylum.alliance.overlay.OverlayHudBadgeBus
import com.lastasylum.alliance.overlay.OverlayHudBadgeReducer
import com.lastasylum.alliance.overlay.OverlayInboxBadgeCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.lastasylum.alliance.data.chat.OverlayReactionLogPreferences
import com.lastasylum.alliance.data.chat.OverlayReactionLogRepository
import com.lastasylum.alliance.data.chat.ChatRoomsSessionCache
import com.lastasylum.alliance.data.chat.ChatRoomPreferences
import com.lastasylum.alliance.data.chat.PinHistoryPreferences
import com.lastasylum.alliance.data.chat.ChatRoomsRepository
import com.lastasylum.alliance.data.chat.ChatSocketManager
import com.lastasylum.alliance.data.network.NetworkModule
import com.lastasylum.alliance.data.network.RealtimeCoordinator
import com.lastasylum.alliance.data.settings.OnboardingPreferences
import com.lastasylum.alliance.data.settings.UserSettingsPreferences
import com.lastasylum.alliance.data.auth.JwtAccessTokenClaims
import com.lastasylum.alliance.data.teams.TeamForumPreferences
import com.lastasylum.alliance.data.teams.TeamForumSocketManager
import com.lastasylum.alliance.data.teams.TeamMembershipNotifier
import com.lastasylum.alliance.data.teams.TeamPresenceSocketManager
import com.lastasylum.alliance.data.teams.TeamsRepository
import com.lastasylum.alliance.overlay.OverlayTeamContextCache
import com.lastasylum.alliance.data.users.UsersRepository
import com.lastasylum.alliance.data.voice.VoiceChatSession
import com.lastasylum.alliance.data.voice.VoiceSocketManager

class AppContainer private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Encrypted prefs + KeyStore: создаём лениво; прогрев — в [SquadRelayApplication] на IO. */
    val tokenStore: TokenStore by lazy { TokenStore(appContext) }

    val chatRoomPreferences: ChatRoomPreferences = ChatRoomPreferences(appContext)
    val pinHistoryPreferences: PinHistoryPreferences = PinHistoryPreferences(appContext)
    val teamForumPreferences: TeamForumPreferences = TeamForumPreferences(appContext)
    val userSettingsPreferences: UserSettingsPreferences = UserSettingsPreferences(appContext)
    val onboardingPreferences: OnboardingPreferences = OnboardingPreferences(appContext)
    val launchDiskCache: LaunchDiskCache = LaunchDiskCache(appContext)

    val squadRelayDatabase: SquadRelayDatabase by lazy { SquadRelayDatabase.get(appContext) }
    val messageStore: MessageStore by lazy { MessageStore(squadRelayDatabase) }
    val deliveryLatencyTracker: DeliveryLatencyTracker by lazy {
        DeliveryLatencyTracker(squadRelayDatabase, appScope)
    }

    val deliveryTelemetryUploader: DeliveryTelemetryUploader by lazy {
        DeliveryTelemetryUploader(
            telemetryApi = authorizedClients.telemetryApi,
            tracker = deliveryLatencyTracker,
            scope = appScope,
        ).also { it.startPeriodicUpload() }
    }
    val inboxBadgeCoordinator: OverlayInboxBadgeCoordinator = OverlayInboxBadgeCoordinator()

    @Volatile
    private var overlayHudBadgeBusInstance: OverlayHudBadgeBus? = null

    fun registerOverlayHudBadgeBus(bus: OverlayHudBadgeBus) {
        overlayHudBadgeBusInstance = bus
    }

    fun overlayHudBadgeBusOrNull(): OverlayHudBadgeBus? = overlayHudBadgeBusInstance

    fun createOverlayHudBadgeBus(
        reducer: OverlayHudBadgeReducer,
    ): OverlayHudBadgeBus = OverlayHudBadgeBus(
        reducer = reducer,
        mergeNews = inboxBadgeCoordinator::mergeHudNews,
        mergeForum = { effective, prev, raw, useAuthoritative ->
            if (useAuthoritative) {
                inboxBadgeCoordinator.mergeForumDisplayed(effective, prev, raw)
            } else {
                inboxBadgeCoordinator.mergeHudForum(effective, prev, useAuthoritative)
            }
        },
    ).also { registerOverlayHudBadgeBus(it) }

    val launchDiskCacheImporter: LaunchDiskCacheImporter by lazy {
        LaunchDiskCacheImporter(
            context = appContext,
            launchDiskCache = launchDiskCache,
            chatRoomPreferences = chatRoomPreferences,
            messageStore = messageStore,
        )
    }

    private val chatSocketManager by lazy {
        ChatSocketManager(latencyTracker = deliveryLatencyTracker)
    }
    private val voiceSocketManager = VoiceSocketManager()
    private val teamForumSocketManager = TeamForumSocketManager()
    private val teamPresenceSocketManager = TeamPresenceSocketManager()
    private val realtimeCoordinator = RealtimeCoordinator()

    @Volatile
    private var chatRepositoryInstance: ChatRepository? = null

    init {
        realtimeCoordinator.registerReconnect {
            chatRepositoryInstance?.onAccessTokenRefreshed()
        }
        realtimeCoordinator.registerReconnect {
            overlayVoiceSession?.onAccessTokenRefreshed()
        }
        realtimeCoordinator.registerReconnect {
            teamForumSocketManager.reconnectWithFreshToken()
        }
        realtimeCoordinator.registerReconnect {
            teamPresenceSocketManager.reconnectWithFreshToken()
        }
        appScope.launch(Dispatchers.IO) {
            delay(5_000L)
            deliveryTelemetryUploader
            while (true) {
                delay(10 * 60_000L)
                deliveryLatencyTracker.logSnapshotIfDebug()
                deliveryLatencyTracker.logSnapshotReleaseSummary()
            }
        }
    }

    /** Set by [com.lastasylum.alliance.overlay.CombatOverlayService] while overlay voice is active. */
    @Volatile
    var overlayVoiceSession: VoiceChatSession? = null

    private val authorizedClients by lazy {
        NetworkModule.createAuthorizedClients(tokenStore) {
            realtimeCoordinator.onAccessTokenRefreshed()
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

    val chatOutbox: ChatOutbox by lazy {
        ChatOutbox(
            db = squadRelayDatabase,
            messageStore = messageStore,
            latencyTracker = deliveryLatencyTracker,
        )
    }

    val chatSyncEngine: ChatSyncEngine by lazy {
        ChatSyncEngine(
            messageStore = messageStore,
            chatOutbox = chatOutbox,
            repository = chatRepository.asSyncGateway(),
            latencyTracker = deliveryLatencyTracker,
        )
    }

    val forumRepository: ForumRepository by lazy {
        ForumRepository(
            db = squadRelayDatabase,
            teamsRepository = teamsRepository,
            launchDiskCache = launchDiskCache,
            forumPrefs = teamForumPreferences,
            latencyTracker = deliveryLatencyTracker,
        )
    }

    val overlayReactionLogPreferences: OverlayReactionLogPreferences =
        OverlayReactionLogPreferences(appContext)

    val overlayReactionLogRepository: OverlayReactionLogRepository by lazy {
        OverlayReactionLogRepository(
            chatApi = authorizedClients.chatApi,
            preferences = overlayReactionLogPreferences,
        )
    }

    val usersRepository: UsersRepository by lazy {
        if (!teamMembershipHookInstalled) {
            teamMembershipHookInstalled = true
            TeamMembershipNotifier.setOnChanged {
                ChatRoomsSessionCache.invalidate()
                OverlayTeamContextCache.invalidate()
                val uid = JwtAccessTokenClaims.sub(tokenStore.getAccessToken())?.trim().orEmpty()
                if (uid.isNotEmpty()) {
                    launchDiskCache.clearChatRooms(uid)
                }
            }
        }
        UsersRepository(
            usersApi = authorizedClients.usersApi,
            launchDiskCache = launchDiskCache,
            tokenStore = tokenStore,
        )
    }

    val authRepository: AuthRepository by lazy {
        AuthRepository(
            authApi = NetworkModule.authApi,
            authorizedAuthApi = authorizedClients.authorizedAuthApi,
            tokenStore = tokenStore,
            chatRoomPreferences = chatRoomPreferences,
            pinHistoryPreferences = pinHistoryPreferences,
            teamForumPreferences = teamForumPreferences,
            userSettingsPreferences = userSettingsPreferences,
            launchDiskCache = launchDiskCache,
            onProfileCacheInvalidate = {
                usersRepository.invalidateProfileCache()
                ChatRoomsSessionCache.invalidate()
            },
        )
    }

    val chatRoomsRepository: ChatRoomsRepository by lazy {
        ChatRoomsRepository(chatApi = authorizedClients.chatApi)
    }

    val adminRepository: AdminRepository by lazy {
        AdminRepository(adminApi = authorizedClients.adminApi)
    }

    val teamsRepository: TeamsRepository by lazy {
        TeamsRepository(teamsApi = authorizedClients.teamsApi)
    }

    val teamForumSocket: TeamForumSocketManager get() = teamForumSocketManager

    val teamPresenceSocket: TeamPresenceSocketManager get() = teamPresenceSocketManager

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
        private var teamMembershipHookInstalled = false

        @Volatile
        private var instance: AppContainer? = null

        fun from(context: Context): AppContainer {
            return instance ?: synchronized(this) {
                instance ?: AppContainer(context).also { instance = it }
            }
        }
    }
}
