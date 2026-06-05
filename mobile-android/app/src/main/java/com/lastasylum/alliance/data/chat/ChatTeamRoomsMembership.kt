package com.lastasylum.alliance.data.chat

/** Whether cached [listRooms] rows match the user's current player-team membership. */
object ChatTeamRoomsMembership {
    private const val PLAYER_TEAM_PREFIX = "pt:"

    fun expectedTeamScope(playerTeamId: String?): String? {
        val id = playerTeamId?.trim().orEmpty()
        if (id.isEmpty()) return null
        return "$PLAYER_TEAM_PREFIX$id"
    }

    fun cacheMatchesProfile(
        rooms: List<ChatRoomDto>,
        playerTeamId: String?,
    ): Boolean {
        val scope = expectedTeamScope(playerTeamId)
        val hub = ChatRoomKindResolver.allianceHubRoom(rooms)
        val raid = ChatRoomKindResolver.allianceRaidRoom(rooms)
        return if (scope == null) {
            !rooms.any { room ->
                val allianceId = room.allianceId.orEmpty()
                allianceId.startsWith(PLAYER_TEAM_PREFIX) &&
                    (
                        ChatRoomKindResolver.isAllianceHubRoom(room) ||
                            ChatRoomKindResolver.isAllianceRaidRoom(room)
                        )
            }
        } else {
            hub != null &&
                raid != null &&
                hub.allianceId == scope &&
                raid.allianceId == scope
        }
    }
}
