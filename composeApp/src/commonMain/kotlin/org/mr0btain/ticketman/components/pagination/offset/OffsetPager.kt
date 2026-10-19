package org.mr0btain.ticketman.components.pagination.offset

import org.mr0btain.ticketman.error.Error
import org.mr0btain.ticketman.error.TmResult
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.mr0btain.ticketman.data.cache.LruCache
import org.mr0btain.ticketman.logging.SimpleResult
import org.mr0btain.ticketman.logging.AppLogger

/**
 * Custom offset pager. We needed more control over the pager state so we can handle jumping to items that may
 * be pretty far down the list. It's easier to handle when we have full control over the pager itself.
 * Doesn't load anything on creation, call [refresh] to do the first load. One pager lives as long as its owner,
 * change the page size with [setPageSize] and empty it with [clear] instead of building a new one, so anything
 * already collecting [items] keeps getting updates.
 * @param config OffsetPagerConfig that defines the parameters of the pager
 * @param fetch Function used to fetch the items
 */
@Suppress("PrivatePropertyName")
class OffsetPager<Value>(
    tag: String,
    config: OffsetPagerConfig = OffsetPagerConfig(),
    private val fetch: suspend (page: Int, pageSize: Int) -> TmResult<List<Value>, Error>
) {
    private val LOAD_NEXT_TAG = "$tag:LoadNext"
    private val LOAD_PREVIOUS_TAG = "$tag:LoadPrevious"
    private val REFRESH_TAG = "$tag:Refresh"
    private val TRIM_TAG = "$tag:Trim"

    /** Parameters of the pager, change the page size with [setPageSize] */
    var config: OffsetPagerConfig = config
        private set

    /** Cache for holding pages that may be requested again, this helps save on hits to a database. */
    private val cache = LruCache<Int, List<Value>>(config.cacheSize)

    // Loaded pages in order, items is rebuilt from this every time it changes
    private val deque = ArrayDeque<List<Value>>()

    private val _items = MutableStateFlow<List<Value>>(emptyList())

    /** Flattened items for UI */
    val items: StateFlow<List<Value>> = _items.asStateFlow()

    private val _loadError = MutableStateFlow<String?>(null)

    /**
     * Why the last page couldn't be loaded, null once one loads. A failed page isn't taken as the end of the list,
     * so scrolling or refreshing tries it again
     */
    val loadError: StateFlow<String?> = _loadError.asStateFlow()

    /** Whether we are loading or not */
    var isLoading by mutableStateOf(false)
        private set

    /** Whether we are refreshing or not */
    var isRefreshing by mutableStateOf(false)
        private set

    // The max number of pages
    private val maxPages get() = config.maxSize / config.pageSize

    // Keeping track of the first page in the list and the last
    private var firstPage: Int = 0
    private var lastPage: Int = 0

    // Whether we have reached the end or start
    private var reachedStart = false
    private var reachedEnd = false

    /** Changes how many items a page holds. Loaded pages were fetched with the old size, so this empties the pager */
    fun setPageSize(pageSize: Int) {
        if (pageSize == config.pageSize) return

        config = config.copy(pageSize = pageSize)
        clear()
    }

    /** Drops every loaded and cached page, nothing is shown until the next [refresh] */
    fun clear() {
        deque.clear()
        cache.clear()
        firstPage = 0
        lastPage = 0
        reachedStart = false
        reachedEnd = false
        publish()
    }

    /** Clears and refreshes the pager data */
    suspend fun refresh() {
        if (isRefreshing) return

        isRefreshing = true

        // Start over from the first page, the data may have changed since anything was cached
        clear()
        reachedStart = true

        loadInitialPages(start = 0)

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
        reachedEnd = false

        // Reset last page so we increment correctly.
        val start = firstPage
        lastPage = start

        loadInitialPages(start)

        isRefreshing = false

        AppLogger.m(REFRESH_TAG, SimpleResult.SUCCESS)
    }

    // The first few pages from start. Stops at the first that fails, so a refused or failed first page isn't
    // followed by asking for the ones after it (which would leave a gap if they did load)
    private suspend fun loadInitialPages(start: Int) {
        if (!load(page = start, append = true, prepend = false)) return

        repeat(config.initialLoadSize - 1) {
            if (reachedEnd || !load(page = lastPage + 1, append = true, prepend = false)) return
        }
    }

    /** Load the next items in database. Nothing loaded yet (the first page failed) means the first page */
    suspend fun loadNext() {
        if (isLoading || reachedEnd) return

        load(
            page = if (deque.isEmpty()) firstPage else lastPage + 1,
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

    // Pushes the loaded pages out to the UI
    private fun publish() {
        _items.value = deque.flatten()
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

    /** Loads a page and inserts the data into the deque. Returns whether it loaded */
    private suspend fun load(
        page: Int,
        append: Boolean,
        prepend: Boolean
    ): Boolean {
        isLoading = true

        val tag = if (append) LOAD_NEXT_TAG else LOAD_PREVIOUS_TAG

        try {
            // Check if we had the item in cache, if not fetch it and insert into cache
            val result = cache.get(page) ?: when (val fetched = fetch(page, config.pageSize)) {
                is TmResult.Success -> fetched.data.also { cache.put(page, it) }
                is TmResult.Error -> {
                    _loadError.value = fetched.error.message
                    AppLogger.i(tag, "${SimpleResult.FAIL}: ${fetched.error.message}")
                    return false
                }
            }

            _loadError.value = null

            when {
                append -> {
                    // Insert into deque
                    deque.addLast(result)

                    // Set last page upon success
                    lastPage = page

                    // Check if we've hit the end
                    if (result.size < config.pageSize) reachedEnd = true

                    // Trim old items if needed
                    trimDeque(append = true, prepend = false)
                }

                prepend -> {
                    // Insert into deque
                    deque.addFirst(result)

                    // Set first page upon success
                    firstPage = page

                    // Check is we've hit start
                    if (page == 0) reachedStart = true

                    // Trim old items if needed
                    trimDeque(append = false, prepend = true)
                }
            }

            publish()

            AppLogger.m(tag, SimpleResult.SUCCESS)
            return true
        } catch (ex: Exception) {
            AppLogger.e(tag, SimpleResult.FAIL, ex)
            return false
        } finally {
            isLoading = false
        }
    }
}
