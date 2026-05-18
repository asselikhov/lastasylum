package com.lastasylum.alliance.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.R

/** Логотип приложения ([R.drawable.la_logo]) — единый вид на splash и экране входа. */
@Composable
fun BrandLogo(
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    cornerRadius: Dp = 22.dp,
    showShadow: Boolean = true,
) {
    val shape = RoundedCornerShape(cornerRadius)
    var logoModifier = modifier
        .size(size)
        .clip(shape)
    if (showShadow) {
        logoModifier = logoModifier.shadow(
            elevation = 10.dp,
            shape = shape,
            spotColor = androidx.compose.ui.graphics.Color(0x66006CFF),
            ambientColor = androidx.compose.ui.graphics.Color(0x33000000),
        )
    }
    Image(
        painter = painterResource(R.drawable.la_logo),
        contentDescription = stringResource(R.string.cd_app_logo),
        modifier = logoModifier,
        contentScale = ContentScale.Fit,
    )
}
