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
 * Expandable overlay voice hub: main mic button reveals «Звук» and «Микрофон» toggles.
 */
class OverlayVoiceControls(
    private val context: Context,
    private val fabCtx: Context,
    private val dp: (Int) -> Int,
    private val makeMiniFab: (iconRes: Int, cd: String) -> FloatingActionButton,
) {
    val root: LinearLayout
    val btnHub: FloatingActionButton
    val btnSound: FloatingActionButton
    val btnMic: FloatingActionButton
    private val soundBadge: TextView
    private val soundHost: FrameLayout

    private var expanded = false
    var onSoundToggle: (() -> Unit)? = null
    var onMicToggle: (() -> Unit)? = null

    init {
        btnHub = makeMiniFab(R.drawable.ic_overlay_mic, context.getString(R.string.overlay_voice_hub_cd))
        btnSound = makeMiniFab(R.drawable.ic_overlay_volume_on, context.getString(R.string.overlay_voice_sound_cd))
        btnMic = makeMiniFab(R.drawable.ic_overlay_mic, context.getString(R.string.overlay_voice_mic_cd))
        btnSound.visibility = View.GONE
        btnMic.visibility = View.GONE

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
            addView(btnSound, FrameLayout.LayoutParams(dp(44), dp(44)))
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

        val gap = dp(6)
        val colW = dp(44)
        root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(btnHub, LinearLayout.LayoutParams(colW, dp(44)))
            addView(
                soundHost,
                LinearLayout.LayoutParams(colW, dp(44)).apply { topMargin = gap },
            )
            addView(btnMic, LinearLayout.LayoutParams(colW, dp(44)).apply { topMargin = gap })
        }

        btnHub.setOnClickListener { toggleExpanded() }
        btnSound.setOnClickListener { onSoundToggle?.invoke() }
        btnMic.setOnClickListener { onMicToggle?.invoke() }
    }

    fun setExpanded(value: Boolean) {
        expanded = value
        val sub = if (value) View.VISIBLE else View.GONE
        soundHost.visibility = sub
        btnMic.visibility = sub
        btnHub.contentDescription = context.getString(
            if (value) R.string.overlay_voice_hub_collapse_cd else R.string.overlay_voice_hub_cd,
        )
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
        val activeTint = Color.parseColor("#4CAF50")
        val idleTint = Color.parseColor("#B0BEC5")
        btnSound.backgroundTintList = android.content.res.ColorStateList.valueOf(
            if (soundOn) activeTint else idleTint,
        )
        btnMic.backgroundTintList = android.content.res.ColorStateList.valueOf(
            if (micOn) activeTint else idleTint,
        )
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

    private fun toggleExpanded() {
        setExpanded(!expanded)
    }

    fun collapse() {
        setExpanded(false)
    }
}
