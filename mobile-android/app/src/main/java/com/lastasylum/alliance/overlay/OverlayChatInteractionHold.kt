package com.lastasylum.alliance.overlay

/**
 * Пока true, [CombatOverlayService.tickGameGate] не скрывает оверлей по usage-stats «не в игре».
 * Нужно для полноэкранного превью вложений в оверлей-чате: иначе гейт может увидеть пакет приложения и снять окна.
 */
object OverlayChatInteractionHold {
    @Volatile
    var suppressGameForegroundGate: Boolean = false
}
