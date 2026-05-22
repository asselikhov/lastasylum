package com.lastasylum.alliance.overlay

import android.content.Context
import android.graphics.drawable.Animatable
import android.os.Build
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import coil.load

/**
 * GIF в TYPE_APPLICATION_OVERLAY: без hardware bitmap (см. [com.lastasylum.alliance.ui.chat.SquadRelayImageLoader]).
 */
internal fun ImageView.bindOverlayGif(context: Context, @DrawableRes res: Int) {
    val drawable = AppCompatResources.getDrawable(context, res)
    if (drawable != null) {
        setImageDrawable(drawable)
        if (drawable is Animatable) {
            drawable.start()
        }
        return
    }
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
        load(res) {
            crossfade(false)
            allowHardware(false)
        }
    }
}

internal fun ImageView.stopOverlayGifAnimation() {
    val drawable = drawable
    if (drawable is Animatable && drawable.isRunning) {
        drawable.stop()
    }
}
