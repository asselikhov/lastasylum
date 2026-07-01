package com.lastasylum.alliance.game

/** Канал «игровая панель перемещения по пустой клетке → SquadRelay». */
object MapRelocPanelBridge {
    const val ACTION_RELOC_PANEL = "com.lastasylum.alliance.action.MAP_RELOC_PANEL"
    const val EXTRA_PAYLOAD = "payload"
}

/** Канал «режим прокладки маршрута: координаты подтверждены в игре». */
object RoutePlacementBridge {
    const val ACTION_ROUTE_PLACEMENT = "com.lastasylum.alliance.action.ROUTE_PLACEMENT"
    const val EXTRA_PAYLOAD = "payload"
}
