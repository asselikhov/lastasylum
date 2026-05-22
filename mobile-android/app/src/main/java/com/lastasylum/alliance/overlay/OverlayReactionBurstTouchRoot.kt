package com.lastasylum.alliance.overlay

import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout

/**
 * Корень окна входящей реакции: никогда не обрабатывает касания.
 * На части OEM [WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE] для overlay игнорируется —
 * возврат false из [dispatchTouchEvent] пропускает жесты в игру под окном.
 */
internal class OverlayReactionBurstTouchRoot(context: Context) : FrameLayout(context) {

    init {
        disableOverlayTouchTarget(this)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean = false

    override fun onTouchEvent(event: MotionEvent): Boolean = false

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = false
}

/** Снимает перехват тапов с контента вспышки (Lottie/GIF/подпись). */
internal fun disableOverlayTouchTarget(view: View) {
    view.isClickable = false
    view.isLongClickable = false
    view.isFocusable = false
    view.isFocusableInTouchMode = false
    if (view is ViewGroup) {
        view.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        for (i in 0 until view.childCount) {
            disableOverlayTouchTarget(view.getChildAt(i))
        }
    }
}
