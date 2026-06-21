package com.lastasylum.alliance.overlay

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import com.lastasylum.alliance.game.GameDeepLinkNavigator

/**
 * Transparent trampoline: FGS/overlay cannot reliably start the game activity on Android 12+.
 * This activity copies clipboard data and fires [globalphslink] VIEW intents, then finishes.
 */
class OverlayGameBridgeActivity : ComponentActivity() {
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val clipLabel = intent?.getStringExtra(EXTRA_CLIP_LABEL).orEmpty()
        val clipText = intent?.getStringExtra(EXTRA_CLIP_TEXT).orEmpty()
        val uris = intent?.getStringArrayExtra(EXTRA_URIS)?.toList().orEmpty()
        if (clipText.isNotBlank()) {
            copyToClipboard(applicationContext, clipLabel.ifBlank { "game_bridge" }, clipText)
        }
        mainHandler.postDelayed({
            GameDeepLinkNavigator.openFirstMatchingFromActivity(this, uris)
            finish()
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
