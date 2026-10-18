package org.mr0btain.ticketman.components.pagination.offset

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow

/**
 * Handles loading for the pager by monitoring the lazyListState
 * @param offsetPager The pager instance
 * @param lazyListState The LazyListState
 */
@Composable
fun <Value>offsetPagerHandler(
    offsetPager: OffsetPager<Value>,
    lazyListState: LazyListState
) {
    // Previous index
    var previousIndex by remember(lazyListState) { mutableStateOf(lazyListState.firstVisibleItemIndex) }

    LaunchedEffect(lazyListState, offsetPager) {
        snapshotFlow { lazyListState.firstVisibleItemIndex to lazyListState.layoutInfo.totalItemsCount }
            .collect { (firstVisible, totalCount) ->
                // Last visible item index
                val lastVisible = firstVisible + lazyListState.layoutInfo.visibleItemsInfo.size

                // Check if we've scrolled enough to move to the next item
                if (previousIndex != lazyListState.firstVisibleItemIndex) {
                    if (previousIndex > lazyListState.firstVisibleItemIndex) {
                        // Scrolling up
                        if (lastVisible <= offsetPager.config.prefetchDistance) offsetPager.loadPrevious()
                    } else {
                        // Scrolling down
                        if (lastVisible >= totalCount - offsetPager.config.prefetchDistance) offsetPager.loadNext()
                    }
                }

                // Set previous index to current
                previousIndex = lazyListState.firstVisibleItemIndex
            }
    }
}