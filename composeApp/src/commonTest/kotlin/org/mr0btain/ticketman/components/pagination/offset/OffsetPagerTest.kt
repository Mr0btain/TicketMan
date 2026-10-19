package org.mr0btain.ticketman.components.pagination.offset

import org.mr0btain.ticketman.server.NetworkError
import org.mr0btain.ticketman.error.Result
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
        if (failing) Result.Error(NetworkError.SERVER_ERROR)
        else Result.Success((page * pageSize..<minOf(total, (page + 1) * pageSize)).toList())
    }

    @Test
    fun aFailedPageIsReportedNotTakenAsTheEnd() = runTest {
        total = 10
        failing = true
        pager.refresh()

        assertTrue(pager.items.value.isEmpty())
        assertEquals(NetworkError.SERVER_ERROR.message, pager.loadError.value)

        // Scrolling on tries again, and a page that loads clears the error
        failing = false
        pager.loadNext()
        assertNull(pager.loadError.value)
        assertTrue(pager.items.value.isNotEmpty())
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
        ) { page, pageSize -> Result.Success((page * pageSize..<(page + 1) * pageSize).toList()) }

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
}
