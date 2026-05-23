package com.lastasylum.alliance.overlay

import android.os.Build
import android.os.Handler

/** Overlay raid voice: deferred connect only after user arms session from HUD toggles. */
internal class OverlayVoiceController(
    private val mainHandler: Handler,
) {
    var userArmedThisSession: Boolean = false

    fun armFromUserAction() {
        userArmedThisSession = true
    }

    fun resetSession() {
        userArmedThisSession = false
    }

    fun isMicSupportedOnDevice(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
}
