package org.mr0btain.ticketman.ui.composables

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
 * @param size The width in DP of the scroll bar
 * @param enabled Whether this scroll bar is enabled or not
 * @param colors The colors for this scroll bar
 * @param contentPadding Padding for the inner content
 * @param clipToContent Whether the scroll bar should clip to the end of the content or the viewport
 * @param scope Coroutine scope for scroll state actions
 * @param scrollState Scroll state for controlling the scrollable content. is also provided to the content so you only need to specify it here to use it throughout
 * @param content The scrollable content
 */

@Composable
fun VerticalScrollBar(
    modifier: Modifier = Modifier,
    scrollBarOnRight: Boolean = true,
    size: Dp = ScrollBarDefaults.size,
    enabled: Boolean = true,
    colors: ScrollBarColors = ScrollBarDefaults.scrollBarColors(),
    contentPadding: PaddingValues = ScrollBarDefaults.ContentPadding,
    clipToContent: Boolean = true,
    scrollState: ScrollState = rememberScrollState(),
    scope: CoroutineScope = rememberCoroutineScope(),
    content: @Composable (ColumnScope.(scrollState: ScrollState) -> Unit)
) {
    val offsetState = remember { mutableStateOf(20f) }
    val viewportWidth = remember { mutableStateOf(1200f) }

    Surface(
        modifier = modifier.getWidth(viewportWidth)
    ) {
        Row {
            if (scrollBarOnRight) {
                // The content
                Box(
                    modifier = if (clipToContent) Modifier else Modifier.width(viewportWidth.value.dp - size)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(contentPadding)
                            .verticalScroll(scrollState)
                    ) {
                        content(scrollState)
                    }
                }

                VerticalScrollBarItem(
                    size = size,
                    colors = colors,
                    enabled = enabled,
                    scrollState = scrollState,
                    offsetState = offsetState,
                    scope = scope
                )
            } else {
                VerticalScrollBarItem(
                    size = size,
                    colors = colors,
                    enabled = enabled,
                    scrollState = scrollState,
                    offsetState = offsetState,
                    scope = scope
                )

                Box (
                    modifier = if (clipToContent) Modifier else Modifier.width(viewportWidth.value.dp - size)
                ) {
                    // The content
                    Column(
                        modifier = Modifier
                            .verticalScroll(scrollState)
                            .padding(contentPadding)
                    ) {
                        content(scrollState)
                    }
                }
            }
        }
    }
}

@Composable
private fun VerticalScrollBarItem(
    modifier: Modifier = Modifier,
    size: Dp,
    enabled: Boolean,
    colors: ScrollBarColors,
    scrollState: ScrollState,
    offsetState: MutableState<Float>,
    scope: CoroutineScope
) {
    // Total content height / max scrollable value
    val totalContentHeight = max(scrollState.maxValue.toFloat(), 1f)

    // Using this avoids a divide by zero exception
    val viewport = max(scrollState.viewportSize.toFloat(), 1f)

    // Compute scrollbar height and position
    val scrollBarHeight = (viewport / totalContentHeight) * viewport * 2

    // Actual location of the scroll bar
    offsetState.value = (max(scrollState.value.toFloat(), 1f) / totalContentHeight) * viewport -(scrollBarHeight / 2)


    // The adjustment amount from the height of the scrolling material to the height of our bars travel area
    val adjust = totalContentHeight / viewport + scrollBarHeight

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
                                scrollState.scrollBy(adjust * delta)
                            }
                        }
                    }
                ),
            thickness = size,
            color = colors.barColor(enabled).value
        )
    }
}

@Composable
fun Modifier.getHeight(
    height: MutableState<Float>
): Modifier {
    return drawWithContent {
        drawContent()

        height.value = max(this.size.height, 1f)
    }
}