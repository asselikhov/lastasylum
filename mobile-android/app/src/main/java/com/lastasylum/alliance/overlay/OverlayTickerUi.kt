package com.lastasylum.alliance.overlay

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.widget.ImageView
import android.widget.TextView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.lastasylum.alliance.R

object OverlayTickerUi {
    /**
     * Контекст с Material 3 для виджетов оверлея из сервиса
     * ([FloatingActionButton], [com.google.android.material.card.MaterialCardView] и т.д.).
     */
    fun themedFabContext(base: Context): Context =
        ContextThemeWrapper(base, R.style.Theme_LastAsylum_OverlayMaterial)

    fun applyTickerStyle(context: Context, view: TextView) {
        val card = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(context, 12f)
            setColor(Color.parseColor("#CC10141E"))
            setStroke(dp(context, 1.25f).toInt(), Color.parseColor("#559B7CFF"))
        }

        view.background = card
        view.setTextColor(Color.parseColor("#FFF1F5FF"))
        view.setPadding(
            dp(context, 12f).toInt(),
            dp(context, 9f).toInt(),
            dp(context, 12f).toInt(),
            dp(context, 9f).toInt(),
        )
        view.maxLines = 3
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
        view.letterSpacing = 0.01f
    }

    fun applyToggleChipStyle(context: Context, view: TextView) {
        val chip = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(context, 18f)
            setColor(Color.parseColor("#CC1A1F2B"))
            setStroke(dp(context, 1f).toInt(), Color.parseColor("#449B7CFF"))
        }
        view.background = chip
        view.setTextColor(Color.parseColor("#FFEAF0FF"))
        view.setPadding(
            dp(context, 12f).toInt(),
            dp(context, 6f).toInt(),
            dp(context, 12f).toInt(),
            dp(context, 6f).toInt(),
        )
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        view.letterSpacing = 0.02f
        view.alpha = 0.92f
    }

    /**
     * Круглый FAB оверлея (Material 3): ripple, подъём, тон иконки.
     * [context] — обычно [themedFabContext].
     */
    @JvmOverloads
    fun styleOverlayFab(
        context: Context,
        fab: FloatingActionButton,
        diameterDp: Float = 48f,
    ) {
        val side = dp(context, diameterDp).toInt()
        fab.setCustomSize(side)
        fab.size = FloatingActionButton.SIZE_AUTO
        fab.scaleType = ImageView.ScaleType.CENTER
        fab.imageTintList = ColorStateList.valueOf(Color.parseColor("#F4F0FF"))
        fab.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#E6282548"))
        fab.elevation = dp(context, 5f)
        fab.alpha = 0.98f
    }

    private fun dp(context: Context, value: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value,
            context.resources.displayMetrics,
        )
    }
}
