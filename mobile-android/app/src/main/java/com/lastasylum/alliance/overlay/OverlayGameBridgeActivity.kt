package com.lastasylum.alliance.overlay

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.activity.ComponentActivity
import com.lastasylum.alliance.game.GameDeepLinkNavigator

/**
 * Transparent trampoline: FGS/overlay cannot reliably start the game activity on Android 12+.
 * Copies clipboard, brings the game to front, fires [globalphslink] VIEW intents, then finishes.
 *
 * Uses a non-focusable translucent window so the game is not paused by an opaque SquadRelay screen.
 */
class OverlayGameBridgeActivity : ComponentActivity() {
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
        )

        val clipLabel = intent?.getStringExtra(EXTRA_CLIP_LABEL).orEmpty()
        val clipText = intent?.getStringExtra(EXTRA_CLIP_TEXT).orEmpty()
        val uris = intent?.getStringArrayExtra(EXTRA_URIS)?.toList().orEmpty()
        if (uris.isEmpty() && clipText.isBlank()) {
            finish()
            return
        }

        if (clipText.isNotBlank()) {
            copyToClipboard(applicationContext, clipLabel.ifBlank { "game_bridge" }, clipText)
        }
        GameDeepLinkNavigator.bringGameToForeground(this)

        mainHandler.postDelayed({
            GameDeepLinkNavigator.openDeepLinksToGame(applicationContext, uris)
            finish()
            overridePendingTransition(0, 0)
        }, GameDeepLinkNavigator.CLIPBOARD_SETTLE_MS)
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    companion object {
        const val EXTRA_CLIP_LABEL = "clip_label"
        const val EXTRA_CLIP_TEXT = "clip_text"
        const val EXTRA_URIS = "uris"

        fun launch(
            context: Context,
            clipLabel: String,
            clipText: String,
            uris: Iterable<String>,
        ) {
            val list = uris.toList()
            if (list.isEmpty() && clipText.isBlank()) return
            val intent = Intent(context, OverlayGameBridgeActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(EXTRA_CLIP_LABEL, clipLabel)
                putExtra(EXTRA_CLIP_TEXT, clipText)
                putExtra(EXTRA_URIS, list.toTypedArray())
            }
            runCatching { context.startActivity(intent) }
        }

        private fun copyToClipboard(context: Context, clipLabel: String, text: String) {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText(clipLabel, text))
        }
    }
}
