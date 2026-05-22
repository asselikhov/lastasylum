package com.lastasylum.alliance.overlay

import android.os.Build
import android.os.Handler

/**
 * Overlay raid voice: deferred connect after user arms session or enters game with voice prefs on.
 */
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

    fun mayScheduleDeferredConnect(micPref: Boolean, soundPref: Boolean): Boolean =
        (micPref || soundPref) && userArmedThisSession

    fun isMicSupportedOnDevice(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
}
