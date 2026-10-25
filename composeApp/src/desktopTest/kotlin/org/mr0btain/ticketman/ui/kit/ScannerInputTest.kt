package org.mr0btain.ticketman.ui.kit

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import org.mr0btain.ticketman.ui.kit.search.TmSearchField
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A barcode scanner types the code and presses Enter, which has to reach a search field's onSearch on desktop
 */
@OptIn(ExperimentalTestApi::class)
class ScannerInputTest {
    @Test
    fun enterInASearchFieldSearches() = runComposeUiTest {
        var searched: String? = null

        setContent {
            TmTheme {
                var query by remember { mutableStateOf("") }
                TmSearchField(query = query, onQueryChange = { query = it }, placeholder = "Scan", onSearch = { searched = it })
            }
        }

        val field = onNode(hasSetTextAction())
        field.performTextInput("0194253401")
        field.performKeyInput { pressKey(androidx.compose.ui.input.key.Key.Enter) }
        waitForIdle()

        assertEquals("0194253401", searched)
    }
}
