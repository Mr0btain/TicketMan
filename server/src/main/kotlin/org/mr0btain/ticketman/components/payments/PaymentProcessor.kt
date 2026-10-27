package org.mr0btain.ticketman.components.payments

import org.mr0btain.ticketman.data.payment.CardCharge
import org.mr0btain.ticketman.data.payment.CardChargeStatus
import org.mr0btain.ticketman.data.payment.CardReader
import org.mr0btain.ticketman.data.payment.PaymentProvider
import org.mr0btain.ticketman.data.payment.PaymentSettings
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** What a processor said */
sealed interface ProcessorResult<out T> {
    data class Ok<T>(val value: T) : ProcessorResult<T>

    /** Refused, or couldn't be reached. [reason] is fit to show staff */
    data class Failed(val reason: String) : ProcessorResult<Nothing>
}

/**
 * Where a charge stands at the processor
 * @param paymentReference The processor's id for the money once it's taken, which refunds go against
 * @param error Why it failed or was cancelled
 */
data class ChargeState(
    val status: CardChargeStatus,
    val paymentReference: String = "",
    val error: String = ""
)

/** A checkout page made for a charge */
data class StartedLink(val reference: String, val url: String)

/** A webhook's ask: check the waiting charges the processor knows by these ids */
sealed interface WebhookResult {
    data class Check(val references: List<String>) : WebhookResult

    /** The signature didn't match, or there's no key to check it with */
    data object Refused : WebhookResult
}

/**
 * One card processor, with the shop's account. The server never trusts what it's told about a charge without
 * asking: a webhook only says which charges to [check], so a forged one can't mark anything paid
 */
interface PaymentProcessor {
    /** The card readers on the account */
    fun readers(): ProcessorResult<List<CardReader>>

    /**
     * Sends [charge]'s amount to its reader
     * @return The processor's id for what was started, kept as [CardCharge.providerReference]
     */
    fun startReader(charge: CardCharge, description: String): ProcessorResult<String>

    /** A checkout page for [charge], coming back to [returnUrl] once paid */
    fun createLink(charge: CardCharge, description: String, returnUrl: String): ProcessorResult<StartedLink>

    fun check(charge: CardCharge): ProcessorResult<ChargeState>

    /** Stops a reader waiting for the card, or closes a link. Done if it was already finished */
    fun cancel(charge: CardCharge): ProcessorResult<Unit>

    /**
     * Gives [amountCents] back of the money the processor knows as [paymentReference]
     * @param key Sent as the request's idempotency key, so a retry doesn't refund twice
     * @return The refund's id
     */
    fun refund(paymentReference: String, amountCents: Long, key: String): ProcessorResult<String>

    /**
     * Checks a webhook's signature and says which charges it's about
     * @param header A header of the request, by name
     * @param url The address the processor sent it to, which Square signs
     */
    fun webhook(header: (String) -> String?, body: String, url: String): WebhookResult
}

/**
 * Makes the processor for the shop's settings. Tests swap [factory], like `NotificationSenders`
 */
object PaymentProcessors {
    private val default: (PaymentSettings) -> PaymentProcessor = { settings ->
        when (settings.provider) {
            PaymentProvider.STRIPE -> StripeProcessor(settings.stripe, settings.currency)
            PaymentProvider.SQUARE -> SquareProcessor(settings.square, settings.currency)
            PaymentProvider.SIMULATED -> SimulatedProcessor()
        }
    }

    var factory: (PaymentSettings) -> PaymentProcessor = default

    fun of(settings: PaymentSettings): PaymentProcessor = factory(settings)

    fun reset() {
        factory = default
    }
}

internal fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray =
    Mac.getInstance("HmacSHA256").run {
        init(SecretKeySpec(key, "HmacSHA256"))
        doFinal(data)
    }

internal fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }

/** Compares in constant time, so a signature can't be guessed a byte at a time */
internal fun sameBytes(a: ByteArray, b: ByteArray): Boolean = java.security.MessageDigest.isEqual(a, b)
