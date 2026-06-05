package com.lastasylum.alliance.ui.util

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Long-press в области композера без фокуса в [TextField]: кастомная кнопка «Вставить».
 * Когда [interceptLongPress] false (поле в фокусе), системное меню вставки не блокируется.
 */
fun Modifier.composerLongPressPaste(
    enabled: Boolean,
    onLongPress: () -> Unit,
    interceptLongPress: Boolean = true,
): Modifier {
    if (!enabled || !interceptLongPress) return this
    return pointerInput(onLongPress) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val longPress = awaitLongPressOrCancellation(down.id)
            if (longPress != null) {
                onLongPress()
                longPress.consume()
            }
            waitForUpOrCancellation(pass = PointerEventPass.Initial)
        }
    }
}
