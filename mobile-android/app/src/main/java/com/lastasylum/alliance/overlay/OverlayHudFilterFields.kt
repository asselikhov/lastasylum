package com.lastasylum.alliance.overlay

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Shared filter + search fields in overlay «Уведомления» and «Участники онлайн». */
object OverlayHudFilterFields {
    /** Same min height for filter dropdowns and search; avoids clipping with [textStyle]. */
    val FieldHeight = 48.dp
    val FieldShape = RoundedCornerShape(10.dp)
    val FieldSpacing = 8.dp

    @Composable
    fun textStyle(): TextStyle =
        MaterialTheme.typography.bodySmall.copy(lineHeight = 16.sp)

    @Composable
    fun menuItemTextStyle(): TextStyle = textStyle()

    fun baseFieldModifier(): Modifier =
        Modifier
            .fillMaxWidth()
            .heightIn(min = FieldHeight)
            .defaultMinSize(minHeight = FieldHeight)

    @Composable
    fun notificationsFieldColors(): TextFieldColors =
        OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = Color(0xFF3A4555),
            focusedContainerColor = Color(0xFF1A2836),
            unfocusedContainerColor = Color(0xFF141C28),
        )

    @Composable
    fun onlineFieldColors(
        textColor: Color,
        focusedBorder: Color,
        unfocusedBorder: Color,
        cursorColor: Color,
    ): TextFieldColors =
        OutlinedTextFieldDefaults.colors(
            focusedTextColor = textColor,
            unfocusedTextColor = textColor,
            focusedBorderColor = focusedBorder,
            unfocusedBorderColor = unfocusedBorder,
            cursorColor = cursorColor,
            focusedContainerColor = Color(0xFF1A2836),
            unfocusedContainerColor = Color(0xFF141C28),
        )
}
