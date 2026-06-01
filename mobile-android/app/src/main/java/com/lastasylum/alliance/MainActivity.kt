package com.lastasylum.alliance

import android.graphics.Color
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.lastasylum.alliance.overlay.CombatOverlayService
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
        handleIncomingShareIntent(intent)
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
        // adjustNothing: IME поверх окна; навбар не скрывается; imePadding только на полях ввода.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemUi()
        setContent {
            SquadRelayApp()
        }
        handleIncomingShareIntent(intent)
    }

    private fun handleIncomingShareIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        if (intent.type != "text/plain") return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
        if (text.isEmpty()) return
        if (com.lastasylum.alliance.game.MapCoordinateParser.parseSharedText(text) == null) {
            Toast.makeText(this, R.string.share_coord_parse_failed, Toast.LENGTH_LONG).show()
            setIntent(Intent(this, MainActivity::class.java).apply { action = Intent.ACTION_MAIN })
            return
        }
        if (!CombatOverlayService.shareMapCoordinatesFromExternal(this, text)) {
            Toast.makeText(this, R.string.share_coord_need_overlay, Toast.LENGTH_LONG).show()
        }
        setIntent(Intent(this, MainActivity::class.java).apply { action = Intent.ACTION_MAIN })
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
