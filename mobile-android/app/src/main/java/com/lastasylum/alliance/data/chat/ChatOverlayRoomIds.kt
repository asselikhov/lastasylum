package com.lastasylum.alliance.data.chat

/** Socket room ids for overlay FGS bootstrap (raid + hub + selected tab). */
internal object ChatOverlayRoomIds {
    fun forOverlayBootstrap(
        raidRoomId: String?,
        hubRoomId: String?,
        selectedRoomId: String?,
    ): List<String> {
        val raid = raidRoomId?.trim().orEmpty()
        val hub = hubRoomId?.trim().orEmpty()
        val selected = selectedRoomId?.trim().orEmpty()
        return buildList {
            if (raid.isNotEmpty()) add(raid)
            if (hub.isNotEmpty() && hub !in this) add(hub)
            if (selected.isNotEmpty() && selected !in this) add(selected)
        }
    }
}
