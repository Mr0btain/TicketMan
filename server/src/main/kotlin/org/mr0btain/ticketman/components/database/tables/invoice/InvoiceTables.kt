package org.mr0btain.ticketman.components.database.tables.invoice

import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.UuidTable
import org.jetbrains.exposed.v1.json.json
import org.mr0btain.ticketman.data.invoice.ReturnedPart
import org.mr0btain.ticketman.data.invoice.TaxRate

/**
 * One invoice per ticket. [ticket] isn't a foreign key, since an invoice with money against it outlives its ticket
 * (databases from before that have it dropped by `LocalDatabase`'s `invoice-ticket-key` fix). It's null for a
 * counter sale (made nullable on older databases by the `invoice-ticket-nullable` fix)
 */
object InvoiceTable : UuidTable("invoices") {
    val numberYear = integer("number_year")
    val numberSequence = long("number_sequence")
    val ticket = uuid("ticket_id").nullable().uniqueIndex()
    val customer = text("customer_id").nullable()
    val status = text("status")
    val discountCents = long("discount_cents").default(0)
    val taxRateBasisPoints = integer("tax_rate_bp").default(0)
    val subtotalCents = long("subtotal_cents").default(0)
    val taxCents = long("tax_cents").default(0)
    val totalCents = long("total_cents").default(0)
    val createdAt = long("created_at")
    val paidAt = long("paid_at").nullable()

    // Added with keeping invoices after their ticket is deleted
    val ticketLabel = text("ticket_label").default("")
    val ticketRemovedAt = long("ticket_removed_at").nullable()

    // Added with tax classes: the shop's classes when it was made, see Invoice.taxRates
    val taxRates = json<List<TaxRate>>("tax_rates", Json { ignoreUnknownKeys = true }).default(emptyList())

    // Added with storage terms: staff took the storage line off, so it isn't put back
    val storageWaived = bool("storage_waived").default(false)

    init {
        uniqueIndex("invoices_number_unique", numberYear, numberSequence)
    }
}

/** Lines on an invoice, in order */
object InvoiceChargeTable : UuidTable("invoice_charges") {
    val invoice = reference("invoice_id", InvoiceTable, onDelete = ReferenceOption.CASCADE)
    val position = integer("position")
    val type = text("type")
    val description = text("description")
    val quantity = double("quantity")
    val unitPriceCents = long("unit_price_cents")
    val inventory = text("inventory_id").nullable()
    val taxable = bool("taxable")

    // Added with gift cards: the card's code on a GIFT_CARD line
    val reference = text("reference").default("")
    val taxClass = text("tax_class").nullable()

    // Added with warranties: the line's own warranty, null for the shop's
    val warrantyDays = integer("warranty_days").nullable()
}

/** Money taken against an invoice, and refunds as negative amounts */
object InvoicePaymentTable : UuidTable("invoice_payments") {
    val invoice = reference("invoice_id", InvoiceTable, onDelete = ReferenceOption.CASCADE)
    val method = text("method")
    val amountCents = long("amount_cents")
    val receivedAt = long("received_at")
    val receivedBy = text("received_by")
    val note = text("note")

    // Added with refunds: parts a refund put back in stock
    val returns = json<List<ReturnedPart>>("returns", Json { ignoreUnknownKeys = true }).default(emptyList())

    // Added with gift cards: the card's code for a GIFT_CARD payment or refund
    val reference = text("reference").default("")
}
