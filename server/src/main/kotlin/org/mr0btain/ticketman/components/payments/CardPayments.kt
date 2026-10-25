package org.mr0btain.ticketman.components.payments

import org.mr0btain.ticketman.components.database.PaymentDatabase
import org.mr0btain.ticketman.data.invoice.Payment
import org.mr0btain.ticketman.data.invoice.PaymentMethod
import org.mr0btain.ticketman.data.payment.CardChargeStatus
import org.mr0btain.ticketman.data.payment.PaymentProvider
import org.mr0btain.ticketman.data.payment.PaymentSettings
import org.mr0btain.ticketman.server.request.PaymentRequest
import kotlin.uuid.Uuid

/**
 * Card payments through the shop's processor, for the invoice and check in services: putting a reader charge paid
 * with no invoice onto one (checked before a transaction, claimed inside it like stored value), and giving money
 * back onto the card it came from
 * @param developmentMode Allows the simulated processor
 */
class CardPayments(
    val charges: PaymentDatabase,
    private val developmentMode: Boolean = false
) {
    /** The saved settings, keys included */
    fun settings(): PaymentSettings = charges.settings()

    /** The processor for [settings] when it can be used, whether or not payments are turned on */
    fun processorFor(settings: PaymentSettings): PaymentProcessor? = when {
        !settings.isConfigured -> null
        settings.provider == PaymentProvider.SIMULATED && !developmentMode -> null
        else -> PaymentProcessors.of(settings)
    }

    /** The processor, null when card payments are off */
    fun processor(settings: PaymentSettings = settings()): PaymentProcessor? =
        if (settings.enabled) processorFor(settings) else null

    val simulatedAllowed: Boolean
        get() = developmentMode

    /** What's wrong with taking [payments], for the reader charges they name, or null */
    fun problem(payments: List<PaymentRequest>): String? {
        val named = payments.mapNotNull { it.chargeIdentifier }
        if (named.distinct().size != named.size) return "The same card payment is on there twice"

        payments.filter { it.chargeIdentifier != null }.forEach { request ->
            val charge = charges.get(request.chargeIdentifier!!) ?: return "That card payment doesn't exist"
            when {
                request.method != PaymentMethod.CARD -> return "A card reader payment is paid by card"
                charge.status != CardChargeStatus.SUCCEEDED -> return "That card payment is ${charge.status.label.lowercase()}, not paid"
                charge.paymentIdentifier != null -> return "That card payment is already on ${charge.invoiceLabel.ifBlank { "an invoice" }}"
                charge.amountCents != request.amountCents -> return "That card payment was for a different amount"
            }
        }
        return null
    }

    /** [payment] with what the processor knows about the charge it names, if it names one */
    fun withCharge(payment: Payment, chargeIdentifier: Uuid?): Payment {
        val charge = chargeIdentifier?.let { charges.get(it) } ?: return payment
        return payment.copy(
            processor = charge.provider,
            processorReference = charge.paymentReference,
            chargeIdentifier = charge.identifier,
            note = payment.note.ifBlank { charge.readerName.ifBlank { charge.kind.label } }
        )
    }

    /** Marks the charge [payment] came from as on the invoice. Inside the transaction that saves the payment */
    fun claim(payment: Payment, invoiceIdentifier: Uuid, label: String) {
        val charge = payment.chargeIdentifier ?: return
        if (!charges.claim(charge, invoiceIdentifier, label, payment.identifier)) throw ChargeTaken("That card payment was put on another invoice meanwhile")
    }

    /**
     * Gives [amountCents] of [payment] back onto its card, through the processor that took it, which needn't be
     * the one the shop uses now as long as its keys are still saved
     * @param key Kept the same when trying again, so the processor doesn't refund twice
     */
    fun refund(payment: Payment, amountCents: Long, key: String): ProcessorResult<String> {
        val provider = payment.processor ?: return ProcessorResult.Failed("That payment wasn't taken through the card processor")
        if (payment.processorReference.isBlank()) return ProcessorResult.Failed("The processor's id for that payment is missing, refund it from ${provider.label} itself")

        val settings = settings().copy(provider = provider)
        val processor = processorFor(settings) ?: return ProcessorResult.Failed("${provider.label} isn't set up any more, so it can't give the money back")
        return processor.refund(payment.processorReference, amountCents, key)
    }

    /** A charge was claimed by someone else between the check and the transaction */
    class ChargeTaken(override val message: String) : Exception(message)
}
