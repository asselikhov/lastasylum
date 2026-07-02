package com.lastasylum.alliance.game

/** Канал «игровая панель перемещения по пустой клетке → SquadRelay». */
object MapRelocPanelBridge {
    const val ACTION_RELOC_PANEL = "com.lastasylum.alliance.action.MAP_RELOC_PANEL"
    /** Тап по внутриигровой кнопке «Маршрут». */
    const val ACTION_ROUTE_CLICK = "com.lastasylum.alliance.action.MAP_RELOC_ROUTE_CLICK"
    const val EXTRA_PAYLOAD = "payload"
    /** Дублирует payload из игры — резервный канал, если broadcast не дошёл. */
    const val SDCARD_PANEL_FILE = "/sdcard/Download/squadrelay_reloc_panel.json"
}

/** Канал «режим прокладки маршрута: координаты подтверждены в игре». */
object RoutePlacementBridge {
    const val ACTION_ROUTE_PLACEMENT = "com.lastasylum.alliance.action.ROUTE_PLACEMENT"
    const val EXTRA_PAYLOAD = "payload"
}
