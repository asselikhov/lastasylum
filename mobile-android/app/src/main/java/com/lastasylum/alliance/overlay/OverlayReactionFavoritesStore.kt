package com.lastasylum.alliance.overlay

import android.content.Context
import java.util.HashSet

/**
 * User-pinned overlay quick reactions (animations, memes, stickers) for the Favorites tab.
 */
internal class OverlayReactionFavoritesStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isFavorite(reactionId: String): Boolean =
        reactionId.isNotBlank() && prefs.getStringSet(KEY_IDS, emptySet()).orEmpty().contains(reactionId)

    fun toggleFavorite(reactionId: String): Boolean {
        if (reactionId.isBlank()) return false
        val cur = prefs.getStringSet(KEY_IDS, emptySet()).orEmpty().toMutableSet()
        val added = if (reactionId in cur) {
            cur.remove(reactionId)
            false
        } else {
            cur.add(reactionId)
            true
        }
        prefs.edit().putStringSet(KEY_IDS, HashSet(cur)).apply()
        return added
    }

    fun favoriteIds(): Set<String> =
        prefs.getStringSet(KEY_IDS, emptySet()).orEmpty()

    companion object {
        private const val PREFS_NAME = "overlay_reaction_favorites_v1"
        private const val KEY_IDS = "ids"
    }
}
