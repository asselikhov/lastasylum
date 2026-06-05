package com.lastasylum.alliance.ui.chat.usecase

import com.lastasylum.alliance.data.chat.ChatRoomDto
import com.lastasylum.alliance.data.chat.ChatRoomKind
import com.lastasylum.alliance.data.chat.ChatRoomKindResolver

/**
 * Room list helpers extracted from [com.lastasylum.alliance.ui.chat.ChatViewModel].
 */
internal object ChatRoomsUseCase {
    fun sortForDisplay(rooms: List<ChatRoomDto>): List<ChatRoomDto> =
        rooms.sortedWith(
            compareBy<ChatRoomDto> { room ->
                when (ChatRoomKindResolver.kindOf(room)) {
                    ChatRoomKind.GlobalUnion -> 0
                    ChatRoomKind.Server -> 1
                    ChatRoomKind.AllianceHub -> 2
                    ChatRoomKind.Raid -> 3
                    ChatRoomKind.Other -> 4
                }
            }.thenBy { it.sortOrder }.thenBy { it.title },
        )

    fun allianceHubRoomId(rooms: List<ChatRoomDto>): String? =
        ChatRoomKindResolver.allianceHubRoom(rooms)?.id

    fun allianceRaidRoomId(
        rooms: List<ChatRoomDto>,
        preferredRaidRoomId: String?,
    ): String? =
        ChatRoomKindResolver.allianceRaidRoom(rooms)?.id
            ?: preferredRaidRoomId?.trim()?.takeIf { it.isNotEmpty() }
}
