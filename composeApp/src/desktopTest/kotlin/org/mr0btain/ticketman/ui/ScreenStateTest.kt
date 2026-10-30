package org.mr0btain.ticketman.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.mr0btain.ticketman.components.data.RecordSelection
import org.mr0btain.ticketman.components.pagination.offset.OffsetPager
import org.mr0btain.ticketman.components.pagination.offset.OffsetPagerConfig
import org.mr0btain.ticketman.error.TmResult
import org.mr0btain.ticketman.ui.screens.navigation.navigateTo
import org.mr0btain.ticketman.ui.screens.records.components.RevealInList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

/** The parts of screen state that only show when they're really composed */
@OptIn(ExperimentalTestApi::class)
class ScreenStateTest {
    @Test
    fun aScreenKeepsItsSavedStateWhileAnotherIsOpen() = runComposeUiTest {
        lateinit var nav: NavHostController

        setContent {
            nav = rememberNavController()
            NavHost(nav, startDestination = "records") {
                listOf("records", "counter", "admin").forEach { route ->
                    composable(route) {
                        var taps by rememberSaveable { mutableIntStateOf(0) }
                        Text("$route $taps", Modifier.clickable { taps++ })
                    }
                }
            }
        }

        // Screens beside the first, and the first itself, which is the one that never leaves the stack
        listOf("records", "counter", "admin").forEach { route ->
            runOnIdle { nav.navigateTo(route) }
            onNodeWithText("$route 0").performClick()
            onNodeWithText("$route 1").assertIsDisplayed()
        }

        listOf("counter", "records", "admin", "records", "counter").forEach { route ->
            runOnIdle { nav.navigateTo(route) }
            onNodeWithText("$route 1").assertIsDisplayed()
        }
    }

    @Test
    fun aRecordFarDownTheListIsLoadedAndScrolledTo() = runComposeUiTest {
        val ids = List(1000) { Uuid.random() }
        val fetched = mutableListOf<Int>()
        val pager = OffsetPager<Uuid>("Test", OffsetPagerConfig(pageSize = 20)) { page, size ->
            fetched += page
            TmResult.Success(ids.drop(page * size).take(size))
        }
        lateinit var selection: RecordSelection

        setContent {
            val state = rememberLazyListState()
            val scope = rememberCoroutineScope()
            selection = RecordSelection(scope)
            val items by androidx.compose.runtime.produceState(emptyList<Uuid>()) { pager.items.collect { value = it } }

            RevealInList(selection, pager, state, { it }, { ids.indexOf(it).toLong() })
            Column(Modifier.height(300.dp)) {
                LazyColumn(Modifier.fillMaxSize(), state) {
                    items(items, key = { it }) { Text("Row ${ids.indexOf(it)}") }
                }
            }
        }

        runOnIdle { kotlinx.coroutines.runBlocking { pager.refresh() } }
        waitForIdle()
        onNodeWithText("Row 0").assertIsDisplayed()

        runOnIdle { selection.reveal(ids[700]) }
        waitUntil(timeoutMillis = 5_000) { selection.revealing.value == null && onAllNodesWithTextExists("Row 700") }

        onNodeWithText("Row 700").assertIsDisplayed()
        // The pages around it, not everything on the way
        assertEquals(false, 20 in fetched)
    }

    private fun androidx.compose.ui.test.ComposeUiTest.onAllNodesWithTextExists(text: String): Boolean =
        onAllNodes(androidx.compose.ui.test.hasText(text)).fetchSemanticsNodes().isNotEmpty()
}
