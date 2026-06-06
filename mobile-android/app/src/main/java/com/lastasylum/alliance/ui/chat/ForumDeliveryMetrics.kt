package com.lastasylum.alliance.ui.chat

import android.util.Log
import com.lastasylum.alliance.BuildConfig
import java.util.concurrent.atomic.AtomicInteger

/** Logcat: `adb logcat -s SR_ForumDiag:D` */
object ForumDeliveryMetrics {
    private const val TAG = "SR_ForumDiag"

    private val mergeCount = AtomicInteger()
    private val dropCount = AtomicInteger()
    private val gapReconcileCount = AtomicInteger()
    private val stashCount = AtomicInteger()

    fun logMerge(teamId: String, topicId: String, count: Int, source: String = "merge") {
        mergeCount.addAndGet(count)
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "merge team=$teamId topic=$topicId count=$count source=$source")
        }
    }

    fun logDrop(teamId: String, topicId: String, messageId: String?, reason: String) {
        dropCount.incrementAndGet()
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "drop team=$teamId topic=$topicId id=${messageId ?: "-"} reason=$reason")
        }
    }

    fun logGapReconcile(teamId: String, topicId: String, trigger: String) {
        gapReconcileCount.incrementAndGet()
        Log.i(TAG, "gapReconcile team=$teamId topic=$topicId trigger=$trigger")
    }

    fun logStash(teamId: String, topicId: String, messageId: String?) {
        stashCount.incrementAndGet()
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "stash team=$teamId topic=$topicId id=${messageId ?: "-"}")
        }
    }

    fun snapshot(): String =
        "merge=${mergeCount.get()} drop=${dropCount.get()} " +
            "gapReconcile=${gapReconcileCount.get()} stash=${stashCount.get()}"
}
