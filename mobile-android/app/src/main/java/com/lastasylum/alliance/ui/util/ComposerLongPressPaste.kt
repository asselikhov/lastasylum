package com.lastasylum.alliance.ui.util

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/**
 * Long-press on the composer area while [TextField] still receives normal taps/typing.
 * Uses [PointerEventPass.Initial] so the gesture is seen before the text field consumes input.
 */
fun Modifier.composerLongPressPaste(
    enabled: Boolean,
    onLongPress: () -> Unit,
): Modifier {
    if (!enabled) return this
    return pointerInput(onLongPress) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var longPressTriggered = false
            try {
                withTimeout(viewConfiguration.longPressTimeoutMillis) {
                    waitForUpOrCancellation(pass = PointerEventPass.Initial)
                }
            } catch (_: TimeoutCancellationException) {
                longPressTriggered = true
                onLongPress()
            }
            if (longPressTriggered) {
                waitForUpOrCancellation(pass = PointerEventPass.Initial)
            }
        }
    }
}
