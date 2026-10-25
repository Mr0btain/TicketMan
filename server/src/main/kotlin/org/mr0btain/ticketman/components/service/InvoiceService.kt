package org.mr0btain.ticketman.components.service

import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.mr0btain.ticketman.components.database.AuditLogDatabase
import org.mr0btain.ticketman.components.database.CustomerDatabase
import org.mr0btain.ticketman.components.database.InventoryDatabase
import org.mr0btain.ticketman.components.database.InvoiceDatabase
import org.mr0btain.ticketman.components.database.PasscodeDatabase
import org.mr0btain.ticketman.components.database.ShopDatabase
import org.mr0btain.ticketman.components.database.StoredValueDatabase
import org.mr0btain.ticketman.data.giftcard.GiftCardCode
import org.mr0btain.ticketman.data.invoice.TaxClasses
import org.mr0btain.ticketman.data.invoice.TaxRate
import org.mr0btain.ticketman.components.database.TicketDatabase
import org.mr0btain.ticketman.data.audit.AuditAction
import org.mr0btain.ticketman.data.invoice.*
import org.mr0btain.ticketman.data.money.Money
import org.mr0btain.ticketman.data.ticket.Ticket
import org.mr0btain.ticketman.data.ticket.TicketStatus
import org.mr0btain.ticketman.data.shop.Storage
import org.mr0btain.ticketman.data.ticket.TicketStatusType
import org.mr0btain.ticketman.server.request.InvoiceChangeRequest
import org.mr0btain.ticketman.server.request.PaymentRequest
import org.mr0btain.ticketman.server.request.PickupRequest
import org.mr0btain.ticketman.server.request.RefundRequest
import org.mr0btain.ticketman.server.request.SaleRequest
import org.mr0btain.ticketman.server.request.WorkRequest
import org.mr0btain.ticketman.data.ticket.WorkEntry
import org.mr0btain.ticketman.data.ticket.WorkType
import org.mr0btain.ticketman.components.database.WorkDatabase
import org.mr0btain.ticketman.components.database.PaymentDatabase
import org.mr0btain.ticketman.components.database.LocationDatabase
import org.mr0btain.ticketman.components.payments.CardPayments
import org.mr0btain.ticketman.components.payments.ProcessorResult
import org.mr0btain.ticketman.data.payment.CardCharge
import org.mr0btain.ticketman.data.payment.CardChargeStatus
import org.mr0btain.ticketman.components.utils.formatQuantity
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Building invoices, taking payments, logging work on tickets and picking up tickets
 */
class InvoiceService(
    private val invoices: InvoiceDatabase,
    private val tickets: TicketDatabase,
    private val inventory: InventoryDatabase,
    private val shop: ShopDatabase,
    private val auditLog: AuditLogDatabase,
    private val work: WorkDatabase = WorkDatabase(),
    private val customers: CustomerDatabase = CustomerDatabase(),
    private val values: StoredValueDatabase = StoredValueDatabase(),
    private val passcodes: PasscodeDatabase = PasscodeDatabase(),
    private val cards: CardPayments = CardPayments(PaymentDatabase()),
    private val locations: LocationDatabase = LocationDatabase()
) {
    private val storedValue = StoredValuePayments(values) { money(it) }

    /** The ticket's invoice, made if it doesn't have one, with its storage fee brought up to date */
    fun forTicket(ticketIdentifier: Uuid, actor: Uuid?): AccountResult<Invoice> {
        val ticket = tickets.getById(ticketIdentifier) ?: return AccountResult.NotFound

        val existing = invoices.getByTicket(ticketIdentifier)
        if (existing != null) return AccountResult.Done(syncStorage(existing, ticket))

        val customer = tickets.getCustomer(ticketIdentifier)?.identifier
        // At the ticket's location's rates
        val profile = profileAt(ticket.locationIdentifier)
        val invoice = invoices.getOrCreate(ticketIdentifier, customer, profile.taxBasisPoints, profile.taxRates) ?: return AccountResult.Invalid("Couldn't make an invoice")

        auditLog.log(actor, AuditAction.CREATE, "invoice", invoice.identifier.toString(), "Invoice ${invoice.label}")
        return AccountResult.Done(syncStorage(invoice, ticket))
    }

    /**
     * Puts the storage fee on an open invoice as one line, a day's fee for each day past the free ones, or takes it
     * off when the ticket isn't ready any more. Not once staff took the line off, which waives it
     */
    private fun syncStorage(invoice: Invoice, ticket: Ticket): Invoice = synchronized(CHANGE_LOCK) {
        if (invoice.status != InvoiceStatus.OPEN || invoice.storageWaived) return invoice
        val terms = shop.get().storage
        val days = Storage.feeDays(ticket, terms, Clock.System.now().toEpochMilliseconds())
        val line = Storage.lineIdentifier(ticket.identifier)
        val existing = invoice.charges.firstOrNull { it.identifier == line }

        val charges = when {
            days == 0 && existing == null -> return invoice
            days == 0 -> invoice.charges.filter { it.identifier != line }
            existing == null -> invoice.charges + Charge(line, ChargeType.OTHER, Storage.description(days), days.toDouble(), terms.dailyFeeCents, taxable = false)
            existing.quantity == days.toDouble() -> return invoice
            else -> invoice.charges.map { if (it.identifier == line) it.copy(quantity = days.toDouble(), description = Storage.description(days)) else it }
        }

        return invoices.setCharges(invoice.identifier, charges, invoice.discountCents, invoice.taxRateBasisPoints) ?: invoice
    }

    /**
     * Applies what someone changed on an open invoice to the lines it has now, so a line added meanwhile (like logged
     * work) stays. A line someone else changed or removed since is a conflict. Keeps the tax rate the invoice was made
     * with, so changing the shop's rate doesn't change what a customer was already quoted
     */
    fun update(identifier: Uuid, request: InvoiceChangeRequest, actor: Uuid?): AccountResult<Invoice> = synchronized(CHANGE_LOCK) {
        val invoice = invoices.getById(identifier) ?: return AccountResult.NotFound
        if (invoice.status != InvoiceStatus.OPEN) return AccountResult.Conflict("Invoice ${invoice.label} is ${invoice.status.name.lowercase()} and can't be changed")
        if (request.isEmpty) return AccountResult.Done(invoice)

        val (charges, discount) = when (val outcome = InvoiceChanges.apply(invoice.charges, invoice.discountCents, request)) {
            is InvoiceChanges.Outcome.Applied -> outcome.charges to outcome.discountCents
            is InvoiceChanges.Outcome.Conflict -> return AccountResult.Conflict(outcome.message)
        }

        chargesProblem(charges)?.let { return AccountResult.Invalid(it) }
        if (discount < 0) return AccountResult.Invalid("Discount can't be negative")
        val rates = ratesFor(invoice.taxRates, charges, invoice.locationIdentifier) ?: return AccountResult.Invalid(unknownClass(charges))

        // Taking the storage line off waives it, so it isn't put back
        invoice.ticketIdentifier?.let { ticket ->
            if (request.removed.any { it.identifier == Storage.lineIdentifier(ticket) }) invoices.setStorageWaived(identifier)
        }

        val saved = invoices.setCharges(identifier, charges, discount, invoice.taxRateBasisPoints, rates)
            ?: return AccountResult.Invalid("Couldn't save the invoice")

        auditLog.log(actor, AuditAction.UPDATE, "invoice", identifier.toString(), "Invoice ${invoice.label} total ${money(saved.totalCents)}")
        return AccountResult.Done(saved)
    }

    /** Takes a payment, like a deposit at check in. Store credit and gift cards are spent then */
    fun addPayment(identifier: Uuid, request: PaymentRequest, receivedBy: String, actor: Uuid?): AccountResult<Invoice> = synchronized(CHANGE_LOCK) {
        val invoice = invoices.getById(identifier) ?: return AccountResult.NotFound
        if (invoice.status != InvoiceStatus.OPEN) return AccountResult.Conflict("Invoice ${invoice.label} is ${invoice.status.name.lowercase()}")
        if (request.amountCents <= 0) return AccountResult.Invalid("Payment has to be more than zero")
        storedValue.problem(listOf(request), invoice.customerIdentifier)?.let { return AccountResult.Conflict(it) }
        cards.problem(listOf(request))?.let { return AccountResult.Conflict(it) }

        val payment = request.toPayment(receivedBy)
        val saved = try {
            transaction {
                val saved = invoices.addPayment(identifier, payment) ?: throw IllegalStateException("Payment not saved")
                storedValue.take(payment, invoice.customerIdentifier, identifier, invoice.label, receivedBy)
                cards.claim(payment, identifier, invoice.label)
                saved
            }
        } catch (ex: StoredValuePayments.ShortOfValue) {
            return AccountResult.Conflict(ex.message)
        } catch (ex: CardPayments.ChargeTaken) {
            return AccountResult.Conflict(ex.message)
        }

        auditLog.log(actor, AuditAction.CREATE, "payment", identifier.toString(), "${money(request.amountCents)} ${describe(payment)} on ${invoice.label}")
        return AccountResult.Done(saved)
    }

    /**
     * Takes a payment back off an open invoice, for one entered by mistake. Store credit or a gift card gets it back,
     * and one taken through the card processor is refunded onto the card first, so it's only taken off once the
     * money is on its way back
     */
    fun removePayment(identifier: Uuid, paymentIdentifier: Uuid, actor: Uuid?): AccountResult<Invoice> = synchronized(CHANGE_LOCK) {
        val invoice = invoices.getById(identifier) ?: return AccountResult.NotFound
        if (invoice.status != InvoiceStatus.OPEN) return AccountResult.Conflict("Payments on a ${invoice.status.name.lowercase()} invoice can't be removed")
        val payment = invoice.payments.firstOrNull { it.identifier == paymentIdentifier } ?: return AccountResult.NotFound
        if (payment.isRefund) return AccountResult.Conflict("A refund can't be taken off")

        val refunded = if (payment.processor != null) {
            when (val result = cards.refund(payment, invoice.refundableCents(payment), "remove-${payment.identifier}")) {
                is ProcessorResult.Ok -> result.value
                is ProcessorResult.Failed -> return AccountResult.Conflict("The card wasn't refunded, so the payment is still on: ${result.reason}")
            }
        } else null

        transaction {
            if (!invoices.removePayment(identifier, paymentIdentifier)) throw IllegalStateException("Payment not removed")
            // Refunds made against it go with it, the money they gave back is part of this one's
            invoice.payments.filter { it.refundOf == paymentIdentifier }.forEach { invoices.removePayment(identifier, it.identifier) }
            storedValue.giveBack(payment, invoice.customerIdentifier, identifier, invoice.label, payment.receivedBy)
            payment.chargeIdentifier?.let { cards.charges.markReturned(it, Clock.System.now().toEpochMilliseconds()) }
            val back = refunded?.let { ", refunded onto the card ($it)" }.orEmpty()
            auditLog.log(actor, AuditAction.DELETE, "payment", identifier.toString(), "Removed ${money(payment.amountCents)} ${describe(payment)} from ${invoice.label}$back")
        }

        return AccountResult.Done(invoices.getById(identifier)!!)
    }

    /**
     * Puts a charge the processor says is paid onto its invoice, as a card payment, and marks the charge paid, both
     * at once and only the first time it's asked. Money taken is always recorded: on an invoice paid or voided
     * meanwhile too, where it shows as overpaid for an admin to refund. A charge whose invoice is gone, or that never
     * had one, is left paid for a payment to claim
     * @return The charge as saved, null when it had already been finished
     */
    fun cardCharged(charge: CardCharge, paymentReference: String): CardCharge? = synchronized(CHANGE_LOCK) {
        val now = Clock.System.now().toEpochMilliseconds()
        val invoice = charge.invoiceIdentifier?.let { invoices.getById(it) }
        val what = "${money(charge.amountCents)} ${charge.kind.label.lowercase()} payment through ${charge.provider.label}"

        if (invoice == null) {
            if (!cards.charges.finish(charge.identifier, CardChargeStatus.SUCCEEDED, now, paymentReference = paymentReference)) return null
            if (charge.invoiceIdentifier != null) {
                cards.charges.detach(charge.identifier)
                auditLog.log(null, AuditAction.CREATE, "payment", charge.identifier.toString(), "$what went through after ${charge.invoiceLabel} was deleted, refund it from Admin > Payments", actorName = charge.createdBy)
            }
            return cards.charges.get(charge.identifier)
        }

        val payment = Payment(
            method = PaymentMethod.CARD,
            amountCents = charge.amountCents,
            receivedAt = now,
            receivedBy = charge.createdBy,
            note = charge.readerName.ifBlank { charge.kind.label },
            processor = charge.provider,
            processorReference = paymentReference,
            chargeIdentifier = charge.identifier
        )

        val saved = transaction {
            if (!cards.charges.finish(charge.identifier, CardChargeStatus.SUCCEEDED, now, paymentReference = paymentReference, paymentIdentifier = payment.identifier)) {
                return@transaction false
            }
            invoices.addPayment(invoice.identifier, payment) ?: throw IllegalStateException("Card payment not saved")
            val late = if (invoice.status != InvoiceStatus.OPEN) ", which was already ${invoice.status.name.lowercase()}: refund what's over" else ""
            auditLog.log(null, AuditAction.CREATE, "payment", invoice.identifier.toString(), "$what on ${invoice.label}$late", actorName = charge.createdBy)
            true
        }

        return if (saved) cards.charges.get(charge.identifier) else null
    }

    /**
     * Hands the ticket back. Everything happens together or not at all: the payments are taken, the invoice has to
     * end up paid in full, parts come out of stock, gift cards on it are loaded and the ticket is closed. A part
     * without enough in stock stops the pickup, so the count gets fixed instead of going below zero
     */
    fun pickup(request: PickupRequest, receivedBy: String, actor: Uuid?): AccountResult<Invoice> = synchronized(CHANGE_LOCK) {
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
        if (owed > 0) return AccountResult.Conflict("${money(owed)} is still owed on ${invoice.label}")
        storedValue.problem(request.payments, invoice.customerIdentifier)?.let { return AccountResult.Conflict(it) }
        cards.problem(request.payments)?.let { return AccountResult.Conflict(it) }

        // From the invoice's location's stock
        val location = locationOf(invoice)
        val parts = partsUsed(invoice.charges)
        parts.forEach { (identifier, quantity) ->
            val stock = inventory.stockAt(identifier, location) ?: return@forEach
            if (stock != Int.MAX_VALUE && stock < quantity) return shortOf(identifier, location)
        }

        val now = Clock.System.now().toEpochMilliseconds()

        try {
            transaction {
                takePayments(invoice, request.payments, receivedBy)

                // A count set by hand since the check above can still leave too few, which undoes the lot
                parts.forEach { (identifier, quantity) ->
                    if (!inventory.adjustStock(identifier, location, quantity)) throw ShortOfStock(identifier)
                }

                loadGiftCards(invoice)
                invoices.recordCosts(invoice.identifier)
                invoices.setStatus(invoice.identifier, InvoiceStatus.PAID, paidAt = now)
                // The warranty starts now, as long as the longest a line gives
                val days = Warranty.days(invoice.charges, shop.get().warrantyDays)
                tickets.markPickedUp(ticket.identifier, now, if (days > 0) now + days * Warranty.DAY_MILLIS else null)
                tickets.recordStatusChange(ticket.identifier, TicketStatus(TicketStatusType.PICKED_UP), receivedBy)
                // The device is the customer's again, its passcode isn't ours to keep
                passcodes.clear(ticket.identifier)

                // Part of the transaction, so it's only there if the pickup is
                auditLog.log(actor, AuditAction.UPDATE, "ticket", ticket.identifier.toString(), "Ticket ${ticket.label} picked up, ${invoice.label} paid ${money(invoice.totalCents)}")
            }
        } catch (ex: ShortOfStock) {
            return shortOf(ex.identifier, location)
        } catch (ex: StoredValuePayments.ShortOfValue) {
            return AccountResult.Conflict(ex.message)
        } catch (ex: CardPayments.ChargeTaken) {
            return AccountResult.Conflict(ex.message)
        }

        return AccountResult.Done(invoices.getById(invoice.identifier)!!)
    }

    /**
     * Sells straight from stock with no ticket. Like a pickup it's all or nothing: the invoice is made with its
     * lines, the payments have to cover it, the parts come out of stock, gift cards on it are loaded and it's saved
     * PAID. Taxed at, and taken from the stock of, [location], the main one for null
     */
    fun sell(request: SaleRequest, receivedBy: String, actor: Uuid?, location: Uuid? = null): AccountResult<Invoice> = synchronized(CHANGE_LOCK) {
        val at = location ?: locations.main().identifier
        val profile = profileAt(at)
        linesProblem(request.charges)?.let { return AccountResult.Invalid(it) }
        if (request.discountCents < 0) return AccountResult.Invalid("Discount can't be negative")
        if (request.payments.any { it.amountCents <= 0 }) return AccountResult.Invalid("Payments have to be more than zero")
        request.customerIdentifier?.let { if (customers.getById(it) == null) return AccountResult.Invalid("That customer doesn't exist") }

        val taxRate = profile.taxBasisPoints
        val rates = ratesFor(profile.taxRates, request.charges, at) ?: return AccountResult.Invalid(unknownClass(request.charges))
        val totals = InvoiceMath.totals(request.charges, request.discountCents, taxRate, rates)
        val owed = totals.totalCents - request.payments.sumOf { it.amountCents }
        if (owed > 0) return AccountResult.Conflict("${money(owed)} is still owed")
        storedValue.problem(request.payments, request.customerIdentifier)?.let { return AccountResult.Conflict(it) }
        cards.problem(request.payments)?.let { return AccountResult.Conflict(it) }

        val parts = partsUsed(request.charges)
        parts.forEach { (identifier, quantity) ->
            val stock = inventory.stockAt(identifier, at) ?: return@forEach
            if (stock != Int.MAX_VALUE && stock < quantity) return shortOf(identifier, at)
        }

        val now = Clock.System.now().toEpochMilliseconds()

        val sold = try {
            invoices.whileNumbering {
                transaction {
                    val created = invoices.createSale(request.customerIdentifier, taxRate, rates, at) ?: throw IllegalStateException("Sale not made")
                    val invoice = invoices.setCharges(created.identifier, request.charges, request.discountCents, taxRate, rates) ?: throw IllegalStateException("Sale not made")
                    takePayments(invoice, request.payments, receivedBy)

                    parts.forEach { (identifier, quantity) ->
                        if (!inventory.adjustStock(identifier, at, quantity)) throw ShortOfStock(identifier)
                    }

                    loadGiftCards(invoice)
                    invoices.recordCosts(invoice.identifier)
                    invoices.setStatus(invoice.identifier, InvoiceStatus.PAID, paidAt = now)
                    auditLog.log(actor, AuditAction.CREATE, "invoice", invoice.identifier.toString(), "Sold ${invoice.label}, ${money(totals.totalCents)}")
                    invoice.identifier
                }
            }
        } catch (ex: ShortOfStock) {
            return shortOf(ex.identifier, at)
        } catch (ex: StoredValuePayments.ShortOfValue) {
            return AccountResult.Conflict(ex.message)
        } catch (ex: CardPayments.ChargeTaken) {
            return AccountResult.Conflict(ex.message)
        }

        return AccountResult.Done(invoices.getById(sold)!!)
    }

    // Adds each payment and spends any stored value it uses. Inside a transaction
    private fun takePayments(invoice: Invoice, requests: List<PaymentRequest>, receivedBy: String) {
        requests.forEach { request ->
            val payment = request.toPayment(receivedBy)
            invoices.addPayment(invoice.identifier, payment)
            storedValue.take(payment, invoice.customerIdentifier, invoice.identifier, invoice.label, receivedBy)
            cards.claim(payment, invoice.identifier, invoice.label)
        }
    }

    // Loads the gift cards the invoice sells, giving new ones a code. Inside the transaction that pays it
    private fun loadGiftCards(invoice: Invoice) {
        if (invoice.charges.none { it.type == ChargeType.GIFT_CARD }) return

        val charges = invoice.charges.map { charge ->
            if (charge.type != ChargeType.GIFT_CARD) return@map charge
            val code = charge.reference.ifBlank { GiftCardCode.generate() }
            values.loadGiftCard(code, charge.totalCents, invoice.identifier, invoice.customerIdentifier) ?: throw IllegalStateException("Gift card not loaded")
            charge.copy(reference = GiftCardCode.normalize(code))
        }
        invoices.setCharges(invoice.identifier, charges, invoice.discountCents, invoice.taxRateBasisPoints)
    }

    /**
     * Gives money back on a paid invoice, and puts returned parts back in stock, together. Admin only. The refund is
     * a payment with a negative amount and the reason as its note; it can't be more than was paid less earlier
     * refunds, and a part can't come back more times than it was sold. Store credit goes to the invoice's customer,
     * a gift card refund onto the card given or a new one
     */
    fun refund(identifier: Uuid, request: RefundRequest, givenBy: String, actor: Uuid?): AccountResult<Invoice> = synchronized(CHANGE_LOCK) {
        val invoice = invoices.getById(identifier) ?: return AccountResult.NotFound
        if (invoice.status != InvoiceStatus.PAID) {
            return AccountResult.Conflict("Only a paid invoice can be refunded, ${invoice.label} is ${invoice.status.name.lowercase()}" +
                if (invoice.status == InvoiceStatus.OPEN) ", take the payment off instead" else "")
        }

        val reason = request.reason.trim()
        if (reason.isBlank()) return AccountResult.Invalid("Give a reason for the refund")
        if (request.amountCents < 0) return AccountResult.Invalid("Refund the amount as a positive number")
        if (request.amountCents == 0L && request.returns.isEmpty()) return AccountResult.Invalid("Refund an amount or return a part")
        if (request.amountCents > invoice.netPaidCents) return AccountResult.Invalid("Only ${money(invoice.netPaidCents)} was paid on ${invoice.label}")
        if (request.method == PaymentMethod.STORE_CREDIT && invoice.customerIdentifier == null) return AccountResult.Invalid("${invoice.label} has no customer to give store credit to")
        if (request.method == PaymentMethod.GIFT_CARD && request.reference.isNotBlank()) GiftCardCode.problem(request.reference)?.let { return AccountResult.Invalid(it) }

        // Back onto the card it came from, through the processor, no more than is left of that payment
        val onto = request.paymentIdentifier?.let { paymentIdentifier ->
            val paid = invoice.payments.firstOrNull { it.identifier == paymentIdentifier } ?: return AccountResult.Invalid("That payment isn't on ${invoice.label}")
            if (paid.processor == null || paid.isRefund) return AccountResult.Invalid("Only a card payment taken through the processor goes back onto its card")
            if (request.method != PaymentMethod.CARD) return AccountResult.Invalid("A refund onto the card is by card")
            val left = invoice.refundableCents(paid)
            if (request.amountCents > left) return AccountResult.Invalid("Only ${money(left)} of that card payment is left to give back")
            paid
        }

        // Each item once, added up, and only what was sold and not already returned
        val returns = request.returns.groupBy { it.inventoryIdentifier }.map { (item, lines) -> ReturnedPart(item, lines.sumOf { it.quantity }) }
        val sold = invoice.partsSold
        val returned = invoice.returnedParts
        returns.forEach { part ->
            if (part.quantity <= 0) return AccountResult.Invalid("Return a whole number of parts, more than zero")
            if (part.inventoryIdentifier !in sold) return AccountResult.Invalid("That part isn't on ${invoice.label}")
            val left = (sold[part.inventoryIdentifier] ?: 0) - (returned[part.inventoryIdentifier] ?: 0)
            if (part.quantity > left) {
                val name = invoice.charges.first { it.inventoryIdentifier == part.inventoryIdentifier }.description
                return AccountResult.Invalid(if (left <= 0) "No $name left to return on ${invoice.label}" else "Only $left of $name can be returned on ${invoice.label}")
            }
        }

        val card = if (request.method == PaymentMethod.GIFT_CARD && request.amountCents > 0) GiftCardCode.normalize(request.reference.ifBlank { GiftCardCode.generate() }) else ""
        val payment = Payment(
            method = request.method,
            amountCents = -request.amountCents,
            receivedAt = Clock.System.now().toEpochMilliseconds(),
            receivedBy = givenBy,
            note = reason,
            returns = returns,
            reference = card,
            processor = onto?.processor,
            refundOf = onto?.identifier
        ).let { refund ->
            if (onto == null || request.amountCents == 0L) return@let refund
            // The money goes back before it's recorded, so a refused refund leaves nothing saved
            when (val result = cards.refund(onto, request.amountCents, "refund-${refund.identifier}")) {
                is ProcessorResult.Ok -> refund.copy(processorReference = result.value)
                is ProcessorResult.Failed -> return AccountResult.Conflict("The card wasn't refunded: ${result.reason}")
            }
        }

        transaction {
            invoices.addPayment(identifier, payment) ?: throw IllegalStateException("Refund not saved")
            returns.forEach { part -> inventory.adjustStock(part.inventoryIdentifier, locationOf(invoice), -part.quantity) }

            if (request.amountCents > 0) when (request.method) {
                PaymentMethod.STORE_CREDIT -> if (!values.changeCredit(invoice.customerIdentifier!!, request.amountCents, "Refund on ${invoice.label}: $reason", identifier, givenBy)) {
                    throw IllegalStateException("Store credit not given")
                }
                PaymentMethod.GIFT_CARD -> values.loadGiftCard(card, request.amountCents, identifier, invoice.customerIdentifier) ?: throw IllegalStateException("Gift card not loaded")
                else -> {}
            }

            val back = if (returns.isEmpty()) "" else ", ${returns.sumOf { it.quantity }} part${if (returns.sumOf { it.quantity } == 1) "" else "s"} back in stock"
            auditLog.log(actor, AuditAction.REFUND, "invoice", identifier.toString(), "Refunded ${money(request.amountCents)} ${describe(payment)} on ${invoice.label}$back: $reason")
        }

        return AccountResult.Done(invoices.getById(identifier)!!)
    }

    /**
     * Cancels an invoice. A paid one puts its parts back in stock, less any a refund already returned, and takes back
     * the gift cards it loaded, refusing if one has been spent since. The ticket stays as it is
     */
    fun void(identifier: Uuid, actor: Uuid?): AccountResult<Invoice> = synchronized(CHANGE_LOCK) {
        val invoice = invoices.getById(identifier) ?: return AccountResult.NotFound
        if (invoice.status == InvoiceStatus.VOID) return AccountResult.Conflict("Invoice ${invoice.label} is already void")

        try {
            transaction {
                if (invoice.status == InvoiceStatus.PAID) {
                    val returned = invoice.returnedParts
                    partsUsed(invoice.charges).forEach { (identifier, quantity) ->
                        val back = quantity - (returned[identifier] ?: 0)
                        if (back > 0) inventory.adjustStock(identifier, locationOf(invoice), -back)
                    }

                    invoice.charges.filter { it.type == ChargeType.GIFT_CARD && it.reference.isNotBlank() }.forEach { charge ->
                        if (!values.unloadGiftCard(charge.reference, charge.totalCents)) {
                            throw StoredValuePayments.ShortOfValue("Gift card ${charge.reference} has been spent, refund instead of voiding")
                        }
                    }
                }

                invoices.setStatus(identifier, InvoiceStatus.VOID)
                auditLog.log(actor, AuditAction.DELETE, "invoice", identifier.toString(), "Voided ${invoice.label}")
            }
        } catch (ex: StoredValuePayments.ShortOfValue) {
            return AccountResult.Conflict(ex.message)
        }

        return AccountResult.Done(invoices.getById(identifier)!!)
    }

    /**
     * Deletes an invoice kept after its ticket was deleted, once an admin decides it isn't needed. Stock taken for
     * its parts stays taken; void it first to put that back. One whose ticket is still there goes with the ticket
     */
    fun remove(identifier: Uuid, actor: Uuid?): AccountResult<Unit> = synchronized(CHANGE_LOCK) {
        val invoice = invoices.getById(identifier) ?: return AccountResult.NotFound
        if (!invoice.ticketRemoved) return AccountResult.Conflict("Invoice ${invoice.label} still has its ticket, it's deleted with the ticket")

        transaction {
            if (!invoices.remove(identifier)) throw IllegalStateException("Invoice ${invoice.label} wasn't deleted")
            auditLog.log(actor, AuditAction.DELETE, "invoice", identifier.toString(), "Deleted ${invoice.label} (ticket ${invoice.ticketLabel}, ${money(invoice.paidCents)} paid)")
        }

        return AccountResult.Done(Unit)
    }

    /** The ticket's invoice, without making one */
    fun invoiceFor(ticketIdentifier: Uuid): Invoice? = invoices.getByTicket(ticketIdentifier)

    // ------------------------------------------ Estimates ------------------------------------------

    /** What's wrong with lines for an invoice or an estimate, or null */
    fun linesProblem(charges: List<Charge>): String? =
        if (charges.isEmpty()) "Add at least one line" else chargesProblem(charges)

    /**
     * Puts an approved estimate's lines on the ticket's open invoice, made then if it has none. [alongside] runs in
     * the same transaction, first: when it returns false (the estimate was answered meanwhile) nothing is saved
     */
    @OptIn(kotlin.uuid.ExperimentalUuidApi::class)
    fun addApprovedLines(ticketIdentifier: Uuid, lines: List<Charge>, label: String, actor: Uuid?, alongside: () -> Boolean): AccountResult<Invoice> = synchronized(CHANGE_LOCK) {
        val ticket = tickets.getById(ticketIdentifier) ?: return AccountResult.NotFound
        if (ticket.status.status == TicketStatusType.PICKED_UP) return AccountResult.Conflict("Ticket ${ticket.label} was already picked up")

        // Fresh ids, so lines copied from the invoice into an estimate don't clash with themselves
        val charges = lines.map { it.copy(identifier = Uuid.generateV7()) }
        chargesProblem(charges)?.let { return AccountResult.Invalid(it) }

        val invoice = when (val result = forTicket(ticketIdentifier, actor)) {
            is AccountResult.Done -> result.value
            else -> return AccountResult.Invalid("Couldn't find or make the invoice")
        }
        if (invoice.status != InvoiceStatus.OPEN) return AccountResult.Conflict("Invoice ${invoice.label} is ${invoice.status.name.lowercase()}, the estimate can't be added to it")
        val rates = ratesFor(invoice.taxRates, charges, invoice.locationIdentifier) ?: return AccountResult.Invalid(unknownClass(charges))

        val saved = try {
            transaction {
                if (!alongside()) throw AlreadyAnswered()
                invoices.setCharges(invoice.identifier, invoice.charges + charges, invoice.discountCents, invoice.taxRateBasisPoints, rates)
                    ?: throw IllegalStateException("Invoice not saved")
            }
        } catch (_: AlreadyAnswered) {
            return AccountResult.Conflict("That estimate was already answered")
        }

        auditLog.log(actor, AuditAction.UPDATE, "invoice", invoice.identifier.toString(), "$label added to ${invoice.label}, total ${money(saved.totalCents)}")
        return AccountResult.Done(saved)
    }

    private class AlreadyAnswered : Exception()

    // ------------------------------------------ Work log ------------------------------------------

    /**
     * Logs time or a part on a ticket and puts the matching line on its invoice, made then if it has none. Both are
     * saved together. Stock is still taken at pickup, from the invoice's lines
     * @param userIdentifier Who's logging it, kept on the entry with [userName]
     */
    fun logWork(ticketIdentifier: Uuid, request: WorkRequest, userIdentifier: Uuid?, userName: String): AccountResult<WorkEntry> =
        when (val result = logWork(ticketIdentifier, listOf(request), userIdentifier, userName)) {
            is AccountResult.Done -> AccountResult.Done(result.value.single())
            is AccountResult.NotFound -> AccountResult.NotFound
            is AccountResult.Invalid -> result
            is AccountResult.Conflict -> result
        }

    /**
     * Logs several things at once, like the time and the parts for one repair: all of them with their invoice lines,
     * or none if one is wrong
     * @return The entries as saved, in the order sent
     */
    fun logWork(ticketIdentifier: Uuid, requests: List<WorkRequest>, userIdentifier: Uuid?, userName: String): AccountResult<List<WorkEntry>> = synchronized(CHANGE_LOCK) {
        if (requests.isEmpty()) return AccountResult.Invalid("Nothing to log")
        val ticket = tickets.getById(ticketIdentifier) ?: return AccountResult.NotFound
        if (ticket.status.status == TicketStatusType.PICKED_UP) return AccountResult.Conflict("Ticket ${ticket.label} was picked up, its work can't change")

        val workedAt = Clock.System.now().toEpochMilliseconds()
        val entries = requests.map { request ->
            val item = request.inventoryIdentifier?.let { inventory.getById(it) ?: return AccountResult.Invalid("That inventory item doesn't exist") }
            if (request.type == WorkType.PART && item == null) return AccountResult.Invalid("Pick the part from inventory")

            WorkEntry(
                type = request.type,
                description = request.description.trim().ifBlank { item?.name.orEmpty() },
                quantity = request.quantity,
                // A warranty return's work is free unless a price is given
                unitPriceCents = request.unitPriceCents ?: if (ticket.warrantyOf != null) 0 else if (request.type == WorkType.PART) item!!.priceCents else shop.get().laborRateCents,
                inventoryIdentifier = item?.identifier,
                userIdentifier = userIdentifier,
                userName = userName,
                workedAt = workedAt
            )
        }
        // Parts at their item's tax class. One the shop has since taken out is charged at the main rate
        val classes = shop.get().taxRates.map { it.identifier }.toSet()
        val charges = entries.map { entry ->
            entry.toCharge().copy(taxClass = entry.inventoryIdentifier?.let { inventory.getById(it)?.taxClass }?.takeIf { it in classes })
        }
        chargesProblem(charges)?.let { return AccountResult.Invalid(it) }

        val invoice = when (val result = forTicket(ticketIdentifier, userIdentifier)) {
            is AccountResult.Done -> result.value
            else -> return AccountResult.Invalid("Couldn't find or make the invoice")
        }
        if (invoice.status != InvoiceStatus.OPEN) return AccountResult.Conflict("Invoice ${invoice.label} is ${invoice.status.name.lowercase()}, work can't be added to it")
        val rates = ratesFor(invoice.taxRates, charges, invoice.locationIdentifier) ?: return AccountResult.Invalid(unknownClass(charges))

        transaction {
            entries.forEach { work.add(ticketIdentifier, it) }
            invoices.setCharges(invoice.identifier, invoice.charges + charges, invoice.discountCents, invoice.taxRateBasisPoints, rates)
            auditLog.log(userIdentifier, AuditAction.CREATE, "ticket", ticketIdentifier.toString(), "Logged ${entries.joinToString(", ") { describe(it) }} on ${ticket.label}")
        }

        return AccountResult.Done(entries)
    }

    /**
     * Takes a work entry off its ticket, and its line off the invoice. Not once the invoice is paid or void, since
     * the work log should match what was charged
     */
    fun removeWork(identifier: Uuid, actor: Uuid?): AccountResult<Unit> = synchronized(CHANGE_LOCK) {
        val (ticketIdentifier, entry) = work.get(identifier) ?: return AccountResult.NotFound
        val ticket = tickets.getById(ticketIdentifier) ?: return AccountResult.NotFound
        if (ticket.status.status == TicketStatusType.PICKED_UP) return AccountResult.Conflict("Ticket ${ticket.label} was picked up, its work can't change")

        val invoice = invoices.getByTicket(ticketIdentifier)
        if (invoice != null && invoice.status != InvoiceStatus.OPEN) {
            return AccountResult.Conflict("Invoice ${invoice.label} is ${invoice.status.name.lowercase()}, its work can't change")
        }

        transaction {
            work.remove(identifier)
            // Taken off by hand at the counter already is fine, there's nothing to take off then
            if (invoice != null && invoice.charges.any { it.identifier == identifier }) {
                invoices.setCharges(invoice.identifier, invoice.charges.filter { it.identifier != identifier }, invoice.discountCents, invoice.taxRateBasisPoints)
            }
            auditLog.log(actor, AuditAction.DELETE, "ticket", ticketIdentifier.toString(), "Removed ${describe(entry)} from ${ticket.label}")
        }

        return AccountResult.Done(Unit)
    }

    // Labor isn't taxed and parts are, which can be changed on the invoice afterwards
    private fun WorkEntry.toCharge(): Charge =
        Charge(
            identifier = identifier,
            type = if (type == WorkType.PART) ChargeType.PART else ChargeType.LABOR,
            description = description,
            quantity = quantity,
            unitPriceCents = unitPriceCents,
            inventoryIdentifier = inventoryIdentifier,
            taxable = type == WorkType.PART
        )

    private fun describe(entry: WorkEntry): String =
        if (entry.type == WorkType.PART) "${formatQuantity(entry.quantity)} x ${entry.description}"
        else "${formatQuantity(entry.quantity)}h ${entry.description}"

    private class ShortOfStock(val identifier: Uuid) : Exception()

    private fun shortOf(identifier: Uuid, location: Uuid): AccountResult.Conflict {
        val item = inventory.getById(identifier, location)
        return AccountResult.Conflict("Only ${item?.stock ?: 0} of ${item?.name?.ifBlank { null } ?: "an item"} in stock, fix the count in Inventory first")
    }

    // How many of each stocked item the part lines use, added up when one is on several lines
    private fun partsUsed(charges: List<Charge>): Map<Uuid, Int> =
        charges
            .filter { it.type == ChargeType.PART && it.inventoryIdentifier != null }
            .groupBy { it.inventoryIdentifier!! }
            .mapValues { (_, charges) -> charges.sumOf { it.quantity.roundToLong().toInt() } }

    // The shop profile with a location's own tax rates, address and contacts
    private fun profileAt(location: Uuid?) = shop.at(location)

    // Where an invoice's stock comes from: its location, the main one for an invoice from before locations
    private fun locationOf(invoice: Invoice): Uuid = invoice.locationIdentifier ?: locations.main().identifier

    /**
     * The tax classes to keep on an invoice that kept [kept] and is getting [charges]: those, plus any of the shop's
     * now that a line uses and it didn't have. Null when a line names a class the shop doesn't have
     */
    private fun ratesFor(kept: List<TaxRate>, charges: List<Charge>, location: Uuid?): List<TaxRate>? {
        val available = TaxClasses.merged(kept, profileAt(location).taxRates)
        val used = charges.filter { it.taxable }.mapNotNull { it.taxClass }.toSet()
        if (used.any { id -> available.none { it.identifier == id } }) return null
        return kept + available.filter { rate -> rate.identifier in used && kept.none { it.identifier == rate.identifier } }
    }

    private fun unknownClass(charges: List<Charge>): String {
        val line = charges.first { it.taxable && it.taxClass != null && shop.get().taxRates.none { rate -> rate.identifier == it.taxClass } }
        return "\"${line.description}\" has a tax class the shop doesn't have any more, pick another"
    }

    // An amount in the shop's currency, for messages and the audit log
    private fun money(cents: Long): String = Money.format(cents, shop.get().currencySymbol)

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
                (charge.warrantyDays ?: 0) !in 0..Warranty.MAX_DAYS -> return "A line's warranty can be 0 to ${Warranty.MAX_DAYS} days"
                charge.type == ChargeType.GIFT_CARD && (charge.quantity != 1.0 || charge.taxable || charge.inventoryIdentifier != null) -> return "A gift card line is one card, untaxed"
                charge.type == ChargeType.GIFT_CARD && charge.unitPriceCents <= 0 -> return "Put an amount on the gift card"
                charge.type == ChargeType.GIFT_CARD && charge.reference.isNotBlank() && GiftCardCode.problem(charge.reference) != null -> return GiftCardCode.problem(charge.reference)
            }
        }

        return null
    }

    private fun PaymentRequest.toPayment(receivedBy: String): Payment =
        cards.withCharge(
            storedValue.normalized(
                Payment(
                    method = method,
                    amountCents = amountCents,
                    receivedAt = Clock.System.now().toEpochMilliseconds(),
                    receivedBy = receivedBy,
                    note = note.trim(),
                    reference = if (method == PaymentMethod.GIFT_CARD) reference else ""
                )
            ),
            chargeIdentifier
        )

    // How it was paid, for the audit log, with the gift card's code
    private fun describe(payment: Payment): String =
        payment.method.name.lowercase().replace('_', ' ') + if (payment.reference.isNotBlank()) " ${payment.reference}" else ""

    companion object {
        /**
         * Changes that check an invoice's state and then change it run one at a time, so two pickups of the same
         * ticket can't both pass the checks before either is saved
         */
        private val CHANGE_LOCK = Any()
    }
}
