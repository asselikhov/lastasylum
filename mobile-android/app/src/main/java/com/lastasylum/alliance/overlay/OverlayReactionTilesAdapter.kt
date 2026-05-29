package com.lastasylum.alliance.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.airbnb.lottie.LottieAnimationView

internal class OverlayReactionTilesAdapter(
    private val context: Context,
    private val tileSizePx: Int,
    private val iconInnerPx: Int,
    private val cellMarginPx: Int,
    private val favorites: OverlayReactionFavoritesStore,
    private val onPick: (OverlayQuickReaction) -> Unit,
    private val onFavoritesChanged: () -> Unit,
) : RecyclerView.Adapter<OverlayReactionTilesAdapter.Holder>() {

    private val items = mutableListOf<OverlayQuickReaction>()
    private val activeLotties = mutableListOf<LottieAnimationView>()

    /** Cap looping Lottie/GIF previews in the grid — visible row can be 10+ tiles on 5 columns. */
    private fun shouldPlayAnimatedPreview(position: Int): Boolean {
        val reaction = items.getOrNull(position) ?: return false
        if (reaction.lottieRawRes == null && reaction.gifDrawableRes == null) return false
        var animatedRank = 0
        for (i in 0..position) {
            val item = items[i]
            if (item.lottieRawRes != null || item.gifDrawableRes != null) {
                if (i == position) {
                    return animatedRank < MAX_CONCURRENT_TILE_ANIMATIONS
                }
                animatedRank++
            }
        }
        return false
    }

    fun submitList(next: List<OverlayQuickReaction>) {
        val diff = DiffUtil.calculateDiff(
            object : DiffUtil.Callback() {
                override fun getOldListSize(): Int = items.size
                override fun getNewListSize(): Int = next.size
                override fun areItemsTheSame(oldPos: Int, newPos: Int): Boolean =
                    items[oldPos].id == next[newPos].id
                override fun areContentsTheSame(oldPos: Int, newPos: Int): Boolean =
                    items[oldPos] == next[newPos] &&
                        favorites.isFavorite(items[oldPos].id) == favorites.isFavorite(next[newPos].id)
            },
        )
        items.clear()
        items.addAll(next)
        diff.dispatchUpdatesTo(this)
    }

    fun activePreviewLotties(): List<LottieAnimationView> = activeLotties.toList()

    fun pauseAllPreviews() {
        activeLotties.forEach { stopOverlayReactionTileAnimation(it) }
        activeLotties.clear()
    }

    fun onTileAttachedToWindow(tileHost: View) {
        val icon = (tileHost as? FrameLayout)?.tag as? android.widget.ImageView ?: return
        resumeOverlayReactionTilePreview(icon)
        if (icon is LottieAnimationView && !activeLotties.contains(icon)) {
            activeLotties.add(icon)
        }
    }

    fun onTileDetachedFromWindow(tileHost: View) {
        val icon = (tileHost as? FrameLayout)?.tag as? android.widget.ImageView ?: return
        stopOverlayReactionTileAnimation(icon)
        if (icon is LottieAnimationView) activeLotties.remove(icon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val host = FrameLayout(parent.context)
        val lp = RecyclerView.LayoutParams(tileSizePx, tileSizePx)
        lp.setMargins(cellMarginPx, cellMarginPx, cellMarginPx, cellMarginPx)
        host.layoutParams = lp
        return Holder(host)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val reaction = items[position]
        val playAnimated = shouldPlayAnimatedPreview(position)
        holder.host.bindOverlayReactionTile(
            reaction = reaction,
            iconInnerPx = iconInnerPx,
            playAnimatedPreview = playAnimated,
            onPick = { onPick(reaction) },
            onToggleFavorite = {
                favorites.toggleFavorite(reaction.id)
                val pos = holder.adapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    notifyItemChanged(pos)
                }
                onFavoritesChanged()
            },
            isFavorite = favorites.isFavorite(reaction.id),
        )
        holder.host.background = cellBackground()
        val icon = holder.host.tag as? android.widget.ImageView
        if (icon is LottieAnimationView && playAnimated) {
            if (!activeLotties.contains(icon)) activeLotties.add(icon)
        }
        if (holder.host.isAttachedToWindow) {
            onTileAttachedToWindow(holder.host)
        }
    }

    override fun onViewRecycled(holder: Holder) {
        onTileDetachedFromWindow(holder.host)
        holder.host.tag = null
        holder.host.removeAllViews()
    }

    override fun getItemCount(): Int = items.size

    private fun cellBackground(): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 12f * context.resources.displayMetrics.density
            setColor(Color.parseColor("#33182533"))
            setStroke(
                (1f * context.resources.displayMetrics.density).toInt().coerceAtLeast(1),
                Color.parseColor("#33445566"),
            )
        }

    class Holder(val host: FrameLayout) : RecyclerView.ViewHolder(host)

    companion object {
        private const val MAX_CONCURRENT_TILE_ANIMATIONS = 4

        fun gridLayoutManager(context: Context, spanCount: Int): GridLayoutManager =
            GridLayoutManager(context, spanCount)

        fun attachVisiblePreviewLifecycle(recycler: RecyclerView, adapter: OverlayReactionTilesAdapter) {
            recycler.addOnChildAttachStateChangeListener(
                object : RecyclerView.OnChildAttachStateChangeListener {
                    override fun onChildViewAttachedToWindow(view: View) {
                        adapter.onTileAttachedToWindow(view)
                    }

                    override fun onChildViewDetachedFromWindow(view: View) {
                        adapter.onTileDetachedFromWindow(view)
                    }
                },
            )
        }
    }
}
