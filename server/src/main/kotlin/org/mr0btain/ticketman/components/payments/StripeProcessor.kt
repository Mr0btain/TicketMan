package org.mr0btain.ticketman.components.payments

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.mr0btain.ticketman.data.payment.CardCharge
import org.mr0btain.ticketman.data.payment.CardChargeStatus
import org.mr0btain.ticketman.data.payment.CardReader
import org.mr0btain.ticketman.data.payment.StripeSettings
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Stripe through its REST API, called directly so no SDK is needed. Readers take a PaymentIntent for card present
 * payments (Stripe Terminal, server driven), links are Checkout Sessions. Refunds go against the PaymentIntent
 * @param baseUrl Swapped in tests for a local server
 * @param now Epoch seconds, for checking a webhook's age
 */
class StripeProcessor(
    private val settings: StripeSettings,
    currency: String,
    private val baseUrl: String = "https://api.stripe.com",
    private val client: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build(),
    private val now: () -> Long = { System.currentTimeMillis() / 1000 }
) : PaymentProcessor {
    private val currency = currency.lowercase()

    override fun readers(): ProcessorResult<List<CardReader>> =
        call("GET", "/v1/terminal/readers?limit=100").map { body ->
            body.array("data").map { reader ->
                CardReader(identifier = reader.string("id"), name = reader.string("label").ifBlank { reader.string("device_type") })
            }
        }

    override fun startReader(charge: CardCharge, description: String): ProcessorResult<String> {
        val intent = call(
            "POST", "/v1/payment_intents",
            form = listOf(
                "amount" to charge.amountCents.toString(),
                "currency" to currency,
                "payment_method_types[]" to "card_present",
                "capture_method" to "automatic",
                "description" to description,
                "metadata[ticketman_charge]" to charge.identifier.toString()
            ),
            key = "intent-${charge.identifier}"
        )
        val id = when (intent) {
            is ProcessorResult.Ok -> intent.value.string("id")
            is ProcessorResult.Failed -> return intent
        }

        val processing = call(
            "POST", "/v1/terminal/readers/${path(charge.readerIdentifier)}/process_payment_intent",
            form = listOf("payment_intent" to id),
            key = "process-${charge.identifier}"
        )
        if (processing is ProcessorResult.Failed) {
            // Nothing is waiting for a card then, so the intent isn't left open
            call("POST", "/v1/payment_intents/${path(id)}/cancel")
            return processing
        }

        return ProcessorResult.Ok(id)
    }

    override fun createLink(charge: CardCharge, description: String, returnUrl: String): ProcessorResult<StartedLink> =
        call(
            "POST", "/v1/checkout/sessions",
            form = listOf(
                "mode" to "payment",
                "line_items[0][quantity]" to "1",
                "line_items[0][price_data][currency]" to currency,
                "line_items[0][price_data][unit_amount]" to charge.amountCents.toString(),
                "line_items[0][price_data][product_data][name]" to description,
                "success_url" to returnUrl,
                "cancel_url" to returnUrl,
                "client_reference_id" to charge.identifier.toString(),
                "metadata[ticketman_charge]" to charge.identifier.toString(),
                "payment_intent_data[metadata][ticketman_charge]" to charge.identifier.toString()
            ),
            key = "link-${charge.identifier}"
        ).map { StartedLink(it.string("id"), it.string("url")) }

    override fun check(charge: CardCharge): ProcessorResult<ChargeState> {
        val reference = charge.providerReference
        if (reference.startsWith("cs_")) {
            return call("GET", "/v1/checkout/sessions/${path(reference)}").map { session ->
                when {
                    session.string("payment_status") == "paid" -> ChargeState(CardChargeStatus.SUCCEEDED, paymentReference = session.string("payment_intent"))
                    session.string("status") == "expired" -> ChargeState(CardChargeStatus.CANCELLED, error = "The link ran out")
                    else -> ChargeState(CardChargeStatus.PENDING)
                }
            }
        }

        val intent = when (val result = call("GET", "/v1/payment_intents/${path(reference)}")) {
            is ProcessorResult.Ok -> result.value
            is ProcessorResult.Failed -> return result
        }

        val declined = (intent["last_payment_error"] as? JsonObject)?.string("message")
        val status = intent.string("status")
        return when {
            status == "succeeded" -> ProcessorResult.Ok(ChargeState(CardChargeStatus.SUCCEEDED, paymentReference = reference))
            status == "canceled" -> ProcessorResult.Ok(ChargeState(CardChargeStatus.CANCELLED, error = "Cancelled at Stripe"))
            status == "requires_payment_method" && !declined.isNullOrBlank() -> ProcessorResult.Ok(ChargeState(CardChargeStatus.FAILED, error = declined))
            status == "requires_payment_method" -> readerFailure(charge)
            else -> ProcessorResult.Ok(ChargeState(CardChargeStatus.PENDING))
        }
    }

    // The reader gave up on its own, like timing out with no card, which leaves the intent waiting for another try
    private fun readerFailure(charge: CardCharge): ProcessorResult<ChargeState> {
        if (charge.readerIdentifier.isBlank()) return ProcessorResult.Ok(ChargeState(CardChargeStatus.PENDING))

        return call("GET", "/v1/terminal/readers/${path(charge.readerIdentifier)}").map { reader ->
            val action = reader["action"] as? JsonObject
            val forThis = (action?.get("process_payment_intent") as? JsonObject)?.string("payment_intent") == charge.providerReference
            if (forThis && action?.string("status") == "failed") {
                ChargeState(CardChargeStatus.FAILED, error = action.string("failure_message").ifBlank { "The reader didn't take the card" })
            } else ChargeState(CardChargeStatus.PENDING)
        }
    }

    override fun cancel(charge: CardCharge): ProcessorResult<Unit> {
        val reference = charge.providerReference
        if (reference.isBlank()) return ProcessorResult.Ok(Unit)
        if (reference.startsWith("cs_")) return call("POST", "/v1/checkout/sessions/${path(reference)}/expire").map { }

        // The reader stops asking first, then the intent closes. A reader that already moved on refuses, which is fine
        if (charge.readerIdentifier.isNotBlank()) call("POST", "/v1/terminal/readers/${path(charge.readerIdentifier)}/cancel_action")
        return call("POST", "/v1/payment_intents/${path(reference)}/cancel").map { }
    }

    override fun refund(paymentReference: String, amountCents: Long, key: String): ProcessorResult<String> =
        call(
            "POST", "/v1/refunds",
            form = listOf("payment_intent" to paymentReference, "amount" to amountCents.toString()),
            key = key
        ).map { it.string("id") }

    override fun webhook(header: (String) -> String?, body: String, url: String): WebhookResult {
        val secret = settings.webhookSecret?.takeIf { it.isNotBlank() } ?: return WebhookResult.Refused
        val parts = header("Stripe-Signature").orEmpty().split(",").mapNotNull { part ->
            part.split("=", limit = 2).takeIf { it.size == 2 }?.let { (name, value) -> name.trim() to value.trim() }
        }
        val time = parts.firstOrNull { it.first == "t" }?.second?.toLongOrNull() ?: return WebhookResult.Refused
        if (kotlin.math.abs(now() - time) > WEBHOOK_TOLERANCE_SECONDS) return WebhookResult.Refused

        val expected = hmacSha256(secret.toByteArray(), "$time.$body".toByteArray()).hex().toByteArray()
        val signed = parts.filter { it.first == "v1" }.any { sameBytes(it.second.toByteArray(), expected) }
        if (!signed) return WebhookResult.Refused

        val event = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return WebhookResult.Check(emptyList())
        val target = ((event["data"] as? JsonObject)?.get("object") as? JsonObject) ?: return WebhookResult.Check(emptyList())
        return WebhookResult.Check(listOf(target.string("id"), target.string("payment_intent")).filter { it.isNotBlank() })
    }

    /**
     * One request, form encoded the way Stripe takes it
     * @param key Sent as Stripe's Idempotency-Key, so a retried request doesn't do it twice
     */
    private fun call(method: String, path: String, form: List<Pair<String, String>> = emptyList(), key: String? = null): ProcessorResult<JsonObject> {
        val body = form.joinToString("&") { (name, value) -> "${encode(name)}=${encode(value)}" }
        val request = HttpRequest.newBuilder(URI.create("$baseUrl$path"))
            .timeout(Duration.ofSeconds(30))
            .header("Authorization", "Bearer ${settings.secretKey.orEmpty()}")
            .apply {
                key?.let { header("Idempotency-Key", it) }
                if (method == "GET") GET() else {
                    header("Content-Type", "application/x-www-form-urlencoded")
                    method(method, HttpRequest.BodyPublishers.ofString(body))
                }
            }
            .build()

        val response = try {
            client.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (ex: Exception) {
            return ProcessorResult.Failed("Couldn't reach Stripe: ${ex.message ?: ex.toString()}")
        }

        val json = runCatching { Json.parseToJsonElement(response.body()).jsonObject }.getOrNull()
        if (response.statusCode() !in 200..299 || json == null) {
            val message = (json?.get("error") as? JsonObject)?.string("message")?.ifBlank { null } ?: response.body().take(200)
            return ProcessorResult.Failed("Stripe said ${response.statusCode()}: $message")
        }

        return ProcessorResult.Ok(json)
    }

    private companion object {
        const val WEBHOOK_TOLERANCE_SECONDS = 300

        fun encode(text: String): String = URLEncoder.encode(text, Charsets.UTF_8)

        // An id put in a path, which Stripe's never need escaping, but a reader id is typed by an admin
        fun path(text: String): String = encode(text)
    }
}

internal inline fun <T, R> ProcessorResult<T>.map(block: (T) -> R): ProcessorResult<R> = when (this) {
    is ProcessorResult.Ok -> ProcessorResult.Ok(block(value))
    is ProcessorResult.Failed -> this
}

/** A string field, blank when it's missing or not a string */
internal fun JsonObject.string(name: String): String =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content.orEmpty()

internal fun JsonObject.array(name: String): List<JsonObject> =
    runCatching { this[name]!!.jsonArray.map { it.jsonObject } }.getOrDefault(emptyList())

internal fun JsonObject.long(name: String): Long? = (this[name] as? JsonPrimitive)?.content?.toLongOrNull()

internal fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject
