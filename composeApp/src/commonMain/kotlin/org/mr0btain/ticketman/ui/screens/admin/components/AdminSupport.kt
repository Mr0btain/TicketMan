package org.mr0btain.ticketman.ui.screens.admin.components

import androidx.compose.material3.SnackbarHostState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mr0btain.ticketman.components.network.ServerError
import org.mr0btain.ticketman.error.EmptyResult
import org.mr0btain.ticketman.error.TmResult

/**
 * What [AdminHandler]'s parts share: the scope they run in, the loading flag, and saying how a change went
 * @param reloadAudit Run after every change, so it shows in the audit log straight away
 */
class AdminSupport(
    val scope: CoroutineScope,
    private val snackBarHost: SnackbarHostState,
    private val reloadAudit: suspend () -> Unit
) {
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    /** Runs [block] with [isLoading] on */
    suspend fun <T> loading(block: suspend () -> T): T {
        _isLoading.value = true
        try {
            return block()
        } finally {
            _isLoading.value = false
        }
    }

    // Snackbars suspend until dismissed, so show them on the side
    fun notify(message: String) {
        scope.launch { snackBarHost.showSnackbar(message) }
    }

    /** For changes that don't go through [report] */
    suspend fun auditChanged() = reloadAudit()

    /** Shows how a request went and runs [onSuccess] if it worked */
    suspend fun report(result: EmptyResult<ServerError>, successMessage: String, onSuccess: suspend () -> Unit) {
        when (result) {
            is TmResult.Success -> {
                onSuccess()
                notify(successMessage)
            }

            is TmResult.Error -> notify(result.error.message)
        }

        reloadAudit()
    }
}
