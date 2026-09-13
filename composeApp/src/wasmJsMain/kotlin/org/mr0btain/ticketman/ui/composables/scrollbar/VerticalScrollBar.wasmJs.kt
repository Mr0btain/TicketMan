package org.mr0btain.ticketman.ui.composables.scrollbar

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp

@Composable
actual fun VerticalScrollBar(
    scrollState: ScrollState,
    colors: ScrollBarColors,
    thickness: Dp,
    enabled: Boolean
) {
}

@Composable
actual fun VerticalScrollBar(
    lazyListState: LazyListState,
    colors: ScrollBarColors,
    thickness: Dp,
    enabled: Boolean
) {
}