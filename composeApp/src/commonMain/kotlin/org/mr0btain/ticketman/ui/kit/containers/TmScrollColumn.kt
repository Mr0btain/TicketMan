package org.mr0btain.ticketman.ui.kit.containers

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.mr0btain.ticketman.ui.kit.foundation.TmVerticalScrollbar
import org.mr0btain.ticketman.ui.kit.foundation.tmShowsScrollbars
import org.mr0btain.ticketman.ui.kit.theme.TmTheme

/**
 * Scrolling column with the same thin scrollbar as [TmLazyColumn], for a few blocks of content that might not fit.
 * Give it a bounded height (a weight or fixed size) or it never scrolls
 */
@Composable
fun TmScrollColumn(
    modifier: Modifier = Modifier,
    state: ScrollState = rememberScrollState(),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(TmTheme.spacing.xxs),
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(state)
                .padding(end = if (tmShowsScrollbars) TmTheme.spacing.md else 0.dp),
            verticalArrangement = verticalArrangement,
            horizontalAlignment = horizontalAlignment,
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
