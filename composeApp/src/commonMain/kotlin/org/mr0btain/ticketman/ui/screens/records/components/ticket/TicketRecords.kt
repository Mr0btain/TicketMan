package org.mr0btain.ticketman.ui.screens.records.components.ticket

import org.mr0btain.ticketman.error.getOrDefault
import org.mr0btain.ticketman.error.valueOr
import org.mr0btain.ticketman.components.network.orNullIfNotFound
import org.mr0btain.ticketman.components.network.isNotFound
import org.mr0btain.ticketman.components.network.ServerError
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
import org.mr0btain.ticketman.error.TmResult
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

    /** The open ticket's work log, loaded with it */
    val work = TicketWork(remoteHandler, scope, snackBarHost, canEdit)

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

    /** Opens a ticket in the seed, from the cache if it's there. [quiet] says nothing when it can't be loaded */
    fun select(identifier: Uuid, quiet: Boolean = false) {
        if (selection.pending.value == identifier) return

        selection.select(identifier) {
            // Notes that failed to load aren't shown as none, saving would then drop them. No customer or tech is fine
            val loaded = cache.get(identifier)?.let { TmResult.Success(it) } ?: coroutineScope {
                val notes = async { remoteHandler.ticketRepo.getNotes(identifier) }
                val customer = async { remoteHandler.ticketRepo.getCustomer(identifier).orNullIfNotFound() }
                val user = async { remoteHandler.ticketRepo.getUser(identifier).orNullIfNotFound() }
                val ticket = remoteHandler.ticketRepo.get(identifier).valueOr { return@coroutineScope TmResult.Error(it) }

                TmResult.Success(
                    TicketCache.TicketData(
                        ticket,
                        notes.await().valueOr { return@coroutineScope TmResult.Error(it) },
                        customer.await().valueOr { return@coroutineScope TmResult.Error(it) },
                        user.await().valueOr { return@coroutineScope TmResult.Error(it) }
                    ).also { cache.put(it.ticket, it.notes, it.customer, it.user) }
                )
            }

            val (ticket, notes, customer, user) = loaded.valueOr { return@select loadFailed(identifier, it, quiet) }

            return@select {
                seed.setTicket(ticket, notes, customer, user)
                work.load(ticket.identifier, quiet)
            }
        }
    }

    /**
     * Loads the open ticket again from the server, for the periodic check for updates. Leaves a new or changed one
     * alone and says nothing if the server can't be reached; one deleted on another device is still dropped
     */
    fun reloadSelected() {
        if (seed.isNew || seed.isDirty) return

        cache.remove(seed.identifier)
        select(seed.identifier, quiet = true)
    }

    /** Empties the seed for a new ticket, dropping any ticket still loading */
    fun clearSelection() {
        selection.cancel()
        seed.clear()
        work.clear()
    }

    // Loading the open ticket found nothing, like reloading it after Cancel when another device deleted it. Stop
    // showing a copy that can't be saved, and reload the list without it
    private fun forgetIfOpen(identifier: Uuid): (() -> Unit)? =
        if (seed.identifier == identifier && !seed.isNew) ({
            seed.clear()
            work.clear()
            cache.remove(identifier)
            scope.launch {
                pager.refreshCurrentData()
                snackBarHost.showSnackbar("That ticket couldn't be loaded, it may have been deleted")
            }
        }) else null

    // Loading failed: a record that's gone is forgotten, anything else is said and what's shown stays
    private fun loadFailed(identifier: Uuid, error: ServerError, quiet: Boolean = false): (() -> Unit)? =
        if (error.isNotFound) forgetIfOpen(identifier)
        else if (quiet) null
        else null.also { scope.launch { snackBarHost.showSnackbar("That ticket couldn't be loaded: ${error.message}") } }

    /** Saves the seed: the ticket, its notes, and who it belongs to and is assigned to */
    fun save() {
        if (!canEdit()) return

        // If no changes, then return
        if (!seed.isDirty) return

        val ticket = seed.ticket
        val notes = seed.notes.toList()
        val loadedNotes = seed.savedNotes
        val customer = seed.customer
        val user = seed.user
        val changed = seed.getChangedFields().toSet()
        val isNew = seed.isNew

        scope.launch {
            val repo = remoteHandler.ticketRepo

            if (isNew) {
                // Add answers with the saved ticket, so the seed picks up the number the server gave it
                when (val created = repo.add(ticket)) {
                    is TmResult.Error -> {
                        snackBarHost.showSnackbar("Ticket not saved: ${created.error.message}")
                        return@launch
                    }

                    is TmResult.Success -> seed.setTicket(created.data, notes, customer, user)
                }
            } else {
                val linkFields = setOf(ChangedField.NOTE, ChangedField.CUSTOMER, ChangedField.USER)

                if ((changed - linkFields).isNotEmpty()) {
                    val updated = repo.update(ticket)

                    if (updated is TmResult.Error) {
                        snackBarHost.showSnackbar("Ticket not saved: ${updated.error.message}")
                        return@launch
                    }
                }
            }

            if (isNew || ChangedField.NOTE in changed) syncNotes(ticket.identifier, notes, loadedNotes, repo, snackBarHost)

            if (customer != null && (isNew || ChangedField.CUSTOMER in changed)) {
                val linked = repo.setCustomer(ticket.identifier, customer.identifier)
                if (linked is TmResult.Error) snackBarHost.showSnackbar("Customer not linked: ${linked.error.message}")
            }

            if (user != null && (isNew || ChangedField.USER in changed)) {
                val assigned = repo.setUser(ticket.identifier, user.identifier)
                if (assigned is TmResult.Error) snackBarHost.showSnackbar("Technician not assigned: ${assigned.error.message}")
            }

            cache.remove(ticket.identifier)
            select(ticket.identifier)
            pager.refreshCurrentData()
        }
    }

    /** Tickets matching [query], at most the search limit setting */
    suspend fun search(query: String) =
        remoteHandler.ticketRepo.search(query, searchLimit()).getOrDefault(emptyList())

    /**
     * Deletes the record if it's the one selected. Once the server has, clears the selection and reloads the list;
     * if it refuses, says why and keeps it open. Does nothing for people who can only look
     */
    fun remove(identifier: Uuid) {
        if (!canEdit()) return

        scope.launch {
            if (seed.identifier != identifier) return@launch

            // The selection stays until the server agrees, so a refused delete doesn't look like it worked
            when (val removed = remoteHandler.ticketRepo.remove(identifier)) {
                is TmResult.Success -> {
                    clearSelection()
                    cache.remove(identifier)
                    pager.refreshCurrentData()
                }

                is TmResult.Error -> snackBarHost.showSnackbar("Ticket not deleted: ${removed.error.message}")
            }
        }
    }

    /** Drops everything loaded, for when whoever loaded it is gone */
    fun clear() {
        clearSelection()
        pager.clear()
    }
}
