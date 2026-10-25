package org.mr0btain.ticketman.ui.screens.counter.components

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mr0btain.ticketman.components.network.RemoteHandler
import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.data.invoice.Invoice
import org.mr0btain.ticketman.error.TmResult
import org.mr0btain.ticketman.server.request.SaleRequest

/** Selling from stock without a ticket */
class CounterSale(
    private val remoteHandler: RemoteHandler,
    private val support: CounterSupport
) {
    // Who it's for, when it was started from their card
    private val _customer = MutableStateFlow<Customer?>(null)
    val customer = _customer.asStateFlow()

    // The paid invoice from the last sale, for the done screen
    private val _last = MutableStateFlow<Invoice?>(null)
    val last = _last.asStateFlow()

    /** Opens a new sale, for [customer] if it came from their card */
    fun start(customer: Customer? = null) {
        if (!support.canEdit()) return

        _customer.value = customer
        support.show(CounterView.SALE)
    }

    /**
     * Sends the sale, which the server only takes paid in full
     * @param onResult Gets null when it's sold, otherwise what went wrong
     */
    fun finish(request: SaleRequest, onResult: (String?) -> Unit) {
        if (!support.canEdit()) return

        support.scope.launch {
            when (val result = remoteHandler.counterRepo.sale(request)) {
                is TmResult.Success -> {
                    _last.value = result.data
                    _customer.value = null
                    support.show(CounterView.SOLD)
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
