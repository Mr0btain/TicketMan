package org.mr0btain.ticketman

import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.mr0btain.ticketman.components.payments.ChargeState
import org.mr0btain.ticketman.components.payments.ProcessorResult
import org.mr0btain.ticketman.components.payments.SimulatedProcessor
import org.mr0btain.ticketman.components.payments.SquareProcessor
import org.mr0btain.ticketman.components.payments.StartedLink
import org.mr0btain.ticketman.components.payments.StripeProcessor
import org.mr0btain.ticketman.components.payments.WebhookResult
import org.mr0btain.ticketman.data.payment.CardCharge
import org.mr0btain.ticketman.data.payment.CardChargeKind
import org.mr0btain.ticketman.data.payment.CardChargeStatus
import org.mr0btain.ticketman.data.payment.CardReader
import org.mr0btain.ticketman.data.payment.PaymentProvider
import org.mr0btain.ticketman.data.payment.SquareSettings
import org.mr0btain.ticketman.data.payment.StripeSettings
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.URLDecoder
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.*

/**
 * The Stripe and Square processors against a local server standing in for their APIs, and the simulated one
 */
class ProcessorTest {
    private data class Request(val method: String, val path: String, val headers: Map<String, String?>, val body: String) {
        val form: Map<String, String>
            get() = body.split("&").filter { it.isNotBlank() }.associate { pair ->
                val (key, value) = pair.split("=", limit = 2)
                URLDecoder.decode(key, Charsets.UTF_8) to URLDecoder.decode(value, Charsets.UTF_8)
            }
    }

    /** Answers each request with what [answer] gives for its method and path, keeping what it was sent */
    private fun api(answer: (String, String) -> Pair<Int, String>, block: (String, MutableList<Request>) -> Unit) {
        val requests = mutableListOf<Request>()
        val server = HttpServer.create(InetSocketAddress("localhost", 0), 0)
        server.createContext("/") { exchange ->
            val headers = listOf("Authorization", "Idempotency-Key", "Square-Version", "Content-Type").associateWith { exchange.requestHeaders.getFirst(it) }
            val path = exchange.requestURI.rawPath + (exchange.requestURI.rawQuery?.let { "?$it" } ?: "")
            synchronized(requests) { requests += Request(exchange.requestMethod, path, headers, exchange.requestBody.readBytes().decodeToString()) }

            val (status, body) = answer(exchange.requestMethod, path)
            val bytes = body.toByteArray()
            exchange.sendResponseHeaders(status, if (bytes.isEmpty()) -1 else bytes.size.toLong())
            if (bytes.isNotEmpty()) exchange.responseBody.use { it.write(bytes) }
            exchange.close()
        }
        server.start()

        try {
            block("http://localhost:${server.address.port}", requests)
        } finally {
            server.stop(0)
        }
    }

    private val reader = CardCharge(kind = CardChargeKind.READER, provider = PaymentProvider.STRIPE, amountCents = 4_250, currency = "USD", readerIdentifier = "tmr_1")
    private val stripe = StripeSettings(secretKey = "sk_test_1", webhookSecret = "whsec_test")

    @Test
    fun stripeSendsAPaymentIntentToTheReader() = api({ _, path ->
        when {
            path == "/v1/payment_intents" -> 200 to """{"id":"pi_1","status":"requires_payment_method"}"""
            path.endsWith("/process_payment_intent") -> 200 to """{"id":"tmr_1"}"""
            else -> 404 to "{}"
        }
    }) { base, requests ->
        val result = StripeProcessor(stripe, "USD", base).startReader(reader, "INV-26-00000001 at Fix It")
        assertEquals(ProcessorResult.Ok("pi_1"), result)

        val (intent, process) = requests
        assertEquals("Bearer sk_test_1", intent.headers["Authorization"])
        assertEquals("intent-${reader.identifier}", intent.headers["Idempotency-Key"])
        assertEquals(
            mapOf(
                "amount" to "4250", "currency" to "usd", "payment_method_types[]" to "card_present", "capture_method" to "automatic",
                "description" to "INV-26-00000001 at Fix It", "metadata[ticketman_charge]" to reader.identifier.toString()
            ),
            intent.form
        )
        assertEquals("/v1/terminal/readers/tmr_1/process_payment_intent", process.path)
        assertEquals(mapOf("payment_intent" to "pi_1"), process.form)
    }

    @Test
    fun stripeCancelsTheIntentWhenTheReaderRefuses() = api({ _, path ->
        when {
            path == "/v1/payment_intents" -> 200 to """{"id":"pi_1"}"""
            path.endsWith("/process_payment_intent") -> 400 to """{"error":{"message":"Reader is currently busy"}}"""
            else -> 200 to "{}"
        }
    }) { base, requests ->
        assertEquals(ProcessorResult.Failed("Stripe said 400: Reader is currently busy"), StripeProcessor(stripe, "USD", base).startReader(reader, "x"))
        assertEquals("/v1/payment_intents/pi_1/cancel", requests.last().path)
    }

    @Test
    fun stripeReadsWhereAChargeStands() {
        fun check(body: String, charge: CardCharge = reader.copy(providerReference = "pi_1"), readerBody: String = "{}"): ChargeState {
            var state: ChargeState? = null
            api({ _, path -> 200 to if (path.startsWith("/v1/terminal/readers")) readerBody else body }) { base, _ ->
                state = (StripeProcessor(stripe, "USD", base).check(charge) as ProcessorResult.Ok).value
            }
            return state!!
        }

        assertEquals(ChargeState(CardChargeStatus.SUCCEEDED, paymentReference = "pi_1"), check("""{"status":"succeeded"}"""))
        assertEquals(CardChargeStatus.CANCELLED, check("""{"status":"canceled"}""").status)
        assertEquals(ChargeState(CardChargeStatus.FAILED, error = "Your card was declined."), check("""{"status":"requires_payment_method","last_payment_error":{"message":"Your card was declined."}}"""))
        assertEquals(CardChargeStatus.PENDING, check("""{"status":"requires_payment_method","last_payment_error":null}""").status)
        assertEquals(CardChargeStatus.PENDING, check("""{"status":"processing"}""").status)

        // The reader timing out leaves the intent waiting, the reader's action says it failed
        val timedOut = """{"action":{"status":"failed","failure_message":"Timed out","process_payment_intent":{"payment_intent":"pi_1"}}}"""
        assertEquals(ChargeState(CardChargeStatus.FAILED, error = "Timed out"), check("""{"status":"requires_payment_method"}""", readerBody = timedOut))

        val link = reader.copy(kind = CardChargeKind.LINK, providerReference = "cs_1", readerIdentifier = "")
        assertEquals(ChargeState(CardChargeStatus.SUCCEEDED, paymentReference = "pi_9"), check("""{"status":"complete","payment_status":"paid","payment_intent":"pi_9"}""", link))
        assertEquals(CardChargeStatus.CANCELLED, check("""{"status":"expired","payment_status":"unpaid"}""", link).status)
        assertEquals(CardChargeStatus.PENDING, check("""{"status":"open","payment_status":"unpaid"}""", link).status)
    }

    @Test
    fun stripeLinksAreCheckoutSessionsAndRefundsGoAgainstTheIntent() = api({ _, path ->
        when (path) {
            "/v1/checkout/sessions" -> 200 to """{"id":"cs_1","url":"https://checkout.stripe.com/c/pay/cs_1"}"""
            "/v1/refunds" -> 200 to """{"id":"re_1"}"""
            else -> 404 to "{}"
        }
    }) { base, requests ->
        val processor = StripeProcessor(stripe, "EUR", base)
        val link = reader.copy(kind = CardChargeKind.LINK, readerIdentifier = "")
        assertEquals(ProcessorResult.Ok(StartedLink("cs_1", "https://checkout.stripe.com/c/pay/cs_1")), processor.createLink(link, "INV at Fix It", "https://repairs.example.com/status/abc"))

        val form = requests.single().form
        assertEquals("payment", form["mode"])
        assertEquals("eur", form["line_items[0][price_data][currency]"])
        assertEquals("4250", form["line_items[0][price_data][unit_amount]"])
        assertEquals("INV at Fix It", form["line_items[0][price_data][product_data][name]"])
        assertEquals("https://repairs.example.com/status/abc", form["success_url"])
        assertEquals(link.identifier.toString(), form["payment_intent_data[metadata][ticketman_charge]"])

        assertEquals(ProcessorResult.Ok("re_1"), processor.refund("pi_9", 1_000, "refund-1"))
        assertEquals(mapOf("payment_intent" to "pi_9", "amount" to "1000"), requests.last().form)
        assertEquals("refund-1", requests.last().headers["Idempotency-Key"])
    }

    @Test
    fun stripeWebhooksAreCheckedAgainstTheSecret() {
        val now = 1_760_000_000L
        val processor = StripeProcessor(stripe, "USD", "http://unused", now = { now })
        val body = """{"type":"payment_intent.succeeded","data":{"object":{"id":"pi_1","object":"payment_intent"}}}"""
        fun signature(time: Long, secret: String = "whsec_test") = "t=$time,v1=" + hmac(secret.toByteArray(), "$time.$body").joinToString("") { "%02x".format(it) }

        assertEquals(WebhookResult.Check(listOf("pi_1")), processor.webhook({ if (it == "Stripe-Signature") signature(now) else null }, body, ""))
        assertEquals(WebhookResult.Refused, processor.webhook({ signature(now, "whsec_other") }, body, ""))
        assertEquals(WebhookResult.Refused, processor.webhook({ signature(now - 600) }, body, ""), "too old")
        assertEquals(WebhookResult.Refused, processor.webhook({ null }, body, ""))
        assertEquals(WebhookResult.Refused, StripeProcessor(stripe.copy(webhookSecret = null), "USD").webhook({ signature(now) }, body, ""))
    }

    private val square = SquareSettings(accessToken = "EAAA1", locationId = "L1", webhookSignatureKey = "sq_key")

    @Test
    fun squareSendsATerminalCheckout() = api({ _, _ -> 200 to """{"checkout":{"id":"chk_1","status":"PENDING"}}""" }) { base, requests ->
        val charge = reader.copy(provider = PaymentProvider.SQUARE, readerIdentifier = "device_1")
        assertEquals(ProcessorResult.Ok("chk_1"), SquareProcessor(square, "USD", base).startReader(charge, "Sale at Fix It"))

        val request = requests.single()
        assertEquals("/v2/terminals/checkouts", request.path)
        assertEquals("Bearer EAAA1", request.headers["Authorization"])
        assertNotNull(request.headers["Square-Version"])
        val checkout = Json.parseToJsonElement(request.body).jsonObject["checkout"]!!.jsonObject
        assertEquals(4250, checkout["amount_money"]!!.jsonObject["amount"]!!.jsonPrimitive.int)
        assertEquals("USD", checkout["amount_money"]!!.jsonObject["currency"]!!.jsonPrimitive.content)
        assertEquals("device_1", checkout["device_options"]!!.jsonObject["device_id"]!!.jsonPrimitive.content)
        assertEquals(charge.identifier.toString(), checkout["reference_id"]!!.jsonPrimitive.content)
        assertEquals("reader-${charge.identifier}", Json.parseToJsonElement(request.body).jsonObject["idempotency_key"]!!.jsonPrimitive.content)
    }

    @Test
    fun squareReadsTerminalCheckoutsAndLinkOrders() {
        fun check(charge: CardCharge, answer: (String) -> String): ChargeState {
            var state: ChargeState? = null
            api({ _, path -> 200 to answer(path) }) { base, _ -> state = (SquareProcessor(square, "USD", base).check(charge) as ProcessorResult.Ok).value }
            return state!!
        }

        val terminal = reader.copy(provider = PaymentProvider.SQUARE, providerReference = "chk_1")
        assertEquals(ChargeState(CardChargeStatus.SUCCEEDED, paymentReference = "pay_1"), check(terminal) { """{"checkout":{"status":"COMPLETED","payment_ids":["pay_1"]}}""" })
        assertEquals(CardChargeStatus.FAILED, check(terminal) { """{"checkout":{"status":"CANCELED","cancel_reason":"TIMED_OUT"}}""" }.status)
        assertEquals(CardChargeStatus.CANCELLED, check(terminal) { """{"checkout":{"status":"CANCELED","cancel_reason":"SELLER_CANCELED"}}""" }.status)
        assertEquals(CardChargeStatus.PENDING, check(terminal) { """{"checkout":{"status":"IN_PROGRESS"}}""" }.status)

        val link = terminal.copy(kind = CardChargeKind.LINK, providerReference = "link_1/order_1")
        val paid = check(link) { path ->
            if (path == "/v2/orders/order_1") """{"order":{"state":"OPEN","tenders":[{"payment_id":"pay_2"}]}}""" else """{"payment":{"status":"COMPLETED"}}"""
        }
        assertEquals(ChargeState(CardChargeStatus.SUCCEEDED, paymentReference = "pay_2"), paid)
        assertEquals(CardChargeStatus.PENDING, check(link) { """{"order":{"state":"OPEN"}}""" }.status)
    }

    @Test
    fun squareLinksAndRefunds() = api({ method, path ->
        when {
            path == "/v2/online-checkout/payment-links" -> 200 to """{"payment_link":{"id":"link_1","order_id":"order_1","url":"https://square.link/u/abc"}}"""
            method == "DELETE" -> 200 to """{"id":"link_1"}"""
            path == "/v2/refunds" -> 200 to """{"refund":{"id":"ref_1"}}"""
            else -> 400 to """{"errors":[{"code":"BAD_REQUEST","detail":"Nope"}]}"""
        }
    }) { base, requests ->
        val processor = SquareProcessor(square, "USD", base)
        val link = reader.copy(provider = PaymentProvider.SQUARE, kind = CardChargeKind.LINK, readerIdentifier = "")
        assertEquals(ProcessorResult.Ok(StartedLink("link_1/order_1", "https://square.link/u/abc")), processor.createLink(link, "INV at Fix It", "https://repairs.example.com/status/abc"))
        val sent = Json.parseToJsonElement(requests.last().body).jsonObject
        assertEquals("L1", sent["quick_pay"]!!.jsonObject["location_id"]!!.jsonPrimitive.content)
        assertEquals("https://repairs.example.com/status/abc", sent["checkout_options"]!!.jsonObject["redirect_url"]!!.jsonPrimitive.content)

        assertEquals(ProcessorResult.Ok(Unit), processor.cancel(link.copy(providerReference = "link_1/order_1")))
        assertEquals("/v2/online-checkout/payment-links/link_1", requests.last().path)

        assertEquals(ProcessorResult.Ok("ref_1"), processor.refund("pay_2", 500, "refund-1"))
        assertEquals("pay_2", Json.parseToJsonElement(requests.last().body).jsonObject["payment_id"]!!.jsonPrimitive.content)

        assertEquals(ProcessorResult.Failed("Square said 400: Nope"), processor.readers())
    }

    @Test
    fun squareWebhooksSignTheAddressAndBody() {
        val processor = SquareProcessor(square, "USD", "http://unused")
        val url = "https://repairs.example.com/status/payments/square/webhook"
        val body = """{"type":"payment.updated","data":{"type":"payment","id":"pay_1","object":{"payment":{"id":"pay_1","order_id":"order_1"}}}}"""
        val signature = Base64.getEncoder().encodeToString(hmac("sq_key".toByteArray(), url + body))

        assertEquals(WebhookResult.Check(listOf("order_1", "pay_1")), processor.webhook({ if (it == "x-square-hmacsha256-signature") signature else null }, body, url))
        assertEquals(WebhookResult.Refused, processor.webhook({ signature }, body, "https://elsewhere.example.com/webhook"))
        assertEquals(WebhookResult.Refused, processor.webhook({ null }, body, url))
    }

    @Test
    fun unreachableProcessorsFailWithAReason() {
        val closed = ServerSocket(0).use { it.localPort }
        val result = StripeProcessor(stripe, "USD", "http://localhost:$closed").readers()
        assertIs<ProcessorResult.Failed>(result)
        assertContains(result.reason, "Couldn't reach Stripe")
    }

    @Test
    fun theSimulatedReaderPaysAfterAMomentUnlessTheAmountEndsInTwo() {
        var now = 1_000L
        val processor = SimulatedProcessor { now }
        val charge = reader.copy(provider = PaymentProvider.SIMULATED, createdAt = 1_000)

        assertEquals(CardChargeStatus.PENDING, (processor.check(charge) as ProcessorResult.Ok).value.status)
        now += SimulatedProcessor.READER_DELAY_MILLIS
        assertEquals(CardChargeStatus.SUCCEEDED, (processor.check(charge) as ProcessorResult.Ok).value.status)
        assertEquals(CardChargeStatus.FAILED, (processor.check(charge.copy(amountCents = 1_002)) as ProcessorResult.Ok).value.status)
        assertEquals(listOf(CardReader("sim_reader_1", "Simulated reader"), CardReader("sim_reader_2", "Simulated reader 2")), (processor.readers() as ProcessorResult.Ok).value)
    }

    private fun hmac(key: ByteArray, data: String): ByteArray =
        Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(key, "HmacSHA256"))
            doFinal(data.toByteArray())
        }
}
