package com.lastasylum.alliance.overlay

import android.util.Log

/** Lightweight overlay runtime metrics for release diagnostics (logcat tag SR_OverlayMetrics). */
internal object OverlayRuntimeMetrics {
    private const val TAG = "SR_OverlayMetrics"

    fun logPresenceTransition(from: String, to: String, inGameProbe: Boolean) {
        Log.i(TAG, "presence from=$from to=$to inGameProbe=$inGameProbe")
    }

    fun logGameGate(inGame: Boolean, showUi: Boolean, mainAppForeground: Boolean) {
        Log.i(
            TAG,
            "gate inGame=$inGame showUi=$showUi mainAppForeground=$mainAppForeground",
        )
    }

    fun logServiceLifecycle(event: String) {
        Log.i(TAG, "service event=$event")
    }
}
