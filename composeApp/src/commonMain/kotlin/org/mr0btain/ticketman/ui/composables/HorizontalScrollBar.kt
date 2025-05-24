package org.mr0btain.ticketman.ui.composables

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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

/**
 * Adds a horizontal scroll bar to scrollable content providing its own scroll state to manage scroll
 * @param modifier Modifier
 * @param scrollBarOnTop Whether the scroll bar should be on top of the content or bottom
 * @param size The height in DP of the scroll bar
 * @param enabled Whether this scroll bar is enabled or not
 * @param colors The colors for this scroll bar
 * @param contentPadding Padding for the inner content
 * @param clipToContent Whether the scroll bar should clip to the end of the content or the viewport
 * @param scope Coroutine scope for scroll state actions
 * @param scrollState Scroll state for controlling the scrollable content. is also provided to the content so you only need to specify it here to use it throughout
 * @param content The scrollable content
 */

@Composable
fun HorizontalScrollBar(
    modifier: Modifier = Modifier,
    scrollBarOnTop: Boolean = false,
    size: Dp = ScrollBarDefaults.size,
    enabled: Boolean = true,
    colors: ScrollBarColors = ScrollBarDefaults.scrollBarColors(),
    contentPadding: PaddingValues = ScrollBarDefaults.ContentPadding,
    clipToContent: Boolean = true,
    scope: CoroutineScope = rememberCoroutineScope(),
    scrollState: ScrollState = rememberScrollState(),
    content: @Composable (RowScope.(scrollState: ScrollState) -> Unit)
) {
    val offsetState = remember { mutableStateOf(0f) }
    val totalWidth = remember { mutableStateOf(0f) }
    val viewportHeight = remember { mutableStateOf(1200f) }

    Surface(
        modifier = modifier.getHeight(viewportHeight)
    ) {
        Column {
            if (scrollBarOnTop) {
                HorizontalScrollBarItem(
                    scrollState = scrollState,
                    offsetState = offsetState,
                    height = size,
                    totalContentWidth = totalWidth.value,
                    colors = colors,
                    enabled = enabled,
                    scope = scope
                )

                // The content
                Box(
                    modifier = if (clipToContent) Modifier else Modifier.width(viewportHeight.value.dp - size)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(contentPadding)
                            .horizontalScroll(scrollState)
                            .wrapContentSize()
                            .getWidth(totalWidth)
                    ) {
                        content(scrollState)
                    }
                }
            } else {
                // The content
                Box(
                    modifier = if (clipToContent) Modifier else Modifier.width(viewportHeight.value.dp - size)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(contentPadding)
                            .horizontalScroll(scrollState)
                            .wrapContentSize()
                            .getWidth(totalWidth)
                    ) {
                        content(scrollState)
                    }
                }

                HorizontalScrollBarItem(
                    scrollState = scrollState,
                    offsetState = offsetState,
                    height = size,
                    totalContentWidth = totalWidth.value,
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

// Here we have to do some things different from vertical. Like we cant trust ScrollState to give us maxvalue or viewport size.
// Something is off with it and I couldn't get consistent results. So we have to get those values ourselves and then do the math.
@Composable
private fun HorizontalScrollBarItem(
    modifier: Modifier = Modifier,
    scrollState: ScrollState,
    offsetState: MutableState<Float>,
    height: Dp = 8.dp,
    totalContentWidth: Float,
    colors: ScrollBarColors,
    enabled: Boolean,
    scope: CoroutineScope
) {
    // Viewport Width
    val viewportWidth = remember { mutableStateOf(0f) }

    // Scrollbar width and position
    val scrollBarWidth = (viewportWidth.value / totalContentWidth) * viewportWidth.value

    // Set offset for the scroll bar
    offsetState.value = (scrollState.value.toFloat() / totalContentWidth) * viewportWidth.value

    // The adjustment amount from the width of the scrolling material to the length of our bars travel area
    val adjust = totalContentWidth / (viewportWidth.value - scrollBarWidth)

    Row(
        modifier
            .fillMaxWidth()
            .background(colors.trackColor(enabled).value)
            .height(height)
            .getWidth(viewportWidth)
    ) {
        // The actual bar
        HorizontalDivider(
            modifier = Modifier
                .width(scrollBarWidth.dp)
                .height(height)
                .offset(offsetState.value.dp, 0.dp)
                .clip(RoundedCornerShape(8.dp))
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        if (enabled) {
                            scope.launch {
                                scrollState.scrollBy(adjust * delta)
                            }
                        }
                    }
                ),
            thickness = height,
            color = colors.barColor(enabled).value
        )
    }
}

/**
 * Updates a mutable state with the current width of a composable
 */
@Composable
fun Modifier.getWidth(
    width: MutableState<Float>
): Modifier {
    return drawWithContent {
        drawContent()
        width.value = this.size.width
    }
}