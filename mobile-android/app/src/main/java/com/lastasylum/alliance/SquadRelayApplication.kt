package com.lastasylum.alliance

import android.app.Application
import android.content.Context
import androidx.profileinstaller.ProfileInstaller
import coil3.ImageLoader
import coil3.SingletonImageLoader
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.lastasylum.alliance.data.auth.JwtAccessTokenClaims
import com.lastasylum.alliance.data.chat.outbox.OutboxResumeScheduler
import com.lastasylum.alliance.data.teams.forum.ForumOutboxResumeScheduler
import com.lastasylum.alliance.di.AppContainer
import com.lastasylum.alliance.overlay.CombatOverlayService
import com.lastasylum.alliance.overlay.OverlayRuntimeScheduler
import com.lastasylum.alliance.push.GameEventPushNotifications
import com.lastasylum.alliance.push.PushTokenRegistrationCoordinator
import com.lastasylum.alliance.push.PushTokenRefreshScheduler
import com.lastasylum.alliance.ui.chat.SquadRelayImageLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SquadRelayApplication : Application(), SingletonImageLoader.Factory {
    override fun newImageLoader(context: Context): ImageLoader = SquadRelayImageLoader.create(context)

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        // Раньше ProfileInstaller: FirebaseInitProvider отключён в манифесте без google-services.json.
        initFirebaseIfConfigured()
        GameEventPushNotifications.ensureChannels(this)
        if (FirebaseApp.getApps(this).isNotEmpty()) {
            runCatching {
                FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = !BuildConfig.DEBUG
            }
        }
        appScope.launch(Dispatchers.IO) {
            runCatching { ProfileInstaller.writeProfile(this@SquadRelayApplication) }
            val container = AppContainer.from(this@SquadRelayApplication)
            runCatching { container.tokenStore.getRefreshToken() }
            val access = runCatching { container.tokenStore.getAccessToken() }.getOrNull()
            if (!access.isNullOrBlank()) {
                runCatching { PushTokenRegistrationCoordinator.registerWithBackend(this@SquadRelayApplication) }
                runCatching { PushTokenRefreshScheduler.schedule(this@SquadRelayApplication) }
                runCatching {
                    val profile = container.usersRepository.peekMyProfileDisk()
                        ?: container.usersRepository.peekMyProfile()
                    if (profile != null) {
                        container.userSettingsPreferences.applyGameEventPushEnabledFromServer(
                            profile.gameEventPushEnabled,
                        )
                    }
                }
                runCatching {
                    CombatOverlayService.ensureRuntimeIfUserEnabled(
                        this@SquadRelayApplication,
                        showErrorToast = false,
                    )
                }
                runCatching { OverlayRuntimeScheduler.syncSchedule(this@SquadRelayApplication) }
                JwtAccessTokenClaims.sub(access)?.trim()?.takeIf { it.isNotEmpty() }?.let { uid ->
                    runCatching { OutboxResumeScheduler.schedule(this@SquadRelayApplication, uid) }
                    runCatching { ForumOutboxResumeScheduler.schedule(this@SquadRelayApplication, uid) }
                }
            }
        }
    }

    private fun initFirebaseIfConfigured() {
        val projectId = BuildConfig.FIREBASE_PROJECT_ID.trim()
        val appId = BuildConfig.FIREBASE_APP_ID.trim()
        val apiKey = BuildConfig.FIREBASE_API_KEY.trim()
        if (projectId.isEmpty() || appId.isEmpty() || apiKey.isEmpty()) {
            return
        }
        if (FirebaseApp.getApps(this).isNotEmpty()) return
        runCatching {
            FirebaseApp.initializeApp(
                this,
                FirebaseOptions.Builder()
                    .setProjectId(projectId)
                    .setApplicationId(appId)
                    .setApiKey(apiKey)
                    .build(),
            )
            FirebaseAnalytics.getInstance(this)
        }
    }
}
