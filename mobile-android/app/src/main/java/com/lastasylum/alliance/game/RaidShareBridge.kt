package com.lastasylum.alliance.game

/**
 * Константы и in-memory шина для канала «панель шаринга в игре → SquadRelay».
 *
 * Игровой Frida-бридж шлёт explicit broadcast с действием [ACTION_SHARE_TARGET]
 * и строковым extra [EXTRA_PAYLOAD] (сырой JSON). Получатель регистрируется в
 * `CombatOverlayService` (runtime, exported), парсит payload в [RaidShareTarget]
 * и показывает/прячет панель «В рейд».
 */
object RaidShareBridge {
    const val ACTION_SHARE_TARGET = "com.lastasylum.alliance.action.SHARE_TARGET"
    const val EXTRA_PAYLOAD = "payload"
}

/**
 * Канал «игровое окно тегов → SquadRelay». Игровой Frida-бридж шлёт broadcast
 * [ACTION_BOOKMARK_TARGET] при открытии/закрытии окна «Добавить тег» с тем же payload,
 * что и шаринг (см. [RaidShareTarget]). Получатель в `CombatOverlayService` показывает
 * панель «В закладки» над игровым окном.
 */
object BookmarkBridge {
    const val ACTION_BOOKMARK_TARGET = "com.lastasylum.alliance.action.BOOKMARK_TARGET"
    const val EXTRA_PAYLOAD = "payload"
}
