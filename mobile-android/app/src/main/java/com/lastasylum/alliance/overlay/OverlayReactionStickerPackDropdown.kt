package com.lastasylum.alliance.overlay

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListPopupWindow
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.DrawableCompat
import com.lastasylum.alliance.R

/**
 * Compact sticker-pack selector for overlay reactions (replaces horizontal pack chips).
 */
internal class OverlayReactionStickerPackDropdown(
    private val context: Context,
    private val dp: (Int) -> Int,
) {
    private var tabs: List<OverlayStickerPackTab> = emptyList()
    private var selectedPackKey: String = OVERLAY_REACTION_STICKER_PACK
    private var popup: ListPopupWindow? = null

    var onPackSelected: ((packKey: String) -> Unit)? = null

    private val titleView: TextView = TextView(context).apply {
        setTextColor(Color.parseColor("#FFE8F4FF"))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        typeface = Typeface.DEFAULT_BOLD
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        includeFontPadding = false
    }

    private val chevronView: ImageView = ImageView(context).apply {
        setImageDrawable(
            AppCompatResources.getDrawable(context, R.drawable.ic_overlay_ui_expand)?.mutate()?.also { d ->
                DrawableCompat.setTint(d, Color.parseColor("#8AA0B8D0"))
            },
        )
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        contentDescription = null
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private val selectorInner: LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(12), dp(8), dp(10), dp(8))
        addView(
            titleView,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )
        addView(
            chevronView,
            LinearLayout.LayoutParams(dp(18), dp(18)),
        )
    }

    val root: FrameLayout = FrameLayout(context).apply {
        visibility = View.GONE
        minimumHeight = dp(34)
        background = rippleOn(fieldBackground())
        addView(
            selectorInner,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        isClickable = true
        isFocusable = true
        setOnClickListener { openPicker() }
    }

    fun bind(tabs: List<OverlayStickerPackTab>, selectedPackKey: String) {
        this.tabs = tabs
        this.selectedPackKey = selectedPackKey
        val selected = tabs.firstOrNull { it.packKey == selectedPackKey } ?: tabs.firstOrNull()
        val title = selected?.let { context.getString(it.titleRes) }.orEmpty()
        titleView.text = title
        root.contentDescription = context.getString(
            R.string.overlay_reactions_sticker_pack_picker_cd,
            title,
        )
        root.isEnabled = tabs.size > 1
        chevronView.alpha = if (tabs.size > 1) 1f else 0.35f
    }

    fun dismissPicker() {
        popup?.dismiss()
        popup = null
    }

    private fun openPicker() {
        if (tabs.size <= 1) return
        dismissPicker()
        val titles = tabs.map { context.getString(it.titleRes) }
        val adapter = object : ArrayAdapter<String>(context, android.R.layout.simple_list_item_1, titles) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val row = convertView as? TextView ?: createPopupRow()
                val tab = tabs[position]
                val selected = tab.packKey == selectedPackKey
                row.text = getItem(position)
                row.setTextColor(Color.parseColor(if (selected) "#FFE8F4FF" else "#C8DCE8F4"))
                row.typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                row.setBackgroundColor(
                    if (selected) Color.parseColor("#332A4558") else Color.TRANSPARENT,
                )
                return row
            }
        }
        val listPopup = ListPopupWindow(context).apply {
            anchorView = root
            setAdapter(adapter)
            width = root.width.takeIf { it > 0 } ?: dp(220)
            isModal = true
            setBackgroundDrawable(
                roundedRect(
                    fillColor = Color.parseColor("#F0141C28"),
                    strokeColor = Color.parseColor("#3D5A7CAA"),
                    cornerDp = 10,
                ),
            )
            setOnItemClickListener { _, _, position, _ ->
                val tab = tabs.getOrNull(position) ?: return@setOnItemClickListener
                dismiss()
                popup = null
                if (tab.packKey != selectedPackKey) {
                    onPackSelected?.invoke(tab.packKey)
                }
            }
            setOnDismissListener { popup = null }
        }
        popup = listPopup
        root.post {
            if (root.isAttachedToWindow) {
                listPopup.width = root.width.coerceAtLeast(dp(200))
                listPopup.show()
            }
        }
    }

    private fun createPopupRow(): TextView =
        TextView(context).apply {
            setPadding(dp(14), dp(11), dp(14), dp(11))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }

    private fun fieldBackground(): GradientDrawable =
        roundedRect(
            fillColor = Color.parseColor("#2A141C28"),
            strokeColor = Color.parseColor("#3D5A7CAA"),
            cornerDp = 8,
        )

    private fun roundedRect(
        fillColor: Int,
        strokeColor: Int? = null,
        cornerDp: Int = 8,
    ): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(cornerDp).toFloat()
            setColor(fillColor)
            strokeColor?.let { setStroke(dp(1).coerceAtLeast(1), it) }
        }

    private fun rippleOn(base: GradientDrawable): RippleDrawable =
        RippleDrawable(
            ColorStateList.valueOf(Color.parseColor("#28FFFFFF")),
            base,
            base,
        )
}
