package com.lastasylum.alliance.overlay

/**
 * Пока true, [CombatOverlayService.tickGameGate] не скрывает оверлей по usage-stats «не в игре».
 * Нужно для полноэкранного превью вложений в оверлей-чате: иначе гейт может увидеть пакет приложения и снять окна.
 */
object OverlayChatInteractionHold {
    @Volatile
    var suppressGameForegroundGate: Boolean = false

    /**
     * Пока пользователь тащит панель оверлея: не скрывать окна по usage-stats (лагает фокус на части ROM).
     */
    @Volatile
    var suppressGameForegroundGateForOverlayPanel: Boolean = false

    /**
     * Полноэкранное окно «Чат + Команда» в оверлее открыто ([CombatOverlayService.showOverlayChatTeamPanel]).
     * Дублирует флаг сервиса для UI-слоя и защиты от ложного [repairDetachedOverlayShellIfNeeded].
     */
    @Volatile
    var isFullscreenChatTeamPanelVisible: Boolean = false

    /** Сбросить suppress только если не открыта полноэкранная панель чата/команды. */
    fun clearSuppressUnlessFullscreenPanel() {
        if (!isFullscreenChatTeamPanelVisible) {
            suppressGameForegroundGate = false
        }
    }
}
