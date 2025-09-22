package org.mr0btain.ticketman.components.pagination.cursor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope

@Composable
fun <Key, T> rememberCursorPagerState(
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
    pageLoadLimit: Int = 10,
    maxCacheSize: Int = 10,
    fetch: suspend (before: Key?, after: Key?) -> CursorPageResult<Key, T>
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