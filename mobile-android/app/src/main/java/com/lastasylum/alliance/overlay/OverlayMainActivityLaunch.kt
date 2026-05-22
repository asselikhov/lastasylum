package com.lastasylum.alliance.overlay

import android.content.Context
import android.content.Intent
import com.lastasylum.alliance.MainActivity
import com.lastasylum.alliance.ui.AppTab

internal object OverlayMainActivityLaunch {
    fun launchTab(context: Context, tabRoute: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(MainActivity.EXTRA_START_TAB, tabRoute)
        }
        runCatching { context.startActivity(intent) }
    }

    fun launchChatTab(context: Context) = launchTab(context, AppTab.CHAT.route)

    fun launchOverlaySettingsTab(context: Context) = launchTab(context, AppTab.OVERLAY.route)
}
