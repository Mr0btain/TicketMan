package org.mr0btain.ticketman.ui.screens.counter.components

import org.mr0btain.ticketman.data.location.at
import org.mr0btain.ticketman.components.print.PrintDocument
import org.mr0btain.ticketman.components.network.orNullIfNotFound
import org.mr0btain.ticketman.components.network.ServerError
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.mr0btain.ticketman.components.network.RemoteHandler
import org.mr0btain.ticketman.components.print.PrintTemplate
import org.mr0btain.ticketman.components.print.PrintTemplates
import org.mr0btain.ticketman.components.print.printDocument
import org.mr0btain.ticketman.data.shop.PaperSize
import org.mr0btain.ticketman.data.inventory.Inventory
import org.mr0btain.ticketman.data.invoice.Invoice
import org.mr0btain.ticketman.components.data.saveFile
import org.mr0btain.ticketman.data.shop.ShopProfile
import org.mr0btain.ticketman.error.getOrNull
import org.mr0btain.ticketman.error.valueOr
import kotlin.uuid.Uuid

/** An invoice open to look over, with [document] being what printing it would print */
data class InvoicePreview(val invoice: Invoice, val document: PrintDocument)

/**
 * Prints the counter's slips
 * @param shop The shop profile already loaded, used when a fresh one can't be read
 */
class CounterPrinter(
    private val remoteHandler: RemoteHandler,
    private val support: CounterSupport,
    private val shop: () -> ShopProfile
) {
    /**
     * Prints one of the slips for a ticket, loading it fresh so the printout matches the server
     * @param onResult Gets null when it went to the printer or they cancelled, otherwise what went wrong
     */
    fun print(ticketIdentifier: Uuid, template: PrintTemplate, paper: PaperSize, onResult: (String?) -> Unit) {
        support.scope.launch {
            val detail = remoteHandler.loadTicketDetail(ticketIdentifier).valueOr { error ->
                onResult("Couldn't load the ticket to print: ${error.message}")
                return@launch
            }

            val shop = remoteHandler.adminRepo.getShopProfile() ?: shop()

            val document = when (template) {
                PrintTemplate.CLAIM_SLIP -> PrintTemplates.claimSlip(shop, detail.ticket, detail.customer, detail.invoice, detail.signature)
                PrintTemplate.DEVICE_TAG -> PrintTemplates.deviceTag(detail.ticket, detail.customer)
                PrintTemplate.RECEIPT -> detail.invoice?.let { PrintTemplates.receipt(shop, detail.ticket, detail.customer, it) } ?: run {
                    onResult("This ticket has no invoice yet")
                    return@launch
                }
                PrintTemplate.ITEM_LABEL -> {
                    onResult("Item labels are printed from the item")
                    return@launch
                }
            }

            onResult(printDocument(document, paper))
        }
    }

    /**
     * Prints the receipt for a sale, which has no ticket to load
     * @param onResult Gets null when it went to the printer or they cancelled, otherwise what went wrong
     */
    fun printSale(invoice: Invoice, paper: PaperSize, onResult: (String?) -> Unit) {
        support.scope.launch {
            val shop = remoteHandler.adminRepo.getShopProfile() ?: shop()
            val customer = invoice.customerIdentifier?.let { remoteHandler.customerRepo.get(it).getOrNull() }

            onResult(printDocument(PrintTemplates.receipt(shop, null, customer, invoice), paper))
        }
    }

    /**
     * Prints an inventory item's label, with its barcode
     * @param onResult Gets null when it went to the printer or they cancelled, otherwise what went wrong
     */
    fun printLabel(item: Inventory, paper: PaperSize, onResult: (String?) -> Unit) {
        support.scope.launch {
            val shop = remoteHandler.adminRepo.getShopProfile() ?: shop()
            onResult(printDocument(PrintTemplates.itemLabel(shop, item), paper))
        }
    }

    private val _invoicePreview = MutableStateFlow<InvoicePreview?>(null)

    /** The invoice open to look over before printing or saving it */
    val invoicePreview = _invoicePreview.asStateFlow()

    /**
     * Opens an invoice as it prints, loaded the way the server loads its PDF so the two match: its ticket and customer,
     * and the address of the location it was made at. Says so when something it needs can't be read
     */
    fun previewInvoice(invoice: Invoice) {
        support.scope.launch {
            val tickets = remoteHandler.ticketRepo
            val failed: (ServerError) -> Unit = { error -> support.notify("Couldn't open ${invoice.label}: ${error.message}") }

            val ticket = invoice.ticketIdentifier?.let { tickets.get(it).orNullIfNotFound().valueOr { error -> failed(error); return@launch } }
            val customerIdentifier = invoice.customerIdentifier
                ?: ticket?.let { tickets.getCustomer(it.identifier).orNullIfNotFound().valueOr { error -> failed(error); return@launch } }?.identifier
            val customer = customerIdentifier?.let { remoteHandler.customerRepo.get(it).orNullIfNotFound().valueOr { error -> failed(error); return@launch } }

            val location = remoteHandler.locationRepo.all().getOrNull()?.let { locations ->
                locations.firstOrNull { it.identifier == invoice.locationIdentifier } ?: locations.firstOrNull { it.main }
            }
            val shop = (remoteHandler.adminRepo.getShopProfile() ?: shop()).at(location)

            _invoicePreview.value = InvoicePreview(invoice, PrintTemplates.invoice(shop, ticket, customer, invoice))
        }
    }

    fun closeInvoicePreview() {
        _invoicePreview.value = null
    }

    /**
     * Prints the open invoice
     * @param onResult Gets null when it went to the printer or they cancelled, otherwise what went wrong
     */
    fun printInvoicePreview(paper: PaperSize, onResult: (String?) -> Unit) {
        val preview = _invoicePreview.value ?: return

        support.scope.launch { onResult(printDocument(preview.document, paper)) }
    }

    /** Saves the invoice as a PDF the server makes, to email or file. Says how it went */
    fun savePdf(invoice: Invoice) {
        support.scope.launch {
            val bytes = remoteHandler.invoiceRepo.pdf(invoice.identifier).valueOr { error ->
                support.notify("Couldn't make the PDF: ${error.message}")
                return@launch
            }

            if (saveFile(bytes, invoice.label, "pdf")) support.notify("Saved ${invoice.label}.pdf")
        }
    }
}
