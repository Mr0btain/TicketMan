package org.mr0btain.ticketman.ui.screens.records.components.customer

import org.mr0btain.ticketman.error.asEmptyDataResult
import org.mr0btain.ticketman.error.getOrDefault
import org.mr0btain.ticketman.error.getOrNull
import org.mr0btain.ticketman.error.valueOr
import org.mr0btain.ticketman.components.network.isNotFound
import org.mr0btain.ticketman.components.network.orNullIfNotFound
import org.mr0btain.ticketman.components.network.ServerError
import androidx.compose.material3.SnackbarHostState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.mr0btain.ticketman.components.data.RecordSelection
import org.mr0btain.ticketman.components.data.customer.CustomerCache
import org.mr0btain.ticketman.components.data.customer.CustomerSeed
import org.mr0btain.ticketman.components.data.operations.ChangedField
import org.mr0btain.ticketman.components.network.RemoteHandler
import org.mr0btain.ticketman.components.pagination.offset.OffsetPager
import org.mr0btain.ticketman.components.pagination.offset.OffsetPagerConfig
import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.data.shared.EntityReference
import org.mr0btain.ticketman.data.ticket.Ticket
import org.mr0btain.ticketman.error.TmResult
import org.mr0btain.ticketman.ui.screens.records.components.syncNotes
import kotlin.uuid.Uuid

/**
 * Customers on the records screen: the list, the one being edited, and saving it
 * @param canEdit Whether the signed in user can change things, restricted users can only look
 * @param cacheSize How many opened customers to keep
 * @param searchLimit Most search results to ask for
 */
@Suppress("PrivatePropertyName")
class CustomerRecords(
    private val remoteHandler: RemoteHandler,
    private val scope: CoroutineScope,
    private val snackBarHost: SnackbarHostState,
    private val canEdit: () -> Boolean,
    pagerConfig: OffsetPagerConfig,
    cacheSize: Int,
    private val searchLimit: () -> Int
) {
    private val PAGER_TAG = "CustomerPager"

    val cache = CustomerCache(cacheSize)

    val pager = OffsetPager<Customer>(
        tag = PAGER_TAG,
        config = pagerConfig,
        fetch = { page, pageSize ->
            remoteHandler.customerRepo.fetch(
                offset = (page * pageSize).toLong(),
                limit = pageSize
            )
        }
    )

    val seed = CustomerSeed()

    /** Customer being loaded after a click, the list highlights it until it's in the seed */
    val selection = RecordSelection(scope)

    /** Opens a customer and scrolls the list to them, for one picked from outside the list */
    fun open(identifier: Uuid) {
        select(identifier)
        selection.reveal(identifier)
    }

    /** Where a customer is in the list, null if they aren't in it or that can't be found out */
    suspend fun listPosition(identifier: Uuid): Long? =
        remoteHandler.customerRepo.position(identifier).getOrNull()?.index

    /** Opens a customer in the seed, from the cache if it's there. [quiet] says nothing when it can't be loaded */
    fun select(identifier: Uuid, quiet: Boolean = false) {
        if (selection.pending.value == identifier) return

        selection.select(identifier) {
            // Notes and tickets that failed to load aren't shown as none, saving would then drop them
            val loaded = cache.get(identifier)?.let { TmResult.Success(it) } ?: coroutineScope {
                val notes = async { remoteHandler.customerRepo.getNotes(identifier) }
                val tickets = async { remoteHandler.customerRepo.getTickets(identifier) }
                val customer = remoteHandler.customerRepo.get(identifier).valueOr { return@coroutineScope TmResult.Error(it) }

                TmResult.Success(
                    CustomerCache.CustomerData(
                        customer,
                        notes.await().valueOr { return@coroutineScope TmResult.Error(it) },
                        tickets.await().valueOr { return@coroutineScope TmResult.Error(it) }
                    ).also { cache.put(it.customer, it.notes, it.tickets) }
                )
            }

            val (customer, notes, tickets) = loaded.valueOr { return@select loadFailed(identifier, it, quiet) }

            return@select { seed.setCustomer(customer, notes, tickets) }
        }
    }

    /**
     * Loads the open customer again from the server, for the periodic check for updates. Leaves a new or changed one
     * alone and says nothing if the server can't be reached; one deleted on another device is still dropped
     */
    fun reloadSelected() {
        if (seed.isNew || seed.isDirty) return

        cache.remove(seed.identifier)
        select(seed.identifier, quiet = true)
    }

    /** Empties the seed for a new customer, dropping any customer still loading */
    fun clearSelection() {
        selection.cancel()
        seed.clear()
    }

    // Loading the open customer found nothing, like reloading it after Cancel when another device deleted it. Stop
    // showing a copy that can't be saved, and reload the list without it
    private fun forgetIfOpen(identifier: Uuid): (() -> Unit)? =
        if (seed.identifier == identifier && !seed.isNew) ({
            seed.clear()
            cache.remove(identifier)
            scope.launch {
                pager.refreshCurrentData()
                snackBarHost.showSnackbar("That customer couldn't be loaded, it may have been deleted")
            }
        }) else null

    // Loading failed: a record that's gone is forgotten, anything else is said and what's shown stays
    private fun loadFailed(identifier: Uuid, error: ServerError, quiet: Boolean = false): (() -> Unit)? =
        if (error.isNotFound) forgetIfOpen(identifier)
        else if (quiet) null
        else null.also { scope.launch { snackBarHost.showSnackbar("That customer couldn't be loaded: ${error.message}") } }

    /** Saves the seed: the customer, its notes, and any tickets linked to it */
    fun save() {
        if (!canEdit()) return

        // If we haven't made any changes, then just return
        if (!seed.isDirty) return

        val customer = seed.customer
        val notes = seed.notes.toList()
        val loadedNotes = seed.savedNotes
        val tickets = seed.tickets.toList()
        val changed = seed.getChangedFields().toSet()
        val isNew = seed.isNew

        scope.launch {
            val repo = remoteHandler.customerRepo

            val saved = if (isNew) repo.add(customer).asEmptyDataResult() else {
                // Notes and tickets are saved on their own, only send the customer if something else changed
                if ((changed - setOf(ChangedField.NOTE, ChangedField.TICKET)).isNotEmpty()) repo.update(customer)
                else TmResult.Success(Unit)
            }

            if (saved is TmResult.Error) {
                snackBarHost.showSnackbar("Customer not saved: ${saved.error.message}")
                return@launch
            }

            if (isNew || ChangedField.NOTE in changed) syncNotes(customer.identifier, notes, loadedNotes, repo, snackBarHost)

            // Tickets can be linked here, unlinking happens by giving the ticket to another customer
            if (isNew || ChangedField.TICKET in changed) {
                val linked = repo.getTickets(customer.identifier).getOrNull().orEmpty().map { it.identifier }.toSet()
                val failed = tickets
                    .filter { it.identifier !in linked }
                    .count { ticket -> repo.addTicket(customer.identifier, ticket.identifier) is TmResult.Error }

                if (failed > 0) snackBarHost.showSnackbar("$failed ${if (failed == 1) "ticket wasn't" else "tickets weren't"} linked")
            }

            cache.remove(customer.identifier)
            select(customer.identifier)
            pager.refreshCurrentData()
        }
    }

    /**
     * Adds a customer made somewhere other than the customer dialog, like the ticket dialog's New customer, leaving
     * what's open alone
     * @param onResult Gets the saved customer, or what went wrong
     */
    fun addCustomer(customer: Customer, onResult: (Customer?, String?) -> Unit) {
        if (!canEdit()) {
            onResult(null, "You can only look, not change things")
            return
        }

        scope.launch {
            when (val result = remoteHandler.customerRepo.add(customer)) {
                is TmResult.Success -> {
                    onResult(result.data, null)
                    pager.refreshCurrentData()
                }
                is TmResult.Error -> onResult(null, result.error.message)
            }
        }
    }

    /** A customer from the cache, or the server if it isn't cached. Null if neither has it */
    suspend fun get(identifier: Uuid): Customer? =
        cache.get(identifier)?.customer
            ?: remoteHandler.customerRepo.get(identifier).getOrNull()

    /**
     * A ticket picked in the customer dialog to link to this customer, with the customer it's on now, so the dialog
     * can say linking moves it
     */
    suspend fun ticketToLink(identifier: Uuid): TmResult<TicketToLink, ServerError> = coroutineScope {
        val tickets = remoteHandler.ticketRepo
        val ticket = async { tickets.get(identifier) }
        // A ticket with no customer answers 404
        val current = async { tickets.getCustomer(identifier).orNullIfNotFound() }

        TmResult.Success(
            TicketToLink(
                ticket = ticket.await().valueOr { return@coroutineScope TmResult.Error(it) },
                currentCustomer = current.await().valueOr { return@coroutineScope TmResult.Error(it) }
            )
        )
    }

    /** Customers matching [query], at most the search limit setting. Nothing when the search fails */
    suspend fun search(query: String) =
        remoteHandler.customerRepo.search(query, searchLimit()).getOrDefault(emptyList())

    /**
     * Deletes the record if it's the one selected. Once the server has, clears the selection and reloads the list;
     * if it refuses, says why and keeps it open. Does nothing for people who can only look
     */
    fun remove(identifier: Uuid) {
        if (!canEdit()) return

        scope.launch {
            if (seed.identifier != identifier) return@launch

            // The selection stays until the server agrees, so a refused delete doesn't look like it worked
            when (val removed = remoteHandler.customerRepo.remove(identifier)) {
                is TmResult.Success -> {
                    clearSelection()
                    cache.remove(identifier)
                    pager.refreshCurrentData()
                }

                is TmResult.Error -> snackBarHost.showSnackbar("Customer not deleted: ${removed.error.message}")
            }
        }
    }

    /** Drops everything loaded, for when whoever loaded it is gone */
    fun clear() {
        clearSelection()
        pager.clear()
    }
}

/**
 * A ticket to link from the customer dialog
 * @param currentCustomer The customer it's on now, null when it has none
 */
data class TicketToLink(
    val ticket: Ticket,
    val currentCustomer: EntityReference?
)
