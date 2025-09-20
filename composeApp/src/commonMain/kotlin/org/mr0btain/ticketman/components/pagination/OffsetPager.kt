package org.mr0btain.ticketman.components.pagination

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.mr0btain.ticketman.data.cache.LruCache

class OffsetPager<Value>(
    coroutineScope: CoroutineScope,
    private val pageLoadLimit: Int,
    maxCacheSize: Int,
    private val pageSize: Int,
    private val count: Long,
    private val fetch: suspend (page: Int, pageSize: Int) -> List<Value>
) {
    init { coroutineScope.launch { refresh() } }

    /** Cache for holding pages that may be requested again, this helps save on hits to a database. */
    private val cache = LruCache<Int, List<Value>>(maxCacheSize, "PagerCache")

    // Mutable state list of pages, we can manage this nicely, and it should update items as this is changed
    private val deque = mutableStateListOf<List<Value>>()

    /** Flattened items as a StateFlow for UI */
    val items: StateFlow<List<Value>> = snapshotFlow { deque.flatten() }
        .stateIn(coroutineScope, SharingStarted.Eagerly, emptyList())

    var isLoading by mutableStateOf(false)
        private set

    var isRefreshing by mutableStateOf(false)
        private set

    var error by mutableStateOf<Throwable?>(null)
        private set

    private val maxPage = (count / pageSize).toInt()

    private var currentPage: Int = 0

    private var reachedStart = false
    private var reachedEnd = false

    suspend fun refresh() = load(currentPage, true, false)

    suspend fun loadAfter() {
        if (isLoading || reachedEnd) return

        if (currentPage < maxPage)
            currentPage++
        else {
            currentPage = maxPage
            reachedEnd = true
        }

        load(currentPage, append = true, prepend = false)
    }

    suspend fun loadBefore() {
        if (isLoading || reachedStart) return

        if (currentPage > 0)
            currentPage--
        else {
            currentPage = 0
            reachedStart = true
        }

        load(currentPage, append = false, prepend = true)
    }

    /** Checks and trims Deque if needed */
    private fun trimDeque(
        append: Boolean,
        prepend: Boolean
    ) {
        if (deque.size > pageLoadLimit) {
            when {
                append -> {
                    deque.removeFirst()
                    reachedStart = false
                }

                prepend -> {
                    deque.removeLast()
                    reachedEnd = false
                }
            }
        }
    }

    private suspend fun load(
        page: Int,
        append: Boolean,
        prepend: Boolean
    ) {
        if (isLoading) return

        isLoading = true

        if (!append && !prepend) isRefreshing = true

        error = null

        try {
            when {
                prepend -> {
                    if (page >= 0) {
                        deque.addFirst(
                            cache.get(page) ?: fetch(page, pageSize)
                        )

                        if (page == 0) reachedStart = true
                    }
                }

                append -> {
                    if (page * pageSize < count) {
                        deque.addLast(
                            cache.get(page) ?: fetch(page, pageSize)
                        )
                    }
                }

                else -> {
                    deque.clear()
                    deque.add(fetch(page, pageSize))

                    reachedStart = true
                    reachedEnd = false
                }
            }

            // Trim old items if needed
            trimDeque(append, prepend)
        } catch (t: Throwable) {
            error = t
        } finally {
            isLoading = false
            isRefreshing = false
        }
    }
}

@Composable
fun <Value>rememberOffsetPager(
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
    pageLoadLimit: Int = 10,
    maxCacheSize: Int = 10,
    pageSize: Int = 20,
    count: Long,
    fetch: suspend (page: Int, pageSize: Int) -> List<Value>,
    lazyListState: LazyListState
): OffsetPager<Value> {
    val state = remember {
        OffsetPager(
            coroutineScope = coroutineScope,
            pageLoadLimit = pageLoadLimit,
            maxCacheSize = maxCacheSize,
            pageSize = pageSize,
            count = count,
            fetch = fetch
        )
    }

    var previousIndex by remember(lazyListState) { mutableStateOf(lazyListState.firstVisibleItemIndex) }

    LaunchedEffect(lazyListState) {
        snapshotFlow { lazyListState.firstVisibleItemIndex to lazyListState.layoutInfo.totalItemsCount }
            .collect { (firstVisible, totalCount) ->
                val lastVisible = firstVisible + lazyListState.layoutInfo.visibleItemsInfo.size

                if (previousIndex != lazyListState.firstVisibleItemIndex) {
                    if (previousIndex > lazyListState.firstVisibleItemIndex) {
                        // Scrolling up
                        if (lastVisible <= 10) state.loadBefore()
                    } else {
                        // Scrolling down
                        if (lastVisible >= totalCount - 10) state.loadAfter()
                    }
                }

                previousIndex = lazyListState.firstVisibleItemIndex
            }
    }

    return state
}

@Composable
fun <Value>handleOffsetPager(
    pager: OffsetPager<Value>,
    lazyListState: LazyListState
) {
    var previousIndex by remember(lazyListState) { mutableStateOf(lazyListState.firstVisibleItemIndex) }

    LaunchedEffect(lazyListState) {
        snapshotFlow { lazyListState.firstVisibleItemIndex to lazyListState.layoutInfo.totalItemsCount }
            .collect { (firstVisible, totalCount) ->
                val lastVisible = firstVisible + lazyListState.layoutInfo.visibleItemsInfo.size

                if (previousIndex != lazyListState.firstVisibleItemIndex) {
                    if (previousIndex > lazyListState.firstVisibleItemIndex) {
                        // Scrolling up
                        if (lastVisible <= 10) pager.loadBefore()
                    } else {
                        // Scrolling down
                        if (lastVisible >= totalCount - 10) pager.loadAfter()
                    }
                }

                previousIndex = lazyListState.firstVisibleItemIndex
            }
    }
}

@Composable
fun <Value>rememberOffsetPagerState(
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
    pageLoadLimit: Int = 10,
    maxCacheSize: Int = 10,
    pageSize: Int = 20,
    count: Long,
    fetch: suspend (page: Int, pageSize: Int) -> List<Value>
): OffsetPager<Value> {
    val state = remember {
        OffsetPager(
            coroutineScope = coroutineScope,
            pageLoadLimit = pageLoadLimit,
            maxCacheSize = maxCacheSize,
            pageSize = pageSize,
            count = count,
            fetch = fetch
        )
    }

    return state
}