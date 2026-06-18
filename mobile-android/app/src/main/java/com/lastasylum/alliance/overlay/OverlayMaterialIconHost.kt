package com.lastasylum.alliance.overlay

import android.content.Context
import android.widget.FrameLayout
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.lastasylum.alliance.ui.theme.SquadRelayTheme

/** Material icon host for overlay View UI (quick commands tabs, etc.). */
internal class OverlayMaterialIconHost(
    context: Context,
    sizePx: Int,
) : FrameLayout(context) {
    private var imageVector by mutableStateOf<ImageVector?>(null)
    private var tint by mutableStateOf(Color.White)

    init {
        addView(
            ComposeView(context).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setContent {
                    SquadRelayTheme {
                        val sizeDp = with(LocalDensity.current) { sizePx.toDp() }
                        imageVector?.let { vector ->
                            Icon(
                                imageVector = vector,
                                contentDescription = null,
                                tint = tint,
                                modifier = Modifier.size(sizeDp),
                            )
                        }
                    }
                }
            },
            LayoutParams(sizePx, sizePx),
        )
    }

    fun setIcon(imageVector: ImageVector, tintArgb: Int) {
        this.imageVector = imageVector
        tint = Color(tintArgb)
    }
}
