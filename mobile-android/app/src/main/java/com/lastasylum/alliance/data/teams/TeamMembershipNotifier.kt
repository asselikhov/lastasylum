package com.lastasylum.alliance.data.teams

import java.util.concurrent.CopyOnWriteArrayList

/** Fired when [MyProfileDto.playerTeamId] changes (join / leave / switch team). */
object TeamMembershipNotifier {
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    @Volatile
    private var onChangedHook: ((String?) -> Unit)? = null

    fun setOnChanged(hook: (String?) -> Unit) {
        onChangedHook = hook
    }

    fun addListener(listener: () -> Unit) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    fun notifyChanged(playerTeamId: String?) {
        onChangedHook?.invoke(playerTeamId)
        listeners.forEach { runCatching { it() } }
    }
}
