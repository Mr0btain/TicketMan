package org.mr0btain.ticketman.ui.screens.records.components.ticket

import androidx.compose.material3.SnackbarHostState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mr0btain.ticketman.components.data.RecordSelection
import org.mr0btain.ticketman.components.data.local.LocalStorage
import org.mr0btain.ticketman.components.data.operations.ChangedField
import org.mr0btain.ticketman.components.data.ticket.TicketCache
import org.mr0btain.ticketman.components.data.ticket.TicketScope
import org.mr0btain.ticketman.components.data.ticket.TicketSeed
import org.mr0btain.ticketman.components.network.RemoteHandler
import org.mr0btain.ticketman.components.pagination.offset.OffsetPager
import org.mr0btain.ticketman.components.pagination.offset.OffsetPagerConfig
import org.mr0btain.ticketman.data.ticket.Ticket
import org.mr0btain.ticketman.error.Result
import org.mr0btain.ticketman.ui.screens.records.components.syncNotes
import kotlin.uuid.Uuid

/**
 * Tickets on the records screen: the list (yours or everyone's), the one being edited, and saving it
 * @param canEdit Whether the signed in user can change things, restricted users can only look
 * @param cacheSize How many opened tickets to keep
 * @param searchLimit Most search results to ask for
 */
@Suppress("PrivatePropertyName")
class TicketRecords(
    private val remoteHandler: RemoteHandler,
    private val localStorage: LocalStorage,
    private val scope: CoroutineScope,
    private val snackBarHost: SnackbarHostState,
    private val canEdit: () -> Boolean,
    pagerConfig: OffsetPagerConfig,
    cacheSize: Int,
    private val searchLimit: () -> Int
) {
    private val PAGER_TAG = "TicketPager"

    val cache = TicketCache(cacheSize)

    private val _scope = MutableStateFlow(TicketScope.DEFAULT)
    /** Whether the list shows the user's tickets or everyone's */
    val listScope = _scope.asStateFlow()

    val pager = OffsetPager<Ticket>(
        tag = PAGER_TAG,
        config = pagerConfig,
        fetch = { page, pageSize ->
            val offset = (page * pageSize).toLong()
            when (_scope.value) {
                TicketScope.MINE -> remoteHandler.ticketRepo.fetchMine(offset = offset, limit = pageSize)
                TicketScope.ALL -> remoteHandler.ticketRepo.fetch(offset = offset, limit = pageSize)
            }
        }
    )

    val seed = TicketSeed()

    /** Ticket being loaded after a click, the list highlights it until it's in the seed */
    val selection = RecordSelection(scope)

    /** Pulls the saved list scope, called once on start */
    fun load() {
        _scope.value = TicketScope.byName(localStorage.ticketScope)
    }

    /** Switches the list between the user's tickets and everyone's, and reloads it */
    fun setListScope(listScope: TicketScope) {
        if (_scope.value == listScope) return

        _scope.value = listScope
        localStorage.ticketScope = listScope.name
        scope.launch { pager.refresh() }
    }

    /** Opens a ticket in the seed, from the cache if it's there */
    fun select(identifier: Uuid) {
        if (selection.pending.value == identifier) return

        selection.select(identifier) {
            val (ticket, notes, customer, user) = cache.get(identifier) ?: coroutineScope {
                val notes = async { remoteHandler.ticketRepo.getNotes(identifier) }
                val customer = async { remoteHandler.ticketRepo.getCustomer(identifier) }
                val user = async { remoteHandler.ticketRepo.getUser(identifier) }
                val ticket = remoteHandler.ticketRepo.get(identifier) ?: return@coroutineScope null

                TicketCache.TicketData(ticket, notes.await(), customer.await(), user.await())
                    .also { cache.put(it.ticket, it.notes, it.customer, it.user) }
            } ?: return@select null

            return@select { seed.setTicket(ticket, notes, customer, user) }
        }
    }

    /** Empties the seed for a new ticket, dropping any ticket still loading */
    fun clearSelection() {
        selection.cancel()
        seed.clear()
    }

    /** Saves the seed: the ticket, its notes, and who it belongs to and is assigned to */
    fun save() {
        if (!canEdit()) return

        // If no changes, then return
        if (!seed.isDirty) return

        val ticket = seed.ticket
        val notes = seed.notes.toList()
        val customer = seed.customer
        val user = seed.user
        val changed = seed.getChangedFields().toSet()
        val isNew = seed.isNew

        scope.launch {
            val repo = remoteHandler.ticketRepo

            if (isNew) {
                // Created through create so the seed picks up the number the server gave it
                when (val created = repo.create(ticket)) {
                    is Result.Error -> {
                        snackBarHost.showSnackbar("Ticket not saved: ${created.error.message}")
                        return@launch
                    }

                    is Result.Success -> seed.setTicket(created.data, notes, customer, user)
                }
            } else {
                val linkFields = setOf(ChangedField.NOTE, ChangedField.CUSTOMER, ChangedField.USER)

                if ((changed - linkFields).isNotEmpty()) {
                    val updated = repo.update(ticket)

                    if (updated is Result.Error) {
                        snackBarHost.showSnackbar("Ticket not saved: ${updated.error.message}")
                        return@launch
                    }
                }
            }

            if (isNew || ChangedField.NOTE in changed) syncNotes(ticket.identifier, notes, repo, snackBarHost)

            if (customer != null && (isNew || ChangedField.CUSTOMER in changed)) {
                val linked = repo.setCustomer(ticket.identifier, customer.identifier)
                if (linked is Result.Error) snackBarHost.showSnackbar("Customer not linked: ${linked.error.message}")
            }

            if (user != null && (isNew || ChangedField.USER in changed)) {
                val assigned = repo.setUser(ticket.identifier, user.identifier)
                if (assigned is Result.Error) snackBarHost.showSnackbar("Technician not assigned: ${assigned.error.message}")
            }

            cache.remove(ticket.identifier)
            select(ticket.identifier)
            pager.refreshCurrentData()
        }
    }

    suspend fun search(query: String) =
        remoteHandler.ticketRepo.search(query, searchLimit())

    fun remove(identifier: Uuid) {
        if (!canEdit()) return

        scope.launch {
            if (seed.identifier == identifier) {
                clearSelection()
                remoteHandler.ticketRepo.remove(identifier)
                pager.refreshCurrentData()
            }
        }
    }

    /** Drops everything loaded, for when whoever loaded it is gone */
    fun clear() {
        clearSelection()
        pager.clear()
    }
}
