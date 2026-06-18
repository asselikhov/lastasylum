package com.lastasylum.alliance.overlay

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.lastasylum.alliance.BuildConfig

/** Debug overlay performance counters (logcat tag SR_OverlayDiag). */
internal object OverlayPerfDiag {
    private const val TAG = "SR_OverlayDiag"

    private var hudRefreshWindowStartMs: Long = 0L
    private var hudRefreshCountInWindow: Int = 0

    fun logHudRefreshScheduled() {
        if (!BuildConfig.DEBUG) return
        val now = System.currentTimeMillis()
        if (hudRefreshWindowStartMs == 0L || now - hudRefreshWindowStartMs > 60_000L) {
            if (hudRefreshWindowStartMs > 0L) {
                Log.d(TAG, "hudRefreshPerMin=$hudRefreshCountInWindow")
            }
            hudRefreshWindowStartMs = now
            hudRefreshCountInWindow = 0
        }
        hudRefreshCountInWindow++
    }

    fun logHudRefreshDone(durationMs: Long, allianceUnread: Int, forumUnread: Int, newsUnread: Int) {
        if (!BuildConfig.DEBUG) return
        Log.d(
            TAG,
            "hudRefreshDone ms=$durationMs chat=$allianceUnread forum=$forumUnread news=$newsUnread",
        )
    }

    fun logRuntimeSnapshot(
        context: Context,
        micOn: Boolean,
        soundOn: Boolean,
        overlayWindows: Int,
        hudOnly: Boolean,
        lightStrip: Boolean,
    ) {
        if (!BuildConfig.DEBUG) return
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val mem = ActivityManager.MemoryInfo().also { am?.getMemoryInfo(it) }
        val availMb = mem.availMem / (1024 * 1024)
        Log.d(
            TAG,
            "runtime device=${Build.MANUFACTURER} ${Build.MODEL} api=${Build.VERSION.SDK_INT} " +
                "memAvailMb=$availMb mic=$micOn sound=$soundOn windows=$overlayWindows " +
                "hudOnly=$hudOnly lightStrip=$lightStrip",
        )
    }

    fun logPanelPrime(durationMs: Long, hubReady: Boolean) {
        if (!BuildConfig.DEBUG) return
        Log.d(TAG, "panelPrime ms=$durationMs hubReady=$hubReady")
    }

    fun logColdHydrate(
        durationMs: Long,
        seededContext: Boolean,
        seededRooms: Boolean,
        seededBadges: Boolean,
    ) {
        if (!BuildConfig.DEBUG) return
        Log.d(
            TAG,
            "coldHydrate ms=$durationMs ctx=$seededContext rooms=$seededRooms badges=$seededBadges",
        )
    }

    fun logPanelOpen(pane: String) {
        if (!BuildConfig.DEBUG) return
        Log.d(TAG, "panelOpen pane=$pane")
    }

    fun logDeliveryLatency(tracker: com.lastasylum.alliance.data.telemetry.DeliveryLatencyTracker) {
        if (!BuildConfig.DEBUG) return
        val snap = tracker.snapshot()
        fun log(type: com.lastasylum.alliance.data.telemetry.LatencySpanType, label: String) {
            val stats = snap.byType[type] ?: return
            if (stats.count <= 0) return
            Log.d(TAG, "latency $label p50=${stats.p50Ms}ms p95=${stats.p95Ms}ms n=${stats.count}")
        }
        log(com.lastasylum.alliance.data.telemetry.LatencySpanType.OverlayRaidQuickCommandSend, "raidQuickSend")
        log(com.lastasylum.alliance.data.telemetry.LatencySpanType.OverlayStripIngest, "stripIngest")
        log(com.lastasylum.alliance.data.telemetry.LatencySpanType.ChatSendToOptimisticPaint, "optimisticPaint")
        log(com.lastasylum.alliance.data.telemetry.LatencySpanType.ChatSendToHttpAck, "httpAck")
        log(com.lastasylum.alliance.data.telemetry.LatencySpanType.ChatSendToSocket, "socketAck")
        log(com.lastasylum.alliance.data.telemetry.LatencySpanType.ChatPeerMessageVisible, "peerVisible")
    }

    fun logGateState(
        inGame: Boolean,
        showUi: Boolean,
        stripNotTouchable: Boolean,
        dismissRectCount: Int,
        zOrderLifted: Boolean,
        hudStatusVisible: Boolean,
        hudTopRightVisible: Boolean,
    ) {
        if (!BuildConfig.DEBUG) return
        Log.d(
            TAG,
            "gate inGame=$inGame showUi=$showUi stripNotTouchable=$stripNotTouchable " +
                "dismissRects=$dismissRectCount zOrderLifted=$zOrderLifted " +
                "hudStatus=$hudStatusVisible hudTopRight=$hudTopRightVisible " +
                "suppressDepth=${OverlayChatInteractionHold.overlayModalSuppressDepthForDiag()}",
        )
    }
}
