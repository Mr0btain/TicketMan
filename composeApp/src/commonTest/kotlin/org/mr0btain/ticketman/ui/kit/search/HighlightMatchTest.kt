package org.mr0btain.ticketman.ui.kit.search

import androidx.compose.ui.text.font.FontWeight
import kotlin.test.*

class HighlightMatchTest {
    private fun bolded(text: String, query: String): List<String> =
        highlightMatch(text, query).let { result ->
            result.spanStyles.filter { it.item.fontWeight == FontWeight.Bold }.map { result.text.substring(it.start, it.end) }
        }

    @Test
    fun everyMatchIsBoldInItsOriginalCase() {
        val result = highlightMatch("Dana Dane", "dan")

        assertEquals("Dana Dane", result.text)
        assertEquals(listOf("Dan", "Dan"), bolded("Dana Dane", "dan"))
    }

    @Test
    fun noMatchOrNoQueryLeavesTheTextAlone() {
        assertTrue(bolded("Dana", "zz").isEmpty())
        assertTrue(bolded("Dana", " ").isEmpty())
        assertEquals("Dana", highlightMatch("Dana", "").text)
    }
}
