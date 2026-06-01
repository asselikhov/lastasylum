package com.lastasylum.alliance.ui.theme

import androidx.compose.ui.unit.dp

/** 8dp grid: единые отступы по всем экранам. */
object SquadRelayDimens {
    /** Минимальная высота ряда вкладок в [com.lastasylum.alliance.ui.AppNavigation]. */
    val bottomNavigationBarHeight = 56.dp
    /** Нижний отступ слота bottomBar (фиксированный, без navigationBars inset). */
    val bottomBarOuterPadding = 8.dp
    /** Радиус панели нижней навигации и кнопок вкладок (одинаковый). */
    val bottomNavigationBarCornerRadius = 16.dp
    val screenPaddingHorizontal = 16.dp
    val screenPaddingVertical = 12.dp
    /** Горизонталь контента внутри вкладок (выравнивание с навбаром). */
    val contentPaddingHorizontal = 12.dp
    val screenTopPadding = 6.dp
    val sectionGap = 12.dp
    val blockGap = 10.dp
    val itemGap = 6.dp
    val cardInnerPadding = 14.dp
    val panelInnerPadding = 14.dp
    val composerInnerPadding = 10.dp
    /** Небольший зазор композера над нижним краем контента / IME ([chatComposerOverlayDock]). */
    val composerBottomGap = 8.dp
    /**
     * Высота слота [bottomBar] Scaffold: padding Box (10+10) + Row (6+56+6).
     * Вычитается из IME-inset, чтобы зазор композер–клавиатура совпадал с зазором композер–навбар.
     */
    val chatComposerScaffoldBottomObstruction =
        bottomNavigationBarHeight + 20.dp + 12.dp + bottomBarOuterPadding
    val headerSubtitleGap = 2.dp
    val listRowHorizontalPadding = 10.dp
    val listRowVerticalPadding = 10.dp
    /** Расстояние между карточками тем форума в списке. */
    val forumTopicListSpacing = 12.dp
    val sectionTitlePaddingVertical = 2.dp
    val composerMinHeight = 44.dp

    /** См. [com.lastasylum.alliance.ui.chat.ChatBubbleMaxWidthFraction]. */
    val chatBubbleMaxWidthFraction = 0.88f
    val chatBubbleMaxWidthCap = 360.dp
}
