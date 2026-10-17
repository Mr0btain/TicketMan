package org.mr0btain.ticketman.components.service

import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.mr0btain.ticketman.components.database.AuditLogDatabase
import org.mr0btain.ticketman.components.database.InventoryDatabaseImpl
import org.mr0btain.ticketman.components.database.InvoiceDatabase
import org.mr0btain.ticketman.components.database.ShopDatabase
import org.mr0btain.ticketman.components.database.TicketDatabaseImpl
import org.mr0btain.ticketman.data.audit.AuditAction
import org.mr0btain.ticketman.data.invoice.*
import org.mr0btain.ticketman.data.money.Money
import org.mr0btain.ticketman.data.ticket.TicketStatusType
import org.mr0btain.ticketman.server.request.InvoiceUpdateRequest
import org.mr0btain.ticketman.server.request.PaymentRequest
import org.mr0btain.ticketman.server.request.PickupRequest
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Building invoices, taking payments and picking up tickets
 */
class InvoiceService(
    private val invoices: InvoiceDatabase,
    private val tickets: TicketDatabaseImpl,
    private val inventory: InventoryDatabaseImpl,
    private val shop: ShopDatabase,
    private val auditLog: AuditLogDatabase
) {
    /** The ticket's invoice, made if it doesn't have one */
    fun forTicket(ticketIdentifier: Uuid, actor: Uuid?): AccountResult<Invoice> {
        tickets.getById(ticketIdentifier) ?: return AccountResult.NotFound

        val existing = invoices.getByTicket(ticketIdentifier)
        if (existing != null) return AccountResult.Done(existing)

        val customer = tickets.getCustomer(ticketIdentifier)?.identifier
        val invoice = invoices.getOrCreate(ticketIdentifier, customer, taxRate()) ?: return AccountResult.Invalid("Couldn't make an invoice")

        auditLog.log(actor, AuditAction.CREATE, "invoice", invoice.identifier.toString(), "Invoice ${invoice.label}")
        return AccountResult.Done(invoice)
    }

    /** Replaces an open invoice's lines and discount. Uses the shop's current tax rate */
    fun update(identifier: Uuid, request: InvoiceUpdateRequest, actor: Uuid?): AccountResult<Invoice> {
        val invoice = invoices.getById(identifier) ?: return AccountResult.NotFound
        if (invoice.status != InvoiceStatus.OPEN) return AccountResult.Conflict("Invoice ${invoice.label} is ${invoice.status.name.lowercase()} and can't be changed")

        chargesProblem(request.charges)?.let { return AccountResult.Invalid(it) }
        if (request.discountCents < 0) return AccountResult.Invalid("Discount can't be negative")

        val saved = invoices.setCharges(identifier, request.charges, request.discountCents, taxRate())
            ?: return AccountResult.Invalid("Couldn't save the invoice")

        auditLog.log(actor, AuditAction.UPDATE, "invoice", identifier.toString(), "Invoice ${invoice.label} total ${Money.format(saved.totalCents)}")
        return AccountResult.Done(saved)
    }

    /** Takes a payment, like a deposit at check in */
    fun addPayment(identifier: Uuid, request: PaymentRequest, receivedBy: String, actor: Uuid?): AccountResult<Invoice> {
        val invoice = invoices.getById(identifier) ?: return AccountResult.NotFound
        if (invoice.status != InvoiceStatus.OPEN) return AccountResult.Conflict("Invoice ${invoice.label} is ${invoice.status.name.lowercase()}")
        if (request.amountCents <= 0) return AccountResult.Invalid("Payment has to be more than zero")

        val saved = invoices.addPayment(identifier, request.toPayment(receivedBy)) ?: return AccountResult.Invalid("Couldn't save the payment")

        auditLog.log(actor, AuditAction.CREATE, "payment", identifier.toString(), "${Money.format(request.amountCents)} ${request.method.name.lowercase()} on ${invoice.label}")
        return AccountResult.Done(saved)
    }

    fun removePayment(identifier: Uuid, paymentIdentifier: Uuid, actor: Uuid?): AccountResult<Invoice> {
        val invoice = invoices.getById(identifier) ?: return AccountResult.NotFound
        if (invoice.status != InvoiceStatus.OPEN) return AccountResult.Conflict("Payments on a ${invoice.status.name.lowercase()} invoice can't be removed")
        if (!invoices.removePayment(identifier, paymentIdentifier)) return AccountResult.NotFound

        auditLog.log(actor, AuditAction.DELETE, "payment", identifier.toString(), "Removed a payment from ${invoice.label}")
        return AccountResult.Done(invoices.getById(identifier)!!)
    }

    /**
     * Hands the ticket back. Everything happens together or not at all: the payments are taken, the invoice has to
     * end up paid in full, parts come out of stock and the ticket is closed
     */
    fun pickup(request: PickupRequest, receivedBy: String, actor: Uuid?): AccountResult<Invoice> {
        val ticket = tickets.getById(request.ticketIdentifier) ?: return AccountResult.NotFound
        if (ticket.status.status == TicketStatusType.PICKED_UP) return AccountResult.Conflict("Ticket ${ticket.label} was already picked up")
        if (request.payments.any { it.amountCents <= 0 }) return AccountResult.Invalid("Payments have to be more than zero")

        val invoice = when (val result = forTicket(request.ticketIdentifier, actor)) {
            is AccountResult.Done -> result.value
            else -> return AccountResult.Invalid("Couldn't find or make the invoice")
        }

        if (invoice.status != InvoiceStatus.OPEN) return AccountResult.Conflict("Invoice ${invoice.label} is ${invoice.status.name.lowercase()}")

        val paying = request.payments.sumOf { it.amountCents }
        val owed = invoice.balanceCents - paying
        if (owed > 0) return AccountResult.Conflict("${Money.format(owed)} is still owed on ${invoice.label}")

        val now = Clock.System.now().toEpochMilliseconds()

        transaction {
            request.payments.forEach { payment -> invoices.addPayment(invoice.identifier, payment.toPayment(receivedBy)) }

            invoice.charges
                .filter { it.type == ChargeType.PART }
                .forEach { charge -> charge.inventoryIdentifier?.let { inventory.adjustStock(it, charge.quantity.roundToLong().toInt()) } }

            invoices.setStatus(invoice.identifier, InvoiceStatus.PAID, paidAt = now)
            tickets.markPickedUp(ticket.identifier, now)
            tickets.recordStatusChange(ticket.identifier, TicketStatusType.PICKED_UP, receivedBy)
        }

        auditLog.log(actor, AuditAction.UPDATE, "ticket", ticket.identifier.toString(), "Ticket ${ticket.label} picked up, ${invoice.label} paid ${Money.format(invoice.totalCents)}")
        return AccountResult.Done(invoices.getById(invoice.identifier)!!)
    }

    /**
     * Cancels an invoice. A paid one puts its parts back in stock. The ticket stays as it is
     */
    fun void(identifier: Uuid, actor: Uuid?): AccountResult<Invoice> {
        val invoice = invoices.getById(identifier) ?: return AccountResult.NotFound
        if (invoice.status == InvoiceStatus.VOID) return AccountResult.Conflict("Invoice ${invoice.label} is already void")

        transaction {
            if (invoice.status == InvoiceStatus.PAID) {
                invoice.charges
                    .filter { it.type == ChargeType.PART }
                    .forEach { charge -> charge.inventoryIdentifier?.let { inventory.adjustStock(it, -charge.quantity.roundToLong().toInt()) } }
            }

            invoices.setStatus(identifier, InvoiceStatus.VOID)
        }

        auditLog.log(actor, AuditAction.DELETE, "invoice", identifier.toString(), "Voided ${invoice.label}")
        return AccountResult.Done(invoices.getById(identifier)!!)
    }

    // Tax rate from the shop profile, nothing if it's blank or not a number
    private fun taxRate(): Int = Money.percentToBasisPoints(shop.get().taxRate) ?: 0

    private fun chargesProblem(charges: List<Charge>): String? {
        charges.forEach { charge ->
            when {
                charge.quantity <= 0 -> return "Quantity has to be more than zero"
                abs(charge.quantity * 100 - (charge.quantity * 100).roundToLong()) > 1e-6 -> return "Quantity can only have two decimals"
                charge.unitPriceCents < 0 -> return "Prices can't be negative"
                charge.description.isBlank() -> return "Every line needs a description"
                charge.type == ChargeType.PART && charge.inventoryIdentifier == null -> return "Parts need an inventory item"
                charge.type == ChargeType.PART && charge.quantity % 1.0 != 0.0 -> return "Parts are counted in whole numbers"
                charge.inventoryIdentifier != null && inventory.getById(charge.inventoryIdentifier!!) == null -> return "Inventory item for \"${charge.description}\" doesn't exist"
            }
        }

        return null
    }

    private fun PaymentRequest.toPayment(receivedBy: String): Payment =
        Payment(
            method = method,
            amountCents = amountCents,
            receivedAt = Clock.System.now().toEpochMilliseconds(),
            receivedBy = receivedBy,
            note = note.trim()
        )
}
