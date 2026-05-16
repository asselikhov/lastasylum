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

    private val suppressLock = Any()

    @Volatile
    private var overlayModalSuppressDepth: Int = 0

    /** Счётчик модалок/sheet в оверлее (AlertDialog, bottom sheet и т.п.). */
    fun acquireGameForegroundSuppress() {
        synchronized(suppressLock) {
            overlayModalSuppressDepth++
            suppressGameForegroundGate = true
        }
    }

    fun releaseGameForegroundSuppress() {
        synchronized(suppressLock) {
            overlayModalSuppressDepth = (overlayModalSuppressDepth - 1).coerceAtLeast(0)
            if (overlayModalSuppressDepth == 0) {
                clearSuppressUnlessFullscreenPanel()
            }
        }
    }

    /**
     * Вызвать из onClick до `showDialog = true`, чтобы гейт не успел снять окна до [OverlayInteractionSuppressEffect].
     * Не увеличивает [overlayModalSuppressDepth] — счётчик ведёт только DisposableEffect модалки.
     */
    fun prepareOverlayModalInteraction(isOverlayUi: Boolean) {
        if (isOverlayUi) suppressGameForegroundGate = true
    }

    fun isOverlayModalSuppressActive(): Boolean =
        synchronized(suppressLock) { overlayModalSuppressDepth > 0 }

    fun isGameForegroundGateSuppressed(): Boolean =
        suppressGameForegroundGate ||
            suppressGameForegroundGateForOverlayPanel ||
            isOverlayModalSuppressActive()

    /** Блокировать BackHandler полноэкранной панели, пока открыта модалка в оверлее. */
    fun blocksFullscreenPanelBack(): Boolean = isOverlayModalSuppressActive()

    /** Сбросить suppress только если не открыта полноэкранная панель чата/команды. */
    fun clearSuppressUnlessFullscreenPanel() {
        if (!isFullscreenChatTeamPanelVisible) {
            suppressGameForegroundGate = false
        }
    }
}
