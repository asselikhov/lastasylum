package com.lastasylum.alliance

import android.graphics.Color
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.lastasylum.alliance.ui.SquadRelayApp

class MainActivity : ComponentActivity() {
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

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
        // adjustResize: окно сжимается под IME; композер — 8dp, навбар остаётся в Scaffold.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemUi()
        setContent {
            SquadRelayApp()
        }
    }

    companion object {
        const val EXTRA_START_TAB = "com.lastasylum.alliance.extra.START_TAB"
    }

    private fun hideSystemUi() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            // Hiding legacy three-button navigation strip so more vertical space is available for game/content.
            hide(WindowInsetsCompat.Type.navigationBars())
        }
    }
}
