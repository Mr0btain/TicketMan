package org.mr0btain.ticketman.components.payments

import org.mr0btain.ticketman.components.database.AuditLogDatabase
import org.mr0btain.ticketman.components.database.InvoiceDatabase
import org.mr0btain.ticketman.components.database.ShopDatabase
import org.mr0btain.ticketman.components.database.TicketDatabase
import org.mr0btain.ticketman.components.service.AccountResult
import org.mr0btain.ticketman.components.service.InvoiceService
import org.mr0btain.ticketman.data.audit.AuditAction
import org.mr0btain.ticketman.data.invoice.Invoice
import org.mr0btain.ticketman.data.invoice.InvoiceStatus
import org.mr0btain.ticketman.data.money.Money
import org.mr0btain.ticketman.data.payment.CardCharge
import org.mr0btain.ticketman.data.payment.CardExtras
import org.mr0btain.ticketman.data.payment.CardChargeKind
import org.mr0btain.ticketman.data.payment.CardChargeStatus
import org.mr0btain.ticketman.data.payment.CardReader
import org.mr0btain.ticketman.data.payment.PaymentOptions
import org.mr0btain.ticketman.data.payment.PaymentProvider
import org.mr0btain.ticketman.data.payment.PaymentSettings
import org.mr0btain.ticketman.data.shared.PagedRequest
import org.mr0btain.ticketman.data.ticket.StatusLink
import org.mr0btain.ticketman.logging.AppLogger
import org.mr0btain.ticketman.server.ApiRoutes
import org.mr0btain.ticketman.server.StatusPaths
import org.mr0btain.ticketman.server.request.PaymentLinkRequest
import org.mr0btain.ticketman.server.request.ReaderChargeRequest
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

/**
 * Taking cards through the shop's processor: its settings, sending amounts to readers, payment links, and
 * following each charge until it's paid or not. A charge is only ever marked paid from what the processor says
 * when asked: the counter asks while it waits, the sweep asks every so often, and webhooks only say when to ask
 */
@Suppress("PrivatePropertyName")
class PaymentService(
    private val cards: CardPayments,
    private val invoiceService: InvoiceService,
    private val invoices: InvoiceDatabase,
    private val tickets: TicketDatabase,
    private val shop: ShopDatabase,
    private val auditLog: AuditLogDatabase,
    private val now: () -> Long = { System.currentTimeMillis() }
) {
    private val TAG = "PaymentService"
    private val CHECK_TAG = "$TAG:Check"
    private val SWEEP_TAG = "$TAG:Sweep"

    private val charges get() = cards.charges

    // ------------------------------------------ Settings ------------------------------------------

    /** The settings without the keys */
    fun settings(): PaymentSettings = cards.settings().withoutSecrets()

    fun saveSettings(received: PaymentSettings, actor: Uuid?): AccountResult<PaymentSettings> {
        if (received.provider == PaymentProvider.SIMULATED && !cards.simulatedAllowed) {
            return AccountResult.Invalid("The simulated processor is only for a server in development mode")
        }

        val settings = received.keepingSecretsOf(cards.settings())
        settings.problem()?.let { return AccountResult.Invalid(it) }
        if (!charges.saveSettings(settings)) return AccountResult.Invalid("Couldn't save the payment settings")

        val state = if (settings.enabled) "on, ${settings.provider.label}, ${settings.readers.size} reader${if (settings.readers.size == 1) "" else "s"}" else "off"
        val extras = listOfNotNull(
            ", paying online".takeIf { settings.onlinePayments },
            ", tips ${settings.tipPercents.joinToString("/")}%".takeIf { settings.tips },
            settings.surchargePercent.takeIf { CardExtras.basisPoints(it)?.let { points -> points > 0 } == true }?.let { ", ${it}% surcharge" }
        ).joinToString("")
        auditLog.log(actor, AuditAction.SETTINGS_UPDATE, "payments", details = "Card payments $state$extras")
        return AccountResult.Done(settings.withoutSecrets())
    }

    /** What the counter can offer at [location]: the readers there and those for every location. All of them for null */
    fun options(location: Uuid? = null): PaymentOptions {
        val settings = cards.settings()
        cards.processor(settings) ?: return PaymentOptions()
        val public = shop.get().publicUrl.isNotBlank()

        return PaymentOptions(
            enabled = true,
            provider = settings.provider,
            readers = settings.readers.filter { location == null || it.locationIdentifier == null || it.locationIdentifier == location },
            links = public,
            onlinePayments = settings.onlinePayments && public,
            tipPercents = if (settings.tips) settings.tipPercents else emptyList(),
            surchargePercent = settings.surchargePercent.takeIf { CardExtras.surchargeCents(10_000, it) > 0 }.orEmpty()
        )
    }

    /** The readers on the processor account, for picking the counter's. Works before payments are turned on */
    fun accountReaders(): AccountResult<List<CardReader>> {
        val processor = cards.processorFor(cards.settings()) ?: return AccountResult.Conflict("Save the account's keys first")
        return when (val result = processor.readers()) {
            is ProcessorResult.Ok -> AccountResult.Done(result.value)
            is ProcessorResult.Failed -> AccountResult.Conflict(result.reason)
        }
    }

    // ------------------------------------------ Charges ------------------------------------------

    /**
     * Sends an amount to one of the shop's readers. With an invoice, the payment lands on it once the card goes
     * through; without, it waits for a sale or check in to name it. A reader takes one card at a time
     */
    fun startReader(request: ReaderChargeRequest, by: String, actor: Uuid?): AccountResult<CardCharge> {
        val settings = cards.settings()
        val processor = cards.processor(settings) ?: return AccountResult.Conflict("Card payments aren't set up")
        val reader = settings.readers.firstOrNull { it.identifier == request.readerIdentifier } ?: return AccountResult.Invalid("That reader isn't one of the shop's")
        if (request.amountCents <= 0) return AccountResult.Invalid("The amount has to be more than zero")
        if (request.tipCents < 0) return AccountResult.Invalid("A tip can't be below zero")
        if (request.tipCents > 0 && !settings.tips) return AccountResult.Invalid("The shop doesn't take tips")
        if (request.tipCents > request.amountCents * CardExtras.MAX_TIP_PERCENT / 100) return AccountResult.Invalid("The tip is more than the amount")
        val surcharge = CardExtras.surchargeCents(request.amountCents, settings.surchargePercent)

        val invoice = request.invoiceIdentifier?.let { invoices.getById(it) ?: return AccountResult.NotFound }
        invoice?.let { owedProblem(it, request.amountCents)?.let { problem -> return problem } }

        // Asked again first, it may have finished since
        val busy = charges.pending(MAX_PENDING).map { it.first }
            .filter { it.kind == CardChargeKind.READER && it.readerIdentifier == reader.identifier }
            .map { refresh(it) }
            .firstOrNull { it.status == CardChargeStatus.PENDING }
        if (busy != null) return AccountResult.Conflict("${reader.name} is waiting for another card (${money(busy.amountCents)}), cancel that one first")

        val charge = CardCharge(
            kind = CardChargeKind.READER,
            provider = settings.provider,
            amountCents = request.amountCents + request.tipCents + surcharge,
            tipCents = request.tipCents,
            surchargeCents = surcharge,
            currency = settings.currency,
            invoiceIdentifier = invoice?.identifier,
            invoiceLabel = invoice?.label.orEmpty(),
            readerIdentifier = reader.identifier,
            readerName = reader.name,
            createdAt = now(),
            createdBy = by
        )
        if (!charges.add(charge)) return AccountResult.Invalid("Couldn't save the charge")

        return when (val started = processor.startReader(charge, description(invoice))) {
            is ProcessorResult.Ok -> {
                charges.setStarted(charge.identifier, started.value, "")
                auditLog.log(actor, AuditAction.CREATE, "card charge", charge.identifier.toString(), "Sent ${money(charge.amountCents)} to ${reader.name}" + (invoice?.let { " for ${it.label}" } ?: ""))
                AccountResult.Done(charge.copy(providerReference = started.value))
            }

            is ProcessorResult.Failed -> {
                charges.finish(charge.identifier, CardChargeStatus.FAILED, now(), error = started.reason)
                AccountResult.Conflict(started.reason)
            }
        }
    }

    /**
     * A checkout page for an open invoice, coming back to the ticket's status page. One waiting for the same
     * amount is handed out again rather than making another, and any for another amount are closed, so a customer
     * can't pay twice from two links
     * @param by Username of whoever asked, `customer` from the status page
     */
    fun link(request: PaymentLinkRequest, by: String, actor: Uuid?): AccountResult<CardCharge> {
        val settings = cards.settings()
        val processor = cards.processor(settings) ?: return AccountResult.Conflict("Card payments aren't set up")
        val publicUrl = shop.get().publicUrl
        if (publicUrl.isBlank()) return AccountResult.Conflict("Set the shop's public address first, the checkout comes back to the status page")

        val invoice = invoices.getById(request.invoiceIdentifier) ?: return AccountResult.NotFound
        val amount = request.amountCents ?: invoice.balanceCents
        if (amount <= 0) return AccountResult.Conflict("Nothing is owed on ${invoice.label}")
        owedProblem(invoice, amount)?.let { return it }

        val waiting = charges.forInvoice(invoice.identifier).orEmpty()
            .filter { it.kind == CardChargeKind.LINK && it.status == CardChargeStatus.PENDING }
            .map { refresh(it) }
            .filter { it.status == CardChargeStatus.PENDING }
        val surcharge = CardExtras.surchargeCents(amount, settings.surchargePercent)
        waiting.firstOrNull { it.appliedCents == amount && it.surchargeCents == surcharge && it.provider == settings.provider }?.let { return AccountResult.Done(it) }
        waiting.forEach { close(it, "Replaced by a link for ${money(amount)}") }
        // One may have been paid just now, which changes what's owed
        if (waiting.isNotEmpty()) invoices.getById(invoice.identifier)?.let { latest -> owedProblem(latest, amount)?.let { return it } }

        val ticket = invoice.ticketIdentifier?.let { tickets.getById(it) }
        val returnUrl = ticket?.let { StatusLink.url(publicUrl, it) } ?: publicUrl.trim().trimEnd('/')

        val charge = CardCharge(
            kind = CardChargeKind.LINK,
            provider = settings.provider,
            amountCents = amount + surcharge,
            surchargeCents = surcharge,
            currency = settings.currency,
            invoiceIdentifier = invoice.identifier,
            invoiceLabel = invoice.label,
            createdAt = now(),
            createdBy = by
        )
        if (!charges.add(charge)) return AccountResult.Invalid("Couldn't save the charge")

        return when (val started = processor.createLink(charge, description(invoice), returnUrl)) {
            is ProcessorResult.Ok -> {
                charges.setStarted(charge.identifier, started.value.reference, started.value.url)
                auditLog.log(actor, AuditAction.CREATE, "card charge", charge.identifier.toString(), "Payment link for ${money(amount)} on ${invoice.label}" + (if (surcharge > 0) " plus ${money(surcharge)} surcharge" else ""), actorName = by.takeIf { actor == null })
                AccountResult.Done(charge.copy(providerReference = started.value.reference, url = started.value.url))
            }

            is ProcessorResult.Failed -> {
                charges.finish(charge.identifier, CardChargeStatus.FAILED, now(), error = started.reason)
                AccountResult.Conflict(started.reason)
            }
        }
    }

    /** A charge, asked about again first while it's waiting */
    fun charge(identifier: Uuid): CardCharge? = charges.get(identifier)?.let { refresh(it) }

    /** Asks about an invoice's waiting links, for the status page after the customer comes back from paying */
    fun refreshLinks(invoiceIdentifier: Uuid) {
        charges.forInvoice(invoiceIdentifier).orEmpty()
            .filter { it.kind == CardChargeKind.LINK && it.status == CardChargeStatus.PENDING }
            .forEach { refresh(it) }
    }

    /**
     * Asks the processor where a waiting charge stands and records it: paid puts the payment on its invoice. A
     * processor that can't be reached leaves it waiting
     */
    fun refresh(charge: CardCharge): CardCharge {
        if (charge.status != CardChargeStatus.PENDING) return charge
        charges.markChecked(charge.identifier, now())

        // The processor it was started on, even if the shop has moved to another since
        val processor = cards.processorFor(cards.settings().copy(provider = charge.provider)) ?: return charge
        val state = when (val result = processor.check(charge)) {
            is ProcessorResult.Ok -> result.value
            is ProcessorResult.Failed -> {
                AppLogger.i(CHECK_TAG, "Couldn't check ${charge.identifier}: ${result.reason}")
                return charge
            }
        }

        return when (state.status) {
            CardChargeStatus.PENDING -> charge
            CardChargeStatus.SUCCEEDED -> invoiceService.cardCharged(charge, state.paymentReference) ?: charges.get(charge.identifier) ?: charge
            CardChargeStatus.FAILED, CardChargeStatus.CANCELLED, CardChargeStatus.REFUNDED -> {
                charges.finish(charge.identifier, state.status, now(), error = state.error)
                charges.get(charge.identifier) ?: charge
            }
        }
    }

    /** Stops a reader waiting for the card, or closes a link. Refused once the card went through */
    fun cancel(identifier: Uuid, by: String, actor: Uuid?): AccountResult<CardCharge> {
        val charge = charges.get(identifier) ?: return AccountResult.NotFound
        if (charge.status != CardChargeStatus.PENDING) return AccountResult.Conflict("That charge is already ${charge.status.label.lowercase()}")

        val latest = close(charge, "Cancelled by $by")
        return when (latest.status) {
            CardChargeStatus.SUCCEEDED -> AccountResult.Conflict("The card went through before it could be cancelled")
            CardChargeStatus.PENDING -> AccountResult.Conflict("${charge.provider.label} didn't cancel it: ${latest.error.ifBlank { "try again" }}")
            else -> {
                auditLog.log(actor, AuditAction.UPDATE, "card charge", identifier.toString(), "Cancelled ${money(charge.amountCents)} ${charge.kind.label.lowercase()}" + charge.invoiceLabel.ifBlank { null }?.let { " for $it" }.orEmpty())
                AccountResult.Done(latest)
            }
        }
    }

    // Calls a charge off at the processor, then asks once more, since the card may have gone through just before
    private fun close(charge: CardCharge, reason: String): CardCharge {
        val processor = cards.processorFor(cards.settings().copy(provider = charge.provider))
        val cancelled = processor?.cancel(charge)
        val latest = refresh(charge)
        if (latest.status != CardChargeStatus.PENDING) return latest

        if (cancelled is ProcessorResult.Failed) return latest.copy(error = cancelled.reason)
        charges.finish(charge.identifier, CardChargeStatus.CANCELLED, now(), error = reason)
        return charges.get(charge.identifier) ?: latest
    }

    /**
     * Gives back a charge that was paid but never made it onto an invoice, like a sale that wasn't finished. Admin
     * only
     */
    fun refundUnclaimed(identifier: Uuid, reason: String, actor: Uuid?): AccountResult<CardCharge> {
        val charge = charges.get(identifier) ?: return AccountResult.NotFound
        if (!charge.unclaimed) return AccountResult.Conflict(
            if (charge.paymentIdentifier != null) "That charge is on ${charge.invoiceLabel.ifBlank { "an invoice" }}, refund it there" else "That charge wasn't paid"
        )
        if (reason.isBlank()) return AccountResult.Invalid("Give a reason for the refund")

        val processor = cards.processorFor(cards.settings().copy(provider = charge.provider))
            ?: return AccountResult.Conflict("${charge.provider.label} isn't set up any more, so it can't give the money back")

        return when (val result = processor.refund(charge.paymentReference, charge.amountCents, "unclaimed-${charge.identifier}")) {
            is ProcessorResult.Ok -> {
                if (!charges.markRefunded(identifier, now())) return AccountResult.Conflict("That charge was put on an invoice meanwhile, refund it there")
                auditLog.log(actor, AuditAction.REFUND, "card charge", identifier.toString(), "Refunded ${money(charge.amountCents)} never put on an invoice (${result.value}): ${reason.trim()}")
                AccountResult.Done(charges.get(identifier)!!)
            }

            is ProcessorResult.Failed -> AccountResult.Conflict("The card wasn't refunded: ${result.reason}")
        }
    }

    /**
     * Follows up waiting charges, called every [SWEEP_INTERVAL]: often while they're new, then every few minutes,
     * and calls them off once they've waited too long (a reader nobody came back to, a link nobody paid)
     */
    fun sweep() {
        charges.pending(MAX_PENDING).forEach { (charge, checkedAt) ->
            try {
                val age = now() - charge.createdAt
                val giveUp = if (charge.kind == CardChargeKind.READER) READER_GIVE_UP else LINK_GIVE_UP
                val every = when {
                    age > FRESH.inWholeMilliseconds -> SLOW_CHECK
                    charge.kind == CardChargeKind.READER -> SWEEP_INTERVAL
                    else -> LINK_CHECK
                }

                when {
                    age > giveUp.inWholeMilliseconds -> close(charge, "Nobody paid it in ${giveUp.inWholeDays.takeIf { it > 0 }?.let { "$it days" } ?: "${giveUp.inWholeMinutes} minutes"}")
                    now() - checkedAt >= every.inWholeMilliseconds -> refresh(charge)
                }
            } catch (ex: Exception) {
                AppLogger.e(SWEEP_TAG, "Couldn't follow up ${charge.identifier}", ex)
            }
        }
    }

    sealed interface Webhook {
        data object Done : Webhook

        /** No such processor, or it isn't set up */
        data object NotFound : Webhook

        /** The signature didn't check out */
        data object Refused : Webhook
    }

    /**
     * A processor telling the server something changed. Only says which waiting charges to ask about
     * @param provider From the path, `stripe` or `square`
     */
    fun webhook(provider: String, header: (String) -> String?, body: String): Webhook {
        val chosen = PaymentProvider.entries.firstOrNull { it.name.equals(provider, ignoreCase = true) && it != PaymentProvider.SIMULATED }
            ?: return Webhook.NotFound
        val processor = cards.processorFor(cards.settings().copy(provider = chosen)) ?: return Webhook.NotFound

        return when (val result = processor.webhook(header, body, webhookUrl(chosen))) {
            WebhookResult.Refused -> Webhook.Refused
            is WebhookResult.Check -> {
                val references = result.references.toSet()
                charges.pending(MAX_PENDING).map { it.first }
                    .filter { charge -> charge.provider == chosen && charge.providerReference.split('/').any { it in references } }
                    .forEach { refresh(it) }
                Webhook.Done
            }
        }
    }

    /** Where the processor is told to send webhooks, which Square signs along with the body */
    fun webhookUrl(provider: PaymentProvider): String =
        shop.get().publicUrl.trim().trimEnd('/') + ApiRoutes.STATUS + StatusPaths.PAYMENT_WEBHOOK.replace("{provider}", provider.name.lowercase())

    fun page(request: PagedRequest): List<CardCharge>? = charges.page(request)

    fun forInvoice(invoiceIdentifier: Uuid): List<CardCharge>? = charges.forInvoice(invoiceIdentifier)

    private fun owedProblem(invoice: Invoice, amountCents: Long): AccountResult<Nothing>? = when {
        invoice.status != InvoiceStatus.OPEN -> AccountResult.Conflict("Invoice ${invoice.label} is ${invoice.status.name.lowercase()}")
        amountCents > invoice.balanceCents -> AccountResult.Invalid("Only ${money(invoice.balanceCents.coerceAtLeast(0))} is owed on ${invoice.label}")
        else -> null
    }

    // What the customer sees on their statement or the checkout page
    private fun description(invoice: Invoice?): String {
        val name = shop.at(invoice?.locationIdentifier).name.ifBlank { "the shop" }
        return invoice?.let { "${it.label} at $name" } ?: "Sale at $name"
    }

    private fun money(cents: Long): String = Money.format(cents, shop.get().currencySymbol)

    companion object {
        val SWEEP_INTERVAL = 10.seconds

        private val LINK_CHECK = 1.minutes
        private val SLOW_CHECK = 5.minutes
        private val FRESH = 10.minutes
        private val READER_GIVE_UP = 30.minutes
        private val LINK_GIVE_UP = 7.days
        private const val MAX_PENDING = 200
    }
}
