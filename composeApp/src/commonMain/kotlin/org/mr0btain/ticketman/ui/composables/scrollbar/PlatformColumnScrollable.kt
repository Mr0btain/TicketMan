package org.mr0btain.ticketman.ui.composables.scrollbar

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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * A scrollable Column that depending on the platform will show a scrollbar or not
 * @param modifier Modifier
 * @param scrollBarOnRight Whether the scroll bar should be on the right or the left of the content. true means right
 * @param barWidth The width in DP of the scroll bar
 * @param enabled Whether this scroll bar is enabled or not
 * @param colors The colors for this scroll bar
 * @param contentPadding Padding for the inner content
 * @param clipToContent Whether the scroll bar should clip to the end of the content or the viewport
 * @param scope Coroutine scope for scroll state actions
 * @param scrollState Scroll state for controlling the scrollable content. is also provided to the content so you only need to specify it here to use it throughout
 * @param content The scrollable content
 */

@Composable
fun PlatformColumnScrollable(
    modifier: Modifier = Modifier,
    columnModifier: Modifier = Modifier,
    scrollBarOnRight: Boolean = true,
    barWidth: Dp = ScrollBarDefaults.size,
    backgroundColor: Color,
    enabled: Boolean = true,
    isVisible: Boolean,
    colors: ScrollBarColors = ScrollBarDefaults.scrollBarColors(),
    contentPadding: PaddingValues = ScrollBarDefaults.ContentPadding,
    clipToContent: Boolean = true,
    scrollState: ScrollState = rememberScrollState(),
    scope: CoroutineScope = rememberCoroutineScope(),
    content: @Composable (ColumnScope.(scrollState: ScrollState) -> Unit)
) {
    val viewportSize = remember { mutableStateOf(Size.Zero) }

    Surface(
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.drawWithContent {
                drawContent()
                viewportSize.value = this.size
            }.background(backgroundColor)
        ) {
            if (scrollBarOnRight) {
                // The content
                Column(
                    modifier = columnModifier
                        .background(backgroundColor)
                        .padding(contentPadding)
                        .verticalScroll(scrollState)
                        .weight(0.95f)
                ) {
                    content(scrollState)
                }

                if (isVisible) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .wrapContentWidth()
                            .weight(0.05f)
                            .background(colors.trackColor(enabled).value)
                    ) {

                        VerticalScrollBar(scrollState)
                    }
                }
            } else {
                if (isVisible) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .wrapContentWidth()
                            .background(colors.trackColor(enabled).value)
                    ) {
                        VerticalScrollBar(scrollState)
                    }
                }

                Box (
                    modifier = if (clipToContent) Modifier.background(backgroundColor) else Modifier.width(viewportSize.value.width.dp - barWidth).background(backgroundColor)
                ) {
                    // The content
                    Column(
                        modifier = Modifier
                            .background(backgroundColor)
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
    barWidth: Dp = ScrollBarDefaults.size,
    size: Size,
    enabled: Boolean = true,
    colors: ScrollBarColors = ScrollBarDefaults.scrollBarColors(),
    scrollState: ScrollState,
    offsetState: MutableState<Float>,
    scope: CoroutineScope = rememberCoroutineScope()
) {
    // Using this avoids a divide by zero exception
    val viewport = max(size.height , 1f)

    // Total content height / max scrollable value minus viewport as we only want to know about the items we cant see
    val totalContentHeight = max(scrollState.maxValue.toFloat() - viewport, 1f)

    // Compute scrollbar height and position
    val scrollBarHeight = max(48f, (viewport / totalContentHeight) * viewport * 2)

    val scrollBarToContentRatio = (viewport - scrollBarHeight * 3.2f) / totalContentHeight

    offsetState.value = scrollBarToContentRatio * scrollState.value.toFloat()
    // Actual location of the scroll bar
    //offsetState.value = (max(scrollState.value.toFloat(), 0.0001f) / totalContentHeight) * viewport

    // The adjustment amount from the height of the scrolling material to the height of our bars travel area
    val adjust = totalContentHeight / (viewport - scrollBarHeight * 3.2f)

    println("Viewport $viewport")
    println("totalContentHeight: $totalContentHeight")
    println("scrollBarheight: $scrollBarHeight")
    println("scrollBarToCOntentRatio: $scrollBarToContentRatio")
    println("Offset: ${offsetState.value}")
    println("Adjust: $adjust")

    Column(
        modifier
            .height(viewport.dp)
            .background(colors.trackColor(enabled).value)
            .width(barWidth)
    ) {
        // The actual bar
        VerticalDivider(
            modifier = Modifier
                .height(scrollBarHeight.dp)
                .offset(0.dp, offsetState.value.dp)
                .clip(RoundedCornerShape(12.dp))
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
            thickness = barWidth,
            color = colors.thumbColor(enabled).value
        )
    }
}

/*
/* The items which are already shown on screen should not be taken
for calculations because they are already on screen!
You have to calculate the items remaining off screen as the 100%
of the data and match this percentage with the distance travelled
by the scrollbar.
*/
private fun calculateScrollbarOffsetY(
    state: LazyListState, dataSize: Int,
    height: MutableState<Float>,
    thumbHeight: Float
): Float {
    val renderedItemsNumberPerScroll = state.layoutInfo.visibleItemsInfo.size - 2
    val itemsToScroll = dataSize - renderedItemsNumberPerScroll
    val index = state.layoutInfo.visibleItemsInfo.first().index
    val indexPercentage = ((100 * index) / itemsToScroll)

    val yMaxValue = height.value - height.value / thumbHeight

    return ((yMaxValue * indexPercentage) / 100)
}
 */