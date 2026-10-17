package org.mr0btain.ticketman.ui.kit.containers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.mr0btain.ticketman.ui.kit.foundation.TmVerticalScrollbar
import org.mr0btain.ticketman.ui.kit.foundation.tmShowsScrollbars
import org.mr0btain.ticketman.ui.kit.theme.TmTheme

/**
 * Lazy list with a thin scrollbar floating on its right edge on desktop
 * @param contentPadding Padding around the items, room for the scrollbar is added on the right
 */
@Composable
fun TmLazyColumn(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(TmTheme.spacing.xxs),
    content: LazyListScope.() -> Unit
) {
    Box(modifier) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(end = if (tmShowsScrollbars) TmTheme.spacing.md else 0.dp),
            state = state,
            contentPadding = contentPadding,
            verticalArrangement = verticalArrangement,
            content = content
        )

        TmVerticalScrollbar(
            state = state,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .padding(vertical = TmTheme.spacing.xs)
        )
    }
}
