package org.mr0btain.ticketman

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.mr0btain.ticketman.components.database.PaymentDatabase
import org.mr0btain.ticketman.components.database.UserDatabase
import org.mr0btain.ticketman.components.payments.ChargeState
import org.mr0btain.ticketman.components.payments.PaymentProcessor
import org.mr0btain.ticketman.components.payments.PaymentProcessors
import org.mr0btain.ticketman.components.payments.ProcessorResult
import org.mr0btain.ticketman.components.payments.StartedLink
import org.mr0btain.ticketman.components.payments.WebhookResult
import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.data.invoice.*
import org.mr0btain.ticketman.data.payment.CardCharge
import org.mr0btain.ticketman.data.payment.CardChargeKind
import org.mr0btain.ticketman.data.payment.CardChargeStatus
import org.mr0btain.ticketman.data.payment.CardReader
import org.mr0btain.ticketman.data.payment.PaymentOptions
import org.mr0btain.ticketman.data.payment.PaymentProvider
import org.mr0btain.ticketman.data.payment.PaymentSettings
import org.mr0btain.ticketman.data.payment.StripeSettings
import org.mr0btain.ticketman.data.shop.ShopProfile
import org.mr0btain.ticketman.data.ticket.Device
import org.mr0btain.ticketman.data.ticket.Ticket
import org.mr0btain.ticketman.data.user.AccessLevel
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.server.request.ChargeRefundRequest
import org.mr0btain.ticketman.server.request.CheckInRequest
import org.mr0btain.ticketman.server.request.InvoiceChangeRequest
import org.mr0btain.ticketman.server.request.LoginRequest
import org.mr0btain.ticketman.server.request.PaymentLinkRequest
import org.mr0btain.ticketman.server.request.PaymentRequest
import org.mr0btain.ticketman.server.request.PickupRequest
import org.mr0btain.ticketman.server.request.ReaderChargeRequest
import org.mr0btain.ticketman.server.request.RefundRequest
import org.mr0btain.ticketman.server.request.SaleRequest
import org.mr0btain.ticketman.server.response.AuthResponse
import org.mr0btain.ticketman.server.response.CheckInResponse
import kotlin.test.*
import kotlin.uuid.Uuid

/**
 * Card payments through a processor: readers, links, the status page, refunds back onto the card. The processor is
 * a fake that says whatever the test sets, see [Fake]
 */
class PaymentTest {
    private val dataDir = createTestDataDir()
    private val json = Json { ignoreUnknownKeys = true }

    private val admin = User(username = "boss", password = "boss_pass", accessLevel = AccessLevel.ADMIN)
    private val tech = User(username = "tech", password = "tech_pass", accessLevel = AccessLevel.NORMAL)
    private val clerk = User(username = "clerk", password = "clerk_pass", accessLevel = AccessLevel.RESTRICTED)

    private lateinit var token: String
    private lateinit var adminToken: String
    private lateinit var clerkToken: String

    private val reader = CardReader("tmr_front", "Front counter")
    private val fake = Fake()

    /** Says each charge is whatever [states] holds for it, waiting otherwise, and keeps what it was asked */
    private class Fake : PaymentProcessor {
        val states = mutableMapOf<String, ChargeState>()
        val started = mutableListOf<CardCharge>()
        val cancelled = mutableListOf<String>()
        val refunds = mutableListOf<Triple<String, Long, String>>()
        var refuseRefunds: String? = null
        var webhookReferences: List<String>? = null

        override fun readers() = ProcessorResult.Ok(listOf(CardReader("tmr_front", "Front counter"), CardReader("tmr_back", "Back")))

        override fun startReader(charge: CardCharge, description: String): ProcessorResult<String> {
            started += charge
            return ProcessorResult.Ok("pi_${charge.identifier}")
        }

        override fun createLink(charge: CardCharge, description: String, returnUrl: String): ProcessorResult<StartedLink> {
            started += charge
            return ProcessorResult.Ok(StartedLink("cs_${charge.identifier}", "https://checkout.example/${charge.identifier}?back=$returnUrl"))
        }

        override fun check(charge: CardCharge) = ProcessorResult.Ok(states[charge.providerReference] ?: ChargeState(CardChargeStatus.PENDING))

        override fun cancel(charge: CardCharge): ProcessorResult<Unit> {
            cancelled += charge.providerReference
            return ProcessorResult.Ok(Unit)
        }

        override fun refund(paymentReference: String, amountCents: Long, key: String): ProcessorResult<String> {
            refuseRefunds?.let { return ProcessorResult.Failed(it) }
            refunds += Triple(paymentReference, amountCents, key)
            return ProcessorResult.Ok("re_${refunds.size}")
        }

        override fun webhook(header: (String) -> String?, body: String, url: String): WebhookResult =
            webhookReferences?.takeIf { header("Signature") == "good" }?.let { WebhookResult.Check(it) } ?: WebhookResult.Refused

        fun pay(charge: CardCharge) {
            states[charge.providerReference.ifBlank { "pi_${charge.identifier}" }] = ChargeState(CardChargeStatus.SUCCEEDED, paymentReference = "pay_${charge.identifier}")
        }
    }

    @BeforeTest
    fun useFake() {
        PaymentProcessors.factory = { fake }
    }

    @AfterTest
    fun cleanup() {
        PaymentProcessors.reset()
        cleanupTestDataDir(dataDir)
    }

    private suspend fun ApplicationTestBuilder.setup(settings: PaymentSettings = stripe(), publicUrl: String = "https://repairs.example.com", developmentMode: Boolean = true) {
        configureTestServer(dataDir, developmentMode = developmentMode)
        startApplication()
        UserDatabase().apply {
            add(admin)
            add(tech)
            add(clerk)
        }
        token = login(tech)
        adminToken = login(admin)
        clerkToken = login(clerk)
        postJson("/api/shop/update", json.encodeToString(ShopProfile(name = "Fix It", publicUrl = publicUrl)), adminToken)
        assertEquals(HttpStatusCode.OK, postJson("/api/payment/settings", json.encodeToString(settings), adminToken).status)
    }

    private fun stripe(enabled: Boolean = true, online: Boolean = true) = PaymentSettings(
        enabled = enabled,
        provider = PaymentProvider.STRIPE,
        stripe = StripeSettings(secretKey = "sk_test_123", webhookSecret = "whsec_1"),
        readers = listOf(CardReader("tmr_front", "Front counter")),
        onlinePayments = online
    )

    private suspend fun ApplicationTestBuilder.login(user: User): String {
        val response = client.post("/api/auth") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(LoginRequest(user.username, user.password)))
        }
        return json.decodeFromString<AuthResponse>(response.bodyAsText()).accessToken!!
    }

    private suspend fun ApplicationTestBuilder.postJson(path: String, body: String, as_: String? = null) =
        client.post(path) {
            contentType(ContentType.Application.Json)
            bearerAuth(as_ ?: token)
            setBody(body)
        }

    private suspend fun ApplicationTestBuilder.get(path: String, as_: String? = null) = client.get(path) { bearerAuth(as_ ?: token) }

    private fun repair() = Ticket(device = Device("Apple", "iPhone 14"), problem = "Cracked screen")

    /** A checked in ticket whose open invoice has $80 of labor on it */
    private suspend fun ApplicationTestBuilder.openInvoice(): Pair<Ticket, Invoice> {
        val customer = Customer(name = "Ada Lovelace", phone = "5551234567")
        assertEquals(HttpStatusCode.Created, postJson("/api/data/customer/add", json.encodeToString(customer)).status)
        val checkIn = postJson("/api/counter/check-in", json.encodeToString(CheckInRequest(ticket = repair(), customerIdentifier = customer.identifier)))
        val ticket = json.decodeFromString<CheckInResponse>(checkIn.bodyAsText()).ticket

        val invoice = json.decodeFromString<Invoice>(postJson("/api/invoice/ticket/${ticket.identifier}", "").bodyAsText())
        val labor = Charge(type = ChargeType.LABOR, description = "Screen", quantity = 1.0, unitPriceCents = 8_000, taxable = false)
        val updated = postJson("/api/invoice/${invoice.identifier}/update", json.encodeToString(InvoiceChangeRequest(added = listOf(labor))))
        assertEquals(HttpStatusCode.OK, updated.status)
        return ticket to json.decodeFromString(updated.bodyAsText())
    }

    private suspend fun ApplicationTestBuilder.invoice(identifier: Uuid): Invoice =
        json.decodeFromString(get("/api/invoice/$identifier").bodyAsText())

    private suspend fun ApplicationTestBuilder.readerCharge(amountCents: Long, invoice: Invoice? = null) =
        postJson("/api/payment/charge/reader", json.encodeToString(ReaderChargeRequest(reader.identifier, amountCents, invoice?.identifier)))

    private suspend fun ApplicationTestBuilder.charge(identifier: Uuid): CardCharge =
        json.decodeFromString(get("/api/payment/charge/$identifier").bodyAsText())

    @Test
    fun settingsKeepTheirKeysOnTheServer() = testApplication {
        setup()

        val read = json.decodeFromString<PaymentSettings>(get("/api/payment/settings", adminToken).bodyAsText())
        assertNull(read.stripe.secretKey)
        assertNull(read.stripe.webhookSecret)
        assertTrue(read.stripeKeySet)
        assertTrue(read.stripeWebhookSecretSet)

        // Saving what came back keeps the keys
        assertEquals(HttpStatusCode.OK, postJson("/api/payment/settings", json.encodeToString(read.copy(currency = "eur")), adminToken).status)
        val saved = PaymentDatabase().settings()
        assertEquals("sk_test_123", saved.stripe.secretKey)
        assertEquals("EUR", saved.currency)

        // Turned on without a key is refused
        val noKey = PaymentSettings(enabled = true, provider = PaymentProvider.SQUARE)
        assertEquals(HttpStatusCode.BadRequest, postJson("/api/payment/settings", json.encodeToString(noKey), adminToken).status)

        assertEquals(HttpStatusCode.Forbidden, get("/api/payment/settings").status)
        assertEquals(HttpStatusCode.Forbidden, get("/api/payment/settings", clerkToken).status)

        // The counter's view has no keys, and anyone logged in reads it
        val options = json.decodeFromString<PaymentOptions>(get("/api/payment/options", clerkToken).bodyAsText())
        assertEquals(PaymentOptions(enabled = true, provider = PaymentProvider.STRIPE, readers = listOf(reader), links = true, onlinePayments = true), options)

        val readers = json.decodeFromString<List<CardReader>>(get("/api/payment/readers", adminToken).bodyAsText())
        assertEquals(2, readers.size)
    }

    @Test
    fun theSimulatedProcessorIsOnlyForDevelopment() = testApplication {
        setup(stripe(enabled = false), developmentMode = false)

        val simulated = PaymentSettings(enabled = true, provider = PaymentProvider.SIMULATED)
        val refused = postJson("/api/payment/settings", json.encodeToString(simulated), adminToken)
        assertEquals(HttpStatusCode.BadRequest, refused.status)
        assertEquals(PaymentOptions(), json.decodeFromString<PaymentOptions>(get("/api/payment/options").bodyAsText()))
    }

    @Test
    fun aReaderPaymentLandsOnItsInvoiceOnce() = testApplication {
        setup()
        val (_, invoice) = openInvoice()

        val response = readerCharge(5_000, invoice)
        assertEquals(HttpStatusCode.Created, response.status)
        val started = json.decodeFromString<CardCharge>(response.bodyAsText())
        assertEquals(CardChargeStatus.PENDING, started.status)
        assertEquals(CardChargeKind.READER, started.kind)
        assertEquals("pi_${started.identifier}", started.providerReference)
        assertEquals("Front counter", started.readerName)
        assertEquals("tech", started.createdBy)

        // One card at a time on a reader
        assertEquals(HttpStatusCode.Conflict, readerCharge(1_000, invoice).status)

        fake.pay(started)
        val paid = charge(started.identifier)
        assertEquals(CardChargeStatus.SUCCEEDED, paid.status)
        assertEquals("pay_${started.identifier}", paid.paymentReference)

        // Asked again doesn't add it twice
        charge(started.identifier)
        val after = invoice(invoice.identifier)
        val payment = after.payments.single()
        assertEquals(PaymentMethod.CARD, payment.method)
        assertEquals(5_000, payment.amountCents)
        assertEquals(PaymentProvider.STRIPE, payment.processor)
        assertEquals("pay_${started.identifier}", payment.processorReference)
        assertEquals(started.identifier, payment.chargeIdentifier)
        assertEquals("Front counter", payment.note)
        assertEquals(paid.paymentIdentifier, payment.identifier)
        assertEquals(3_000, after.balanceCents)

        val listed = json.decodeFromString<List<CardCharge>>(get("/api/payment/charges/invoice/${invoice.identifier}", clerkToken).bodyAsText())
        assertEquals(listOf(started.identifier), listed.map { it.identifier })
    }

    @Test
    fun readerChargesAreHeldToWhatsOwed() = testApplication {
        setup()
        val (_, invoice) = openInvoice()

        assertEquals(HttpStatusCode.BadRequest, readerCharge(8_001, invoice).status)
        assertEquals(HttpStatusCode.BadRequest, readerCharge(0, invoice).status)
        val other = postJson("/api/payment/charge/reader", json.encodeToString(ReaderChargeRequest("tmr_unknown", 100)))
        assertEquals(HttpStatusCode.BadRequest, other.status)
        assertEquals(HttpStatusCode.Forbidden, postJson("/api/payment/charge/reader", json.encodeToString(ReaderChargeRequest(reader.identifier, 100)), clerkToken).status)

        // Turned off, nothing can be sent
        postJson("/api/payment/settings", json.encodeToString(stripe(enabled = false)), adminToken)
        assertEquals(HttpStatusCode.Conflict, readerCharge(100, invoice).status)
    }

    @Test
    fun aDeclinedCardChangesNothing() = testApplication {
        setup()
        val (_, invoice) = openInvoice()

        val started = json.decodeFromString<CardCharge>(readerCharge(5_000, invoice).bodyAsText())
        fake.states[started.providerReference] = ChargeState(CardChargeStatus.FAILED, error = "Your card was declined.")

        val failed = charge(started.identifier)
        assertEquals(CardChargeStatus.FAILED, failed.status)
        assertEquals("Your card was declined.", failed.error)
        assertTrue(invoice(invoice.identifier).payments.isEmpty())

        // The reader is free again
        assertEquals(HttpStatusCode.Created, readerCharge(5_000, invoice).status)
    }

    @Test
    fun aSaleClaimsAReaderPaymentOnce() = testApplication {
        setup()
        val started = json.decodeFromString<CardCharge>(readerCharge(1_500).bodyAsText())
        assertNull(started.invoiceIdentifier)

        val line = Charge(type = ChargeType.OTHER, description = "Case", quantity = 1.0, unitPriceCents = 1_500, taxable = false)
        val sale = SaleRequest(charges = listOf(line), payments = listOf(PaymentRequest(PaymentMethod.CARD, 1_500, chargeIdentifier = started.identifier)))

        // Not paid yet
        assertEquals(HttpStatusCode.Conflict, postJson("/api/counter/sale", json.encodeToString(sale)).status)

        fake.pay(started)
        assertTrue(charge(started.identifier).unclaimed)

        val wrongAmount = sale.copy(payments = listOf(PaymentRequest(PaymentMethod.CARD, 1_400, chargeIdentifier = started.identifier), PaymentRequest(PaymentMethod.CASH, 100)))
        assertEquals(HttpStatusCode.Conflict, postJson("/api/counter/sale", json.encodeToString(wrongAmount)).status)

        val sold = postJson("/api/counter/sale", json.encodeToString(sale))
        assertEquals(HttpStatusCode.Created, sold.status)
        val invoice = json.decodeFromString<Invoice>(sold.bodyAsText())
        val payment = invoice.payments.single()
        assertEquals(PaymentProvider.STRIPE, payment.processor)
        assertEquals("pay_${started.identifier}", payment.processorReference)

        val claimed = charge(started.identifier)
        assertEquals(invoice.identifier, claimed.invoiceIdentifier)
        assertEquals(payment.identifier, claimed.paymentIdentifier)
        assertFalse(claimed.unclaimed)

        // Not twice
        val again = postJson("/api/counter/sale", json.encodeToString(sale))
        assertEquals(HttpStatusCode.Conflict, again.status)
        assertContains(again.bodyAsText(), "already on ${invoice.label}")
    }

    @Test
    fun aRefundGoesBackOntoTheCard() = testApplication {
        setup()
        val (ticket, invoice) = openInvoice()
        val started = json.decodeFromString<CardCharge>(readerCharge(8_000, invoice).bodyAsText())
        fake.pay(started)
        charge(started.identifier)
        assertEquals(HttpStatusCode.OK, postJson("/api/counter/pickup", json.encodeToString(PickupRequest(ticket.identifier))).status)
        val paid = invoice(invoice.identifier)
        val card = paid.payments.single()

        // The processor refusing leaves nothing saved
        fake.refuseRefunds = "Stripe said 400: Charge already refunded"
        val refused = postJson("/api/invoice/${invoice.identifier}/refund", json.encodeToString(RefundRequest(PaymentMethod.CARD, 2_000, "Scratched", paymentIdentifier = card.identifier)), adminToken)
        assertEquals(HttpStatusCode.Conflict, refused.status)
        assertContains(refused.bodyAsText(), "Charge already refunded")
        assertEquals(1, invoice(invoice.identifier).payments.size)
        fake.refuseRefunds = null

        val response = postJson("/api/invoice/${invoice.identifier}/refund", json.encodeToString(RefundRequest(PaymentMethod.CARD, 2_000, "Scratched", paymentIdentifier = card.identifier)), adminToken)
        assertEquals(HttpStatusCode.OK, response.status)
        val refunded = json.decodeFromString<Invoice>(response.bodyAsText())
        val refund = refunded.payments.single { it.isRefund }
        assertEquals(-2_000, refund.amountCents)
        assertEquals(card.identifier, refund.refundOf)
        assertEquals(PaymentProvider.STRIPE, refund.processor)
        assertEquals("re_1", refund.processorReference)
        assertEquals(Triple("pay_${started.identifier}", 2_000L, "refund-${refund.identifier}"), fake.refunds.single())
        assertEquals(6_000, refunded.refundableCents(card))

        // No more than is left of that payment
        val tooMuch = postJson("/api/invoice/${invoice.identifier}/refund", json.encodeToString(RefundRequest(PaymentMethod.CARD, 6_001, "More", paymentIdentifier = card.identifier)), adminToken)
        assertEquals(HttpStatusCode.BadRequest, tooMuch.status)
        val notCard = postJson("/api/invoice/${invoice.identifier}/refund", json.encodeToString(RefundRequest(PaymentMethod.CASH, 100, "Cash", paymentIdentifier = card.identifier)), adminToken)
        assertEquals(HttpStatusCode.BadRequest, notCard.status)
    }

    @Test
    fun takingACardPaymentOffRefundsIt() = testApplication {
        setup()
        val (_, invoice) = openInvoice()
        val started = json.decodeFromString<CardCharge>(readerCharge(3_000, invoice).bodyAsText())
        fake.pay(started)
        charge(started.identifier)
        val payment = invoice(invoice.identifier).payments.single()

        fake.refuseRefunds = "Square said 500: down"
        assertEquals(HttpStatusCode.Conflict, client.delete("/api/invoice/${invoice.identifier}/payments/${payment.identifier}") { bearerAuth(token) }.status)
        assertEquals(1, invoice(invoice.identifier).payments.size)
        fake.refuseRefunds = null

        val removed = client.delete("/api/invoice/${invoice.identifier}/payments/${payment.identifier}") { bearerAuth(token) }
        assertEquals(HttpStatusCode.OK, removed.status)
        assertTrue(invoice(invoice.identifier).payments.isEmpty())
        assertEquals(3_000, fake.refunds.single().second)
        assertEquals(CardChargeStatus.REFUNDED, charge(started.identifier).status)
    }

    @Test
    fun cancellingStopsTheReaderUnlessTheCardWentThrough() = testApplication {
        setup()
        val (_, invoice) = openInvoice()

        val first = json.decodeFromString<CardCharge>(readerCharge(1_000, invoice).bodyAsText())
        val cancelled = postJson("/api/payment/charge/${first.identifier}/cancel", "")
        assertEquals(HttpStatusCode.OK, cancelled.status)
        assertEquals(CardChargeStatus.CANCELLED, json.decodeFromString<CardCharge>(cancelled.bodyAsText()).status)
        assertEquals(listOf(first.providerReference), fake.cancelled)
        assertEquals(HttpStatusCode.Conflict, postJson("/api/payment/charge/${first.identifier}/cancel", "").status)

        // Paid just before the cancel: it's kept, and lands on the invoice
        val second = json.decodeFromString<CardCharge>(readerCharge(1_000, invoice).bodyAsText())
        fake.pay(second)
        val late = postJson("/api/payment/charge/${second.identifier}/cancel", "")
        assertEquals(HttpStatusCode.Conflict, late.status)
        assertEquals("The card went through before it could be cancelled", late.bodyAsText())
        assertEquals(1_000, invoice(invoice.identifier).paidCents)
    }

    @Test
    fun anUnclaimedPaymentIsRefundedByAnAdmin() = testApplication {
        setup()
        val started = json.decodeFromString<CardCharge>(readerCharge(2_500).bodyAsText())
        fake.pay(started)
        charge(started.identifier)

        val all = json.decodeFromString<List<CardCharge>>(get("/api/payment/charges", adminToken).bodyAsText())
        assertTrue(all.single().unclaimed)

        val path = "/api/payment/charge/${started.identifier}/refund"
        assertEquals(HttpStatusCode.Forbidden, postJson(path, json.encodeToString(ChargeRefundRequest("Sale not finished"))).status)
        assertEquals(HttpStatusCode.BadRequest, postJson(path, json.encodeToString(ChargeRefundRequest(" ")), adminToken).status)

        val refunded = postJson(path, json.encodeToString(ChargeRefundRequest("Sale not finished")), adminToken)
        assertEquals(HttpStatusCode.OK, refunded.status)
        assertEquals(CardChargeStatus.REFUNDED, json.decodeFromString<CardCharge>(refunded.bodyAsText()).status)
        assertEquals(Triple("pay_${started.identifier}", 2_500L, "unclaimed-${started.identifier}"), fake.refunds.single())
        assertEquals(HttpStatusCode.Conflict, postJson(path, json.encodeToString(ChargeRefundRequest("Again")), adminToken).status)
    }

    @Test
    fun paymentLinksAreHandedOutOncePerAmount() = testApplication {
        setup()
        val (ticket, invoice) = openInvoice()

        val response = postJson("/api/payment/charge/link", json.encodeToString(PaymentLinkRequest(invoice.identifier)))
        assertEquals(HttpStatusCode.Created, response.status)
        val link = json.decodeFromString<CardCharge>(response.bodyAsText())
        assertEquals(CardChargeKind.LINK, link.kind)
        assertEquals(8_000, link.amountCents)
        assertContains(link.url, "back=https://repairs.example.com/status/${ticket.statusToken}")

        // The same amount gets the same link; another closes it
        val again = json.decodeFromString<CardCharge>(postJson("/api/payment/charge/link", json.encodeToString(PaymentLinkRequest(invoice.identifier))).bodyAsText())
        assertEquals(link.identifier, again.identifier)
        val part = json.decodeFromString<CardCharge>(postJson("/api/payment/charge/link", json.encodeToString(PaymentLinkRequest(invoice.identifier, 2_000))).bodyAsText())
        assertNotEquals(link.identifier, part.identifier)
        assertEquals(CardChargeStatus.CANCELLED, charge(link.identifier).status)

        assertEquals(HttpStatusCode.BadRequest, postJson("/api/payment/charge/link", json.encodeToString(PaymentLinkRequest(invoice.identifier, 9_000))).status)
    }

    @Test
    fun linksNeedThePublicAddress() = testApplication {
        setup(publicUrl = "")
        val (_, invoice) = openInvoice()

        assertFalse(json.decodeFromString<PaymentOptions>(get("/api/payment/options").bodyAsText()).links)
        assertEquals(HttpStatusCode.Conflict, postJson("/api/payment/charge/link", json.encodeToString(PaymentLinkRequest(invoice.identifier))).status)
    }

    @Test
    fun customersPayFromTheStatusPage() = testApplication {
        setup()
        val (ticket, invoice) = openInvoice()
        val noRedirects = createClient { followRedirects = false }

        val page = client.get("/status/${ticket.statusToken}").bodyAsText()
        assertContains(page, """href="${ticket.statusToken}/pay"""")
        assertContains(page, "Pay $80.00 online")

        val pay = noRedirects.get("/status/${ticket.statusToken}/pay")
        assertEquals(HttpStatusCode.SeeOther, pay.status)
        val charge = PaymentDatabase().forInvoice(invoice.identifier)!!.single()
        assertEquals("customer", charge.createdBy)
        assertEquals(charge.url, pay.headers[HttpHeaders.Location])

        // Back from paying, the page asks and shows it paid
        fake.pay(charge)
        val back = client.get("/status/${ticket.statusToken}").bodyAsText()
        assertFalse(back.contains("online</a>"))
        assertEquals(0, invoice(invoice.identifier).balanceCents)

        assertEquals(HttpStatusCode.NotFound, noRedirects.get("/status/${"0".repeat(32)}/pay").status)
    }

    @Test
    fun theStatusPageOnlyOffersPayingWhenItsOn() = testApplication {
        setup(stripe(online = false))
        val (ticket, _) = openInvoice()

        assertFalse(client.get("/status/${ticket.statusToken}").bodyAsText().contains("/pay\""))
        val pay = createClient { followRedirects = false }.get("/status/${ticket.statusToken}/pay")
        assertEquals(HttpStatusCode.Conflict, pay.status)
        assertTrue(PaymentDatabase().forInvoice(Uuid.random()).orEmpty().isEmpty())
    }

    @Test
    fun webhooksOnlySayWhichChargesToAsk() = testApplication {
        setup()
        val (_, invoice) = openInvoice()
        val started = json.decodeFromString<CardCharge>(readerCharge(5_000, invoice).bodyAsText())
        fake.pay(started)
        fake.webhookReferences = listOf(started.providerReference)

        // A bad signature is turned away, and nothing is asked
        val forged = client.post("/status/payments/stripe/webhook") { header("Signature", "bad"); setBody("{}") }
        assertEquals(HttpStatusCode.BadRequest, forged.status)
        assertTrue(invoice(invoice.identifier).payments.isEmpty())

        assertEquals(HttpStatusCode.NotFound, client.post("/status/payments/simulated/webhook") { setBody("{}") }.status)
        assertEquals(HttpStatusCode.NotFound, client.post("/status/payments/paypal/webhook") { setBody("{}") }.status)

        val signed = client.post("/status/payments/stripe/webhook") { header("Signature", "good"); setBody("{}") }
        assertEquals(HttpStatusCode.OK, signed.status)
        assertEquals(5_000, invoice(invoice.identifier).paidCents)
    }

    @Test
    fun aDepositCanBeAReaderPayment() = testApplication {
        setup()
        val started = json.decodeFromString<CardCharge>(readerCharge(2_000).bodyAsText())
        fake.pay(started)
        charge(started.identifier)

        val customer = Customer(name = "Grace Hopper")
        postJson("/api/data/customer/add", json.encodeToString(customer))
        val checkIn = postJson(
            "/api/counter/check-in",
            json.encodeToString(CheckInRequest(repair(), customer.identifier, deposit = PaymentRequest(PaymentMethod.CARD, 2_000, chargeIdentifier = started.identifier)))
        )
        assertEquals(HttpStatusCode.Created, checkIn.status)
        val invoice = json.decodeFromString<CheckInResponse>(checkIn.bodyAsText()).invoice!!
        assertEquals(started.identifier, invoice.payments.single().chargeIdentifier)
        assertEquals(invoice.identifier, charge(started.identifier).invoiceIdentifier)
    }
}
