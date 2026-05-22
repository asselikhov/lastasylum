package com.lastasylum.alliance.data.chat

/** Socket room ids for overlay FGS bootstrap (raid + hub + selected tab). */
internal object ChatOverlayRoomIds {
    fun forOverlayBootstrap(
        raidRoomId: String?,
        hubRoomId: String?,
        selectedRoomId: String?,
    ): List<String> {
        val out = LinkedHashSet<String>()
        fun add(id: String?) {
            val trimmed = id?.trim().orEmpty()
            if (trimmed.isNotEmpty()) out.add(trimmed)
        }
        add(raidRoomId)
        add(hubRoomId)
        add(selectedRoomId)
        // Prefs may be empty before first listRooms; cache still has raid/hub ids.
        ChatSessionCache.getFreshRooms()?.forEach { room ->
            if (ChatRaidRoomSync.isAllianceRaidRoom(room)) add(room.id)
            if (ChatHubRoomSync.isAllianceHubRoom(room)) add(room.id)
        }
        return out.toList()
    }
}
