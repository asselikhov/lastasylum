package com.lastasylum.alliance.overlay

import android.content.Context
import android.os.Build
import android.os.Handler
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.chat.ChatMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Radial quick commands + emoji row around the overlay bubble (separate overlay windows).
 */
class OverlayQuickCommandsPopover(
    private val context: Context,
    private val windowManagerProvider: () -> WindowManager?,
    private val mainHandler: Handler,
    private val externalScope: CoroutineScope,
    private val dp: (Int) -> Int,
    private val sendChatText: suspend (String) -> Result<ChatMessage>,
    private val onSendSuccess: (ChatMessage, String) -> Unit,
    private val onSendFailure: () -> Unit,
) {
    private val views = mutableListOf<TextView>()

    fun isShowing(): Boolean = views.isNotEmpty()

    fun hide() {
        val manager = windowManagerProvider() ?: return
        if (views.isEmpty()) return
        views.forEach { view ->
            view.animate().cancel()
            runCatching { manager.removeView(view) }
        }
        views.clear()
    }

    fun toggle(bubbleX: Int, bubbleY: Int) {
        if (isShowing()) hide() else show(bubbleX, bubbleY)
    }

    private fun show(bubbleX: Int, bubbleY: Int) {
        val manager = windowManagerProvider() ?: return
        data class QuickCommand(
            val label: String,
            val text: String,
            val style: OverlayBubbleUi.BubbleState,
        )

        val commands = listOf(
            QuickCommand(
                context.getString(R.string.overlay_cmd_assembly_label),
                context.getString(R.string.overlay_cmd_assembly_text),
                OverlayBubbleUi.BubbleState.IDLE,
            ),
            QuickCommand(
                context.getString(R.string.overlay_cmd_focus_label),
                context.getString(R.string.overlay_cmd_focus_text),
                OverlayBubbleUi.BubbleState.RECORDING,
            ),
            QuickCommand(
                context.getString(R.string.overlay_cmd_help_label),
                context.getString(R.string.overlay_cmd_help_text),
                OverlayBubbleUi.BubbleState.ERROR,
            ),
            QuickCommand(
                context.getString(R.string.overlay_cmd_stand_down_label),
                context.getString(R.string.overlay_cmd_stand_down_text),
                OverlayBubbleUi.BubbleState.SENDING,
            ),
        )
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val screenWidth = context.resources.displayMetrics.widthPixels
        val sideIsLeft = bubbleX < screenWidth / 2
        val xDirection = if (sideIsLeft) 1 else -1
        val offsets = listOf(
            Pair(0, -dp(88)),
            Pair(xDirection * dp(72), -dp(34)),
            Pair(xDirection * dp(72), dp(34)),
            Pair(0, dp(88)),
        )

        commands.forEachIndexed { index, command ->
            val action = TextView(context).apply {
                text = command.label
                OverlayBubbleUi.applyQuickCommandStyle(
                    context,
                    this,
                    command.style,
                )
                alpha = 0f
                scaleX = 0.84f
                scaleY = 0.84f
                setOnClickListener {
                    externalScope.launch {
                        val result = sendChatText(command.text)
                        mainHandler.post {
                            result.onSuccess { sent ->
                                onSendSuccess(sent, command.text)
                            }.onFailure {
                                onSendFailure()
                            }
                        }
                    }
                    hide()
                }
            }
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                OverlayWindowLayout.popupWindowFlags(),
                android.graphics.PixelFormat.TRANSLUCENT,
            ).apply {
                OverlayWindowLayout.applyPopupLayoutCompat(this)
                gravity = Gravity.TOP or Gravity.START
                x = bubbleX + offsets[index].first
                y = bubbleY + offsets[index].second
            }
            manager.addView(action, params)
            action.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setStartDelay(index * 22L)
                .setDuration(150L)
                .start()
            views.add(action)
        }

        val step = dp(44)
        val rowStart = -((REACTIONS.size - 1) * step) / 2
        REACTIONS.forEachIndexed { index, emoji ->
            val action = TextView(context).apply {
                text = emoji
                OverlayBubbleUi.applyQuickCommandStyle(
                    context,
                    this,
                    OverlayBubbleUi.BubbleState.IDLE,
                )
                alpha = 0f
                scaleX = 0.84f
                scaleY = 0.84f
                setOnClickListener {
                    externalScope.launch {
                        val result = sendChatText(emoji)
                        mainHandler.post {
                            result.onSuccess { sent ->
                                onSendSuccess(sent, emoji)
                            }.onFailure {
                                onSendFailure()
                            }
                        }
                    }
                    hide()
                }
            }
            val rParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                OverlayWindowLayout.popupWindowFlags(),
                android.graphics.PixelFormat.TRANSLUCENT,
            ).apply {
                OverlayWindowLayout.applyPopupLayoutCompat(this)
                gravity = Gravity.TOP or Gravity.START
                x = bubbleX + rowStart + index * step
                y = bubbleY + dp(108)
            }
            manager.addView(action, rParams)
            action.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setStartDelay((commands.size + index) * 22L)
                .setDuration(150L)
                .start()
            views.add(action)
        }
    }

    private companion object {
        val REACTIONS = listOf("👍", "✅", "❓", "🎯")
    }
}
