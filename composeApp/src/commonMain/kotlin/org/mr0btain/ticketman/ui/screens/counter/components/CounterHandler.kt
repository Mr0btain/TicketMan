package org.mr0btain.ticketman.ui.screens.counter.components

import androidx.compose.material3.SnackbarHostState
import kotlinx.coroutines.CoroutineScope
import org.mr0btain.ticketman.ui.kit.feedback.label
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mr0btain.ticketman.components.network.RemoteHandler
import org.mr0btain.ticketman.components.print.PrintTemplate
import org.mr0btain.ticketman.components.print.PrintTemplates
import org.mr0btain.ticketman.components.print.printDocument
import org.mr0btain.ticketman.data.counter.TicketSummary
import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.data.inventory.Inventory
import org.mr0btain.ticketman.data.invoice.Charge
import org.mr0btain.ticketman.data.invoice.Invoice
import org.mr0btain.ticketman.data.invoice.Payment
import org.mr0btain.ticketman.data.search.SearchResult
import org.mr0btain.ticketman.data.shared.EntityReference
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.data.shop.PaperSize
import org.mr0btain.ticketman.data.shop.ShopProfile
import org.mr0btain.ticketman.data.ticket.Device
import org.mr0btain.ticketman.data.ticket.Ticket
import org.mr0btain.ticketman.data.ticket.TicketStatus
import org.mr0btain.ticketman.data.ticket.TicketSignature
import org.mr0btain.ticketman.data.ticket.TicketStatusChange
import org.mr0btain.ticketman.data.ticket.TicketStatusType
import org.mr0btain.ticketman.error.Result
import org.mr0btain.ticketman.server.request.CheckInRequest
import org.mr0btain.ticketman.server.request.PaymentRequest
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
    NEW_CUSTOMER,
    PICKUP,
    PICKED_UP
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

    // Ticket and invoice being picked up
    private val _pickup = MutableStateFlow<TicketDetail?>(null)
    val pickup = _pickup.asStateFlow()

    // Invoice list, newest first
    private val _invoices = MutableStateFlow<List<Invoice>>(emptyList())
    val invoices = _invoices.asStateFlow()

    private val _invoicesEndReached = MutableStateFlow(false)
    val invoicesEndReached = _invoicesEndReached.asStateFlow()

    // The paid invoice from the last pickup, for the done screen
    private val _lastPickup = MutableStateFlow<Invoice?>(null)
    val lastPickup = _lastPickup.asStateFlow()

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

    /** Reloads the queue, and the shop profile with it so new terms and paper sizes show up */
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

    /** Loads a customer with their tickets and shows them */
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

    /** Adds a text note to the open ticket straight away, signed by whoever is signed in, then reloads it */
    fun addTicketNote(message: String) {
        val detail = _ticket.value ?: return
        if (!canEdit() || message.isBlank()) return

        scope.launch {
            // The server signs it with whoever is signed in
            val added = remoteHandler.ticketRepo.addNote(detail.ticket.identifier, Note(message = message.trim()))
            if (added is Result.Error) notify("Note not saved: ${added.error.message}")

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

    /** Opens the quick add customer screen */
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

    // ------------------------------------------ Pickup ------------------------------------------

    /** Opens pickup for a ticket, making its invoice if it doesn't have one */
    fun startPickup(ticketIdentifier: Uuid) {
        if (!canEdit()) return

        scope.launch {
            val detail = loadTicket(ticketIdentifier) ?: run {
                notify("Couldn't load that ticket")
                return@launch
            }

            if (detail.ticket.status.status == TicketStatusType.PICKED_UP) {
                notify("Ticket ${detail.ticket.label} was already picked up")
                return@launch
            }

            when (val invoice = remoteHandler.invoiceRepo.openForTicket(ticketIdentifier)) {
                is Result.Success -> {
                    _pickup.value = detail.copy(invoice = invoice.data)
                    _view.value = CounterView.PICKUP
                }

                is Result.Error -> notify(invoice.error.message)
            }
        }
    }

    /** Parts for adding to an invoice */
    suspend fun searchParts(text: String): List<Inventory> {
        if (text.isBlank()) return emptyList()

        val inventory = remoteHandler.inventoryRepo
        return inventory.search(text.trim(), SEARCH_LIMIT).mapNotNull { inventory.get(it.identifier) }
    }

    /**
     * Saves the invoice's lines so the server works out the totals
     * @param onResult Gets null when saved, otherwise what went wrong
     */
    fun saveCharges(charges: List<Charge>, discountCents: Long, onResult: (String?) -> Unit) {
        val invoice = _pickup.value?.invoice ?: return
        if (!canEdit()) return

        scope.launch {
            when (val result = remoteHandler.invoiceRepo.update(invoice.identifier, charges, discountCents)) {
                is Result.Success -> {
                    _pickup.value = _pickup.value?.copy(invoice = result.data)
                    onResult(null)
                }

                is Result.Error -> onResult(result.error.message)
            }
        }
    }

    /** Takes a deposit or other payment off the open invoice */
    fun removePayment(payment: Payment) {
        val invoice = _pickup.value?.invoice ?: return
        if (!canEdit()) return

        scope.launch {
            when (val result = remoteHandler.invoiceRepo.removePayment(invoice.identifier, payment.identifier)) {
                is Result.Success -> _pickup.value = _pickup.value?.copy(invoice = result.data)
                is Result.Error -> notify(result.error.message)
            }
        }
    }

    /**
     * Finishes the pickup. Unsaved line changes are saved first so the server checks the right total
     * @param onResult Gets null when the ticket is picked up, otherwise what went wrong
     */
    fun finishPickup(charges: List<Charge>, discountCents: Long, payments: List<PaymentRequest>, onResult: (String?) -> Unit) {
        val detail = _pickup.value ?: return
        val invoice = detail.invoice ?: return
        if (!canEdit()) return

        scope.launch {
            if (charges != invoice.charges || discountCents != invoice.discountCents) {
                val saved = remoteHandler.invoiceRepo.update(invoice.identifier, charges, discountCents)

                if (saved is Result.Error) {
                    onResult(saved.error.message)
                    return@launch
                }
            }

            when (val result = remoteHandler.counterRepo.pickup(detail.ticket.identifier, payments)) {
                is Result.Success -> {
                    _lastPickup.value = result.data
                    _pickup.value = null
                    _view.value = CounterView.PICKED_UP
                    refreshQueue()
                    onResult(null)
                }

                is Result.Error -> {
                    // Totals may have changed when saving, show them
                    remoteHandler.invoiceRepo.getForTicket(detail.ticket.identifier)?.let { latest ->
                        _pickup.value = _pickup.value?.copy(invoice = latest)
                    }
                    onResult(result.error.message)
                }
            }
        }
    }

    // ------------------------------------------ Printing ------------------------------------------

    /**
     * Prints one of the counter's slips for a ticket, loading it fresh so the printout matches the server
     * @param onResult Gets null when it went to the printer or they cancelled, otherwise what went wrong
     */
    fun print(ticketIdentifier: Uuid, template: PrintTemplate, paper: PaperSize, onResult: (String?) -> Unit) {
        scope.launch {
            val detail = loadTicket(ticketIdentifier) ?: run {
                onResult("Couldn't load the ticket to print")
                return@launch
            }

            val shop = remoteHandler.adminRepo.getShopProfile() ?: _shop.value

            val document = when (template) {
                PrintTemplate.CLAIM_SLIP -> PrintTemplates.claimSlip(shop, detail.ticket, detail.customer, detail.invoice, detail.signature)
                PrintTemplate.DEVICE_TAG -> PrintTemplates.deviceTag(detail.ticket, detail.customer)
                PrintTemplate.RECEIPT -> detail.invoice?.let { PrintTemplates.receipt(shop, detail.ticket, detail.customer, it) } ?: run {
                    onResult("This ticket has no invoice yet")
                    return@launch
                }
            }

            onResult(printDocument(document, paper))
        }
    }

    // ------------------------------------------ Invoice list ------------------------------------------

    /** Loads the invoice list, from the start or the next page */
    fun loadInvoices(reset: Boolean) {
        scope.launch {
            val offset = if (reset) 0L else _invoices.value.size.toLong()
            val page = remoteHandler.invoiceRepo.fetch(offset, INVOICE_PAGE_SIZE)

            _invoices.value = if (reset) page else _invoices.value + page
            _invoicesEndReached.value = page.size < INVOICE_PAGE_SIZE
        }
    }

    /** Admin only, the server refuses anyone else */
    fun voidInvoice(invoice: Invoice) {
        scope.launch {
            when (val result = remoteHandler.invoiceRepo.void(invoice.identifier)) {
                is Result.Success -> {
                    notify("Voided ${invoice.label}")
                    _invoices.value = _invoices.value.map { if (it.identifier == invoice.identifier) result.data else it }
                }

                is Result.Error -> notify(result.error.message)
            }
        }
    }

    // ------------------------------------------ Price check ------------------------------------------

    /** Looks up inventory for the price check, waiting for a pause in typing like the main search */
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
        _pickup.value = null
        _lastPickup.value = null
        _invoices.value = emptyList()
        _invoicesEndReached.value = false
    }

    private fun notify(message: String) {
        scope.launch { snackBarHost.showSnackbar(message) }
    }

    companion object {
        private const val SEARCH_DELAY_MILLIS = 300L
        private const val SEARCH_LIMIT = 20
        private const val RECENT_LIMIT = 10
        private const val INVOICE_PAGE_SIZE = 50

        /** DONE reads better as "Ready for pickup" at the counter */
        fun statusLabel(status: TicketStatusType): String = status.label
    }
}
