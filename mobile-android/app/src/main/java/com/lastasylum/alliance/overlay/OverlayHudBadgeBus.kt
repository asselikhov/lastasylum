package com.lastasylum.alliance.overlay

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.ArrayDeque

/**
 * Main-thread event bus — single coalesced commit per frame into [OverlayHudBadgeReducer].
 */
class OverlayHudBadgeBus(
    private val reducer: OverlayHudBadgeReducer,
    private val mergeNews: (Int, Int, Boolean) -> Int,
    private val mergeForum: (Int, Int, Int, Boolean) -> Int,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pending = ArrayDeque<OverlayHudBadgeEvent>()
    private var flushPosted = false

    private val flushRunnable = Runnable {
        flushPosted = false
        val batch = synchronized(pending) {
            val copy = pending.toList()
            pending.clear()
            copy
        }
        if (batch.isEmpty()) return@Runnable
        reducer.commit { prev ->
            var next = prev
            batch.forEach { event ->
                next = applyEvent(next, event)
            }
            next
        }
    }

    fun emit(event: OverlayHudBadgeEvent) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            enqueueAndSchedule(event)
        } else {
            mainHandler.post { enqueueAndSchedule(event) }
        }
    }

    fun current(): OverlayGameStatusHudState = reducer.current()

    private fun enqueueAndSchedule(event: OverlayHudBadgeEvent) {
        synchronized(pending) {
            pending.addLast(event)
        }
        if (!flushPosted) {
            flushPosted = true
            mainHandler.post(flushRunnable)
        }
    }

    private fun applyEvent(
        prev: OverlayGameStatusHudState,
        event: OverlayHudBadgeEvent,
    ): OverlayGameStatusHudState = when (event) {
        is OverlayHudBadgeEvent.HubUnread ->
            prev.copy(allianceChatUnread = event.displayed.coerceIn(0, 999))
        is OverlayHudBadgeEvent.NewsUnread ->
            prev.copy(
                teamNewsUnread = mergeNews(
                    event.effective,
                    prev.teamNewsUnread,
                    event.useAuthoritative,
                ),
            )
        is OverlayHudBadgeEvent.ForumUnread ->
            prev.copy(
                forumUnread = mergeForum(
                    event.effective,
                    prev.forumUnread,
                    event.rawServer,
                    event.useAuthoritative,
                ),
            )
        is OverlayHudBadgeEvent.FullRefreshResult ->
            prev.copy(
                allianceChatUnread = event.allianceChatUnread.coerceIn(0, 999),
                teamNewsUnread = event.teamNewsUnread.coerceAtLeast(0),
                forumUnread = event.forumUnread.coerceAtLeast(0),
            )
        is OverlayHudBadgeEvent.SeedFromLocal ->
            prev.copy(
                allianceChatUnread = event.allianceChatUnread.coerceIn(0, 999),
                teamNewsUnread = event.teamNewsUnread.coerceAtLeast(0),
                forumUnread = event.forumUnread.coerceAtLeast(0),
            )
        is OverlayHudBadgeEvent.AppUpdateUrl ->
            prev.copy(appUpdateDownloadUrl = event.url?.trim()?.takeIf { it.isNotEmpty() })
        OverlayHudBadgeEvent.ClearHub ->
            prev.copy(allianceChatUnread = 0)
    }
}
