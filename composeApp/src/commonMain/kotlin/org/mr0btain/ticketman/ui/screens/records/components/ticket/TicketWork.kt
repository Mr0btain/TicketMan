package org.mr0btain.ticketman.ui.screens.records.components.ticket

import androidx.compose.material3.SnackbarHostState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mr0btain.ticketman.components.network.RemoteHandler
import org.mr0btain.ticketman.data.ticket.WorkEntry
import org.mr0btain.ticketman.error.TmResult
import org.mr0btain.ticketman.server.request.WorkRequest
import kotlin.uuid.Uuid

/**
 * The open ticket's work log: loading it, logging work and taking it off. Changes go to the server straight away,
 * like staff notes, not when the ticket is saved, since each one changes the invoice
 * @param canEdit Whether the signed in user can change things, restricted users can only look
 */
class TicketWork(
    private val remoteHandler: RemoteHandler,
    private val scope: CoroutineScope,
    private val snackBarHost: SnackbarHostState,
    private val canEdit: () -> Boolean
) {
    private val _entries = MutableStateFlow<List<WorkEntry>?>(null)
    /** Oldest first. Null while loading, or when it couldn't be loaded, see [loadError] */
    val entries = _entries.asStateFlow()

    private val _loadError = MutableStateFlow<String?>(null)
    val loadError = _loadError.asStateFlow()

    // The ticket shown, so a slow answer for one picked before it is dropped
    private var ticket: Uuid? = null
    private var loading: Job? = null

    /**
     * Loads [ticketIdentifier]'s work, keeping what's shown while it reloads the same ticket
     * @param quiet For the periodic check: a failure keeps what's shown and says nothing
     */
    fun load(ticketIdentifier: Uuid, quiet: Boolean = false) {
        if (ticket != ticketIdentifier) {
            ticket = ticketIdentifier
            _entries.value = null
            _loadError.value = null
        }

        loading?.cancel()
        loading = scope.launch {
            when (val result = remoteHandler.ticketRepo.getWork(ticketIdentifier)) {
                is TmResult.Success -> if (ticket == ticketIdentifier) {
                    _entries.value = result.data
                    _loadError.value = null
                }
                is TmResult.Error -> if (ticket == ticketIdentifier && !quiet) _loadError.value = result.error.message
            }
        }
    }

    /**
     * Logs work on the open ticket, like the time and the parts for one repair: all of it, or none
     * @param onResult Gets null when it's saved, otherwise what went wrong
     */
    fun log(requests: List<WorkRequest>, onResult: (String?) -> Unit) {
        val ticketIdentifier = ticket ?: return
        if (!canEdit()) {
            onResult("You can only look, not change things")
            return
        }

        scope.launch {
            when (val result = remoteHandler.ticketRepo.addWork(ticketIdentifier, requests)) {
                is TmResult.Success -> {
                    if (ticket == ticketIdentifier) _entries.value = _entries.value.orEmpty() + result.data
                    onResult(null)
                }
                is TmResult.Error -> onResult(result.error.message)
            }
        }
    }

    /** Takes an entry off, and its invoice line */
    fun remove(entry: WorkEntry) {
        if (!canEdit()) return

        scope.launch {
            when (val result = remoteHandler.ticketRepo.removeWork(entry.identifier)) {
                is TmResult.Success -> _entries.value = _entries.value?.filter { it.identifier != entry.identifier }
                is TmResult.Error -> snackBarHost.showSnackbar("Work not removed: ${result.error.message}")
            }
        }
    }

    /** No ticket open */
    fun clear() {
        loading?.cancel()
        ticket = null
        _entries.value = null
        _loadError.value = null
    }
}
