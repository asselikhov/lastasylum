package com.lastasylum.alliance.ui.chat

import android.util.Log
import com.lastasylum.alliance.BuildConfig
import java.util.concurrent.atomic.AtomicInteger

/** Logcat: `adb logcat -s SR_ChatDiag:D SR_ChatDelivery:D` */
object ChatDeliveryMetrics {
    private const val TAG = "SR_ChatDiag"
    private const val DELIVERY_TAG = "SR_ChatDelivery"

    private val stashCount = AtomicInteger()
    private val applyCount = AtomicInteger()
    private val dropCount = AtomicInteger()
    private val skipRefreshCount = AtomicInteger()
    private val capTrimCount = AtomicInteger()
    private val gapReconcileCount = AtomicInteger()
    private val stripDropCount = AtomicInteger()
    private val stripPendingCount = AtomicInteger()
    private val ingressDropCount = AtomicInteger()

    fun logStash(roomId: String, messageId: String?, reason: String = "socket") {
        stashCount.incrementAndGet()
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "stash room=$roomId id=${messageId ?: "-"} reason=$reason")
        }
    }

    fun logApply(roomId: String, count: Int, source: String = "batch") {
        applyCount.addAndGet(count)
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "apply room=$roomId count=$count source=$source")
        }
    }

    fun logDrop(roomId: String, messageId: String?, reason: String) {
        dropCount.incrementAndGet()
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "drop room=$roomId id=${messageId ?: "-"} reason=$reason")
        }
    }

    fun logSkipRefresh(roomId: String, reason: String = "cache_match") {
        skipRefreshCount.incrementAndGet()
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "skipRefresh room=$roomId reason=$reason")
            Log.d(DELIVERY_TAG, "skipRefresh room=$roomId reason=$reason")
        }
    }

    fun logSocketReceived(roomId: String, messageId: String?, receivedAtMs: Long = System.currentTimeMillis()) {
        if (BuildConfig.DEBUG) {
            Log.d(DELIVERY_TAG, "socketReceived room=$roomId id=${messageId ?: "-"} at=$receivedAtMs")
        }
    }

    fun logSocketApplied(roomId: String, messageId: String?, receivedAtMs: Long) {
        if (BuildConfig.DEBUG) {
            val latencyMs = (System.currentTimeMillis() - receivedAtMs).coerceAtLeast(0L)
            Log.d(DELIVERY_TAG, "socketApplied room=$roomId id=${messageId ?: "-"} latencyMs=$latencyMs")
        }
    }

    fun logIngressDrop(roomId: String, messageId: String?, reason: String) {
        ingressDropCount.incrementAndGet()
        if (BuildConfig.DEBUG) {
            Log.d(DELIVERY_TAG, "ingressDrop room=$roomId id=${messageId ?: "-"} reason=$reason")
        }
    }

    fun logCapTrim(roomId: String, before: Int, after: Int) {
        capTrimCount.incrementAndGet()
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "capTrim room=$roomId before=$before after=$after")
        }
    }

    fun logGapReconcile(roomId: String, trigger: String) {
        gapReconcileCount.incrementAndGet()
        Log.i(TAG, "gapReconcile room=$roomId trigger=$trigger")
    }

    fun logStripDrop(messageId: String?, reason: String) {
        stripDropCount.incrementAndGet()
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "stripDrop id=${messageId ?: "-"} reason=$reason")
        }
    }

    fun logStripPending(action: String, messageId: String?) {
        stripPendingCount.incrementAndGet()
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "stripPending action=$action id=${messageId ?: "-"}")
        }
    }

    fun logMergeAnchorDrop(roomId: String, messageId: String) {
        dropCount.incrementAndGet()
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "mergeAnchorDrop room=$roomId id=$messageId")
        }
    }

    fun snapshot(): String =
        "stash=${stashCount.get()} apply=${applyCount.get()} drop=${dropCount.get()} " +
            "skipRefresh=${skipRefreshCount.get()} capTrim=${capTrimCount.get()} " +
            "gapReconcile=${gapReconcileCount.get()} stripDrop=${stripDropCount.get()} " +
            "stripPending=${stripPendingCount.get()} ingressDrop=${ingressDropCount.get()}"
}
