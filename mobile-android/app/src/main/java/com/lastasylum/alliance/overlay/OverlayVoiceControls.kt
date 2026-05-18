package com.lastasylum.alliance.overlay

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.lastasylum.alliance.R

/**
 * Голосовой хаб: «Звук» и «Микрофон» раскрываются столбцом рядом с хабом
 * (отступы как у вертикального стека FAB оверлея — 6dp).
 */
class OverlayVoiceControls(
    private val context: Context,
    private val fabCtx: Context,
    private val dp: (Int) -> Int,
    private val makeMiniFab: (iconRes: Int, cd: String) -> FloatingActionButton,
) {
    val root: FrameLayout
    val btnHub: FloatingActionButton
    val btnSound: FloatingActionButton
    val btnMic: FloatingActionButton
    private val soundBadge: TextView
    private val soundHost: FrameLayout
    private val expandColumn: LinearLayout
    private val soundLabel: TextView
    private val micLabel: TextView

    private var expanded = false
    var expandTowardStart: Boolean = false
        private set

    private val btnSizePx: Int
    private val gapPx: Int
    private val columnHeightPx: Int

    var onSoundToggle: (() -> Unit)? = null
    var onMicToggle: (() -> Unit)? = null
    var onExpansionChanged: (() -> Unit)? = null

    init {
        btnSizePx = dp(44)
        gapPx = dp(6)
        columnHeightPx = btnSizePx * 2 + gapPx

        btnHub = makeMiniFab(R.drawable.ic_overlay_mic, context.getString(R.string.overlay_voice_hub_cd))
        btnSound = makeMiniFab(R.drawable.ic_overlay_volume_on, context.getString(R.string.overlay_voice_sound_cd))
        btnMic = makeMiniFab(R.drawable.ic_overlay_mic, context.getString(R.string.overlay_voice_mic_cd))

        soundBadge = TextView(context).apply {
            visibility = View.GONE
            setTextColor(Color.WHITE)
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 9f)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(dp(4), dp(1), dp(4), dp(1))
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(Color.parseColor("#43A047"))
            }
        }
        soundHost = FrameLayout(context).apply {
            addView(btnSound, FrameLayout.LayoutParams(btnSizePx, btnSizePx))
            addView(
                soundBadge,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP or Gravity.END,
                ).apply {
                    setMargins(0, dp(2), dp(2), 0)
                },
            )
        }

        soundLabel = makeToggleLabel(context.getString(R.string.overlay_voice_sound_label)).apply {
            visibility = View.GONE
        }
        micLabel = makeToggleLabel(context.getString(R.string.overlay_voice_mic_label)).apply {
            visibility = View.GONE
        }

        expandColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            clipChildren = false
            clipToPadding = false
            addView(soundHost, LinearLayout.LayoutParams(btnSizePx, btnSizePx))
            addView(
                btnMic,
                LinearLayout.LayoutParams(btnSizePx, btnSizePx).apply {
                    topMargin = gapPx
                },
            )
        }

        root = FrameLayout(context).apply {
            clipChildren = false
            clipToPadding = false
            minimumWidth = btnSizePx
            minimumHeight = btnSizePx
            addView(expandColumn, expandColumnLayoutParams())
            addView(btnHub, hubLayoutParams())
        }

        btnHub.setOnClickListener { toggleExpanded() }
        btnSound.setOnClickListener { onSoundToggle?.invoke() }
        btnMic.setOnClickListener { onMicToggle?.invoke() }
    }

    fun setExpandTowardStart(towardStart: Boolean) {
        if (expandTowardStart == towardStart) return
        expandTowardStart = towardStart
        relayoutSlots()
    }

    private fun hubLayoutParams(): FrameLayout.LayoutParams =
        FrameLayout.LayoutParams(btnSizePx, btnSizePx).apply {
            gravity = Gravity.CENTER_VERTICAL or if (expandTowardStart) Gravity.END else Gravity.START
        }

    private fun expandColumnLayoutParams(): FrameLayout.LayoutParams =
        FrameLayout.LayoutParams(btnSizePx, columnHeightPx).apply {
            gravity = Gravity.CENTER_VERTICAL or if (expandTowardStart) Gravity.END else Gravity.START
            if (expandTowardStart) {
                marginEnd = btnSizePx + gapPx
            } else {
                marginStart = btnSizePx + gapPx
            }
        }

    private fun relayoutSlots() {
        root.removeView(expandColumn)
        root.removeView(btnHub)
        root.addView(expandColumn, expandColumnLayoutParams())
        root.addView(btnHub, hubLayoutParams())
        val expandedWidth = if (expanded) btnSizePx + gapPx + btnSizePx else btnSizePx
        val expandedHeight = if (expanded) columnHeightPx.coerceAtLeast(btnSizePx) else btnSizePx
        root.minimumWidth = expandedWidth
        root.minimumHeight = expandedHeight
    }

    private fun makeToggleLabel(text: String): TextView =
        TextView(context).apply {
            this.text = text
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 9f)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_VERTICAL
            includeFontPadding = false
        }

    fun setExpanded(value: Boolean) {
        if (expanded == value) return
        expanded = value
        expandColumn.visibility = if (value) View.VISIBLE else View.GONE
        btnHub.contentDescription = context.getString(
            if (value) R.string.overlay_voice_hub_collapse_cd else R.string.overlay_voice_hub_cd,
        )
        relayoutSlots()
        onExpansionChanged?.invoke()
    }

    fun applyState(micOn: Boolean, soundOn: Boolean) {
        btnSound.setImageResource(
            if (soundOn) R.drawable.ic_overlay_volume_on else R.drawable.ic_overlay_volume_off,
        )
        btnSound.contentDescription = context.getString(
            if (soundOn) R.string.overlay_voice_sound_on_cd else R.string.overlay_voice_sound_off_cd,
        )
        btnMic.setImageResource(
            if (micOn) R.drawable.ic_overlay_mic_on else R.drawable.ic_overlay_mic_off,
        )
        btnMic.contentDescription = context.getString(
            if (micOn) R.string.overlay_voice_mic_on_cd else R.string.overlay_voice_mic_off_cd,
        )
        styleToggleButton(btnSound, soundOn)
        styleToggleButton(btnMic, micOn)
        styleToggleLabel(soundLabel, soundOn)
        styleToggleLabel(micLabel, micOn)
    }

    private fun styleToggleButton(fab: FloatingActionButton, enabled: Boolean) {
        val activeBg = Color.parseColor("#2E7D32")
        val idleBg = Color.parseColor("#455A64")
        val activeIcon = Color.parseColor("#FFFFFF")
        val idleIcon = Color.parseColor("#90A4AE")
        fab.backgroundTintList = android.content.res.ColorStateList.valueOf(
            if (enabled) activeBg else idleBg,
        )
        fab.imageTintList = android.content.res.ColorStateList.valueOf(
            if (enabled) activeIcon else idleIcon,
        )
        fab.alpha = if (enabled) 1f else 0.88f
    }

    private fun styleToggleLabel(label: TextView, enabled: Boolean) {
        label.setTextColor(
            Color.parseColor(if (enabled) "#81C784" else "#78909C"),
        )
        label.alpha = if (enabled) 1f else 0.85f
    }

    fun setActiveSpeakerCount(count: Int) {
        if (count <= 0) {
            soundBadge.visibility = View.GONE
            soundBadge.text = ""
            return
        }
        soundBadge.visibility = View.VISIBLE
        soundBadge.text = if (count > 9) "9+" else count.toString()
        soundBadge.contentDescription = context.getString(
            R.string.overlay_voice_speakers_cd,
            count,
        )
    }

    fun isExpanded(): Boolean = expanded

    fun toggleExpanded() {
        setExpanded(!expanded)
    }

    fun collapse() {
        setExpanded(false)
    }
}
