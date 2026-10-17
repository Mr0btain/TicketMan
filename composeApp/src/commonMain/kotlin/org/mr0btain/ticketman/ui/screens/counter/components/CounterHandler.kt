package org.mr0btain.ticketman.ui.screens.counter.components

import androidx.compose.material3.SnackbarHostState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mr0btain.ticketman.components.network.RemoteHandler
import org.mr0btain.ticketman.data.counter.TicketSummary
import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.data.inventory.Inventory
import org.mr0btain.ticketman.data.invoice.Invoice
import org.mr0btain.ticketman.data.search.SearchResult
import org.mr0btain.ticketman.data.shared.EntityReference
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.data.shop.ShopProfile
import org.mr0btain.ticketman.data.ticket.Device
import org.mr0btain.ticketman.data.ticket.Ticket
import org.mr0btain.ticketman.data.ticket.TicketStatus
import org.mr0btain.ticketman.data.ticket.TicketSignature
import org.mr0btain.ticketman.data.ticket.TicketStatusChange
import org.mr0btain.ticketman.data.ticket.TicketStatusType
import org.mr0btain.ticketman.error.Result
import org.mr0btain.ticketman.server.request.CheckInRequest
import org.mr0btain.ticketman.server.request.RepeatCheckRequest
import org.mr0btain.ticketman.server.response.CheckInResponse
import org.mr0btain.ticketman.server.response.CounterQueueResponse
import org.mr0btain.ticketman.server.response.CounterSearchResponse
import kotlin.uuid.Uuid

/** What the counter's main area is showing */
enum class CounterView {
    HOME,
    TICKET,
    CUSTOMER,
    PRICE_CHECK,
    CHECK_IN,
    CHECKED_IN,
    NEW_CUSTOMER
}

/** Everything the ticket card shows */
data class TicketDetail(
    val ticket: Ticket,
    val customer: Customer?,
    val technician: EntityReference?,
    val notes: List<Note>,
    val history: List<TicketStatusChange>,
    val invoice: Invoice?,
    val signature: TicketSignature?
)

data class CustomerDetail(
    val customer: Customer,
    val tickets: List<Ticket>
)

/**
 * State and actions for the shop counter. Owned by the main view model
 * @param canEdit Whether the signed in user can change things, restricted users can only look
 */
@Suppress("PrivatePropertyName")
class CounterHandler(
    private val remoteHandler: RemoteHandler,
    private val scope: CoroutineScope,
    private val snackBarHost: SnackbarHostState,
    private val canEdit: () -> Boolean
) {
    private val TAG = "CounterHandler"

    private val _view = MutableStateFlow(CounterView.HOME)
    val view = _view.asStateFlow()

    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()

    private val _results = MutableStateFlow(CounterSearchResponse())
    val results = _results.asStateFlow()

    private val _searching = MutableStateFlow(false)
    val searching = _searching.asStateFlow()

    private val _queue = MutableStateFlow<CounterQueueResponse?>(null)
    val queue = _queue.asStateFlow()

    private val _ticket = MutableStateFlow<TicketDetail?>(null)
    val ticket = _ticket.asStateFlow()

    private val _customer = MutableStateFlow<CustomerDetail?>(null)
    val customer = _customer.asStateFlow()

    private val _priceResults = MutableStateFlow<List<Inventory>>(emptyList())
    val priceResults = _priceResults.asStateFlow()

    private val _shop = MutableStateFlow(ShopProfile())
    /** Terms customers sign at check in, loaded with the queue */
    val shop = _shop.asStateFlow()

    // Customer to start a check in with, when it's started from their card
    private val _checkInCustomer = MutableStateFlow<Customer?>(null)
    val checkInCustomer = _checkInCustomer.asStateFlow()

    private val _lastCheckIn = MutableStateFlow<CheckInResponse?>(null)
    val lastCheckIn = _lastCheckIn.asStateFlow()

    private var searchJob: Job? = null
    private var priceJob: Job? = null

    // ------------------------------------------ Search and queue ------------------------------------------

    /** Searches as they type, waiting for a short pause so every key doesn't hit the server */
    fun setQuery(text: String) {
        _query.value = text
        _view.value = CounterView.HOME

        searchJob?.cancel()

        if (text.isBlank()) {
            _results.value = CounterSearchResponse()
            _searching.value = false
            return
        }

        searchJob = scope.launch {
            _searching.value = true
            delay(SEARCH_DELAY_MILLIS)
            _results.value = remoteHandler.counterRepo.search(text.trim(), SEARCH_LIMIT)
            _searching.value = false
        }
    }

    fun clearSearch() = setQuery("")

    fun refreshQueue() {
        scope.launch {
            remoteHandler.counterRepo.queue(RECENT_LIMIT)?.let { _queue.value = it }
            remoteHandler.adminRepo.getShopProfile()?.let { _shop.value = it }
        }
    }

    // ------------------------------------------ Moving around ------------------------------------------

    fun goHome() {
        _view.value = CounterView.HOME
    }

    fun openPriceCheck() {
        _view.value = CounterView.PRICE_CHECK
    }

    /** Loads a ticket with its customer, notes, history and invoice and shows it */
    fun openTicket(identifier: Uuid) {
        scope.launch {
            loadTicket(identifier)?.let {
                _ticket.value = it
                _view.value = CounterView.TICKET
            } ?: notify("Couldn't load that ticket")
        }
    }

    fun openCustomer(identifier: Uuid) {
        scope.launch {
            val customer = remoteHandler.customerRepo.get(identifier)

            if (customer == null) {
                notify("Couldn't load that customer")
                return@launch
            }

            _customer.value = CustomerDetail(customer, remoteHandler.customerRepo.getTickets(identifier))
            _view.value = CounterView.CUSTOMER
        }
    }

    /** Reloads whatever ticket is open, after something changed it */
    fun reloadTicket() {
        val identifier = _ticket.value?.ticket?.identifier ?: return
        scope.launch { loadTicket(identifier)?.let { _ticket.value = it } }
    }

    private suspend fun loadTicket(identifier: Uuid): TicketDetail? {
        val repo = remoteHandler.ticketRepo
        val ticket = repo.get(identifier) ?: return null

        val customer = repo.getCustomer(identifier)?.let { remoteHandler.customerRepo.get(it.identifier) }

        return TicketDetail(
            ticket = ticket,
            customer = customer,
            technician = repo.getUser(identifier),
            notes = repo.getNotes(identifier),
            history = repo.getStatusHistory(identifier),
            invoice = remoteHandler.invoiceRepo.getForTicket(identifier),
            signature = repo.getSignature(identifier)
        )
    }

    // ------------------------------------------ Changing tickets ------------------------------------------

    /** Quick status buttons on the ticket card */
    fun setTicketStatus(status: TicketStatusType) {
        val detail = _ticket.value ?: return
        if (!canEdit()) return

        scope.launch {
            when (val result = remoteHandler.ticketRepo.update(detail.ticket.copy(status = TicketStatus(status)))) {
                is Result.Success -> {
                    notify("Ticket ${detail.ticket.label} is now ${statusLabel(status).lowercase()}")
                    reloadTicket()
                    refreshQueue()
                }

                is Result.Error -> notify(result.error.message)
            }
        }
    }

    fun addTicketNote(message: String) {
        val detail = _ticket.value ?: return
        if (!canEdit() || message.isBlank()) return

        scope.launch {
            // The server signs it with whoever is signed in
            remoteHandler.ticketRepo.addNote(detail.ticket.identifier, Note(message = message.trim()))
            reloadTicket()
        }
    }

    // ------------------------------------------ Check in ------------------------------------------

    /** Opens the check in wizard, starting with [customer] if it came from their card */
    fun startCheckIn(customer: Customer? = null) {
        if (!canEdit()) return

        _checkInCustomer.value = customer
        _view.value = CounterView.CHECK_IN
    }

    fun startNewCustomer() {
        if (!canEdit()) return
        _view.value = CounterView.NEW_CUSTOMER
    }

    /** Customers for the check in wizard's lookup */
    suspend fun searchCustomers(text: String): List<Customer> =
        if (text.isBlank()) emptyList() else remoteHandler.counterRepo.search(text.trim(), SEARCH_LIMIT).customers

    /** Staff for picking a technician */
    suspend fun searchTechnicians(text: String): List<SearchResult> =
        remoteHandler.userRepo.search(text.trim(), SEARCH_LIMIT)

    /** Earlier repairs of this device for the customer, for the repeat warning */
    suspend fun repeatCheck(customer: Uuid, device: Device, excludeTicket: Uuid): List<TicketSummary> =
        remoteHandler.counterRepo.repeatCheck(RepeatCheckRequest(customer, device, excludeTicket))

    /**
     * Adds a customer
     * @param onResult Gets the saved customer, or null and what went wrong
     */
    fun addCustomer(customer: Customer, onResult: (Customer?, String?) -> Unit) {
        if (!canEdit()) return

        scope.launch {
            when (val result = remoteHandler.customerRepo.add(customer)) {
                is Result.Success -> onResult(customer, null)
                is Result.Error -> onResult(null, result.error.message)
            }
        }
    }

    /**
     * Sends a finished check in
     * @param onResult Gets null when it went through, otherwise what went wrong
     */
    fun submitCheckIn(request: CheckInRequest, onResult: (String?) -> Unit) {
        if (!canEdit()) return

        scope.launch {
            when (val result = remoteHandler.counterRepo.checkIn(request)) {
                is Result.Success -> {
                    _lastCheckIn.value = result.data
                    _checkInCustomer.value = null
                    _view.value = CounterView.CHECKED_IN
                    refreshQueue()
                    onResult(null)
                }

                is Result.Error -> onResult(result.error.message)
            }
        }
    }

    // ------------------------------------------ Price check ------------------------------------------

    fun searchPrices(text: String) {
        priceJob?.cancel()

        if (text.isBlank()) {
            _priceResults.value = emptyList()
            return
        }

        priceJob = scope.launch {
            delay(SEARCH_DELAY_MILLIS)

            val inventory = remoteHandler.inventoryRepo
            _priceResults.value = inventory.search(text.trim(), SEARCH_LIMIT).mapNotNull { inventory.get(it.identifier) }
        }
    }

    /** Clears everything, used on log out and when someone else signs in */
    fun clear() {
        searchJob?.cancel()
        priceJob?.cancel()
        _view.value = CounterView.HOME
        _query.value = ""
        _results.value = CounterSearchResponse()
        _queue.value = null
        _ticket.value = null
        _customer.value = null
        _priceResults.value = emptyList()
        _checkInCustomer.value = null
        _lastCheckIn.value = null
    }

    private fun notify(message: String) {
        scope.launch { snackBarHost.showSnackbar(message) }
    }

    companion object {
        private const val SEARCH_DELAY_MILLIS = 300L
        private const val SEARCH_LIMIT = 20
        private const val RECENT_LIMIT = 10

        /** DONE reads better as "Ready for pickup" at the counter */
        fun statusLabel(status: TicketStatusType): String = when (status) {
            TicketStatusType.IN_PROGRESS -> "In progress"
            TicketStatusType.ON_HOLD -> "On hold"
            TicketStatusType.DONE -> "Ready for pickup"
            TicketStatusType.PICKED_UP -> "Picked up"
        }
    }
}
