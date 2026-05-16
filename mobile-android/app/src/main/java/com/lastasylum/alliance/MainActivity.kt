package com.lastasylum.alliance

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.lastasylum.alliance.ui.SquadRelayApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        @Suppress("DEPRECATION")
        window.statusBarColor = Color.parseColor("#FF070B14")
        @Suppress("DEPRECATION")
        window.navigationBarColor = Color.parseColor("#FF04060D")
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        super.onCreate(savedInstanceState)
        // Иначе при enableEdgeToEdge контент часто остаётся «на полный экран», а IME только накладывается —
        // композер оказывается под клавиатурой без покадрового imePadding (который даёт лаги).
        WindowCompat.setDecorFitsSystemWindows(window, true)
        hideSystemUi()
        setContent {
            SquadRelayApp()
        }
    }

    private fun hideSystemUi() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            // Manifest adjustResize + decorFitsSystemWindows(true): система сжимает окно, без тяжёлого ime padding в Compose.
            // Hiding legacy three-button navigation strip so more vertical space is available for game/content.
            hide(WindowInsetsCompat.Type.navigationBars())
        }
    }
}
