package org.mr0btain.ticketman.ui.composables.scrollbar

import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope

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
fun LazyColumnScrollBar(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    scrollBarOnRight: Boolean = ScrollBarDefaults.scrollBarOnRight,
    thickness: Dp = ScrollBarDefaults.size,
    enabled: Boolean = true,
    isVisible: Boolean,
    colors: ScrollBarColors = ScrollBarDefaults.scrollBarColors(),
    contentPadding: PaddingValues = ScrollBarDefaults.ContentPadding,
    clipToContent: Boolean = ScrollBarDefaults.clipToContent,
    scope: CoroutineScope = rememberCoroutineScope(),
    content: LazyListScope.() -> Unit
) {
    val viewportWidth = state.layoutInfo.viewportSize.width

    Surface(
        modifier = modifier
    ) {
        Row {
            if (scrollBarOnRight) {
                // The content
                Box(
                    modifier = modifier.weight(0.95f)
                ) {
                    LazyColumn(
                        state = state,
                        content = content,
                    )
                }

                if (isVisible) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .wrapContentWidth()
                            .weight(0.05f)
                            .background(colors.trackColor(enabled).value)
                    ) {
                        VerticalScrollbar(
                            modifier = Modifier.fillMaxHeight(),
                            style = LocalScrollbarStyle.current.copy(
                                minimalHeight = 48.dp,
                                thickness = thickness,
                                unhoverColor = colors.thumbColor(enabled).value.copy(alpha = 0.7f),
                                hoverColor = MaterialTheme.colorScheme.onSurface
                            ),
                            adapter = rememberScrollbarAdapter(state)
                        )
                    }
                }
            } else {
                if (isVisible) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .wrapContentWidth()
                            .weight(0.05f)
                            .background(colors.trackColor(enabled).value)
                    ) {
                        VerticalScrollbar(
                            modifier = Modifier.fillMaxHeight(),
                            style = LocalScrollbarStyle.current.copy(
                                minimalHeight = 48.dp,
                                thickness = thickness,
                                unhoverColor = colors.thumbColor(enabled).value.copy(alpha = 0.7f),
                                hoverColor = MaterialTheme.colorScheme.onSurface
                            ),
                            adapter = rememberScrollbarAdapter(state)
                        )
                    }
                }

                // The content
                Box(
                    modifier = Modifier.weight(0.95f)
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