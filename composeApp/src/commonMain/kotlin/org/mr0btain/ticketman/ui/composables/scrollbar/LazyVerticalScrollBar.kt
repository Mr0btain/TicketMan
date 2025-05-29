package org.mr0btain.ticketman.ui.composables.scrollbar

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * Adds a vertical scroll bar to scrollable content providing its own scroll state to manage scroll
 * @param modifier Modifier
 * @param scrollBarOnRight Whether the scroll bar should be on the right or the left of the content. true means right
 * @param thickness The width in DP of the scroll bar
 * @param enabled Whether this scroll bar is enabled or not
 * @param colors The colors for this scroll bar
 * @param contentPadding Padding for the inner content
 * @param clipToContent Whether the scroll bar should clip to the end of the content or the viewport
 * @param scope Coroutine scope for scroll state actions
 * @param  state state for the lazy list
 * @param content The scrollable content
 */

@Composable
fun LazyVerticalScrollBar(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    scrollBarOnRight: Boolean = ScrollBarDefaults.scrollBarOnRight,
    thickness: Dp = ScrollBarDefaults.size,
    enabled: Boolean = true,
    colors: ScrollBarColors = ScrollBarDefaults.scrollBarColors(),
    contentPadding: PaddingValues = ScrollBarDefaults.ContentPadding,
    clipToContent: Boolean = ScrollBarDefaults.clipToContent,
    scope: CoroutineScope = rememberCoroutineScope(),
    content: LazyListScope.() -> Unit
) {
    val offsetState = remember { mutableFloatStateOf(20f) }
    val viewportWidth = state.layoutInfo.viewportSize.width

    Surface(
        modifier = modifier
    ) {
        Row {
            if (scrollBarOnRight) {
                // The content
                Box(
                    modifier = if (clipToContent) Modifier else Modifier.width(viewportWidth.dp - thickness)
                ) {
                    LazyColumn(
                        state = state,
                        content = content
                    )
                }

                LazyVerticalScrollBarItem(
                    size = thickness,
                    colors = colors,
                    enabled = enabled,
                    lazyListState = state,
                    offsetState = offsetState,
                    scope = scope
                )
            } else {
                LazyVerticalScrollBarItem(
                    size = thickness,
                    colors = colors,
                    enabled = enabled,
                    lazyListState = state,
                    offsetState = offsetState,
                    scope = scope
                )

                Box (
                    modifier = if (clipToContent) Modifier else Modifier.width(viewportWidth.dp - thickness)
                ) {
                    // The content
                    Box(
                        modifier = if (clipToContent) Modifier else Modifier.width(viewportWidth.dp - thickness)
                    ) {
                        LazyColumn(
                            state = state,
                            content = content
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LazyVerticalScrollBarItem(
    modifier: Modifier = Modifier,
    size: Dp,
    enabled: Boolean,
    colors: ScrollBarColors,
    lazyListState: LazyListState,
    offsetState: MutableState<Float>,
    scope: CoroutineScope
) {
    // Calculations. Some of this seems really wild but its not hard once you get your head around what were actually doing.
    // Basically just getting the total size of our items by multiplying the count of items by the height and space between them
    // Then we get our scroll value from just taking our first visible item and calculating its length from the start. We then have
    // where we are and how far we can go. The res is pretty basic.

    // Total item count.
    val totalItems = lazyListState.layoutInfo.totalItemsCount

    // The size of one item plus it's spacing between it and the next item.
    val itemSize = if (totalItems > 0) lazyListState.layoutInfo.visibleItemsInfo.first().size + lazyListState.layoutInfo.mainAxisItemSpacing else 12

    // The maximum scroll value / end of the content.
    val totalContentHeight = max(totalItems * itemSize.toFloat(), 1f)

    // To find the scroll value were at we need to take the first visible item index and multiply it by the size per item
    // this gives us the top of the viewport scroll value.
    val scrollValue = if (totalItems > 0) lazyListState.layoutInfo.visibleItemsInfo.first().index * itemSize else 12f

    // The viewport or visible section on screen.
    val viewport = max(lazyListState.layoutInfo.viewportSize.height.toFloat(), 1f)

    // Compute scrollbar height and position
    val scrollBarHeight = (viewport / totalContentHeight) * viewport * 2

    // Actual location of the scroll bar on its track.
    offsetState.value = (max(scrollValue.toFloat(), 1f) / totalContentHeight) * viewport - (scrollBarHeight / 4)


    // The adjustment amount from the length of the scrolling content to the length of our bars travel area.
    val adjust = totalContentHeight / viewport

    Column(
        modifier
            .fillMaxHeight()
            .background(colors.trackColor(enabled).value)
            .width(size)
    ) {
        // The actual bar
        VerticalDivider(
            modifier = Modifier
                .height(scrollBarHeight.dp)
                .offset(0.dp, offsetState.value.dp)
                .clip(RoundedCornerShape(8.dp))
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { delta ->
                        if (enabled) {
                            scope.launch {
                                // Scroll the content by the delta multiplied by the adjustment
                                lazyListState.scrollBy(adjust * delta)
                            }
                        }
                    }
                ),
            thickness = size,
            color = colors.barColor(enabled).value
        )
    }
}