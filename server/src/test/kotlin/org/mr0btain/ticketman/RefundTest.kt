package org.mr0btain.ticketman

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.mr0btain.ticketman.components.database.AuditLogDatabase
import org.mr0btain.ticketman.components.database.InventoryDatabase
import org.mr0btain.ticketman.components.database.UserDatabase
import org.mr0btain.ticketman.data.audit.AuditAction
import org.mr0btain.ticketman.data.inventory.Inventory
import org.mr0btain.ticketman.data.invoice.*
import org.mr0btain.ticketman.data.shared.PagedRequest
import org.mr0btain.ticketman.data.ticket.Ticket
import org.mr0btain.ticketman.data.user.AccessLevel
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.server.request.LoginRequest
import org.mr0btain.ticketman.server.request.PaymentRequest
import org.mr0btain.ticketman.server.request.RefundRequest
import org.mr0btain.ticketman.server.request.SaleRequest
import org.mr0btain.ticketman.server.response.AuthResponse
import org.mr0btain.ticketman.server.response.DailyReport
import java.time.LocalDate
import kotlin.test.*

/**
 * Money back on paid invoices, with parts going back in stock
 */
class RefundTest {
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

    private suspend fun ApplicationTestBuilder.refund(invoice: Invoice, request: RefundRequest, as_: String? = null) =
        postJson("/api/invoice/${invoice.identifier}/refund", json.encodeToString(request), as_ ?: adminToken)

    /** Three cables at $10 sold, no tax, paid $30 cash */
    private suspend fun ApplicationTestBuilder.soldCables(): Pair<Inventory, Invoice> {
        val cable = Inventory(name = "Cable", stock = 10, priceCents = 1_000)
        postJson("/api/data/inventory/add", json.encodeToString(cable))

        val charge = Charge(type = ChargeType.PART, description = "Cable", quantity = 3.0, unitPriceCents = 1_000, inventoryIdentifier = cable.identifier)
        val response = postJson("/api/counter/sale", json.encodeToString(SaleRequest(charges = listOf(charge), payments = listOf(PaymentRequest(PaymentMethod.CASH, 3_000)))))
        assertEquals(HttpStatusCode.Created, response.status)
        return cable to json.decodeFromString(response.bodyAsText())
    }

    private fun stock(item: Inventory) = InventoryDatabase().getById(item.identifier)!!.stock

    @Test
    fun aRefundGivesMoneyBackAndPutsPartsInStock() = testApplication {
        setup()
        val (cable, invoice) = soldCables()
        assertEquals(7, stock(cable))

        val response = refund(invoice, RefundRequest(PaymentMethod.CASH, 1_000, "  Didn't fit  ", listOf(ReturnedPart(cable.identifier, 1))))
        assertEquals(HttpStatusCode.OK, response.status)

        val refunded = json.decodeFromString<Invoice>(response.bodyAsText())
        assertEquals(InvoiceStatus.PAID, refunded.status)
        assertEquals(3_000, refunded.paidCents)
        assertEquals(1_000, refunded.refundedCents)
        assertEquals(2_000, refunded.netPaidCents)
        assertEquals(0, refunded.balanceCents, "a refund doesn't make anything owed")

        val payment = refunded.payments.single { it.isRefund }
        assertEquals(-1_000, payment.amountCents)
        assertEquals("Didn't fit", payment.note)
        assertEquals("boss", payment.receivedBy)
        assertEquals(mapOf(cable.identifier to 1), refunded.returnedParts)
        assertEquals(8, stock(cable))

        // On the day's report as a refund, netted out of what was taken in cash
        val report = json.decodeFromString<DailyReport>(client.get("/api/admin/report/daily") { bearerAuth(adminToken) }.bodyAsText())
        assertEquals(2_000, report.takenByMethod[PaymentMethod.CASH])
        assertEquals(1_000, report.refundedCents)
        assertEquals(1, report.refundCount)
        assertEquals(1, report.paymentCount)

        val audit = AuditLogDatabase(UserDatabase()).getPagedItems(PagedRequest(0, 50))!!.first { it.action == AuditAction.REFUND }
        assertContains(audit.details, "Refunded $10.00 cash on ${invoice.label}, 1 part back in stock: Didn't fit")

        // The export has its own column for it
        val today = LocalDate.now()
        val csv = client.get("/api/invoice/export?from=$today&to=$today") { bearerAuth(adminToken) }.bodyAsText()
        assertContains(csv, ",Refunded\r\n")
        assertContains(csv, ",30.00,30.00,0.00,10.00\r\n")
    }

    @Test
    fun refundsAreHeldToWhatWasPaidAndSold() = testApplication {
        setup()
        val (cable, invoice) = soldCables()

        assertEquals(HttpStatusCode.BadRequest, refund(invoice, RefundRequest(PaymentMethod.CASH, 100, " ")).status)
        assertEquals(HttpStatusCode.BadRequest, refund(invoice, RefundRequest(PaymentMethod.CASH, 0, "Nothing")).status)
        assertEquals(HttpStatusCode.BadRequest, refund(invoice, RefundRequest(PaymentMethod.CASH, -100, "Backwards")).status)

        val tooMuch = refund(invoice, RefundRequest(PaymentMethod.CASH, 3_001, "Too much"))
        assertEquals(HttpStatusCode.BadRequest, tooMuch.status)
        assertEquals("Only $30.00 was paid on ${invoice.label}", tooMuch.bodyAsText())

        val tooMany = refund(invoice, RefundRequest(PaymentMethod.CASH, 0, "Returned", listOf(ReturnedPart(cable.identifier, 4))))
        assertEquals("Only 3 of Cable can be returned on ${invoice.label}", tooMany.bodyAsText())

        // Two refunds, each within what's left
        assertEquals(HttpStatusCode.OK, refund(invoice, RefundRequest(PaymentMethod.CASH, 2_000, "Two back", listOf(ReturnedPart(cable.identifier, 2)))).status)
        val second = refund(invoice, RefundRequest(PaymentMethod.CASH, 0, "More", listOf(ReturnedPart(cable.identifier, 2))))
        assertEquals("Only 1 of Cable can be returned on ${invoice.label}", second.bodyAsText())
        assertEquals("Only $10.00 was paid on ${invoice.label}", refund(invoice, RefundRequest(PaymentMethod.CASH, 1_500, "More")).bodyAsText())

        // A part the invoice didn't sell
        val other = Inventory(name = "Case")
        postJson("/api/data/inventory/add", json.encodeToString(other))
        assertEquals("That part isn't on ${invoice.label}",
            refund(invoice, RefundRequest(PaymentMethod.CASH, 0, "Not ours", listOf(ReturnedPart(other.identifier, 1)))).bodyAsText())

        // Only admins
        assertEquals(HttpStatusCode.Forbidden, refund(invoice, RefundRequest(PaymentMethod.CASH, 100, "Me"), token).status)
        assertEquals(9, stock(cable))
    }

    @Test
    fun onlyPaidInvoicesAreRefunded() = testApplication {
        setup()
        val ticket = json.decodeFromString<Ticket>(postJson("/api/data/ticket/add", json.encodeToString(Ticket())).bodyAsText())
        val open = json.decodeFromString<Invoice>(postJson("/api/invoice/ticket/${ticket.identifier}", "").bodyAsText())

        val response = refund(open, RefundRequest(PaymentMethod.CASH, 100, "Deposit back"))
        assertEquals(HttpStatusCode.Conflict, response.status)
        assertEquals("Only a paid invoice can be refunded, ${open.label} is open, take the payment off instead", response.bodyAsText())

        assertEquals(HttpStatusCode.NotFound, refund(open.copy(identifier = Invoice(ticketIdentifier = null).identifier), RefundRequest(PaymentMethod.CASH, 100, "x")).status)
    }

    @Test
    fun voidingAfterAReturnOnlyPutsBackTheRest() = testApplication {
        setup()
        val (cable, invoice) = soldCables()
        refund(invoice, RefundRequest(PaymentMethod.CASH, 1_000, "One back", listOf(ReturnedPart(cable.identifier, 1))))
        assertEquals(8, stock(cable))

        assertEquals(HttpStatusCode.OK, postJson("/api/invoice/${invoice.identifier}/void", "", adminToken).status)
        assertEquals(10, stock(cable))

        assertEquals(HttpStatusCode.Conflict, refund(invoice, RefundRequest(PaymentMethod.CASH, 100, "After void")).status)
    }
}
