package org.mr0btain.ticketman.ui.screens.counter.components

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mr0btain.ticketman.components.network.RemoteHandler
import org.mr0btain.ticketman.data.inventory.Inventory
import org.mr0btain.ticketman.data.invoice.Charge
import org.mr0btain.ticketman.data.invoice.Invoice
import org.mr0btain.ticketman.data.invoice.Payment
import org.mr0btain.ticketman.data.ticket.TicketStatusType
import org.mr0btain.ticketman.error.TmResult
import org.mr0btain.ticketman.error.getOrDefault
import org.mr0btain.ticketman.error.getOrNull
import org.mr0btain.ticketman.error.valueOr
import org.mr0btain.ticketman.server.request.PaymentRequest
import kotlin.uuid.Uuid

/** Pickup: the ticket's invoice, its lines and payments, and closing the ticket */
class CounterPickup(
    private val remoteHandler: RemoteHandler,
    private val support: CounterSupport
) {
    private val scope get() = support.scope

    // Ticket and invoice being picked up
    private val _detail = MutableStateFlow<TicketDetail?>(null)
    val detail = _detail.asStateFlow()

    // The paid invoice from the last pickup, for the done screen
    private val _last = MutableStateFlow<Invoice?>(null)
    val last = _last.asStateFlow()

    /** Opens pickup for a ticket, making its invoice if it doesn't have one */
    fun start(ticketIdentifier: Uuid) {
        if (!support.canEdit()) return

        scope.launch {
            val detail = remoteHandler.loadTicketDetail(ticketIdentifier).valueOr { error ->
                support.notify("Couldn't load that ticket: ${error.message}")
                return@launch
            }

            if (detail.ticket.status.status == TicketStatusType.PICKED_UP) {
                support.notify("Ticket ${detail.ticket.label} was already picked up")
                return@launch
            }

            when (val invoice = remoteHandler.invoiceRepo.openForTicket(ticketIdentifier)) {
                is TmResult.Success -> {
                    _detail.value = detail.copy(invoice = invoice.data)
                    support.show(CounterView.PICKUP)
                }

                is TmResult.Error -> support.notify(invoice.error.message)
            }
        }
    }

    /** Parts for adding to an invoice */
    suspend fun searchParts(text: String): List<Inventory> {
        if (text.isBlank()) return emptyList()

        val inventory = remoteHandler.inventoryRepo
        return inventory.search(text.trim(), CounterHandler.SEARCH_LIMIT).getOrDefault(emptyList())
            .mapNotNull { inventory.get(it.identifier).getOrNull() }
    }

    /**
     * Saves the invoice's lines so the server works out the totals
     * @param onResult Gets null when saved, otherwise what went wrong
     */
    fun saveCharges(charges: List<Charge>, discountCents: Long, onResult: (String?) -> Unit) {
        val invoice = _detail.value?.invoice ?: return
        if (!support.canEdit()) return

        scope.launch {
            when (val result = remoteHandler.invoiceRepo.update(invoice.identifier, charges, discountCents)) {
                is TmResult.Success -> {
                    _detail.value = _detail.value?.copy(invoice = result.data)
                    onResult(null)
                }

                is TmResult.Error -> onResult(result.error.message)
            }
        }
    }

    /** Takes a deposit or other payment off the open invoice */
    fun removePayment(payment: Payment) {
        val invoice = _detail.value?.invoice ?: return
        if (!support.canEdit()) return

        scope.launch {
            when (val result = remoteHandler.invoiceRepo.removePayment(invoice.identifier, payment.identifier)) {
                is TmResult.Success -> _detail.value = _detail.value?.copy(invoice = result.data)
                is TmResult.Error -> support.notify(result.error.message)
            }
        }
    }

    /**
     * Finishes the pickup. Unsaved line changes are saved first so the server checks the right total
     * @param onResult Gets null when the ticket is picked up, otherwise what went wrong
     */
    fun finish(charges: List<Charge>, discountCents: Long, payments: List<PaymentRequest>, onResult: (String?) -> Unit) {
        val detail = _detail.value ?: return
        val invoice = detail.invoice ?: return
        if (!support.canEdit()) return

        scope.launch {
            if (charges != invoice.charges || discountCents != invoice.discountCents) {
                val saved = remoteHandler.invoiceRepo.update(invoice.identifier, charges, discountCents)

                if (saved is TmResult.Error) {
                    onResult(saved.error.message)
                    return@launch
                }
            }

            when (val result = remoteHandler.counterRepo.pickup(detail.ticket.identifier, payments)) {
                is TmResult.Success -> {
                    _last.value = result.data
                    _detail.value = null
                    support.show(CounterView.PICKED_UP)
                    support.ticketsChanged()
                    onResult(null)
                }

                is TmResult.Error -> {
                    // Totals may have changed when saving, show them
                    remoteHandler.invoiceRepo.getForTicket(detail.ticket.identifier)?.let { latest ->
                        _detail.value = _detail.value?.copy(invoice = latest)
                    }
                    onResult(result.error.message)
                }
            }
        }
    }

    fun clear() {
        _detail.value = null
        _last.value = null
    }
}
