package org.mr0btain.ticketman.components.pagination.offset

import org.mr0btain.ticketman.server.NetworkError
import org.mr0btain.ticketman.error.TmResult
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class OffsetPagerTest {
    private var total = 0
    private val fetched = mutableListOf<Int>()

    private var failing = false

    // Items are just their position, so a page of a list of [total] items is easy to check
    private val pager = OffsetPager<Int>(
        tag = "TestPager",
        config = OffsetPagerConfig(pageSize = 2, initialLoadSize = 3)
    ) { page, pageSize ->
        fetched += page
        if (failing) TmResult.Error(NetworkError.SERVER_ERROR)
        else TmResult.Success((page * pageSize..<minOf(total, (page + 1) * pageSize)).toList())
    }

    @Test
    fun aFailedPageIsReportedNotTakenAsTheEnd() = runTest {
        total = 10
        failing = true
        pager.refresh()

        assertTrue(pager.items.value.isEmpty())
        assertEquals(NetworkError.SERVER_ERROR.message, pager.loadError.value)

        // Scrolling on tries again, from the first page, and a page that loads clears the error
        failing = false
        pager.loadNext()
        assertNull(pager.loadError.value)
        assertEquals(listOf(0, 1), pager.items.value)
    }

    @Test
    fun aFailedFirstPageStopsTheFirstLoad() = runTest {
        total = 10
        failing = true
        pager.refresh()

        // Not followed by asking for the pages after it, which would also fail (or leave a gap)
        assertEquals(listOf(0), fetched)
    }

    @Test
    fun aFailedLaterPageStopsTheFirstLoadThere() = runTest {
        total = 10
        var calls = 0
        val pager = OffsetPager<Int>("TestPager", OffsetPagerConfig(pageSize = 2, initialLoadSize = 3)) { page, pageSize ->
            fetched += page
            if (++calls == 2) TmResult.Error(NetworkError.SERVER_ERROR)
            else TmResult.Success((page * pageSize..<(page + 1) * pageSize).toList())
        }

        pager.refresh()

        assertEquals(listOf(0, 1), fetched)
        assertEquals(listOf(0, 1), pager.items.value)
    }

    @Test
    fun refreshFillsItemsCollectedBeforeTheFirstLoad() = runTest {
        total = 10
        val items = pager.items

        pager.refresh()

        assertEquals(listOf(0, 1, 2, 3, 4, 5), items.value)
    }

    @Test
    fun refreshStartsOverAfterReachingTheEnd() = runTest {
        total = 1
        pager.refresh()
        assertEquals(listOf(0), pager.items.value)

        // More came in on the server, a refresh has to fetch again instead of stopping at the old end or using the cache
        total = 10
        fetched.clear()
        pager.refresh()

        assertEquals(listOf(0, 1, 2), fetched)
        assertEquals(listOf(0, 1, 2, 3, 4, 5), pager.items.value)
    }

    @Test
    fun refreshCurrentDataReloadsFromTheFirstLoadedPage() = runTest {
        total = 100
        val small = OffsetPager<Int>(
            tag = "TestPager",
            config = OffsetPagerConfig(pageSize = 2, initialLoadSize = 2, maxSize = 4)
        ) { page, pageSize -> TmResult.Success((page * pageSize..<(page + 1) * pageSize).toList()) }

        // Load far enough that the first pages get trimmed off
        small.refresh()
        repeat(4) { small.loadNext() }
        assertEquals(listOf(8, 9, 10, 11), small.items.value)

        small.refreshCurrentData()

        assertEquals(listOf(8, 9, 10, 11), small.items.value)
    }

    @Test
    fun pageSizeChangeEmptiesThePagerUntilTheNextRefresh() = runTest {
        total = 10
        val items = pager.items
        pager.refresh()

        pager.setPageSize(3)
        assertTrue(items.value.isEmpty())

        pager.refresh()
        assertEquals((0..8).toList(), items.value)
    }

    @Test
    fun clearDropsEverything() = runTest {
        total = 10
        pager.refresh()

        pager.clear()

        assertTrue(pager.items.value.isEmpty())
    }

    @Test
    fun revealLoadsThePagesAroundAnItemFurtherDown() = runTest {
        total = 40
        pager.refresh()
        fetched.clear()

        val index = pager.reveal(matches = { it == 21 }, position = { 21 })

        // Item 21 is on page 10, the load starts a page before it so there's something to scroll up over
        assertEquals(listOf(9, 10, 11), fetched)
        assertEquals((18..23).toList(), pager.items.value)
        assertEquals(3, index)

        // And scrolling both ways carries on from there
        pager.loadPrevious()
        assertEquals(16, pager.items.value.first())
        pager.loadNext()
        assertEquals(25, pager.items.value.last())
    }

    @Test
    fun revealDoesntAskAboutWhatsAlreadyLoaded() = runTest {
        total = 40
        pager.refresh()
        fetched.clear()

        val index = pager.reveal(matches = { it == 3 }, position = { fail("Shouldn't need to ask") })

        assertEquals(3, index)
        assertTrue(fetched.isEmpty())
    }

    @Test
    fun revealLeavesTheListAloneWhenTheItemIsntInIt() = runTest {
        total = 40
        pager.refresh()
        val before = pager.items.value

        assertNull(pager.reveal(matches = { it == 99 }, position = { null }))
        assertEquals(before, pager.items.value)
    }

    @Test
    fun refreshingCurrentDataKeepsEveryLoadedPageAndNeverShowsFewer() = runTest {
        total = 40
        pager.refresh()
        repeat(3) { pager.loadNext() }
        assertEquals(12, pager.items.value.size)

        val sizes = mutableListOf<Int>()
        val watching = launch(UnconfinedTestDispatcher(testScheduler)) { pager.items.collect { sizes += it.size } }

        pager.refreshCurrentData()
        watching.cancel()

        assertEquals(12, pager.items.value.size)
        assertTrue(sizes.all { it == 12 }, "Sizes shown while reloading: $sizes")
    }
}
