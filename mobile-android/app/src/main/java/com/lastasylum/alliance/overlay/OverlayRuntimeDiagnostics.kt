package com.lastasylum.alliance.overlay

/**
 * Last-known overlay runtime state from [CombatOverlayService] for support / settings diagnostics.
 */
object OverlayRuntimeDiagnostics {
    @Volatile
    var lastGateTickAtMs: Long = 0L

    @Volatile
    var lastInGameProbe: Boolean = false

    @Volatile
    var lastStableShow: Boolean = false

    @Volatile
    var hudStatusAttached: Boolean = false

    @Volatile
    var hudTopRightAttached: Boolean = false

    @Volatile
    var hudAttachAllowed: Boolean = false

    @Volatile
    var lastAddViewError: String? = null

    @Volatile
    var entryBoostActive: Boolean = false

    @Volatile
    var lastFcmTokenRegisteredAtMs: Long = 0L

    fun recordGateTick(
        inGameProbe: Boolean,
        stableShow: Boolean,
        hudAttachAllowed: Boolean,
        statusAttached: Boolean,
        topRightAttached: Boolean,
        entryBoost: Boolean,
    ) {
        lastGateTickAtMs = System.currentTimeMillis()
        lastInGameProbe = inGameProbe
        lastStableShow = stableShow
        this.hudAttachAllowed = hudAttachAllowed
        hudStatusAttached = statusAttached
        hudTopRightAttached = topRightAttached
        entryBoostActive = entryBoost
    }

    fun recordAddViewFailure(label: String, error: Throwable?) {
        lastAddViewError = "$label: ${error?.message ?: "unknown"}"
    }

    fun recordFcmTokenRegistered() {
        lastFcmTokenRegisteredAtMs = System.currentTimeMillis()
    }
}
