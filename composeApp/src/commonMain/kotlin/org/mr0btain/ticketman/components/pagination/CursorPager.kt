package org.mr0btain.ticketman.components.pagination

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import org.mr0btain.ticketman.data.cache.LruCache

class CursorPagerState<Key, T>(
    coroutineScope: CoroutineScope,
    private val pageLoadLimit: Int,
    maxCacheSize: Int,
    private val fetch: suspend (before: Key?, after: Key?) -> PageResult<Key, T>
) {
    /** Cache for holding pages that may be requested again, this helps save on hits to a database. */
    private val cache = LruCache<Key, PageResult<Key, T>>(maxCacheSize, "PagerCache")

    // Mutable state list of pages, we can manage this nicely
    private val deque = mutableStateListOf<List<T>>()

    /** Flattened items as a StateFlow for UI */
    val items: StateFlow<List<T>> = snapshotFlow { deque.flatten() }
        .stateIn(coroutineScope, SharingStarted.Eagerly, emptyList())

    var isLoading by mutableStateOf(false)
        private set

    var isRefreshing by mutableStateOf(false)
        private set

    var error by mutableStateOf<Throwable?>(null)
        private set

    private var prevCursor: Key? = null
    private var nextCursor: Key? = null

    private var reachedStart = false
    private var reachedEnd = false

    suspend fun refresh() {
        load(before = null, after = null, append = false, prepend = false)
    }

    suspend fun loadAfter() {
        if (isLoading || reachedEnd) return

        load(before = null, after = nextCursor, append = true, prepend = false)
    }

    suspend fun loadBefore() {
        if (isLoading || reachedStart) return
        load(before = prevCursor, after = null, append = false, prepend = true)
    }

    /** Checks and trims Deque if needed */
    private fun trimDeque(
        append: Boolean,
        prepend: Boolean
    ) {
        if (deque.size > pageLoadLimit) {
            when {
                append -> deque.removeFirst()
                prepend -> deque.removeLast()
            }
        }
    }

    private suspend fun load(
        before: Key?,
        after: Key?,
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
                    before?.let { key ->
                        val page = cache.get(key) ?: fetch(before, after)

                        deque.addFirst(page.items)

                        prevCursor = page.prevCursor
                        reachedStart = page.prevCursor == null
                    }
                }

                append -> {
                    after?.let { key ->
                        val page = cache.get(key) ?: fetch(before, after)

                        deque.addLast(page.items)

                        nextCursor = page.nextCursor
                        reachedEnd = page.nextCursor == null
                    }
                }

                else -> {
                    val page = fetch(before, after)

                    deque.clear()
                    deque.add(page.items)

                    prevCursor = page.prevCursor
                    nextCursor = page.nextCursor

                    reachedStart = page.prevCursor == null
                    reachedEnd = page.nextCursor == null
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

data class PageResult<Key, T>(
    val items: List<T>,
    val prevCursor: Key?,
    val nextCursor: Key?
)

@Composable
fun <Key, T> rememberCursorPagerState(
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
    pageLoadLimit: Int = 10,
    maxCacheSize: Int = 10,
    fetch: suspend (before: Key?, after: Key?) -> PageResult<Key, T>
): CursorPagerState<Key, T> {
    val state = remember {
        CursorPagerState(
            coroutineScope = coroutineScope,
            pageLoadLimit = pageLoadLimit,
            maxCacheSize = maxCacheSize,
            fetch = fetch
        )
    }

    LaunchedEffect(Unit) {
        state.refresh()
    }

    return state
}
