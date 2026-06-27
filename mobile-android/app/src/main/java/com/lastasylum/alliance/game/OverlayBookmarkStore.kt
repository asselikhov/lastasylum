package com.lastasylum.alliance.game

import android.content.Context
import org.json.JSONArray

/**
 * Локальное хранилище закладок карты (без бэкенда). Цели хранятся по тегам
 * ([OverlayBookmarkTag.storageKey]) в [android.content.SharedPreferences] как JSON-массив
 * сериализованных [RaidShareTarget]. Доступ синхронный — оверлей строит вью на main-потоке.
 */
object OverlayBookmarkStore {
    private const val PREFS = "overlay_bookmarks"
    private const val MAX_PER_TAG = 100

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Список целей тега, новые — первыми (порядок добавления, сверху последние). */
    fun list(context: Context, tag: OverlayBookmarkTag): List<RaidShareTarget> {
        val raw = prefs(context).getString(tag.storageKey, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                RaidShareTarget.fromJson(arr.optString(i))
            }
        }.getOrDefault(emptyList())
    }

    /** Уже сохранена ли цель (по серверу+координатам) в данном теге. */
    fun contains(context: Context, tag: OverlayBookmarkTag, target: RaidShareTarget): Boolean =
        list(context, tag).any { it.bookmarkKey() == target.bookmarkKey() }

    /**
     * Добавить цель в тег (новые сверху, дедуп по [RaidShareTarget.bookmarkKey]).
     * @return true — добавлено, false — уже была.
     */
    fun add(context: Context, tag: OverlayBookmarkTag, target: RaidShareTarget): Boolean {
        val current = list(context, tag)
        if (current.any { it.bookmarkKey() == target.bookmarkKey() }) return false
        val updated = (listOf(target) + current).take(MAX_PER_TAG)
        save(context, tag, updated)
        return true
    }

    /** Удалить цель из тега по ключу сервер+координаты. */
    fun remove(context: Context, tag: OverlayBookmarkTag, target: RaidShareTarget) {
        val updated = list(context, tag).filterNot { it.bookmarkKey() == target.bookmarkKey() }
        save(context, tag, updated)
    }

    private fun save(context: Context, tag: OverlayBookmarkTag, items: List<RaidShareTarget>) {
        val arr = JSONArray()
        items.forEach { arr.put(it.toJson()) }
        prefs(context).edit().putString(tag.storageKey, arr.toString()).apply()
    }
}
