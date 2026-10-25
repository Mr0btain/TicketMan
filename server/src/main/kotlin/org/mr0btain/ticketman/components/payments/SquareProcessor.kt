package org.mr0btain.ticketman.components.payments

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.mr0btain.ticketman.data.payment.CardCharge
import org.mr0btain.ticketman.data.payment.CardChargeStatus
import org.mr0btain.ticketman.data.payment.CardReader
import org.mr0btain.ticketman.data.payment.SquareEnvironment
import org.mr0btain.ticketman.data.payment.SquareSettings
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Base64

/**
 * Square through its REST API, called directly so no SDK is needed. Readers are Square Terminals paired through
 * device codes and take terminal checkouts; links are quick pay payment links, whose order shows when they're paid.
 * A link's reference is `<link id>/<order id>`, since closing it needs the one and checking it the other
 * @param baseUrl Swapped in tests for a local server, otherwise from the environment
 */
class SquareProcessor(
    private val settings: SquareSettings,
    private val currency: String,
    private val baseUrl: String = if (settings.environment == SquareEnvironment.SANDBOX) "https://connect.squareupsandbox.com" else "https://connect.squareup.com",
    private val client: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build()
) : PaymentProcessor {
    override fun readers(): ProcessorResult<List<CardReader>> =
        call("GET", "/v2/devices/codes?status=PAIRED").map { body ->
            body.array("device_codes").filter { it.string("device_id").isNotBlank() }.map { code ->
                CardReader(identifier = code.string("device_id"), name = code.string("name").ifBlank { code.string("device_id") })
            }
        }

    override fun startReader(charge: CardCharge, description: String): ProcessorResult<String> =
        call(
            "POST", "/v2/terminals/checkouts",
            buildJsonObject {
                put("idempotency_key", "reader-${charge.identifier}")
                putJsonObject("checkout") {
                    money("amount_money", charge.amountCents)
                    putJsonObject("device_options") { put("device_id", charge.readerIdentifier) }
                    put("reference_id", charge.identifier.toString())
                    put("note", description.take(NOTE_LENGTH))
                }
            }
        ).map { it.obj("checkout")?.string("id").orEmpty() }

    override fun createLink(charge: CardCharge, description: String, returnUrl: String): ProcessorResult<StartedLink> =
        call(
            "POST", "/v2/online-checkout/payment-links",
            buildJsonObject {
                put("idempotency_key", "link-${charge.identifier}")
                putJsonObject("quick_pay") {
                    put("name", description.take(NAME_LENGTH))
                    money("price_money", charge.amountCents)
                    put("location_id", settings.locationId.trim())
                }
                putJsonObject("checkout_options") { put("redirect_url", returnUrl) }
                put("payment_note", charge.identifier.toString())
            }
        ).map { body ->
            val link = body.obj("payment_link")
            StartedLink("${link?.string("id").orEmpty()}/${link?.string("order_id").orEmpty()}", link?.string("url").orEmpty())
        }

    override fun check(charge: CardCharge): ProcessorResult<ChargeState> {
        val reference = charge.providerReference
        if ('/' in reference) return checkLink(reference.substringAfter('/'))

        return call("GET", "/v2/terminals/checkouts/${path(reference)}").map { body ->
            val checkout = body.obj("checkout") ?: JsonObject(emptyMap())
            when (checkout.string("status")) {
                "COMPLETED" -> ChargeState(CardChargeStatus.SUCCEEDED, paymentReference = checkout.firstOf("payment_ids"))
                "CANCELED" -> when (checkout.string("cancel_reason")) {
                    "TIMED_OUT" -> ChargeState(CardChargeStatus.FAILED, error = "The reader timed out waiting for the card")
                    "BUYER_CANCELED" -> ChargeState(CardChargeStatus.FAILED, error = "Cancelled on the reader")
                    else -> ChargeState(CardChargeStatus.CANCELLED, error = "Cancelled")
                }
                else -> ChargeState(CardChargeStatus.PENDING)
            }
        }
    }

    // A link is paid once its order has a tender whose payment completed
    private fun checkLink(orderIdentifier: String): ProcessorResult<ChargeState> {
        val order = when (val result = call("GET", "/v2/orders/${path(orderIdentifier)}")) {
            is ProcessorResult.Ok -> result.value.obj("order") ?: JsonObject(emptyMap())
            is ProcessorResult.Failed -> return result
        }
        if (order.string("state") == "CANCELED") return ProcessorResult.Ok(ChargeState(CardChargeStatus.CANCELLED, error = "The order was cancelled"))

        val payment = order.array("tenders").map { it.string("payment_id") }.firstOrNull { it.isNotBlank() }
            ?: return ProcessorResult.Ok(ChargeState(CardChargeStatus.PENDING))

        return call("GET", "/v2/payments/${path(payment)}").map { body ->
            if (body.obj("payment")?.string("status") == "COMPLETED") ChargeState(CardChargeStatus.SUCCEEDED, paymentReference = payment)
            else ChargeState(CardChargeStatus.PENDING)
        }
    }

    override fun cancel(charge: CardCharge): ProcessorResult<Unit> {
        val reference = charge.providerReference
        if (reference.isBlank()) return ProcessorResult.Ok(Unit)
        if ('/' in reference) return call("DELETE", "/v2/online-checkout/payment-links/${path(reference.substringBefore('/'))}").map { }
        return call("POST", "/v2/terminals/checkouts/${path(reference)}/cancel").map { }
    }

    override fun refund(paymentReference: String, amountCents: Long, key: String): ProcessorResult<String> =
        call(
            "POST", "/v2/refunds",
            buildJsonObject {
                put("idempotency_key", key.take(IDEMPOTENCY_LENGTH))
                put("payment_id", paymentReference)
                money("amount_money", amountCents)
            }
        ).map { it.obj("refund")?.string("id").orEmpty() }

    override fun webhook(header: (String) -> String?, body: String, url: String): WebhookResult {
        val key = settings.webhookSignatureKey?.takeIf { it.isNotBlank() } ?: return WebhookResult.Refused
        val signature = header("x-square-hmacsha256-signature")?.trim() ?: return WebhookResult.Refused
        val expected = Base64.getEncoder().encodeToString(hmacSha256(key.toByteArray(), (url + body).toByteArray()))
        if (!sameBytes(signature.toByteArray(), expected.toByteArray())) return WebhookResult.Refused

        val target = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull()?.obj("data")?.obj("object")
            ?: return WebhookResult.Check(emptyList())
        val references = listOfNotNull(
            target.obj("checkout")?.string("id"),
            target.obj("payment")?.string("order_id"),
            target.obj("payment")?.string("id")
        )
        return WebhookResult.Check(references.filter { it.isNotBlank() })
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.money(name: String, cents: Long) {
        putJsonObject(name) {
            put("amount", cents)
            put("currency", currency.uppercase())
        }
    }

    private fun JsonObject.firstOf(name: String): String =
        runCatching { (this[name] as kotlinx.serialization.json.JsonArray).first().let { (it as kotlinx.serialization.json.JsonPrimitive).content } }.getOrDefault("")

    private fun call(method: String, path: String, body: JsonObject? = null): ProcessorResult<JsonObject> {
        val request = HttpRequest.newBuilder(URI.create("$baseUrl$path"))
            .timeout(Duration.ofSeconds(30))
            .header("Authorization", "Bearer ${settings.accessToken.orEmpty()}")
            .header("Square-Version", API_VERSION)
            .header("Accept", "application/json")
            .apply {
                if (body == null) method(method, HttpRequest.BodyPublishers.noBody()) else {
                    header("Content-Type", "application/json")
                    method(method, HttpRequest.BodyPublishers.ofString(body.toString()))
                }
            }
            .build()

        val response = try {
            client.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (ex: Exception) {
            return ProcessorResult.Failed("Couldn't reach Square: ${ex.message ?: ex.toString()}")
        }

        val json = runCatching { Json.parseToJsonElement(response.body().ifBlank { "{}" }).jsonObject }.getOrNull()
        if (response.statusCode() !in 200..299 || json == null) {
            val message = json?.array("errors")?.firstOrNull()?.let { it.string("detail").ifBlank { it.string("code") } }?.ifBlank { null }
                ?: response.body().take(200)
            return ProcessorResult.Failed("Square said ${response.statusCode()}: $message")
        }

        return ProcessorResult.Ok(json)
    }

    private companion object {
        /** The API version the calls were written against */
        const val API_VERSION = "2025-01-23"
        const val NOTE_LENGTH = 250
        const val NAME_LENGTH = 255
        const val IDEMPOTENCY_LENGTH = 45

        fun path(text: String): String = URLEncoder.encode(text, Charsets.UTF_8)
    }
}
