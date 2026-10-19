package org.mr0btain.ticketman.ui.screens.counter.components

import androidx.compose.material3.SnackbarHostState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * What [CounterHandler]'s parts share
 * @param canEdit Whether the signed in user can change things, restricted users can only look
 * @param show Switches the counter's main area
 * @param ticketsChanged Run after a check in or pickup, so the queue reloads
 */
class CounterSupport(
    val scope: CoroutineScope,
    private val snackBarHost: SnackbarHostState,
    val canEdit: () -> Boolean,
    val show: (CounterView) -> Unit,
    val ticketsChanged: () -> Unit
) {
    // Snackbars suspend until dismissed, so show them on the side
    fun notify(message: String) {
        scope.launch { snackBarHost.showSnackbar(message) }
    }
}
