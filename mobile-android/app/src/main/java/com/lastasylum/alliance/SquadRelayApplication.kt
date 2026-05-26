package com.lastasylum.alliance

import android.app.Application
import androidx.profileinstaller.ProfileInstaller
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.lastasylum.alliance.ui.chat.SquadRelayImageLoader
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.lastasylum.alliance.di.AppContainer
import com.lastasylum.alliance.overlay.CombatOverlayService
import com.lastasylum.alliance.overlay.OverlayRuntimeScheduler
import com.lastasylum.alliance.push.ExcavationPushNotifications
import com.lastasylum.alliance.push.FcmTokenManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SquadRelayApplication : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader = SquadRelayImageLoader.create(this)

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        // Раньше ProfileInstaller: FirebaseInitProvider отключён в манифесте без google-services.json.
        initFirebaseIfConfigured()
        ExcavationPushNotifications.ensureChannel(this)
        if (FirebaseApp.getApps(this).isNotEmpty()) {
            runCatching {
                FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = !BuildConfig.DEBUG
            }
        }
        appScope.launch(Dispatchers.IO) {
            runCatching { ProfileInstaller.writeProfile(this@SquadRelayApplication) }
            val container = AppContainer.from(this@SquadRelayApplication)
            val access = runCatching { container.tokenStore.getAccessToken() }.getOrNull()
            if (!access.isNullOrBlank()) {
                runCatching { FcmTokenManager.registerWithBackend(this@SquadRelayApplication) }
                runCatching {
                    val profile = container.usersRepository.peekMyProfileDisk()
                        ?: container.usersRepository.peekMyProfile()
                    if (profile != null) {
                        container.userSettingsPreferences.setExcavationPushEnabled(
                            profile.excavationPushEnabled,
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
