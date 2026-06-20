package com.lastasylum.alliance.data.chat.sync

internal const val CHAT_INITIAL_PAGE_SIZE = 20
internal const val CHAT_PAGE_SIZE = 30
internal const val CHAT_INCOMING_SOCKET_DEBOUNCE_MS = 24L
internal const val CHAT_INCOMING_SOCKET_DEBOUNCE_SINGLE_MS = 0L
internal const val CHAT_INCOMING_SOCKET_DEBOUNCE_BURST_MS = 8L
internal const val CHAT_ACTIVE_ROOM_RECONCILE_INTERVAL_MS = 12_000L
/** Faster REST tail when chat/overlay panel is actively showing messages. */
internal const val CHAT_ACTIVE_ROOM_REALTIME_RECONCILE_INTERVAL_MS = 4_000L
internal const val CHAT_BACKGROUND_MESSAGE_REFRESH_DEFER_MS = 0L
internal const val CHAT_UNREAD_SYNC_DEBOUNCE_MS = 200L
/** Keep socket-bumped unread until listRooms catches up or grace expires. */
internal const val CHAT_UNREAD_RECONCILE_GRACE_MS = 2_000L
internal const val CHAT_ROOMS_SYNC_ON_RESUME_TTL_MS = 45_000L
