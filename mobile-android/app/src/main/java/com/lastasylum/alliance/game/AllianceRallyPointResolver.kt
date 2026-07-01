package com.lastasylum.alliance.game

import android.content.Context
import com.lastasylum.alliance.di.AppContainer
import org.json.JSONObject

/**
 * Пункт сбора для оверлея «Перемещение»: сначала кэш/мост, затем закладки (вкладка «Альянс»
 * и записи с названием пункта сбора).
 */
object AllianceRallyPointResolver {
    private val rallyNameHints = listOf("пункт", "сбор", "rally", "gather", "assembly", "сбора")

    fun resolve(context: Context, serverHint: Int? = null): AllianceRallyPoint? {
        val appContext = context.applicationContext
        val prefs = AppContainer.from(appContext).userSettingsPreferences
        fromJson(prefs.getAllianceRallyJson(), serverHint)?.let { return it }
        return fromBookmarks(appContext, serverHint)
    }

    fun fromBookmark(target: RaidShareTarget, serverHint: Int? = null): AllianceRallyPoint? {
        if (!target.isAllianceRallyBookmark()) return null
        return toPoint(target, serverHint)
    }

    /** Закладка во вкладке «Альянс» — координаты пункта сбора без проверки названия. */
    fun fromAllianceTagBookmark(target: RaidShareTarget, serverHint: Int? = null): AllianceRallyPoint? =
        toPoint(target, serverHint)

    fun fromJson(json: String?, serverHint: Int? = null): AllianceRallyPoint? =
        AllianceRallyPoint.parse(json, serverHint)

    fun toPersistJson(point: AllianceRallyPoint): String =
        JSONObject()
            .put("x", point.x)
            .put("y", point.y)
            .put("sid", point.serverNumber)
            .toString()

    private fun fromBookmarks(context: Context, serverHint: Int?): AllianceRallyPoint? {
        val allianceItems = OverlayBookmarkStore.list(context, OverlayBookmarkTag.ALLIANCE)
        allianceItems.firstNotNullOfOrNull { fromBookmark(it, serverHint) }?.let { return it }
        if (allianceItems.size == 1) {
            toPoint(allianceItems.first(), serverHint)?.let { return it }
        }
        for (tag in OverlayBookmarkTag.entries) {
            if (tag == OverlayBookmarkTag.ALLIANCE) continue
            OverlayBookmarkStore.list(context, tag)
                .firstNotNullOfOrNull { fromBookmark(it, serverHint) }
                ?.let { return it }
        }
        return null
    }

    private fun RaidShareTarget.isAllianceRallyBookmark(): Boolean {
        if (x <= 0 || y <= 0) return false
        val label = listOfNotNull(name, playerName, titleLine())
            .joinToString(" ")
            .lowercase()
        return rallyNameHints.any { label.contains(it) }
    }

    private fun toPoint(target: RaidShareTarget, serverHint: Int?): AllianceRallyPoint? {
        val sid = target.serverNumber?.takeIf { it > 0 } ?: serverHint?.takeIf { it > 0 } ?: return null
        return AllianceRallyPoint(target.x, target.y, sid).takeIf { it.x > 0 && it.y > 0 }
    }
}
