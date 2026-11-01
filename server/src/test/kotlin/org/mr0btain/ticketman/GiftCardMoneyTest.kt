package org.mr0btain.ticketman

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.mr0btain.ticketman.components.database.UserDatabase
import org.mr0btain.ticketman.data.giftcard.GiftCard
import org.mr0btain.ticketman.data.invoice.Charge
import org.mr0btain.ticketman.data.invoice.ChargeType
import org.mr0btain.ticketman.data.invoice.Invoice
import org.mr0btain.ticketman.data.invoice.PaymentMethod
import org.mr0btain.ticketman.data.user.AccessLevel
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.server.request.LoginRequest
import org.mr0btain.ticketman.server.request.PaymentRequest
import org.mr0btain.ticketman.server.request.RefundRequest
import org.mr0btain.ticketman.server.request.SaleRequest
import org.mr0btain.ticketman.server.response.AuthResponse
import kotlin.test.*

/** What's on a gift card was paid for, and money only comes back once if it comes off the card */
class GiftCardMoneyTest {
    private val dataDir = createTestDataDir()
    private val json = Json { ignoreUnknownKeys = true }
    private val admin = User(username = "boss", password = "boss_pass", accessLevel = AccessLevel.ADMIN)
    private lateinit var token: String

    @AfterTest
    fun cleanup() = cleanupTestDataDir(dataDir)

    private suspend fun ApplicationTestBuilder.setup() {
        configureTestServer(dataDir)
        startApplication()
        UserDatabase().add(admin)
        token = json.decodeFromString<AuthResponse>(
            client.post("/api/auth") {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(LoginRequest(admin.username, admin.password)))
            }.bodyAsText()
        ).accessToken!!
    }

    private suspend fun ApplicationTestBuilder.post(path: String, body: String) =
        client.post(path) {
            contentType(ContentType.Application.Json)
            bearerAuth(token)
            setBody(body)
        }

    private fun card(cents: Long) = Charge(type = ChargeType.GIFT_CARD, description = "Gift card", quantity = 1.0, unitPriceCents = cents, taxable = false)
    private fun fee(cents: Long) = Charge(type = ChargeType.OTHER, description = "Repair", quantity = 1.0, unitPriceCents = cents, taxable = false)
    private fun cash(cents: Long) = PaymentRequest(PaymentMethod.CASH, cents)

    private suspend fun ApplicationTestBuilder.sale(request: SaleRequest): Invoice {
        val response = post("/api/counter/sale", json.encodeToString(request))
        assertEquals(HttpStatusCode.Created, response.status, response.bodyAsText())
        return json.decodeFromString(response.bodyAsText())
    }

    private suspend fun ApplicationTestBuilder.balance(code: String): Long =
        json.decodeFromString<GiftCard>(client.get("/api/giftcard/code/$code") { bearerAuth(token) }.bodyAsText()).balanceCents

    private suspend fun ApplicationTestBuilder.refund(invoice: Invoice, cents: Long) =
        post("/api/invoice/${invoice.identifier}/refund", json.encodeToString(RefundRequest(PaymentMethod.CASH, cents, "Changed their mind")))

    private fun Invoice.code() = charges.single { it.type == ChargeType.GIFT_CARD }.reference

    @Test
    fun aDiscountCantPayForAGiftCard() = testApplication {
        setup()

        val refused = post("/api/counter/sale", json.encodeToString(SaleRequest(charges = listOf(card(10_000)), discountCents = 10_000)))
        assertEquals(HttpStatusCode.Conflict, refused.status)
        assertContains(refused.bodyAsText(), "still owed")

        // It only comes off everything else on the invoice
        val mixed = sale(SaleRequest(charges = listOf(card(10_000), fee(2_000)), discountCents = 5_000, payments = listOf(cash(10_000))))
        assertEquals(2_000, mixed.discountCents)
        assertEquals(10_000, mixed.totalCents)
        assertEquals(10_000, balance(mixed.code()))
    }

    @Test
    fun refundingASoldCardTakesItsMoneyOffTheCard() = testApplication {
        setup()
        val sold = sale(SaleRequest(charges = listOf(card(5_000)), payments = listOf(cash(5_000))))

        assertEquals(HttpStatusCode.OK, refund(sold, 2_000).status)
        assertEquals(3_000, balance(sold.code()))

        assertEquals(HttpStatusCode.OK, refund(sold, 3_000).status)
        assertEquals(0, balance(sold.code()))
    }

    @Test
    fun aSpentCardCantBeRefunded() = testApplication {
        setup()
        val sold = sale(SaleRequest(charges = listOf(card(5_000)), payments = listOf(cash(5_000))))
        sale(SaleRequest(charges = listOf(fee(4_000)), payments = listOf(PaymentRequest(PaymentMethod.GIFT_CARD, 4_000, reference = sold.code()))))

        val refused = refund(sold, 2_000)
        assertEquals(HttpStatusCode.Conflict, refused.status)
        assertContains(refused.bodyAsText(), "has been spent")
        assertEquals(1_000, balance(sold.code()))

        // What's left on it can still go back
        assertEquals(HttpStatusCode.OK, refund(sold, 1_000).status)
        assertEquals(0, balance(sold.code()))
    }

    @Test
    fun aRefundComesOffEverythingElseFirst() = testApplication {
        setup()
        val sold = sale(SaleRequest(charges = listOf(card(5_000), fee(3_000)), payments = listOf(cash(8_000))))

        assertEquals(HttpStatusCode.OK, refund(sold, 3_000).status)
        assertEquals(5_000, balance(sold.code()))

        assertEquals(HttpStatusCode.OK, refund(sold, 1_000).status)
        assertEquals(4_000, balance(sold.code()))
    }

    @Test
    fun voidingAfterARefundOnlyTakesBackWhatsLeft() = testApplication {
        setup()
        val sold = sale(SaleRequest(charges = listOf(card(5_000)), payments = listOf(cash(5_000))))
        assertEquals(HttpStatusCode.OK, refund(sold, 2_000).status)

        assertEquals(HttpStatusCode.OK, post("/api/invoice/${sold.identifier}/void", "").status)
        assertEquals(0, balance(sold.code()))

        // The rest of the money can go back, without taking anything more off the card
        assertEquals(HttpStatusCode.OK, refund(sold, 3_000).status)
        assertEquals(0, balance(sold.code()))
    }
}
