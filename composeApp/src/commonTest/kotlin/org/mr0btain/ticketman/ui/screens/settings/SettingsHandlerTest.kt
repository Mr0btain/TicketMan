package org.mr0btain.ticketman.ui.screens.settings

import kotlinx.coroutines.test.TestScope
import org.mr0btain.ticketman.components.data.local.InMemoryKeyValueStore
import org.mr0btain.ticketman.components.data.local.LocalStorage
import org.mr0btain.ticketman.ui.kit.theme.TmPalettes
import org.mr0btain.ticketman.ui.screens.settings.components.SettingsHandler
import kotlin.test.*

/**
 * Settings are saved as they change, kept in range, and passed on to whatever they affect
 */
class SettingsHandlerTest {
    private val localStorage = LocalStorage(InMemoryKeyValueStore())
    private val serverUrls = mutableListOf<String>()
    private val pageSizes = mutableListOf<Int>()

    private val settings = SettingsHandler(
        localStorage = localStorage,
        scope = TestScope(),
        onServerUrlChanged = { serverUrls += it },
        onPageSizeChanged = { pageSizes += it }
    )

    @Test
    fun changesAreSavedAndLoadedBack() {
        val palette = TmPalettes.all.last()
        settings.setPalette(palette)
        settings.toggleDarkMode()
        settings.setSearchLimit(42)

        val reloaded = SettingsHandler(localStorage, TestScope(), {}, {}).apply { load() }

        assertEquals(palette, reloaded.palette.value)
        assertEquals(settings.darkMode.value, reloaded.darkMode.value)
        assertEquals(42, reloaded.searchLimit.value)
    }

    @Test
    fun sizesAreKeptInRange() {
        settings.setPageSize(1000)
        settings.setSearchLimit(0)
        settings.setCacheSize(-5)
        settings.setDefaultTab(SettingsHandler.MAIN_TAB_COUNT)

        assertEquals(SettingsHandler.PAGE_SIZE_RANGE.last, settings.pageSize.value)
        assertEquals(SettingsHandler.SEARCH_LIMIT_RANGE.first, settings.searchLimit.value)
        assertEquals(SettingsHandler.CACHE_SIZE_RANGE.first, settings.cacheSize.value)
        assertEquals(0, settings.defaultTab.value)
    }

    @Test
    fun serverAndPageSizeChangesArePassedOn() {
        settings.setServerUrl("http://shop:8080")
        settings.setPageSize(20)

        assertEquals(listOf("http://shop:8080"), serverUrls)
        assertEquals(listOf(20), pageSizes)
    }
}
