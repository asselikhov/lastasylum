package com.lastasylum.alliance.ui.components.team.journal

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.ui.theme.SquadRelayDimens

/** Высота слота bottomBar редактора: vertical padding (12+12) + кнопка (~48). */
private val journalEditorBottomBarObstruction =
    24.dp + 48.dp + SquadRelayDimens.composerBottomGap

/**
 * Оверлей: IME поднимает корень [android.widget.FrameLayout] в [com.lastasylum.alliance.overlay.CombatOverlayService];
 * здесь только зазор над нижним краем контента.
 */
fun Modifier.journalEditorOverlayBottomDock(): Modifier =
    padding(bottom = SquadRelayDimens.composerBottomGap)

/**
 * Приложение: подъём CTA «Продолжить» над IME с вычитанием высоты слота bottomBar.
 */
@Composable
fun Modifier.journalEditorImePadding(): Modifier {
    val density = LocalDensity.current
    val imePx = WindowInsets.ime.getBottom(density)
    if (imePx <= 0) return this
    val obstructionPx = with(density) { journalEditorBottomBarObstruction.roundToPx() }
    val bottomPx = (imePx - obstructionPx).coerceAtLeast(0)
    return padding(bottom = with(density) { bottomPx.toDp() })
}

@Composable
fun Modifier.journalEditorBottomBarInsets(overlayUi: Boolean): Modifier =
    if (overlayUi) {
        navigationBarsPadding().journalEditorOverlayBottomDock()
    } else {
        navigationBarsPadding().journalEditorImePadding()
    }
