package com.lastasylum.alliance.game

/**
 * Канал «ростер альянса из игры → SquadRelay». Игровой Frida-бридж периодически читает
 * список членов альянса (`Data.AllianceData.member.memberDic`) и шлёт explicit broadcast
 * [ACTION_ALLIANCE_ROSTER] со строковым extra [EXTRA_PAYLOAD] — JSON-массив
 * `[{"id","name","power","level","rank"}]`. Получатель в `CombatOverlayService` кэширует
 * его в [com.lastasylum.alliance.overlay.AllianceRosterCache] и сохраняет в настройках, чтобы
 * в выборе соалийцев для авто-штурма показывать актуальный список именно из игры.
 */
object AllianceRosterBridge {
    const val ACTION_ALLIANCE_ROSTER = "com.lastasylum.alliance.action.ALLIANCE_ROSTER"
    const val EXTRA_PAYLOAD = "payload"
}
