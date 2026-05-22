package com.lastasylum.alliance.overlay

import android.widget.ImageView
import androidx.annotation.DrawableRes
import coil.load

internal fun ImageView.bindOverlayGif(@DrawableRes res: Int) {
    load(res) {
        crossfade(false)
        allowHardware(true)
    }
}
