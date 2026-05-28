package com.lastasylum.alliance.game

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.lastasylum.alliance.R
import com.lastasylum.alliance.di.AppContainer
import com.lastasylum.alliance.overlay.GameForegroundGate

/**
 * Opens the configured target game at map coordinates: clipboard, foreground, then deep link attempts.
 */
object GameMapNavigator {
    private val DEEP_LINK_TEMPLATES = listOf(
        "globalphslink://map?x=%d&y=%d",
        "globalphslink://world?x=%d&y=%d",
        "globalphslink://coordinate?x=%d&y=%d",
    )

    fun open(context: Context, x: Int, y: Int) {
        val appContext = context.applicationContext
        copyCoordinatesToClipboard(appContext, x, y)
        val packages = targetPackages(appContext)
        bringGameToForeground(appContext, packages)
        val deepLinkLaunched = tryDeepLinks(appContext, packages, x, y)
        val messageRes = if (deepLinkLaunched) {
            R.string.map_coord_opened_game
        } else {
            R.string.map_coord_copied_fallback
        }
        Toast.makeText(appContext, appContext.getString(messageRes), Toast.LENGTH_SHORT).show()
    }

    fun openFromMessage(context: Context, text: String) {
        val coord = MapCoordinateParser.parse(text) ?: return
        open(context, coord.x, coord.y)
    }

    private fun targetPackages(context: Context): List<String> =
        AppContainer.from(context).userSettingsPreferences.getOverlayTargetGamePackages()
            .ifEmpty {
                listOf(
                    GameForegroundGate.DEFAULT_TARGET_GAME_PACKAGES_CSV
                        .split(",")
                        .first()
                        .trim(),
                )
            }

    private fun copyCoordinatesToClipboard(context: Context, x: Int, y: Int) {
        val clipText = "X:$x Y:$y"
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("map_coordinates", clipText))
    }

    private fun bringGameToForeground(context: Context, packages: List<String>) {
        for (pkg in packages) {
            val launch = context.packageManager.getLaunchIntentForPackage(pkg) ?: continue
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            runCatching { context.startActivity(launch) }
            return
        }
    }

    private fun tryDeepLinks(context: Context, packages: List<String>, x: Int, y: Int): Boolean {
        for (template in DEEP_LINK_TEMPLATES) {
            val uri = Uri.parse(template.format(x, y))
            for (pkg in packages) {
                val targeted = Intent(Intent.ACTION_VIEW, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    setPackage(pkg)
                }
                if (context.packageManager.resolveActivity(targeted, 0) != null) {
                    runCatching {
                        context.startActivity(targeted)
                        return true
                    }
                }
            }
            val generic = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (context.packageManager.resolveActivity(generic, 0) != null) {
                runCatching {
                    context.startActivity(generic)
                    return true
                }
            }
        }
        return false
    }
}
