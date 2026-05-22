package com.lastasylum.alliance.overlay

import android.os.Build
import android.os.Handler

/**
 * Overlay raid voice: connect only after explicit user action in the current FGS session.
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
        userArmedThisSession && (micPref || soundPref)

    fun isMicSupportedOnDevice(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
}
