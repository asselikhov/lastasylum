package com.lastasylum.alliance.data.settings

import android.content.Context

/** Одноразовый онбординг разрешений после входа. */
class OnboardingPreferences(context: Context) {
    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isPermissionOnboardingDone(): Boolean =
        prefs.getBoolean(KEY_PERMISSION_ONBOARDING_DONE, false)

    fun setPermissionOnboardingDone(value: Boolean) {
        prefs.edit().putBoolean(KEY_PERMISSION_ONBOARDING_DONE, value).apply()
    }

    private companion object {
        const val PREFS_NAME = "squadrelay_onboarding"
        const val KEY_PERMISSION_ONBOARDING_DONE = "permission_onboarding_done"
    }
}
