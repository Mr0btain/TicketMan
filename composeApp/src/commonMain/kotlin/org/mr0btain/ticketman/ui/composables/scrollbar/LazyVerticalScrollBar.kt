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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * Adds a vertical scroll bar to scrollable content providing its own scroll state to manage scroll
 * @param modifier Modifier
 * @param scrollBarOnRight Whether the scroll bar should be on the right or the left of the content. true means right
 * @param size The width in DP of the scroll bar
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
    scrollBarOnRight: Boolean = true,
    size: Dp = ScrollBarDefaults.size,
    enabled: Boolean = true,
    colors: ScrollBarColors = ScrollBarDefaults.scrollBarColors(),
    contentPadding: PaddingValues = ScrollBarDefaults.ContentPadding,
    clipToContent: Boolean = true,
    scope: CoroutineScope = rememberCoroutineScope(),
    content: LazyListScope.() -> Unit
) {
    val offsetState = remember { mutableFloatStateOf(20f) }
    val viewportWidth = remember { mutableFloatStateOf(1200f) }

    Surface(
        modifier = modifier.getWidth(viewportWidth)
    ) {
        Row {
            if (scrollBarOnRight) {
                // The content
                Box(
                    modifier = if (clipToContent) Modifier else Modifier.width(viewportWidth.floatValue.dp - size)
                ) {
                    LazyColumn(
                        state = state,
                        content = content
                    )
                }

                LazyVerticalScrollBarItem(
                    size = size,
                    colors = colors,
                    enabled = enabled,
                    lazyListState = state,
                    offsetState = offsetState,
                    scope = scope
                )
            } else {
                LazyVerticalScrollBarItem(
                    size = size,
                    colors = colors,
                    enabled = enabled,
                    lazyListState = state,
                    offsetState = offsetState,
                    scope = scope
                )

                Box (
                    modifier = if (clipToContent) Modifier else Modifier.width(viewportWidth.floatValue.dp - size)
                ) {
                    // The content
                    Box(
                        modifier = if (clipToContent) Modifier else Modifier.width(viewportWidth.floatValue.dp - size)
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
    // TODO: We may be able to hoist these values higher and pass them to this to maybe save on
    //  computations every scroll we only need to update them when the items themselves change
    // Total content height / max scrollable value
    val viewportSize = lazyListState.layoutInfo.viewportSize
    val spaceBetweenItems = lazyListState.layoutInfo.mainAxisItemSpacing
    val totalItems = lazyListState.layoutInfo.totalItemsCount
    val itemSize = if (totalItems > 0) lazyListState.layoutInfo.visibleItemsInfo.first().size + spaceBetweenItems else 12

    val maxValue = totalItems * itemSize.toFloat()

    // To find the scroll value were at we need to take the first visible item index and multiply it by the size per item
    // this gives us the top of the viewport scroll state. a more accurate value would be grabbing the center item and using its value.
    val scrollValue = if (totalItems > 0) lazyListState.layoutInfo.visibleItemsInfo.first().index * itemSize else 12f

    val totalContentHeight = max(maxValue, 1f)

    // Using this avoids a divide by zero exception
    val viewport = max(viewportSize.height.toFloat(), 1f)

    // Compute scrollbar height and position
    val scrollBarHeight = (viewport / totalContentHeight) * viewport * 2

    // Actual location of the scroll bar
    offsetState.value = (max(scrollValue.toFloat(), 1f) / totalContentHeight) * viewport - (scrollBarHeight / 4)


    // The adjustment amount from the height of the scrolling material to the height of our bars travel area
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