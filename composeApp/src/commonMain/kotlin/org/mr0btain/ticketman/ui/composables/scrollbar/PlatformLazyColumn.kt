package org.mr0btain.ticketman.ui.composables.scrollbar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusModifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import org.mr0btain.ticketman.getPlatform

/**
 * A Lazy column that depending on platform will show a scrollbar or not
 * @param modifier Modifier
 * @param thickness The width in DP of the scroll bar
 * @param enabled Whether this scroll bar is enabled or not
 * @param colors The colors for this scroll bar
 * @param contentPadding Padding for the inner content
 * @param scope Coroutine scope for scroll state actions
 * @param state state for the lazy list
 * @param content The scrollable content
 */

@Composable
fun PlatformLazyColumn(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(3.dp),
    thickness: Dp = ScrollBarDefaults.size,
    enabled: Boolean = true,
    isVisible: Boolean,
    colors: ScrollBarColors = ScrollBarDefaults.scrollBarColors(),
    contentPadding: PaddingValues = ScrollBarDefaults.ContentPadding,
    scope: CoroutineScope = rememberCoroutineScope(),
    content: LazyListScope.() -> Unit
) {
    val isDesktop = remember { getPlatform().name.contains("Java") }

    Row(
        modifier = modifier
    ) {
        if (isDesktop) {
            // The content
            Box(
                modifier = Modifier
                    .weight(0.95f)
            ) {
                LazyColumn(
                    state = state,
                    content = content,
                    verticalArrangement = verticalArrangement
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
                    VerticalScrollBar(state)
                }
            }
        } else {
            LazyColumn(
                state = state,
                content = content,
                verticalArrangement = verticalArrangement
            )
        }
    }
}