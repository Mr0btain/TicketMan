package org.mr0btain.ticketman.ui.screens.counter.components

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mr0btain.ticketman.components.network.RemoteHandler
import org.mr0btain.ticketman.data.giftcard.GiftCard
import org.mr0btain.ticketman.data.invoice.Invoice
import org.mr0btain.ticketman.server.request.BalanceAdjustRequest
import org.mr0btain.ticketman.error.TmResult
import org.mr0btain.ticketman.server.request.RefundRequest
import org.mr0btain.ticketman.error.valueOr
import org.mr0btain.ticketman.components.data.saveFile

/** The invoice list, newest first, and the gift cards, shown on the admin screen's Invoices tab */
class CounterInvoices(
    private val remoteHandler: RemoteHandler,
    private val support: CounterSupport
) {
    private val _list = MutableStateFlow<List<Invoice>>(emptyList())
    val list = _list.asStateFlow()

    private val _endReached = MutableStateFlow(false)
    val endReached = _endReached.asStateFlow()

    private val _giftCards = MutableStateFlow<List<GiftCard>>(emptyList())
    /** Newest first, admin only */
    val giftCards = _giftCards.asStateFlow()

    /** Loads the list, from the start or the next page */
    fun load(reset: Boolean) {
        support.scope.launch {
            val offset = if (reset) 0L else _list.value.size.toLong()
            val page = remoteHandler.invoiceRepo.fetch(offset, PAGE_SIZE).valueOr { error ->
                support.notify("Couldn't load invoices: ${error.message}")
                return@launch
            }

            _list.value = if (reset) page else _list.value + page
            _endReached.value = page.size < PAGE_SIZE
        }
    }

    /** Admin only, the server refuses anyone else */
    fun void(invoice: Invoice) {
        support.scope.launch {
            when (val result = remoteHandler.invoiceRepo.void(invoice.identifier)) {
                is TmResult.Success -> {
                    support.notify("Voided ${invoice.label}")
                    _list.value = _list.value.map { if (it.identifier == invoice.identifier) result.data else it }
                }

                is TmResult.Error -> support.notify(result.error.message)
            }
        }
    }

    /**
     * Gives money back on a paid invoice, admin only
     * @param onResult Gets null when it's done, otherwise what went wrong
     */
    fun refund(invoice: Invoice, request: RefundRequest, onResult: (String?) -> Unit) {
        support.scope.launch {
            when (val result = remoteHandler.invoiceRepo.refund(invoice.identifier, request)) {
                is TmResult.Success -> {
                    support.notify("Refunded ${invoice.label}")
                    _list.value = _list.value.map { if (it.identifier == invoice.identifier) result.data else it }
                    onResult(null)
                }

                is TmResult.Error -> onResult(result.error.message)
            }
        }
    }

    /** Deletes an invoice kept after its ticket was deleted, admin only */
    fun remove(invoice: Invoice) {
        support.scope.launch {
            when (val result = remoteHandler.invoiceRepo.remove(invoice.identifier)) {
                is TmResult.Success -> {
                    support.notify("Deleted ${invoice.label}")
                    _list.value = _list.value.filter { it.identifier != invoice.identifier }
                }

                is TmResult.Error -> support.notify(result.error.message)
            }
        }
    }

    /**
     * Saves the invoices made from [from] to [to] (days like 2026-10-01, both included) as CSV for the books
     * @param onResult Gets null when it was saved or they cancelled, otherwise what went wrong
     */
    fun export(from: String, to: String, onResult: (String?) -> Unit) {
        support.scope.launch {
            val bytes = remoteHandler.invoiceRepo.export(from, to).valueOr { error ->
                onResult("Couldn't export: ${error.message}")
                return@launch
            }

            if (saveFile(bytes, "invoices-$from-to-$to", "csv")) support.notify("Invoices saved")
            onResult(null)
        }
    }

    /** Loads the newest gift cards */
    fun loadGiftCards() {
        support.scope.launch {
            _giftCards.value = remoteHandler.giftCardRepo.fetch(0, GIFT_CARD_PAGE).valueOr { error ->
                support.notify("Couldn't load gift cards: ${error.message}")
                return@launch
            }
        }
    }

    /**
     * Corrects a gift card's balance, admin only
     * @param onResult Gets null when it's saved, otherwise what went wrong
     */
    fun adjustGiftCard(card: GiftCard, amountCents: Long, reason: String, onResult: (String?) -> Unit) {
        support.scope.launch {
            when (val result = remoteHandler.giftCardRepo.adjust(card.code, BalanceAdjustRequest(amountCents, reason))) {
                is TmResult.Success -> {
                    _giftCards.value = _giftCards.value.map { if (it.identifier == card.identifier) result.data else it }
                    onResult(null)
                }

                is TmResult.Error -> onResult(result.error.message)
            }
        }
    }

    fun clear() {
        _giftCards.value = emptyList()
        _list.value = emptyList()
        _endReached.value = false
    }

    private companion object {
        const val PAGE_SIZE = 50
        const val GIFT_CARD_PAGE = 200
    }
}
