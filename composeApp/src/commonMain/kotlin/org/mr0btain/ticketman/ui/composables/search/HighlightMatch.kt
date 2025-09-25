package org.mr0btain.ticketman.ui.composables.search

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/**
 * Takes an annotated string with letters matching the search query bolded
 * @param text The text to modify
 * @param query The search query used to match
 */
fun highlightMatch(text: String, query: String): AnnotatedString {
    if (query.isBlank()) return AnnotatedString(text)

    val lowerText = text.lowercase()
    val lowerQuery = query.lowercase()
    val result = buildAnnotatedString {
        var startIndex = 0
        while (true) {
            val index = lowerText.indexOf(lowerQuery, startIndex)
            if (index == -1) {
                append(text.substring(startIndex))
                break
            }
            append(text.substring(startIndex, index))
            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                append(text.substring(index, index + query.length))
            }
            startIndex = index + query.length
        }
    }
    return result
}