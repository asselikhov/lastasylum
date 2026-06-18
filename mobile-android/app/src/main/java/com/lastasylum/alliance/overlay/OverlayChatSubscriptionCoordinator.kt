package com.lastasylum.alliance.overlay

/**
 * Serializes overlay chat socket subscription bootstrap so concurrent
 * [CombatOverlayService.beginOverlayChatSubscription] calls do not register duplicate listeners.
 */
internal class OverlayChatSubscriptionCoordinator {
    @Volatile
    private var starting: Boolean = false

    fun begin(
        hasActiveListener: Boolean,
        onAlreadySubscribed: () -> Unit,
        onStartSubscription: () -> Unit,
    ) {
        if (hasActiveListener) {
            onAlreadySubscribed()
            return
        }
        if (starting) return
        starting = true
        try {
            onStartSubscription()
        } finally {
            starting = false
        }
    }

    internal fun isStartingForTests(): Boolean = starting
}
