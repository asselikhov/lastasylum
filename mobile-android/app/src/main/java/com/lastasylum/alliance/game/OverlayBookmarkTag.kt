package com.lastasylum.alliance.game

import com.lastasylum.alliance.R

/**
 * Категории закладок карты (выпадающий список «Цель → Закладки» и панель «В закладки»
 * над игровым окном тегов). [storageKey] — стабильный ключ хранилища (не зависит от перевода).
 */
enum class OverlayBookmarkTag(val storageKey: String, val labelRes: Int) {
    ENEMIES("enemies", R.string.overlay_bookmark_enemies),
    FRIENDS("friends", R.string.overlay_bookmark_friends),
    MOBS("mobs", R.string.overlay_bookmark_mobs),
    CHESTS("chests", R.string.overlay_bookmark_chests),
    CITIES("cities", R.string.overlay_bookmark_cities),
    ;

    companion object {
        fun fromKey(key: String?): OverlayBookmarkTag? = entries.firstOrNull { it.storageKey == key }
    }
}
