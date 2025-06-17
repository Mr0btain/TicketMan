package org.mr0btain.ticketman.ui.composables.scrollbar

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
expect fun VerticalScrollBar(
    scrollState: ScrollState,
    colors: ScrollBarColors = ScrollBarDefaults.scrollBarColors(),
    thickness: Dp = 6.dp,
    enabled: Boolean = true
)

@Composable
expect fun VerticalScrollBar(
    lazyListState: LazyListState,
    colors: ScrollBarColors = ScrollBarDefaults.scrollBarColors(),
    thickness: Dp = 6.dp,
    enabled: Boolean = true
)