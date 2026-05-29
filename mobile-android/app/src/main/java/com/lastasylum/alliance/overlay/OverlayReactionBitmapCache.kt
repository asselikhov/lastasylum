package com.lastasylum.alliance.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import com.lastasylum.alliance.data.chat.stickers.StickerPacks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

internal object OverlayReactionBitmapCache {
    private const val MAX_ENTRIES = 48
    private const val PRELOAD_MAX_STICKERS = 12
    private val cache = LruCache<String, Bitmap>(MAX_ENTRIES)
    private val inFlight = ConcurrentHashMap<String, Any>()
    private var scopeJob = SupervisorJob()
    private var scope = CoroutineScope(scopeJob + Dispatchers.IO)

    internal fun cacheKey(packKey: String, stem: String): String = "$packKey/$stem"

    fun get(packKey: String, stem: String): Bitmap? = cache.get(cacheKey(packKey, stem))

    fun loadAsync(
        context: Context,
        packKey: String,
        stem: String,
        onReady: (Bitmap?) -> Unit,
    ) {
        val key = cacheKey(packKey, stem)
        cache.get(key)?.let {
            onReady(it)
            return
        }
        val lock = inFlight.computeIfAbsent(key) { Any() }
        scope.launch {
            val bmp = synchronized(lock) {
                cache.get(key) ?: decodeSticker(context.applicationContext, packKey, stem)?.also {
                    cache.put(key, it)
                }
            }
            inFlight.remove(key)
            withContext(Dispatchers.Main) {
                onReady(bmp)
            }
        }
    }

    fun loadSync(context: Context, packKey: String, stem: String): Bitmap? {
        val key = cacheKey(packKey, stem)
        cache.get(key)?.let { return it }
        return decodeSticker(context.applicationContext, packKey, stem)?.also { cache.put(key, it) }
    }

    fun clear() {
        scope.coroutineContext.cancelChildren()
        cache.evictAll()
        inFlight.clear()
    }

    /** Прогрев одного стикера (например перед вспышкой реакции). */
    fun preloadSticker(context: Context, packKey: String, stem: String) {
        if (packKey.isBlank() || stem.isBlank()) return
        loadAsync(context, packKey, stem) { }
    }

    /** Прогрев части стикеров вкладки «Стикеры» до отрисовки сетки. */
    fun preloadOverlayStickerPack(context: Context, maxEntries: Int = PRELOAD_MAX_STICKERS) {
        preloadStickerPack(context, OVERLAY_REACTION_STICKER_PACK, maxEntries)
    }

    fun preloadStickerPack(
        context: Context,
        packKey: String,
        maxEntries: Int = PRELOAD_MAX_STICKERS,
    ) {
        val app = context.applicationContext
        val limit = maxEntries.coerceIn(1, PRELOAD_MAX_STICKERS)
        scope.launch {
            val stems = when (packKey) {
                OVERLAY_REACTION_STICKER_PACK -> listOverlayStickerStems(app)
                else -> StickerPacks.listStems(app, packKey)
            }
            stems.take(limit).forEach { stem ->
                val key = cacheKey(packKey, stem)
                if (cache.get(key) == null) {
                    decodeSticker(app, packKey, stem)?.let { cache.put(key, it) }
                }
            }
        }
    }

    internal fun listOverlayStickerStems(context: Context): List<String> =
        runCatching {
            context.assets.list("stickerpacks/$OVERLAY_REACTION_STICKER_PACK")
                ?.filter { name ->
                    name.endsWith(".png", ignoreCase = true) ||
                        name.endsWith(".webp", ignoreCase = true)
                }
                ?.map { name ->
                    name.substringBeforeLast('.')
                }
                ?.sorted()
                ?: emptyList()
        }.getOrElse { emptyList() }

    private fun decodeSticker(context: Context, packKey: String, stem: String): Bitmap? = runCatching {
        val base = "stickerpacks/$packKey/$stem"
        for (ext in listOf(".png", ".webp")) {
            val bmp = runCatching {
                context.assets.open(base + ext).use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            }.getOrNull()
            if (bmp != null) return@runCatching bmp
        }
        null
    }.getOrNull()
}
