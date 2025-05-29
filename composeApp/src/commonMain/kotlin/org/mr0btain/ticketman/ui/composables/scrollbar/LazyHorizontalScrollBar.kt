package org.mr0btain.ticketman.ui.composables.scrollbar

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
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
 * Adds a horizontal scroll bar to scrollable content providing its own scroll state to manage scroll
 * @param modifier Modifier
 * @param scrollBarOnTop Whether the scroll bar should be on top of the content or bottom
 * @param thickness The height in DP of the scroll bar itself
 * @param enabled Whether this scroll bar is enabled or not
 * @param colors The colors for this scroll bar
 * @param contentPadding Padding for the inner content
 * @param clipToContent Whether the scroll bar should clip to the end of the content or the viewport
 * @param scope Coroutine scope for scroll state actions
 * @param state The state of the lazy list
 * @param content The scrollable content
 */

@Composable
fun LazyHorizontalScrollBar(
    modifier: Modifier = Modifier,
    scrollBarOnTop: Boolean = ScrollBarDefaults.scrollBarOnTop,
    thickness: Dp = ScrollBarDefaults.size,
    enabled: Boolean = true,
    colors: ScrollBarColors = ScrollBarDefaults.scrollBarColors(),
    contentPadding: PaddingValues = ScrollBarDefaults.ContentPadding,
    clipToContent: Boolean = ScrollBarDefaults.clipToContent,
    scope: CoroutineScope = rememberCoroutineScope(),
    state: LazyListState = rememberLazyListState(),
    content: LazyListScope.() -> Unit
) {
    val offsetState = remember { mutableFloatStateOf(1f) }
    val viewportHeight = remember { mutableFloatStateOf(1200f) }

    Surface(
        modifier = modifier
            .drawWithContent {
                // this is how we get the viewport height.
                drawContent()
                viewportHeight.floatValue = max(this.size.height, 1f)
            }
    ) {
        Column {
            if (scrollBarOnTop) {
                LazyHorizontalScrollBarItem(
                    lazyListState = state,
                    offsetState = offsetState,
                    thickness = thickness,
                    colors = colors,
                    enabled = enabled,
                    scope = scope
                )

                // The content
                Box(
                    modifier = if (clipToContent) Modifier else Modifier.height(viewportHeight.floatValue.dp - thickness)
                ) {
                    LazyRow(
                        modifier = Modifier.padding(contentPadding),
                        content = content
                    )
                }
            } else {
                // The content
                Box(
                    modifier = if (clipToContent) Modifier else Modifier.height(viewportHeight.floatValue.dp - thickness)
                ) {
                    LazyRow(
                        modifier = Modifier.padding(contentPadding),
                        content = content
                    )
                }

                LazyHorizontalScrollBarItem(
                    lazyListState = state,
                    offsetState = offsetState,
                    thickness = thickness,
                    colors = colors,
                    enabled = enabled,
                    scope = scope
                )
            }
        }
    }
}

/**
 * The actual scroll bar itself.
 */

// Here we have to do some things different from vertical.
// Something is off with it and I couldn't get consistent results.
@Composable
private fun LazyHorizontalScrollBarItem(
    modifier: Modifier = Modifier,
    lazyListState: LazyListState,
    offsetState: MutableState<Float>,
    thickness: Dp = 8.dp,
    colors: ScrollBarColors,
    enabled: Boolean,
    scope: CoroutineScope
) {
    // Total content height / max scrollable value
    val viewportSize = lazyListState.layoutInfo.viewportSize
    val spaceBetweenItems = lazyListState.layoutInfo.mainAxisItemSpacing
    val totalItems = lazyListState.layoutInfo.totalItemsCount
    val itemSize = if (totalItems > 0) lazyListState.layoutInfo.visibleItemsInfo.first().size + spaceBetweenItems else 12

    val maxValue = totalItems * itemSize.toFloat()

    // To find the scroll value were at we need to take the first visible item index and multiply it by the size per item
    // this gives us the top of the viewport scroll state. a more accurate value would be grabbing the center item and using its value.
    val scrollValue = if (totalItems > 0) lazyListState.layoutInfo.visibleItemsInfo.first().index * itemSize else 12

    // Scrollbar width and position
    val scrollBarWidth = (viewportSize.width / maxValue) * viewportSize.width

    // Set offset for the scroll bar
    offsetState.value = (max(scrollValue.toFloat(), 1f) / maxValue) * viewportSize.width

    // The adjustment amount from the width of the scrolling material to the length of our bars travel area
    val adjust = maxValue / (viewportSize.width - scrollBarWidth)

    Row(
        modifier
            .background(colors.trackColor(enabled).value)
            .height(thickness)
    ) {
        // The actual bar
        HorizontalDivider(
            modifier = Modifier
                .width(scrollBarWidth.dp)
                .height(thickness)
                .offset(offsetState.value.dp, 0.dp)
                .clip(RoundedCornerShape(8.dp))
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        if (enabled) {
                            scope.launch {
                                lazyListState.scrollBy(adjust * delta)
                            }
                        }
                    }
                ),
            thickness = thickness,
            color = colors.barColor(enabled).value
        )
    }
}