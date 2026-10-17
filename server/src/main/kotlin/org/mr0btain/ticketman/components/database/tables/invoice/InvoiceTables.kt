package org.mr0btain.ticketman.components.database.tables.invoice

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.UuidTable
import org.mr0btain.ticketman.components.database.tables.ticket.TicketTable

/** One invoice per ticket */
object InvoiceTable : UuidTable("invoices") {
    val numberYear = integer("number_year")
    val numberSequence = long("number_sequence")
    val ticket = reference("ticket_id", TicketTable, onDelete = ReferenceOption.CASCADE).uniqueIndex()
    val customer = text("customer_id").nullable()
    val status = text("status")
    val discountCents = long("discount_cents").default(0)
    val taxRateBasisPoints = integer("tax_rate_bp").default(0)
    val subtotalCents = long("subtotal_cents").default(0)
    val taxCents = long("tax_cents").default(0)
    val totalCents = long("total_cents").default(0)
    val createdAt = long("created_at")
    val paidAt = long("paid_at").nullable()

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
}

/** Money taken against an invoice */
object InvoicePaymentTable : UuidTable("invoice_payments") {
    val invoice = reference("invoice_id", InvoiceTable, onDelete = ReferenceOption.CASCADE)
    val method = text("method")
    val amountCents = long("amount_cents")
    val receivedAt = long("received_at")
    val receivedBy = text("received_by")
    val note = text("note")
}
