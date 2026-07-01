package com.lastasylum.alliance.overlay

import android.widget.EditText
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** Thousands grouping for overlay numeric fields (RU-style: space as separator). */
object OverlayGroupedNumberField {
    private val symbols = DecimalFormatSymbols(Locale("ru", "RU")).apply {
        groupingSeparator = '\u00A0'
    }
    private val formatter = DecimalFormat("#,###", symbols).apply {
        isGroupingUsed = true
        groupingSize = 3
    }

    fun formatDisplay(value: Long): String =
        if (value <= 0L) "" else formatter.format(value)

    fun parse(text: CharSequence?): Long? {
        val digits = text?.toString()?.replace(Regex("\\D"), "").orEmpty()
        if (digits.isEmpty()) return null
        return digits.toLongOrNull()
    }

    fun bind(
        edit: EditText,
        onValueChanged: () -> Unit,
    ) {
        var suppressCallback = false
        edit.setOnFocusChangeListener { _, hasFocus ->
            suppressCallback = true
            try {
                val parsed = parse(edit.text)
                edit.setText(
                    when {
                        hasFocus -> parsed?.takeIf { it > 0L }?.toString().orEmpty()
                        parsed == null || parsed <= 0L -> ""
                        else -> formatDisplay(parsed)
                    },
                )
            } finally {
                suppressCallback = false
            }
        }
        edit.addTextChangedListener(
            object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: android.text.Editable?) {
                    if (!suppressCallback) onValueChanged()
                }
            },
        )
    }
}
