package org.mr0btain.ticketman.data.payment

import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Who takes card payments for the shop */
@Serializable
enum class PaymentProvider(val label: String) {
    STRIPE("Stripe"),
    SQUARE("Square"),

    /** Pretends to take cards, only on a server in development mode. For trying the app out and the tests */
    SIMULATED("Simulated")
}

/**
 * The shop's Stripe account
 * @param secretKey `sk_live_...` or `sk_test_...`, or a restricted key. Never sent back by the server. When saving,
 * null keeps the saved one and anything else replaces it
 * @param webhookSecret `whsec_...` from the webhook endpoint, optional. Kept like [secretKey]
 */
@Serializable
data class StripeSettings(
    val secretKey: String? = null,
    val webhookSecret: String? = null
)

@Serializable
enum class SquareEnvironment(val label: String) {
    PRODUCTION("Production"),
    SANDBOX("Sandbox")
}

/**
 * The shop's Square account
 * @param accessToken Never sent back by the server, kept like [StripeSettings.secretKey]
 * @param locationId The Square location payment links are made for
 * @param webhookSignatureKey From the webhook subscription, optional. Kept like [accessToken]
 */
@Serializable
data class SquareSettings(
    val environment: SquareEnvironment = SquareEnvironment.PRODUCTION,
    val accessToken: String? = null,
    val locationId: String = "",
    val webhookSignatureKey: String? = null
)

/**
 * A card reader the counter can send an amount to
 * @param identifier The provider's id for it: a Stripe reader `tmr_...` or a Square device id
 */
@Serializable
data class CardReader(
    val identifier: String,
    val name: String
)

/**
 * How the shop takes cards through a processor. Kept apart from the shop profile because it holds the account's
 * keys, and only admins read it
 * @param currency ISO 4217 code amounts are charged in, like `USD`
 * @param readers The readers the counter offers, picked from the account's
 * @param onlinePayments Customers can pay the balance from the status page
 * @param stripeKeySet Filled in by the server: whether a secret key is saved, and so on for the other secrets
 */
@Serializable
data class PaymentSettings(
    val enabled: Boolean = false,
    val provider: PaymentProvider = PaymentProvider.STRIPE,
    val currency: String = "USD",
    val stripe: StripeSettings = StripeSettings(),
    val stripeKeySet: Boolean = false,
    val stripeWebhookSecretSet: Boolean = false,
    val square: SquareSettings = SquareSettings(),
    val squareTokenSet: Boolean = false,
    val squareWebhookKeySet: Boolean = false,
    val readers: List<CardReader> = emptyList(),
    val onlinePayments: Boolean = false
) {
    /** Whether the provider has what it needs to be called. The server only knows for sure, it holds the secrets */
    val isConfigured: Boolean
        get() = when (provider) {
            PaymentProvider.STRIPE -> !stripe.secretKey.isNullOrBlank() || stripeKeySet
            PaymentProvider.SQUARE -> (!square.accessToken.isNullOrBlank() || squareTokenSet) && square.locationId.isNotBlank()
            PaymentProvider.SIMULATED -> true
        }

    /** What's wrong with these settings, or null. Checked by both the client and the server */
    fun problem(): String? = when {
        !CURRENCY.matches(currency) -> "The currency is a 3 letter code, like USD or EUR"
        enabled && !isConfigured -> when (provider) {
            PaymentProvider.STRIPE -> "Stripe needs the account's secret key"
            PaymentProvider.SQUARE -> "Square needs an access token and a location"
            PaymentProvider.SIMULATED -> null
        }
        readers.any { it.identifier.isBlank() } -> "Every reader needs its id"
        readers.any { it.name.isBlank() } -> "Every reader needs a name"
        readers.distinctBy { it.identifier }.size != readers.size -> "A reader is on the list twice"
        readers.size > MAX_READERS -> "Up to $MAX_READERS readers"
        else -> null
    }

    /** Without the keys, for sending to a client */
    fun withoutSecrets(): PaymentSettings =
        copy(
            stripe = StripeSettings(),
            stripeKeySet = !stripe.secretKey.isNullOrEmpty(),
            stripeWebhookSecretSet = !stripe.webhookSecret.isNullOrEmpty(),
            square = square.copy(accessToken = null, webhookSignatureKey = null),
            squareTokenSet = !square.accessToken.isNullOrEmpty(),
            squareWebhookKeySet = !square.webhookSignatureKey.isNullOrEmpty()
        )

    /** These settings as saved over [saved]: a key left null keeps the saved one */
    fun keepingSecretsOf(saved: PaymentSettings): PaymentSettings =
        copy(
            stripe = StripeSettings(
                secretKey = stripe.secretKey ?: saved.stripe.secretKey,
                webhookSecret = stripe.webhookSecret ?: saved.stripe.webhookSecret
            ),
            square = square.copy(
                accessToken = square.accessToken ?: saved.square.accessToken,
                webhookSignatureKey = square.webhookSignatureKey ?: saved.square.webhookSignatureKey
            ),
            currency = currency.trim().uppercase(),
            stripeKeySet = false,
            stripeWebhookSecretSet = false,
            squareTokenSet = false,
            squareWebhookKeySet = false
        )

    companion object {
        const val MAX_READERS = 50
        private val CURRENCY = Regex("^[A-Za-z]{3}$")
    }
}

/**
 * What the counter needs to offer card payments, without any keys. Anyone logged in reads it
 * @param enabled A processor is set up and turned on
 * @param onlinePayments Customers can pay from the status page, which also needs the shop's public address
 */
@Serializable
data class PaymentOptions(
    val enabled: Boolean = false,
    val provider: PaymentProvider? = null,
    val readers: List<CardReader> = emptyList(),
    val links: Boolean = false,
    val onlinePayments: Boolean = false
)

@Serializable
enum class CardChargeKind(val label: String) {
    /** Sent to a card reader at the counter */
    READER("Card reader"),

    /** A checkout page the customer pays on, from a link or the status page */
    LINK("Payment link")
}

@Serializable
enum class CardChargeStatus(val label: String) {
    /** Waiting for the card, or for the customer to pay */
    PENDING("Waiting"),
    SUCCEEDED("Paid"),

    /** Declined, or the reader gave up */
    FAILED("Failed"),

    /** Called off before it was paid, or the link ran out */
    CANCELLED("Cancelled"),

    /** Paid, then given back through the processor without ever being on an invoice */
    REFUNDED("Refunded");

    val finished: Boolean
        get() = this != PENDING
}

/**
 * One go at taking a card through the processor: an amount sent to a reader, or a payment link. Once it succeeds
 * it becomes a [org.mr0btain.ticketman.data.invoice.Payment] on its invoice. One made with no invoice, for a sale
 * or a check in deposit that isn't saved yet, waits to be claimed by the payment that names it
 * @param invoiceIdentifier The invoice it's paying, null until a sale or check in claims it
 * @param readerIdentifier The reader it was sent to, blank for a link
 * @param url The checkout page for a link
 * @param providerReference The processor's id for what was started: a Stripe PaymentIntent or Checkout Session, a
 * Square terminal checkout or payment link's order
 * @param paymentReference The processor's id for the money taken, which refunds go against
 * @param paymentIdentifier The invoice payment it became
 * @param error Why it failed, from the processor
 * @param createdBy Username of whoever started it, `customer` from the status page
 */
@OptIn(ExperimentalUuidApi::class)
@Serializable
data class CardCharge(
    val identifier: Uuid = Uuid.generateV7(),
    val kind: CardChargeKind,
    val provider: PaymentProvider,
    val status: CardChargeStatus = CardChargeStatus.PENDING,
    val amountCents: Long,
    val currency: String,
    val invoiceIdentifier: Uuid? = null,
    val invoiceLabel: String = "",
    val readerIdentifier: String = "",
    val readerName: String = "",
    val url: String = "",
    val providerReference: String = "",
    val paymentReference: String = "",
    val paymentIdentifier: Uuid? = null,
    val error: String = "",
    val createdAt: Long = 0,
    val finishedAt: Long? = null,
    val createdBy: String = ""
) {
    /** Paid, but no invoice has it yet */
    val unclaimed: Boolean
        get() = status == CardChargeStatus.SUCCEEDED && paymentIdentifier == null
}
