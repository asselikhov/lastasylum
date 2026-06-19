package com.lastasylum.alliance.ui.util

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.composed

/**
 * Long-press в области композера: кастомная кнопка «Вставить» над полем (в т.ч. когда [TextField] в фокусе).
 */
fun Modifier.composerLongPressPaste(
    enabled: Boolean,
    onLongPress: () -> Unit,
): Modifier {
    if (!enabled) return this
    return composed {
        val haptics = LocalHapticFeedback.current
        pointerInput(onLongPress) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val longPress = awaitLongPressOrCancellation(down.id)
                if (longPress != null) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongPress()
                    longPress.consume()
                }
                waitForUpOrCancellation(pass = PointerEventPass.Initial)
            }
        }
    }
}
