package org.mr0btain.ticketman.components.utils

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import java.text.DecimalFormat

class MoneyVisualTransformation : VisualTransformation {

    private val formatter = DecimalFormat("#,##0.00")

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text

        // Remove everything except digits and dot
        val cleaned = raw.filter { it.isDigit() || it == '.' }

        // Try to parse to Double, fallback to 0.0
        val value = cleaned.toDoubleOrNull() ?: 0.0

        val formatted = formatter.format(value)

        return TransformedText(
            AnnotatedString(formatted),
            moneyOffsetMapping(raw, formatted)
        )
    }

    private fun moneyOffsetMapping(original: String, transformed: String): OffsetMapping =
        object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                // Cursor goes to end of formatted text
                return transformed.length
            }

            override fun transformedToOriginal(offset: Int): Int {
                // Always point back to end of original
                return original.length
            }
        }
}