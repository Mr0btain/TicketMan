package org.mr0btain.ticketman.ui.screens.counter.components

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mr0btain.ticketman.components.network.RemoteHandler
import org.mr0btain.ticketman.data.invoice.Invoice
import org.mr0btain.ticketman.error.TmResult
import org.mr0btain.ticketman.error.valueOr
import org.mr0btain.ticketman.components.data.saveFile

/** The invoice list, newest first, shown on the admin screen's Invoices tab */
class CounterInvoices(
    private val remoteHandler: RemoteHandler,
    private val support: CounterSupport
) {
    private val _list = MutableStateFlow<List<Invoice>>(emptyList())
    val list = _list.asStateFlow()

    private val _endReached = MutableStateFlow(false)
    val endReached = _endReached.asStateFlow()

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

    fun clear() {
        _list.value = emptyList()
        _endReached.value = false
    }

    private companion object {
        const val PAGE_SIZE = 50
    }
}
