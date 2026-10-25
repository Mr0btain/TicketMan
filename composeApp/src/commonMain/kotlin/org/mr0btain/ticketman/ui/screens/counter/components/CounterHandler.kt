package org.mr0btain.ticketman.ui.screens.counter.components

import org.mr0btain.ticketman.error.getOrNull
import org.mr0btain.ticketman.error.valueOr
import org.mr0btain.ticketman.components.network.isNotFound
import androidx.compose.material3.SnackbarHostState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mr0btain.ticketman.components.network.RemoteHandler
import org.mr0btain.ticketman.data.giftcard.GiftCard
import org.mr0btain.ticketman.data.ticket.CustomerDevice
import org.mr0btain.ticketman.data.inventory.Inventory
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.data.shop.ShopProfile
import org.mr0btain.ticketman.data.ticket.StatusChoice
import org.mr0btain.ticketman.data.ticket.label
import org.mr0btain.ticketman.error.TmResult
import org.mr0btain.ticketman.error.getOrDefault
import org.mr0btain.ticketman.server.request.BalanceAdjustRequest
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
    PICKED_UP,
    SALE,
    SOLD
}

/**
 * State and actions for the shop counter. Owned by the main view model. Holds the search, queue, shop profile,
 * ticket and customer cards and price check itself, and one part each for [checkIn], [pickup], [sale], the
 * [invoices] list, the [printer] and the open ticket's [estimates] and messages
 * @param canEdit Whether the signed in user can change things, restricted users can only look
 */
class CounterHandler(
    private val remoteHandler: RemoteHandler,
    scope: CoroutineScope,
    snackBarHost: SnackbarHostState,
    canEdit: () -> Boolean
) {
    private val _view = MutableStateFlow(CounterView.HOME)
    val view = _view.asStateFlow()

    private val support = CounterSupport(scope, snackBarHost, canEdit, show = { _view.value = it }, ticketsChanged = { refreshQueue() })
    private val scope get() = support.scope

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
    /** Terms customers sign at check in, the currency and paper size. Loaded at login and with the queue */
    val shop = _shop.asStateFlow()

    val checkIn = CounterCheckIn(remoteHandler, support)
    val pickup = CounterPickup(remoteHandler, support)
    val sale = CounterSale(remoteHandler, support)
    val invoices = CounterInvoices(remoteHandler, support)
    val printer = CounterPrinter(remoteHandler, support, shop = { _shop.value })
    val estimates = CounterEstimates(remoteHandler, support, reload = { reloadTicket() })

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
            _results.value = remoteHandler.counterRepo.search(text.trim(), SEARCH_LIMIT).valueOr { error ->
                support.notify("Search failed: ${error.message}")
                CounterSearchResponse()
            }
            _searching.value = false
        }
    }

    fun clearSearch() = setQuery("")

    /** Loads the shop profile, done at login since amounts everywhere use its currency */
    fun refreshShop() {
        scope.launch { remoteHandler.adminRepo.getShopProfile()?.let { _shop.value = it } }
    }

    /** An admin saved the shop profile on this device */
    fun shopChanged(profile: ShopProfile) {
        _shop.value = profile
    }

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
            when (val loaded = remoteHandler.loadTicketDetail(identifier)) {
                is TmResult.Success -> {
                    _ticket.value = loaded.data
                    _view.value = CounterView.TICKET
                }

                is TmResult.Error -> support.notify(
                    if (loaded.error.isNotFound) "That ticket isn't there any more" else "Couldn't load that ticket: ${loaded.error.message}"
                )
            }
        }
    }

    /** Loads a customer with their tickets and shows them */
    fun openCustomer(identifier: Uuid) {
        scope.launch {
            val repo = remoteHandler.customerRepo
            val customer = repo.get(identifier).valueOr { error ->
                support.notify(if (error.isNotFound) "That customer isn't there any more" else "Couldn't load that customer: ${error.message}")
                return@launch
            }
            val tickets = repo.getTickets(identifier).valueOr { error ->
                support.notify("Couldn't load that customer's tickets: ${error.message}")
                return@launch
            }
            // Only shown, never saved back, so a failed read just shows nothing
            val credit = repo.getCredit(identifier).getOrDefault(emptyList())
            val invoices = repo.getInvoices(identifier).getOrDefault(emptyList())
            val devices = repo.getDevices(identifier).getOrDefault(emptyList())

            _customer.value = CustomerDetail(customer, tickets, devices, credit, invoices)
            _view.value = CounterView.CUSTOMER
        }
    }

    /**
     * Reloads whatever ticket is open, after something changed it
     * @param quiet For the periodic check: says nothing when it can't be reached
     */
    fun reloadTicket(quiet: Boolean = false) {
        val identifier = _ticket.value?.ticket?.identifier ?: return
        scope.launch {
            when (val loaded = remoteHandler.loadTicketDetail(identifier)) {
                // Only if it's still the one open
                is TmResult.Success -> if (_ticket.value?.ticket?.identifier == identifier) _ticket.value = loaded.data
                is TmResult.Error -> if (!quiet) support.notify("Couldn't reload the ticket: ${loaded.error.message}")
            }
        }
    }

    // ------------------------------------------ Changing tickets ------------------------------------------

    /** Quick status buttons on the ticket card */
    fun setTicketStatus(status: StatusChoice) {
        val detail = _ticket.value ?: return
        if (!support.canEdit()) return

        scope.launch {
            when (val result = remoteHandler.ticketRepo.update(detail.ticket.copy(status = status.toStatus()))) {
                is TmResult.Success -> {
                    support.notify("Ticket ${detail.ticket.label} is now ${_shop.value.statuses.label(status).lowercase()}")
                    reloadTicket()
                    refreshQueue()
                }

                is TmResult.Error -> support.notify(result.error.message)
            }
        }
    }

    /** Replaces the open ticket's status page link, for when the old one went somewhere it shouldn't */
    fun replaceStatusLink() {
        val detail = _ticket.value ?: return
        if (!support.canEdit()) return

        scope.launch {
            when (val result = remoteHandler.ticketRepo.replaceStatusToken(detail.ticket.identifier)) {
                is TmResult.Success -> {
                    support.notify("Ticket ${detail.ticket.label} has a new status link, the old one no longer works")
                    reloadTicket()
                }

                is TmResult.Error -> support.notify("Link not replaced: ${result.error.message}")
            }
        }
    }

    /** Adds a text note to the open ticket straight away, signed by whoever is signed in, then reloads it */
    fun addTicketNote(message: String) {
        val detail = _ticket.value ?: return
        if (!support.canEdit() || message.isBlank()) return

        scope.launch {
            // The server signs it with whoever is signed in
            val added = remoteHandler.ticketRepo.addNote(detail.ticket.identifier, Note(message = message.trim()))
            if (added is TmResult.Error) support.notify("Note not saved: ${added.error.message}")

            reloadTicket()
        }
    }

    /**
     * Gives or takes the open customer's store credit, admin only
     * @param onResult Gets null when it's saved, otherwise what went wrong
     */
    fun adjustCredit(amountCents: Long, reason: String, onResult: (String?) -> Unit) {
        val detail = _customer.value ?: return

        scope.launch {
            when (val result = remoteHandler.customerRepo.adjustCredit(detail.customer.identifier, BalanceAdjustRequest(amountCents, reason))) {
                is TmResult.Success -> {
                    onResult(null)
                    openCustomer(detail.customer.identifier)
                }

                is TmResult.Error -> onResult(result.error.message)
            }
        }
    }

    /** The item with exactly this barcode or SKU, for a scan. Null when there's none or it couldn't be read */
    suspend fun findItem(code: String): Inventory? = remoteHandler.inventoryRepo.getByCode(code).getOrNull()

    /**
     * Enter in the main search, which is how a barcode scanner ends what it types: an item's code opens the price
     * check on it. Anything else stays a search
     */
    fun scan(code: String) {
        if (code.isBlank()) return
        scope.launch {
            val item = findItem(code.trim()) ?: return@launch
            searchJob?.cancel()
            _query.value = ""
            _searching.value = false
            _results.value = CounterSearchResponse()
            _priceResults.value = listOf(item)
            _view.value = CounterView.PRICE_CHECK
        }
    }

    /**
     * Saves a device's details on the open customer
     * @param onResult Gets null when it's saved, otherwise what went wrong
     */
    fun saveDevice(device: CustomerDevice, onResult: (String?) -> Unit) {
        val detail = _customer.value ?: return
        if (!support.canEdit()) return

        scope.launch {
            when (val result = remoteHandler.customerRepo.updateDevice(device)) {
                is TmResult.Success -> {
                    onResult(null)
                    openCustomer(detail.customer.identifier)
                }

                is TmResult.Error -> onResult(result.error.message)
            }
        }
    }

    /** Deletes one of the open customer's devices. Its tickets stay */
    fun removeDevice(device: CustomerDevice) {
        val detail = _customer.value ?: return
        if (!support.canEdit()) return

        scope.launch {
            when (val result = remoteHandler.customerRepo.removeDevice(device.identifier)) {
                is TmResult.Success -> openCustomer(detail.customer.identifier)
                is TmResult.Error -> support.notify(result.error.message)
            }
        }
    }

    /** The ticket's passcode, which the server writes down as looked at */
    suspend fun showPasscode(ticket: Uuid): Result<String> =
        when (val result = remoteHandler.ticketRepo.getPasscode(ticket)) {
            is TmResult.Success -> Result.success(result.data.passcode)
            is TmResult.Error -> Result.failure(Exception(if (result.error.isNotFound) "There's no passcode on file" else result.error.message))
        }

    /**
     * Saves a new passcode for the ticket
     * @param onResult Gets null when it's saved, otherwise what went wrong
     */
    fun setPasscode(ticket: Uuid, passcode: String, onResult: (String?) -> Unit) {
        if (!support.canEdit()) return
        scope.launch {
            when (val result = remoteHandler.ticketRepo.setPasscode(ticket, passcode)) {
                is TmResult.Success -> {
                    onResult(null)
                    reloadTicket()
                }
                is TmResult.Error -> onResult(result.error.message)
            }
        }
    }

    fun clearPasscode(ticket: Uuid) {
        if (!support.canEdit()) return
        scope.launch {
            when (val result = remoteHandler.ticketRepo.clearPasscode(ticket)) {
                is TmResult.Success -> reloadTicket()
                is TmResult.Error -> support.notify(result.error.message)
            }
        }
    }

    /** The gift card with this code, for paying with it. Null when there's none or it couldn't be read */
    suspend fun findGiftCard(code: String): GiftCard? = remoteHandler.giftCardRepo.get(code).getOrNull()

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
            _priceResults.value = inventory.search(text.trim(), SEARCH_LIMIT).valueOr { error ->
                support.notify("Price check failed: ${error.message}")
                emptyList()
            }.mapNotNull { inventory.get(it.identifier).getOrNull() }
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
        _shop.value = ShopProfile()
        checkIn.clear()
        pickup.clear()
        sale.clear()
        invoices.clear()
    }

    companion object {
        private const val SEARCH_DELAY_MILLIS = 300L
        const val SEARCH_LIMIT = 20
        private const val RECENT_LIMIT = 10

    }
}
