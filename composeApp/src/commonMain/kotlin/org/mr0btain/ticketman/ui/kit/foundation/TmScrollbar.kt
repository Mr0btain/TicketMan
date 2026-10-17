package org.mr0btain.ticketman.ui.kit.foundation

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Thin scrollbar for a lazy list. Only drawn on desktop, touch screens scroll without one */
@Composable
expect fun TmVerticalScrollbar(state: LazyListState, modifier: Modifier = Modifier)

/** Thin scrollbar for a scrolling column. Only drawn on desktop, touch screens scroll without one */
@Composable
expect fun TmVerticalScrollbar(state: ScrollState, modifier: Modifier = Modifier)

/** Whether [TmVerticalScrollbar] draws anything here, lists leave room for it when it does */
expect val tmShowsScrollbars: Boolean
