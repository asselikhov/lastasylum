package com.lastasylum.alliance.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

internal object OverlayReactionBitmapCache {
    private const val MAX_ENTRIES = 24
    private val cache = LruCache<String, Bitmap>(MAX_ENTRIES)
    private val inFlight = ConcurrentHashMap<String, Any>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun get(stem: String): Bitmap? = cache.get(stem)

    fun loadAsync(context: Context, stem: String, onReady: (Bitmap?) -> Unit) {
        cache.get(stem)?.let {
            onReady(it)
            return
        }
        val lock = inFlight.computeIfAbsent(stem) { Any() }
        scope.launch {
            val bmp = synchronized(lock) {
                cache.get(stem) ?: decodeSticker(context.applicationContext, stem)?.also {
                    cache.put(stem, it)
                }
            }
            inFlight.remove(stem)
            withContext(Dispatchers.Main) {
                onReady(bmp)
            }
        }
    }

    fun loadSync(context: Context, stem: String): Bitmap? {
        cache.get(stem)?.let { return it }
        return decodeSticker(context.applicationContext, stem)?.also { cache.put(stem, it) }
    }

    fun clear() {
        cache.evictAll()
        inFlight.clear()
    }

    /** Прогрев стикеров вкладки «Стикеры» до отрисовки сетки. */
    fun preloadOverlayStickerPack(context: Context) {
        val app = context.applicationContext
        scope.launch {
            listOverlayStickerStems(app).forEach { stem ->
                if (cache.get(stem) == null) {
                    decodeSticker(app, stem)?.let { cache.put(stem, it) }
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

    private fun decodeSticker(context: Context, stem: String): Bitmap? = runCatching {
        val pack = if (stem.startsWith("overlay_sticker_")) {
            OVERLAY_REACTION_STICKER_PACK
        } else {
            "zlobyaka"
        }
        val base = "stickerpacks/$pack/$stem"
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
