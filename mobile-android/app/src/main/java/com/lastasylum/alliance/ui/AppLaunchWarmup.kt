package com.lastasylum.alliance.ui

import android.app.Application
import com.lastasylum.alliance.data.chat.stickers.ChushuyStickerPack
import com.lastasylum.alliance.data.chat.stickers.ZlobyakaStickerPack
import com.lastasylum.alliance.di.AppContainer
import com.lastasylum.alliance.ui.chat.ChatViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * Параллельная подготовка данных до показа [AppNavigation], чтобы первый кадр чата/команды
 * не подвисал на сети.
 */
suspend fun runAppLaunchWarmup(
    application: Application,
    container: AppContainer,
    chatViewModel: ChatViewModel,
) {
    withContext(Dispatchers.IO) {
        coroutineScope {
            val chat = async { chatViewModel.warmUpForLaunch() }
            val profile = async { container.usersRepository.getMyProfile() }
            val stickers = async {
                ZlobyakaStickerPack.listSortedStems(application)
                ChushuyStickerPack.listSortedStems(application)
            }
            val team = async {
                val teamId = profile.await().getOrNull()?.playerTeamId?.trim().orEmpty()
                if (teamId.isNotEmpty()) {
                    container.teamsRepository.getTeam(teamId)
                }
            }
            chat.await()
            stickers.await()
            team.await()
        }
    }
}
