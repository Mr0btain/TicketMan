package org.mr0btain.ticketman.ui.screens.counter.components

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mr0btain.ticketman.components.network.RemoteHandler
import org.mr0btain.ticketman.data.counter.TicketSummary
import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.data.search.SearchResult
import org.mr0btain.ticketman.data.ticket.Device
import org.mr0btain.ticketman.error.TmResult
import org.mr0btain.ticketman.error.getOrDefault
import org.mr0btain.ticketman.error.getOrNull
import org.mr0btain.ticketman.server.request.CheckInRequest
import org.mr0btain.ticketman.server.request.RepeatCheckRequest
import org.mr0btain.ticketman.server.response.CheckInResponse
import kotlin.uuid.Uuid

/** The check in wizard and the quick add customer screen */
class CounterCheckIn(
    private val remoteHandler: RemoteHandler,
    private val support: CounterSupport
) {
    private val scope get() = support.scope

    // Customer to start a check in with, when it's started from their card
    private val _customer = MutableStateFlow<Customer?>(null)
    val customer = _customer.asStateFlow()

    private val _last = MutableStateFlow<CheckInResponse?>(null)
    /** The last check in, for the done screen */
    val last = _last.asStateFlow()

    /** Opens the check in wizard, starting with [customer] if it came from their card */
    fun start(customer: Customer? = null) {
        if (!support.canEdit()) return

        _customer.value = customer
        support.show(CounterView.CHECK_IN)
    }

    /** Opens the quick add customer screen */
    fun startNewCustomer() {
        if (!support.canEdit()) return
        support.show(CounterView.NEW_CUSTOMER)
    }

    /** Customers for the wizard's lookup */
    suspend fun searchCustomers(text: String): List<Customer> =
        remoteHandler.counterRepo.search(text.trim(), CounterHandler.SEARCH_LIMIT).getOrNull()?.customers.orEmpty()

    /** Staff for picking a technician */
    suspend fun searchTechnicians(text: String): List<SearchResult> =
        remoteHandler.userRepo.search(text.trim(), CounterHandler.SEARCH_LIMIT).getOrDefault(emptyList())

    /** Earlier repairs of this device for the customer, for the repeat warning */
    suspend fun repeatCheck(customer: Uuid, device: Device, excludeTicket: Uuid): List<TicketSummary> =
        remoteHandler.counterRepo.repeatCheck(RepeatCheckRequest(customer, device, excludeTicket))

    /**
     * Adds a customer
     * @param onResult Gets the saved customer, or null and what went wrong
     */
    fun addCustomer(customer: Customer, onResult: (Customer?, String?) -> Unit) {
        if (!support.canEdit()) return

        scope.launch {
            when (val result = remoteHandler.customerRepo.add(customer)) {
                is TmResult.Success -> onResult(result.data, null)
                is TmResult.Error -> onResult(null, result.error.message)
            }
        }
    }

    /**
     * Sends a finished check in
     * @param onResult Gets null when it went through, otherwise what went wrong
     */
    fun submit(request: CheckInRequest, onResult: (String?) -> Unit) {
        if (!support.canEdit()) return

        scope.launch {
            when (val result = remoteHandler.counterRepo.checkIn(request)) {
                is TmResult.Success -> {
                    _last.value = result.data
                    _customer.value = null
                    support.show(CounterView.CHECKED_IN)
                    support.ticketsChanged()
                    onResult(null)
                }

                is TmResult.Error -> onResult(result.error.message)
            }
        }
    }

    fun clear() {
        _customer.value = null
        _last.value = null
    }
}
