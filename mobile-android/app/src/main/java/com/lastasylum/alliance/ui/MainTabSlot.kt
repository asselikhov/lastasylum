package com.lastasylum.alliance.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout

/**
 * Keeps tab content in composition for state retention, but gives it zero size when
 * inactive so inactive tabs cannot paint over the active tab.
 */
fun Modifier.mainTabSlot(active: Boolean): Modifier = layout { measurable, constraints ->
    if (!active) {
        layout(0, 0) {}
    } else {
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
}
