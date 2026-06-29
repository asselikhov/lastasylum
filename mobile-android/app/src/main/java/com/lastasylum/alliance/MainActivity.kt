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
import com.lastasylum.alliance.update.installDownloadedApk
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleInstallApkIntent(intent)
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
        handleInstallApkIntent(intent)
        handleIncomingShareIntent(intent)
    }

    /**
     * Запуск установщика обновления приложения из foreground. Сервис оверлея только скачивает
     * APK и поднимает эту Activity — запуск установщика из фона блокируется BAL на многих ROM.
     */
    private fun handleInstallApkIntent(intent: Intent?) {
        val path = intent?.getStringExtra(EXTRA_INSTALL_APK_PATH)?.trim().orEmpty()
        if (path.isEmpty()) return
        // Снимаем экстру, чтобы установщик не открывался повторно при пересоздании Activity.
        setIntent(Intent(this, MainActivity::class.java).apply { action = Intent.ACTION_MAIN })
        val apk = File(path)
        if (!apk.exists() || apk.length() <= 0L) {
            Toast.makeText(this, R.string.chat_apk_install_failed, Toast.LENGTH_LONG).show()
            return
        }
        installDownloadedApk(apk)
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

        /** Абсолютный путь к скачанному APK обновления, который нужно установить из foreground. */
        const val EXTRA_INSTALL_APK_PATH = "com.lastasylum.alliance.extra.INSTALL_APK_PATH"
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
