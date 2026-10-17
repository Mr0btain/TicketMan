package org.mr0btain.ticketman.ui.kit.foundation

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.mr0btain.ticketman.ui.kit.theme.TmTheme

@Composable
private fun tmScrollbarStyle(): ScrollbarStyle = ScrollbarStyle(
    minimalHeight = 40.dp,
    thickness = 6.dp,
    shape = TmTheme.shapes.pill,
    hoverDurationMillis = 200,
    unhoverColor = TmTheme.colors.textFaint.copy(alpha = 0.5f),
    hoverColor = TmTheme.colors.textMuted
)

@Composable
actual fun TmVerticalScrollbar(state: LazyListState, modifier: Modifier) {
    VerticalScrollbar(modifier = modifier, adapter = rememberScrollbarAdapter(state), style = tmScrollbarStyle())
}

@Composable
actual fun TmVerticalScrollbar(state: ScrollState, modifier: Modifier) {
    VerticalScrollbar(modifier = modifier, adapter = rememberScrollbarAdapter(state), style = tmScrollbarStyle())
}

actual val tmShowsScrollbars: Boolean = true
