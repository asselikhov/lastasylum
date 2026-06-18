package com.lastasylum.alliance.ui.chat

import com.lastasylum.alliance.data.isObjectIdNewer

/** Forum topics: align gap window with general chat (apply-first, background reconcile). */
internal const val FORUM_GAP_RECONCILE_THRESHOLD_MS = 120_000L

/** Max ObjectId timestamp gap before we assume missed messages and force REST reconcile. */
internal const val CHAT_GAP_RECONCILE_THRESHOLD_MS = 120_000L

/** Raid room: shorter gap window — overlay stash + quick commands need faster reconcile. */
internal const val RAID_GAP_RECONCILE_THRESHOLD_MS = 60_000L

internal fun incomingMessageFingerprint(
    senderId: String,
    text: String,
    createdAt: String?,
): String = "${senderId.trim()}|${text.trim()}|${createdAt?.trim().orEmpty()}"

internal fun objectIdTimestampMs(id: String): Long? {
    val trimmed = id.trim()
    if (trimmed.length != 24) return null
    return runCatching {
        trimmed.substring(0, 8).toLong(16) * 1000L
    }.getOrNull()
}

internal fun objectIdTimestampGapMs(olderId: String, newerId: String): Long {
    val olderTs = objectIdTimestampMs(olderId) ?: return 0L
    val newerTs = objectIdTimestampMs(newerId) ?: return 0L
    return (newerTs - olderTs).coerceAtLeast(0L)
}

/**
 * True when a newer socket message suggests messages were missed between visible head and incoming.
 * [knownMessageIds] should be ids present before the batch apply (not after commit).
 */
internal fun shouldTriggerGapReconcile(
    visibleNewestId: String?,
    incomingId: String?,
    knownMessageIds: Set<String>,
    thresholdMs: Long = CHAT_GAP_RECONCILE_THRESHOLD_MS,
): Boolean {
    val newest = visibleNewestId?.trim().orEmpty()
    val incoming = incomingId?.trim().orEmpty()
    if (newest.isEmpty() || incoming.isEmpty()) return false
    if (incoming in knownMessageIds) return false
    if (!isObjectIdNewer(incoming, newest)) return false
    val counterGap = objectIdCounterGap(newest, incoming)
    if (counterGap > 1L) return true
    return objectIdTimestampGapMs(newest, incoming) >= thresholdMs
}

/** Same ObjectId second-bucket; counter delta when machine/process share prefix. */
internal fun objectIdCounterGap(olderId: String, newerId: String): Long {
    val older = olderId.trim()
    val newer = newerId.trim()
    if (older.length != 24 || newer.length != 24) return 0L
    if (older.substring(0, 18) != newer.substring(0, 18)) return 0L
    return (newer.substring(18).toLong(16) - older.substring(18).toLong(16)).coerceAtLeast(0L)
}
