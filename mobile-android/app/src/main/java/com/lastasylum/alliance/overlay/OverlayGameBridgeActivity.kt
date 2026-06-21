package com.lastasylum.alliance.overlay

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import com.lastasylum.alliance.game.GameDeepLinkNavigator

/**
 * NoDisplay relay: copy clipboard and fire map/search deep links without visible UI.
 * Must [finish] in [onCreate] before resume ([Theme.NoDisplay] requirement).
 * Map burst is scheduled by [GameDeepLinkNavigator.openMapCoordinates] after launch returns.
 */
class OverlayGameBridgeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        suppressActivityTransition(isClosing = false)

        val clipLabel = intent?.getStringExtra(EXTRA_CLIP_LABEL).orEmpty()
        val clipText = intent?.getStringExtra(EXTRA_CLIP_TEXT).orEmpty()
        val bracketClipText = intent?.getStringExtra(EXTRA_BRACKET_CLIP_TEXT).orEmpty()
        val uris = intent?.getStringArrayExtra(EXTRA_URIS)?.toList().orEmpty()
        val mapBurst = intent?.getBooleanExtra(EXTRA_MAP_BURST, false) == true

        if (uris.isEmpty() && clipText.isBlank() && bracketClipText.isBlank()) {
            finish()
            return
        }

        val clipPayloads = buildList {
            if (bracketClipText.isNotBlank()) add(bracketClipText)
            if (clipText.isNotBlank() && clipText != bracketClipText) add(clipText)
        }
        copyClipPayloads(applicationContext, clipLabel.ifBlank { "game_bridge" }, clipPayloads)

        if (mapBurst) {
            // Burst runs from FGS after finish (NoDisplay must not survive onResume).
            suppressActivityTransition(isClosing = true)
            finish()
            return
        }

        GameDeepLinkNavigator.openDeepLinksToGame(this, uris, clipPayloads)
        suppressActivityTransition(isClosing = true)
        finish()
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
        const val EXTRA_BRACKET_CLIP_TEXT = "bracket_clip_text"
        const val EXTRA_URIS = "uris"
        const val EXTRA_MAP_BURST = "map_burst"

        fun launch(
            context: Context,
            clipLabel: String,
            clipText: String,
            uris: Iterable<String>,
            mapBurst: Boolean = false,
            bracketClipText: String = clipText,
        ): Boolean {
            val list = uris.toList()
            if (list.isEmpty() && clipText.isBlank() && bracketClipText.isBlank()) return false
            val intent = Intent(context, OverlayGameBridgeActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP,
                )
                putExtra(EXTRA_CLIP_LABEL, clipLabel)
                putExtra(EXTRA_CLIP_TEXT, clipText)
                putExtra(EXTRA_BRACKET_CLIP_TEXT, bracketClipText)
                putExtra(EXTRA_URIS, list.toTypedArray())
                putExtra(EXTRA_MAP_BURST, mapBurst)
            }
            return runCatching {
                GameDeepLinkNavigator.startActivityAllowBackground(context, intent)
            }.getOrDefault(false)
        }

        private fun copyClipPayloads(context: Context, clipLabel: String, payloads: List<String>) {
            val primary = payloads.firstOrNull()?.takeIf { it.isNotBlank() } ?: return
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText(clipLabel, primary))
        }
    }
}
