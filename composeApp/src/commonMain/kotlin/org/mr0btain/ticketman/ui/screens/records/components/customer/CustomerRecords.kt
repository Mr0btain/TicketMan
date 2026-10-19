package org.mr0btain.ticketman.ui.screens.records.components.customer

import org.mr0btain.ticketman.error.asEmptyDataResult
import org.mr0btain.ticketman.error.getOrDefault
import org.mr0btain.ticketman.error.getOrNull
import org.mr0btain.ticketman.error.valueOr
import org.mr0btain.ticketman.components.network.isNotFound
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
import org.mr0btain.ticketman.error.Result
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

    /** Opens a customer in the seed, from the cache if it's there */
    fun select(identifier: Uuid) {
        if (selection.pending.value == identifier) return

        selection.select(identifier) {
            // Notes and tickets that failed to load aren't shown as none, saving would then drop them
            val loaded = cache.get(identifier)?.let { Result.Success(it) } ?: coroutineScope {
                val notes = async { remoteHandler.customerRepo.getNotes(identifier) }
                val tickets = async { remoteHandler.customerRepo.getTickets(identifier) }
                val customer = remoteHandler.customerRepo.get(identifier).valueOr { return@coroutineScope Result.Error(it) }

                Result.Success(
                    CustomerCache.CustomerData(
                        customer,
                        notes.await().valueOr { return@coroutineScope Result.Error(it) },
                        tickets.await().valueOr { return@coroutineScope Result.Error(it) }
                    ).also { cache.put(it.customer, it.notes, it.tickets) }
                )
            }

            val (customer, notes, tickets) = loaded.valueOr { return@select loadFailed(identifier, it) }

            return@select { seed.setCustomer(customer, notes, tickets) }
        }
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
    private fun loadFailed(identifier: Uuid, error: ServerError): (() -> Unit)? =
        if (error.isNotFound) forgetIfOpen(identifier)
        else null.also { scope.launch { snackBarHost.showSnackbar("That customer couldn't be loaded: ${error.message}") } }

    /** Saves the seed: the customer, its notes, and any tickets linked to it */
    fun save() {
        if (!canEdit()) return

        // If we haven't made any changes, then just return
        if (!seed.isDirty) return

        val customer = seed.customer
        val notes = seed.notes.toList()
        val tickets = seed.tickets.toList()
        val changed = seed.getChangedFields().toSet()
        val isNew = seed.isNew

        scope.launch {
            val repo = remoteHandler.customerRepo

            val saved = if (isNew) repo.add(customer).asEmptyDataResult() else {
                // Notes and tickets are saved on their own, only send the customer if something else changed
                if ((changed - setOf(ChangedField.NOTE, ChangedField.TICKET)).isNotEmpty()) repo.update(customer)
                else Result.Success(Unit)
            }

            if (saved is Result.Error) {
                snackBarHost.showSnackbar("Customer not saved: ${saved.error.message}")
                return@launch
            }

            if (isNew || ChangedField.NOTE in changed) syncNotes(customer.identifier, notes, repo, snackBarHost)

            // Tickets can be linked here, unlinking happens by giving the ticket to another customer
            if (isNew || ChangedField.TICKET in changed) {
                val linked = repo.getTickets(customer.identifier).getOrNull().orEmpty().map { it.identifier }.toSet()
                val failed = tickets
                    .filter { it.identifier !in linked }
                    .count { ticket -> repo.addTicket(customer.identifier, ticket.identifier) is Result.Error }

                if (failed > 0) snackBarHost.showSnackbar("$failed ${if (failed == 1) "ticket wasn't" else "tickets weren't"} linked")
            }

            cache.remove(customer.identifier)
            select(customer.identifier)
            pager.refreshCurrentData()
        }
    }

    /** A customer from the cache, or the server if it isn't cached. Null if neither has it */
    suspend fun get(identifier: Uuid): Customer? =
        cache.get(identifier)?.customer
            ?: remoteHandler.customerRepo.get(identifier).getOrNull()

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
                is Result.Success -> {
                    clearSelection()
                    cache.remove(identifier)
                    pager.refreshCurrentData()
                }

                is Result.Error -> snackBarHost.showSnackbar("Customer not deleted: ${removed.error.message}")
            }
        }
    }

    /** Drops everything loaded, for when whoever loaded it is gone */
    fun clear() {
        clearSelection()
        pager.clear()
    }
}
