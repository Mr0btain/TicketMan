package org.mr0btain.ticketman.ui.composables.scrollbar

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
actual fun VerticalScrollBar(
    scrollState: ScrollState,
    colors: ScrollBarColors,
    thickness: Dp,
    enabled: Boolean
) {
    VerticalScrollbar(
        modifier = Modifier.fillMaxHeight(),
        style = ScrollbarStyle(
            minimalHeight = 48.dp,
            thickness = thickness,
            unhoverColor = colors.thumbColor(enabled).value.copy(alpha = 0.7f),
            hoverColor = MaterialTheme.colorScheme.onSurface,
            shape = RoundedCornerShape(8.dp),
            hoverDurationMillis = 200
        ),
        adapter = rememberScrollbarAdapter(scrollState)
    )
}

@Composable
actual fun VerticalScrollBar(
    lazyListState: LazyListState,
    colors: ScrollBarColors,
    thickness: Dp,
    enabled: Boolean
) {
    VerticalScrollbar(
        modifier = Modifier.fillMaxHeight(),
        style = ScrollbarStyle(
            minimalHeight = 48.dp,
            thickness = thickness,
            unhoverColor = colors.thumbColor(enabled).value.copy(alpha = 0.7f),
            hoverColor = MaterialTheme.colorScheme.onSurface,
            shape = RoundedCornerShape(8.dp),
            hoverDurationMillis = 200
        ),
        adapter = rememberScrollbarAdapter(lazyListState)
    )
}