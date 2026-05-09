package com.lastasylum.alliance

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.lastasylum.alliance.ui.SquadRelayApp
import com.lastasylum.alliance.ui.util.ImeSnapInsets

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        super.onCreate(savedInstanceState)
        hideSystemUi()
        window.decorView.post { ImeSnapInsets.install(window.decorView) }
        setContent {
            SquadRelayApp()
        }
    }

    private fun hideSystemUi() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            // Manifest uses adjustNothing; chat/forum use composerImeAboveBottomNav to sit on IME while the tab bar stays visible.
            // Hiding legacy three-button navigation strip so more vertical space is available for game/content.
            hide(WindowInsetsCompat.Type.navigationBars())
        }
    }
}
