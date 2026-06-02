package com.lastasylum.alliance.overlay

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
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
    /** Single-line row height for dropdowns and search (fits [textStyle] without clipping). */
    val FieldHeight = 34.dp
    val FieldShape = RoundedCornerShape(8.dp)
    val FieldSpacing = 6.dp
    val SearchIconSize = 16.dp
    val RowVerticalPadding = 2.dp

    @Composable
    fun textStyle(): TextStyle =
        MaterialTheme.typography.labelLarge.copy(
            fontSize = 13.sp,
            lineHeight = 16.sp,
        )

    @Composable
    fun menuItemTextStyle(): TextStyle = textStyle()

    fun baseFieldModifier(): Modifier =
        Modifier
            .fillMaxWidth()
            .height(FieldHeight)

    @Composable
    fun dropdownContentPadding(): PaddingValues =
        OutlinedTextFieldDefaults.contentPadding(
            start = 8.dp,
            top = 0.dp,
            end = 2.dp,
            bottom = 0.dp,
        )

    @Composable
    fun searchContentPadding(): PaddingValues =
        OutlinedTextFieldDefaults.contentPadding(
            start = 0.dp,
            top = 0.dp,
            end = 8.dp,
            bottom = 0.dp,
        )

    @Composable
    fun searchLeadingIconModifier(): Modifier = Modifier.size(SearchIconSize)

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
