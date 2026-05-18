package com.lastasylum.alliance.ui.team

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.lastasylum.alliance.data.chat.ChatRoomDto
import com.lastasylum.alliance.data.chat.ChatAllianceIds
import com.lastasylum.alliance.data.voice.TeamVoicePresenceStore
import com.lastasylum.alliance.data.voice.VoicePresenceListenSession
import com.lastasylum.alliance.di.AppContainer

private fun pickRaidRoomId(rooms: List<ChatRoomDto>): String? =
    rooms.firstOrNull { room ->
        room.title == "Рейд" &&
            room.allianceId != null &&
            room.allianceId != ChatAllianceIds.GLOBAL &&
            room.allianceId.startsWith("pt:")
    }?.id

@Composable
fun TeamVoiceRosterPresenceBinding(
    active: Boolean,
    overlayVisible: Boolean,
    currentUserId: String,
) {
    if (!active) return
    val context = LocalContext.current
    val app = remember { AppContainer.from(context) }
    var raidRoomId by remember { mutableStateOf(app.chatRoomPreferences.getRaidRoomId()) }

    LaunchedEffect(Unit) {
        if (!raidRoomId.isNullOrBlank()) return@LaunchedEffect
        app.chatRoomsRepository.listRooms()
            .onSuccess { rooms ->
                val raid = pickRaidRoomId(rooms)
                if (raid != null) {
                    app.chatRoomPreferences.setRaidRoomId(raid)
                    raidRoomId = raid
                }
            }
    }

    DisposableEffect(active, raidRoomId, overlayVisible, app.overlayVoiceSession) {
        val roomId = raidRoomId?.trim().orEmpty()
        if (!active || roomId.isEmpty()) {
            return@DisposableEffect onDispose { }
        }
        val overlayActive = app.overlayVoiceSession != null
        val listenSession = if (!overlayActive) {
            VoicePresenceListenSession(
                socketManager = app.voiceSocket,
                tokenStore = app.tokenStore,
                isOverlayVoiceActive = { app.overlayVoiceSession != null },
            ).also { it.start(roomId, currentUserId) }
        } else {
            null
        }
        onDispose {
            listenSession?.stop()
            if (app.overlayVoiceSession == null) {
                TeamVoicePresenceStore.clear()
            }
        }
    }
}
