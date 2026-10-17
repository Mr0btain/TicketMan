package org.mr0btain.ticketman.ui.screens.main

import androidx.compose.runtime.Composable
import org.mr0btain.ticketman.ui.screens.main.components.MainViewModel
import org.mr0btain.ticketman.ui.screens.main.expanded.ExpandedMainScreen

/**
 * Customers, tickets and inventory. The same layout on every screen size, it stacks its panes when narrow
 */
@Composable
fun MainScreen(viewModel: MainViewModel) {
    ExpandedMainScreen(viewModel = viewModel)
}
