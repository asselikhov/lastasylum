package com.lastasylum.alliance.ui

import android.app.Application
import com.lastasylum.alliance.data.chat.stickers.ChushuyStickerPack
import com.lastasylum.alliance.data.chat.stickers.ObzhoryStickerPack
import com.lastasylum.alliance.data.chat.stickers.SoidowCatStickerPack
import com.lastasylum.alliance.data.chat.stickers.ZlobyakaStickerPack
import com.lastasylum.alliance.di.AppContainer
import com.lastasylum.alliance.ui.chat.ChatViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Параллельная подготовка данных до показа [com.lastasylum.alliance.ui.Navigation]:
 * критический путь — профиль + комнаты чата; остальное — в фоне.
 */
suspend fun runAppLaunchWarmup(
    application: Application,
    container: AppContainer,
    chatViewModel: ChatViewModel,
    userId: String,
) {
    withContext(Dispatchers.IO) {
        coroutineScope {
            val profile = async {
                container.usersRepository.getMyProfile()
                    .onSuccess { p ->
                        if (userId.isNotBlank()) {
                            container.launchDiskCache.saveProfile(userId, p)
                        }
                    }
            }
            val chat = async { chatViewModel.warmUpForLaunchLight() }
            launch {
                ZlobyakaStickerPack.listSortedStems(application)
                ChushuyStickerPack.listSortedStems(application)
                SoidowCatStickerPack.listSortedStems(application)
                ObzhoryStickerPack.listSortedStems(application)
            }
            launch {
                val profileResult = profile.await()
                val teamId = profileResult.getOrNull()?.playerTeamId?.trim().orEmpty()
                if (userId.isNotBlank() && teamId.isNotEmpty()) {
                    prefetchTeamLaunchContent(
                        userId = userId,
                        profile = profileResult.getOrThrow(),
                        container = container,
                    )
                }
            }
            profile.await()
            chat.await()
        }
    }
}

private val launchBackgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/** Догрузка после splash (сообщения чата, если splash уложился в таймаут). */
fun schedulePostSplashLaunchContinuation(chatViewModel: ChatViewModel) {
    launchBackgroundScope.launch {
        chatViewModel.continueLaunchWarmup()
    }
}
