package org.mr0btain.ticketman

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.mr0btain.ticketman.components.database.UserDatabase
import org.mr0btain.ticketman.data.customer.CreditEntry
import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.data.giftcard.GiftCard
import org.mr0btain.ticketman.data.giftcard.GiftCardCode
import org.mr0btain.ticketman.data.invoice.*
import org.mr0btain.ticketman.data.ticket.Device
import org.mr0btain.ticketman.data.ticket.Ticket
import org.mr0btain.ticketman.data.user.AccessLevel
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.server.request.*
import org.mr0btain.ticketman.server.response.AuthResponse
import org.mr0btain.ticketman.server.response.CheckInResponse
import org.mr0btain.ticketman.server.response.DailyReport
import kotlin.random.Random
import kotlin.test.*

/**
 * Store credit and gift cards, given, sold, spent, refunded onto and taken back
 */
class StoredValueTest {
    private val dataDir = createTestDataDir()
    private val json = Json { ignoreUnknownKeys = true }

    private val admin = User(username = "boss", password = "boss_pass", accessLevel = AccessLevel.ADMIN)
    private val tech = User(username = "tech", password = "tech_pass", accessLevel = AccessLevel.NORMAL)

    private lateinit var token: String
    private lateinit var adminToken: String

    @AfterTest
    fun cleanup() = cleanupTestDataDir(dataDir)

    private suspend fun ApplicationTestBuilder.setup() {
        configureTestServer(dataDir)
        startApplication()
        UserDatabase().apply {
            add(admin)
            add(tech)
        }
        token = login(tech)
        adminToken = login(admin)
    }

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

    private suspend fun ApplicationTestBuilder.customer(name: String = "Sam"): Customer {
        val customer = Customer(name = name)
        assertEquals(HttpStatusCode.Created, postJson("/api/data/customer/add", json.encodeToString(customer)).status)
        return customer
    }

    private suspend fun ApplicationTestBuilder.creditOf(customer: Customer): Long =
        json.decodeFromString<Customer>(client.get("/api/data/customer/get/${customer.identifier}") { bearerAuth(token) }.bodyAsText()).creditCents

    private suspend fun ApplicationTestBuilder.giveCredit(customer: Customer, cents: Long, reason: String = "Goodwill", as_: String? = null) =
        postJson("/api/data/customer/credit/${customer.identifier}", json.encodeToString(BalanceAdjustRequest(cents, reason)), as_ ?: adminToken)

    private suspend fun ApplicationTestBuilder.sell(request: SaleRequest) = postJson("/api/counter/sale", json.encodeToString(request))

    private suspend fun ApplicationTestBuilder.card(code: String): GiftCard? {
        val response = client.get("/api/giftcard/code/$code") { bearerAuth(token) }
        return if (response.status == HttpStatusCode.OK) json.decodeFromString(response.bodyAsText()) else null
    }

    private fun fee(cents: Long) = Charge(description = "Fee", unitPriceCents = cents, taxable = false)

    private fun giftCardLine(cents: Long, code: String = "") = Charge(type = ChargeType.GIFT_CARD, description = "Gift card", unitPriceCents = cents, taxable = false, reference = code)

    /** Sells a gift card for cash and gives back its code */
    private suspend fun ApplicationTestBuilder.soldCard(cents: Long): Pair<Invoice, String> {
        val response = sell(SaleRequest(charges = listOf(giftCardLine(cents)), payments = listOf(PaymentRequest(PaymentMethod.CASH, cents))))
        assertEquals(HttpStatusCode.Created, response.status, response.bodyAsText())
        val invoice = json.decodeFromString<Invoice>(response.bodyAsText())
        return invoice to invoice.charges.single().reference
    }

    // ------------------------------------------ Store credit ------------------------------------------

    @Test
    fun adminsGiveAndTakeStoreCredit() = testApplication {
        setup()
        val sam = customer()

        assertEquals(HttpStatusCode.Forbidden, giveCredit(sam, 1_000, as_ = token).status)
        assertEquals(HttpStatusCode.BadRequest, giveCredit(sam, 1_000, reason = " ").status)
        assertEquals(HttpStatusCode.BadRequest, giveCredit(sam, 0).status)

        val given = giveCredit(sam, 2_000)
        assertEquals(HttpStatusCode.OK, given.status)
        assertEquals(2_000, json.decodeFromString<Customer>(given.bodyAsText()).creditCents)

        val tooMuch = giveCredit(sam, -2_500, "Mistake")
        assertEquals(HttpStatusCode.Conflict, tooMuch.status)
        assertEquals("Sam only has $20.00 of store credit", tooMuch.bodyAsText())
        assertEquals(HttpStatusCode.OK, giveCredit(sam, -500, "Mistake").status)

        val history = json.decodeFromString<List<CreditEntry>>(client.get("/api/data/customer/credit/${sam.identifier}") { bearerAuth(token) }.bodyAsText())
        assertEquals(listOf(-500L, 2_000L), history.map { it.amountCents })
        assertEquals(listOf(1_500L, 2_000L), history.map { it.balanceAfterCents })
        assertEquals("boss", history.first().by)

        // Saving the customer doesn't touch it, and they can't be deleted while they have some
        postJson("/api/data/customer/update", json.encodeToString(sam.copy(name = "Sam Smith", creditCents = 99_999)))
        assertEquals(1_500, creditOf(sam))
        assertEquals(HttpStatusCode.Conflict, client.delete("/api/data/customer/remove/${sam.identifier}") { bearerAuth(token) }.status)
    }

    @Test
    fun storeCreditPaysForASaleWithACustomer() = testApplication {
        setup()
        val sam = customer()
        giveCredit(sam, 1_000)

        val noCustomer = sell(SaleRequest(charges = listOf(fee(800)), payments = listOf(PaymentRequest(PaymentMethod.STORE_CREDIT, 800))))
        assertEquals(HttpStatusCode.Conflict, noCustomer.status)
        assertEquals("Store credit needs a customer, add one first", noCustomer.bodyAsText())

        val tooMuch = sell(SaleRequest(sam.identifier, listOf(fee(1_500)), payments = listOf(PaymentRequest(PaymentMethod.STORE_CREDIT, 1_500))))
        assertEquals("Only $10.00 of store credit left", tooMuch.bodyAsText())
        assertEquals(1_000, creditOf(sam))

        // Credit and cash together
        val paid = sell(SaleRequest(sam.identifier, listOf(fee(1_500)), payments = listOf(PaymentRequest(PaymentMethod.STORE_CREDIT, 1_000), PaymentRequest(PaymentMethod.CASH, 500))))
        assertEquals(HttpStatusCode.Created, paid.status)
        val invoice = json.decodeFromString<Invoice>(paid.bodyAsText())
        assertEquals(0, creditOf(sam))

        val spent = json.decodeFromString<List<CreditEntry>>(client.get("/api/data/customer/credit/${sam.identifier}") { bearerAuth(token) }.bodyAsText()).first()
        assertEquals(-1_000, spent.amountCents)
        assertEquals(invoice.identifier, spent.invoiceIdentifier)
        assertEquals("Spent on ${invoice.label}", spent.reason)

        // Money taken leaves credit out, it was taken when the credit was
        val report = json.decodeFromString<DailyReport>(client.get("/api/admin/report/daily") { bearerAuth(adminToken) }.bodyAsText())
        assertEquals(500, report.takenCents)
        assertEquals(1_000, report.takenByMethod[PaymentMethod.STORE_CREDIT])

        // On the customer's invoices
        val invoices = json.decodeFromString<List<Invoice>>(client.get("/api/data/customer/get/invoices/${sam.identifier}") { bearerAuth(token) }.bodyAsText())
        assertEquals(listOf(invoice.identifier), invoices.map { it.identifier })
    }

    @Test
    fun aRefundCanGoToStoreCredit() = testApplication {
        setup()
        val sam = customer()
        val invoice = json.decodeFromString<Invoice>(sell(SaleRequest(sam.identifier, listOf(fee(2_000)), payments = listOf(PaymentRequest(PaymentMethod.CASH, 2_000)))).bodyAsText())

        val response = postJson("/api/invoice/${invoice.identifier}/refund", json.encodeToString(RefundRequest(PaymentMethod.STORE_CREDIT, 2_000, "Changed their mind")), adminToken)
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(2_000, creditOf(sam))

        // A sale with no customer has nobody to give it to
        val anonymous = json.decodeFromString<Invoice>(sell(SaleRequest(charges = listOf(fee(500)), payments = listOf(PaymentRequest(PaymentMethod.CASH, 500)))).bodyAsText())
        val refused = postJson("/api/invoice/${anonymous.identifier}/refund", json.encodeToString(RefundRequest(PaymentMethod.STORE_CREDIT, 500, "x")), adminToken)
        assertEquals(HttpStatusCode.BadRequest, refused.status)
    }

    @Test
    fun aCheckInDepositCanBeStoreCredit() = testApplication {
        setup()
        val sam = customer()
        giveCredit(sam, 3_000)

        val request = CheckInRequest(
            ticket = Ticket(device = Device(manufacturer = "Apple", model = "iPhone"), problem = "Screen"),
            customerIdentifier = sam.identifier,
            deposit = PaymentRequest(PaymentMethod.STORE_CREDIT, 2_000)
        )
        val response = postJson("/api/counter/check-in", json.encodeToString(request))
        assertEquals(HttpStatusCode.Created, response.status, response.bodyAsText())
        val invoice = json.decodeFromString<CheckInResponse>(response.bodyAsText()).invoice!!
        assertEquals(1_000, creditOf(sam))

        // Taking the payment off gives it back
        val payment = invoice.payments.single()
        assertEquals(HttpStatusCode.OK, client.delete("/api/invoice/${invoice.identifier}/payments/${payment.identifier}") { bearerAuth(token) }.status)
        assertEquals(3_000, creditOf(sam))

        val tooMuch = postJson("/api/counter/check-in", json.encodeToString(request.copy(ticket = Ticket(device = Device(model = "iPad"), problem = "Battery"), deposit = PaymentRequest(PaymentMethod.STORE_CREDIT, 5_000))))
        assertEquals(HttpStatusCode.Conflict, tooMuch.status)
    }

    // ------------------------------------------ Gift cards ------------------------------------------

    @Test
    fun aSoldGiftCardGetsACodeAndItsBalance() = testApplication {
        setup()
        val (invoice, code) = soldCard(5_000)

        assertNull(GiftCardCode.problem(code))
        assertTrue(code.startsWith("GC-"))
        assertEquals(5_000, invoice.totalCents, "gift cards aren't taxed")

        val card = card(code)!!
        assertEquals(5_000, card.balanceCents)
        assertEquals(5_000, card.issuedCents)
        assertEquals(invoice.identifier, card.invoiceIdentifier)

        // Found however it's typed
        assertNotNull(card(code.lowercase()))
        assertNull(card("GC-NOPE-NOPE-NOPE-NOPE"))

        // A taxed line or a quantity isn't a gift card
        assertEquals(HttpStatusCode.BadRequest, sell(SaleRequest(charges = listOf(giftCardLine(1_000).copy(taxable = true)), payments = listOf(PaymentRequest(PaymentMethod.CASH, 1_100)))).status)
        assertEquals(HttpStatusCode.BadRequest, sell(SaleRequest(charges = listOf(giftCardLine(1_000).copy(quantity = 2.0)), payments = listOf(PaymentRequest(PaymentMethod.CASH, 2_000)))).status)

        // Topped up by selling one with its code
        soldCardWithCode(code, 2_000)
        assertEquals(7_000, card(code)!!.balanceCents)
        assertEquals(7_000, card(code)!!.issuedCents)

        val listed = json.decodeFromString<List<GiftCard>>(client.get("/api/giftcard/fetch") { bearerAuth(adminToken) }.bodyAsText())
        assertEquals(listOf(code), listed.map { it.code })
        assertEquals(HttpStatusCode.Forbidden, client.get("/api/giftcard/fetch") { bearerAuth(token) }.status)
    }

    private suspend fun ApplicationTestBuilder.soldCardWithCode(code: String, cents: Long) {
        val response = sell(SaleRequest(charges = listOf(giftCardLine(cents, code)), payments = listOf(PaymentRequest(PaymentMethod.CASH, cents))))
        assertEquals(HttpStatusCode.Created, response.status)
    }

    @Test
    fun giftCardsPayUntilTheyRunOut() = testApplication {
        setup()
        val (_, code) = soldCard(2_000)

        val unknown = sell(SaleRequest(charges = listOf(fee(500)), payments = listOf(PaymentRequest(PaymentMethod.GIFT_CARD, 500, reference = "GC-AAAA-BBBB"))))
        assertEquals(HttpStatusCode.Conflict, unknown.status)
        assertEquals("There's no gift card GC-AAAA-BBBB", unknown.bodyAsText())

        assertEquals(HttpStatusCode.Created, sell(SaleRequest(charges = listOf(fee(1_500)), payments = listOf(PaymentRequest(PaymentMethod.GIFT_CARD, 1_500, reference = code.lowercase())))).status)
        assertEquals(500, card(code)!!.balanceCents)

        val short = sell(SaleRequest(charges = listOf(fee(1_000)), payments = listOf(PaymentRequest(PaymentMethod.GIFT_CARD, 1_000, reference = code))))
        assertEquals(HttpStatusCode.Conflict, short.status)
        assertEquals("Only $5.00 left on gift card $code", short.bodyAsText())

        // The payment keeps the code, cleaned up
        val paid = json.decodeFromString<Invoice>(
            sell(SaleRequest(charges = listOf(fee(1_000)), payments = listOf(PaymentRequest(PaymentMethod.GIFT_CARD, 500, reference = code.lowercase()), PaymentRequest(PaymentMethod.CARD, 500)))).bodyAsText()
        )
        assertEquals(code, paid.payments.first().reference)
        assertEquals(0, card(code)!!.balanceCents)
    }

    @Test
    fun aDepositOnAGiftCardComesBackWhenTakenOff() = testApplication {
        setup()
        val (_, code) = soldCard(3_000)
        val ticket = json.decodeFromString<Ticket>(postJson("/api/data/ticket/add", json.encodeToString(Ticket())).bodyAsText())
        val invoice = json.decodeFromString<Invoice>(postJson("/api/invoice/ticket/${ticket.identifier}", "").bodyAsText())

        val paid = json.decodeFromString<Invoice>(postJson("/api/invoice/${invoice.identifier}/payments", json.encodeToString(PaymentRequest(PaymentMethod.GIFT_CARD, 1_000, reference = code))).bodyAsText())
        assertEquals(2_000, card(code)!!.balanceCents)

        client.delete("/api/invoice/${invoice.identifier}/payments/${paid.payments.single().identifier}") { bearerAuth(token) }
        assertEquals(3_000, card(code)!!.balanceCents)
    }

    @Test
    fun refundsGoOntoAGiftCardAndVoidingTakesOneBack() = testApplication {
        setup()
        val invoice = json.decodeFromString<Invoice>(sell(SaleRequest(charges = listOf(fee(2_000)), payments = listOf(PaymentRequest(PaymentMethod.CASH, 2_000)))).bodyAsText())

        // Onto a new card
        val refunded = json.decodeFromString<Invoice>(
            postJson("/api/invoice/${invoice.identifier}/refund", json.encodeToString(RefundRequest(PaymentMethod.GIFT_CARD, 1_200, "Part refund")), adminToken).bodyAsText()
        )
        val code = refunded.payments.single { it.isRefund }.reference
        assertEquals(1_200, card(code)!!.balanceCents)

        // Voiding a sold card it's been spent from is refused, an unspent one goes to nothing
        val (spentSale, spentCode) = soldCard(1_000)
        sell(SaleRequest(charges = listOf(fee(100)), payments = listOf(PaymentRequest(PaymentMethod.GIFT_CARD, 100, reference = spentCode))))
        val refused = postJson("/api/invoice/${spentSale.identifier}/void", "", adminToken)
        assertEquals(HttpStatusCode.Conflict, refused.status)
        assertEquals("Gift card $spentCode has been spent, refund instead of voiding", refused.bodyAsText())

        val (unspentSale, unspentCode) = soldCard(1_000)
        assertEquals(HttpStatusCode.OK, postJson("/api/invoice/${unspentSale.identifier}/void", "", adminToken).status)
        assertEquals(0, card(unspentCode)!!.balanceCents)
        assertEquals(0, card(unspentCode)!!.issuedCents)
    }

    @Test
    fun adminsCorrectAGiftCard() = testApplication {
        setup()
        val (_, code) = soldCard(1_000)

        val path = "/api/giftcard/code/$code/adjust"
        assertEquals(HttpStatusCode.Forbidden, postJson(path, json.encodeToString(BalanceAdjustRequest(500, "Promo")), token).status)
        assertEquals(1_500, json.decodeFromString<GiftCard>(postJson(path, json.encodeToString(BalanceAdjustRequest(500, "Promo")), adminToken).bodyAsText()).balanceCents)
        assertEquals(HttpStatusCode.Conflict, postJson(path, json.encodeToString(BalanceAdjustRequest(-2_000, "Promo")), adminToken).status)
        assertEquals(HttpStatusCode.NotFound, postJson("/api/giftcard/code/GC-NOPE-NOPE/adjust", json.encodeToString(BalanceAdjustRequest(100, "x")), adminToken).status)
    }

    @Test
    fun codesAreCleanedUpAndHardToGuess() {
        val codes = (1..200).map { GiftCardCode.generate(Random(it)) }
        assertEquals(200, codes.toSet().size)
        codes.forEach { assertNull(GiftCardCode.problem(it), it) }
        assertEquals("GC-AB12-CD34", GiftCardCode.normalize(" gc-ab12 -cd34 "))
        assertNotNull(GiftCardCode.problem("abc"))
        assertNotNull(GiftCardCode.problem("GC_AB12_CD34"))
    }
}
