package com.lastasylum.alliance.data.chat.store

/** Room sync layer — legacy strangler flags removed; paths are always enabled. */
object ChatArchitectureFlags {
    const val useRoomMessageStore: Boolean = true
    const val useChatOutbox: Boolean = true
    const val useChatSyncEngine: Boolean = true
}
