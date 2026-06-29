package com.lastasylum.alliance.game

/**
 * Канал «авто-штурм в игре → SquadRelay». Игровой Frida-бридж шлёт explicit broadcast
 * [ACTION_ASSAULT_JOIN] со строковым extra [EXTRA_PAYLOAD] (сырой JSON одной записи лога:
 * creator, type, power, level, dist, squad, time). Получатель в `CombatOverlayService`
 * сохраняет запись в [com.lastasylum.alliance.data.settings.UserSettingsPreferences] и
 * показывает её в списке последних вступлений во вкладке «Штурм».
 */
object AssaultJoinBridge {
    const val ACTION_ASSAULT_JOIN = "com.lastasylum.alliance.action.ASSAULT_JOIN"
    const val EXTRA_PAYLOAD = "payload"
}
