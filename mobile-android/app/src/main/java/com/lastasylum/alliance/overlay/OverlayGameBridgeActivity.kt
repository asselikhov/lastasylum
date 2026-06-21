package com.lastasylum.alliance.overlay

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.activity.ComponentActivity
import com.lastasylum.alliance.game.GameDeepLinkNavigator

/**
 * Transparent trampoline when FGS cannot start the game activity directly.
 * Clipboard is usually pre-set by [GameDeepLinkNavigator]; this Activity only
 * fires VIEW intents from an Activity context (BAL-safe), then finishes immediately.
 */
class OverlayGameBridgeActivity : ComponentActivity() {
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        suppressActivityTransition(isClosing = false)
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

        val delayMs = if (clipText.isNotBlank()) GameDeepLinkNavigator.CLIPBOARD_SETTLE_MS else 0L
        mainHandler.postDelayed({
            GameDeepLinkNavigator.openDeepLinksToGame(this@OverlayGameBridgeActivity, uris)
            suppressActivityTransition(isClosing = true)
            finish()
        }, delayMs)
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun suppressActivityTransition(isClosing: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val type = if (isClosing) {
                OVERRIDE_TRANSITION_CLOSE
            } else {
                OVERRIDE_TRANSITION_OPEN
            }
            overrideActivityTransition(type, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
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
        ): Boolean {
            val list = uris.toList()
            if (list.isEmpty() && clipText.isBlank()) return false
            val intent = Intent(context, OverlayGameBridgeActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP,
                )
                putExtra(EXTRA_CLIP_LABEL, clipLabel)
                putExtra(EXTRA_CLIP_TEXT, clipText)
                putExtra(EXTRA_URIS, list.toTypedArray())
            }
            return runCatching { context.startActivity(intent); true }.getOrDefault(false)
        }

        private fun copyToClipboard(context: Context, clipLabel: String, text: String) {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText(clipLabel, text))
        }
    }
}
