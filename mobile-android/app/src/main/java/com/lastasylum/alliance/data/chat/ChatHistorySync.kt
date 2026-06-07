package com.lastasylum.alliance.data.chat

/**
 * Reconcile admin chat wipe for clients that missed [chat:history:cleared] while offline.
 */
object ChatHistorySync {
    suspend fun reconcileIfNeeded(
        repository: ChatRepository,
        chatRoomPreferences: ChatRoomPreferences,
        onServerHistoryCleared: suspend () -> Unit,
    ) {
        val serverAt = repository.getChatSyncState()
            .getOrNull()
            ?.historyClearedAt
            ?.trim()
            .orEmpty()
        if (serverAt.isEmpty()) return
        val localAck = chatRoomPreferences.getAcknowledgedHistoryClearedAt()
        if (localAck.isNotEmpty() && localAck >= serverAt) return
        onServerHistoryCleared()
    }
}
