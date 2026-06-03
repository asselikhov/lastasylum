package com.lastasylum.alliance.gameevents

import android.graphics.Color

enum class GameEventCategory {
    HQ,
    PVE,
    PVP,
    ;

    val apiKey: String
        get() = when (this) {
            HQ -> "hq"
            PVE -> "pve"
            PVP -> "pvp"
        }
}

data class GameEventDefinition(
    val id: String,
    val category: GameEventCategory,
    val messageText: String,
    val channelId: String,
)

object GameEventCatalog {
    val all: List<GameEventDefinition> = listOf(
        GameEventDefinition(
            id = "hq_excavation",
            category = GameEventCategory.HQ,
            messageText = "[ШТАБ] Раскопки альянса",
            channelId = "game_event_hq_excavation",
        ),
        GameEventDefinition(
            id = "hq_enemy_at_gates",
            category = GameEventCategory.HQ,
            messageText = "[ШТАБ] Враг у ворот",
            channelId = "game_event_hq_enemy_at_gates",
        ),
        GameEventDefinition(
            id = "hq_all_online",
            category = GameEventCategory.HQ,
            messageText = "[ШТАБ] Всем онлайн",
            channelId = "game_event_hq_all_online",
        ),
        GameEventDefinition(
            id = "hq_important",
            category = GameEventCategory.HQ,
            messageText = "[ШТАБ] Важное объявление",
            channelId = "game_event_hq_important",
        ),
        GameEventDefinition(
            id = "hq_help_needed",
            category = GameEventCategory.HQ,
            messageText = "[ШТАБ] Требуется помощь альянсу",
            channelId = "game_event_hq_help_needed",
        ),
        GameEventDefinition(
            id = "pve_gather_5m",
            category = GameEventCategory.PVE,
            messageText = "[PvE] Сбор (5 минут)",
            channelId = "game_event_pve_gather_5m",
        ),
        GameEventDefinition(
            id = "pve_event_started",
            category = GameEventCategory.PVE,
            messageText = "[PvE] Событие началось",
            channelId = "game_event_pve_event_started",
        ),
        GameEventDefinition(
            id = "pvp_gather_5m",
            category = GameEventCategory.PVP,
            messageText = "[PvP] Сбор (5 минут)",
            channelId = "game_event_pvp_gather_5m",
        ),
        GameEventDefinition(
            id = "pvp_war_started",
            category = GameEventCategory.PVP,
            messageText = "[PvP] Война началась",
            channelId = "game_event_pvp_war_started",
        ),
    )

    private val byId = all.associateBy { it.id }

    fun byId(eventId: String): GameEventDefinition? = byId[eventId.trim()]

    fun isValid(eventId: String): Boolean = byId.containsKey(eventId.trim())

    fun notificationColor(category: GameEventCategory): Int = when (category) {
        GameEventCategory.HQ -> Color.parseColor("#FFB86B00")
        GameEventCategory.PVE -> Color.parseColor("#FF3D5AFE")
        GameEventCategory.PVP -> Color.parseColor("#FFE53935")
    }

    fun allMessageTexts(): Set<String> = all.map { it.messageText }.toSet()

    fun eventsByCategory(category: GameEventCategory): List<GameEventDefinition> =
        all.filter { it.category == category }
}
