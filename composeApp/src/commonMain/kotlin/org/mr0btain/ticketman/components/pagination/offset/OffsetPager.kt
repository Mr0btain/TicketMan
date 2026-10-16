package org.mr0btain.ticketman.components.pagination.offset

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import org.mr0btain.ticketman.data.cache.LruCache
import org.mr0btain.ticketman.logging.SimpleResult
import org.mr0btain.ticketman.logging.AppLogger

/**
 * Custom offset pager. We needed more control over the pager state so we can handle jumping to items that may
 * be pretty far down the list. It's easier to handle when we have full control over the pager itself.
 * Doesn't load anything on creation, call [refresh] to do the first load.
 * @param coroutineScope Coroutine scope for suspend functions
 * @param config OffsetPagerConfig that defines the parameters of the pager
 * @param fetch Function used to fetch the items
 */
@Suppress("PrivatePropertyName")
class OffsetPager<Value>(
    tag: String,
    coroutineScope: CoroutineScope,
    val config: OffsetPagerConfig = OffsetPagerConfig(),
    private val fetch: suspend (page: Int, pageSize: Int) -> List<Value>
) {
    private val CACHE_TAG = "$tag:Cache"
    private val LOAD_NEXT_TAG = "$tag:LoadNext"
    private val LOAD_PREVIOUS_TAG = "$tag:LoadPrevious"
    private val REFRESH_TAG = "$tag:Refresh"
    private val TRIM_TAG = "$tag:Trim"

    /** Cache for holding pages that may be requested again, this helps save on hits to a database. */
    private val cache = LruCache<Int, List<Value>>(config.cacheSize, CACHE_TAG)

    // Mutable state list of pages, we can manage this nicely, and it should update items as this is changed
    private val deque = mutableStateListOf<List<Value>>()

    /** Flattened items as a StateFlow for UI */
    val items: StateFlow<List<Value>> = snapshotFlow { deque.flatten() }
        .stateIn(coroutineScope, SharingStarted.Companion.Eagerly, emptyList())

    /** Whether we are loading or not */
    var isLoading by mutableStateOf(false)
        private set

    /** Whether we are refreshing or not */
    var isRefreshing by mutableStateOf(false)
        private set

    // The max number of pages
    private val maxPages = config.maxSize / config.pageSize

    // Keeping track of the first page in the list and the last
    private var firstPage: Int = 0
    private var lastPage: Int = 0

    // Whether we have reached the end or start
    private var reachedStart = false
    private var reachedEnd = false

    /** Clears and refreshes the pager data */
    suspend fun refresh() {
        if (isRefreshing) return

        isRefreshing = true

        // Clear deque
        deque.clear()

        // Fetch data
        (0..<config.initialLoadSize).forEach { page ->
            // This check is needed so we can load the first page properly, else well end up fetching the second page first
            if (page == 0)
                load(page = 0, append = true, prepend = false)
            else
                loadNext()
        }

        reachedStart = true
        reachedEnd = false
        isRefreshing = false

        AppLogger.m(REFRESH_TAG, SimpleResult.SUCCESS)
    }

    /** Clears and refreshes current data after a change may have happened in database */
    suspend fun refreshCurrentData() {
        // Make sure were not currently refreshing.
        if (isRefreshing) return
        isRefreshing = true

        // Clear queue and cache
        deque.clear()
        cache.clear()

        // Reset last page so we increment correctly.
        lastPage = firstPage

        // Fetch data
        (firstPage..<config.initialLoadSize).forEach { page ->
            if (page == firstPage)
                load(page = firstPage, append = true, prepend = false)
            else
                loadNext()
        }

        isRefreshing = false

        AppLogger.m(REFRESH_TAG, SimpleResult.SUCCESS)
    }

    /** Load the next items in database */
    suspend fun loadNext() {
        if (isLoading || reachedEnd) return

        load(
            page = lastPage + 1,
            append = true,
            prepend = false
        )
    }

    /** Load the previous items in database */
    suspend fun loadPrevious() {
        if (isLoading || reachedStart) return

        load(
            page = firstPage - 1,
            append = false,
            prepend = true
        )
    }

    /** Checks and trims Deque if needed */
    private fun trimDeque(
        append: Boolean,
        prepend: Boolean
    ) {
        if (deque.size > maxPages) {
            when {
                append -> {
                    deque.removeFirst()
                    firstPage++
                    reachedStart = false

                    AppLogger.m("${TRIM_TAG}First", SimpleResult.SUCCESS)
                }

                prepend -> {
                    deque.removeLast()
                    lastPage--
                    reachedEnd = false

                    AppLogger.m("${TRIM_TAG}Last", SimpleResult.SUCCESS)
                }
            }
        }
    }

    /** Loads a page and inserts the data into the deque */
    private suspend fun load(
        page: Int,
        append: Boolean,
        prepend: Boolean
    ) {
        isLoading = true

        try {
            // Check if we had the item in cache, if not fetch it and insert into cache
            val result = cache.get(page)
                ?: fetch(page, config.pageSize)
                    .also { cache.put(page, it) }

            when {
                append -> {
                    // Insert into deque
                    deque.add(result) //addLast(result)

                    // Set last page upon success
                    lastPage = page

                    // Check if we've hit the end
                    if (result.size < config.pageSize) reachedEnd = true

                    // Trim old items if needed
                    trimDeque(append = true, prepend = false)
                }

                prepend -> {
                    // Insert into deque
                    deque.add(0, result) //addFirst(result)

                    // Set first page upon success
                    firstPage = page

                    // Check is we've hit start
                    if (page == 0) reachedStart = true

                    // Trim old items if needed
                    trimDeque(append = false, prepend = true)
                }
            }
        } catch (ex: Exception) {
            if (append)
                AppLogger.e(LOAD_NEXT_TAG, SimpleResult.FAIL, ex)
            else
                AppLogger.e(LOAD_PREVIOUS_TAG, SimpleResult.FAIL,ex)
        } finally {
            isLoading = false

            if (append)
                AppLogger.m(LOAD_NEXT_TAG, SimpleResult.SUCCESS)
            else
                AppLogger.m(LOAD_PREVIOUS_TAG, SimpleResult.SUCCESS)
        }
    }
}