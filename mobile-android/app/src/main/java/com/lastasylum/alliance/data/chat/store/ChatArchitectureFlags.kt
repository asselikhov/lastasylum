package com.lastasylum.alliance.data.chat.store

/** Strangler flags for Room sync layer rollout. */
object ChatArchitectureFlags {
    /** Dual-write confirmed messages + read cursors to Room; read path uses Room when populated. */
    @Volatile
    var useRoomMessageStore: Boolean = true

    /** Durable outbox for pending sends (survives process kill). */
    @Volatile
    var useChatOutbox: Boolean = true

    /** Delegate persist/reconnect/read REST to [com.lastasylum.alliance.data.chat.sync.ChatSyncEngine]. */
    @Volatile
    var useChatSyncEngine: Boolean = true
}
