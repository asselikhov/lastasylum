package com.lastasylum.alliance.overlay.layout

import com.lastasylum.alliance.data.settings.UserSettingsPreferences

/** Смещения оверлея в dp (конвертация в px в сервисе через `dp()`). */
data class OverlayLayoutDp(
    val toggleX: Int,
    val toggleY: Int,
    val lockX: Int,
    val lockY: Int,
    val tickerY: Int,
) {
    companion object {
        fun forPreset(presetKey: String): OverlayLayoutDp = when (presetKey) {
            UserSettingsPreferences.PRESET_COMMANDER ->
                OverlayLayoutDp(10, 120, 10, 192, 150)
            UserSettingsPreferences.PRESET_MINIMAL ->
                OverlayLayoutDp(10, 52, 10, 104, 34)
            else ->
                OverlayLayoutDp(10, 86, 10, 138, 110)
        }
    }
}
