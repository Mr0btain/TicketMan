package org.mr0btain.ticketman

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.mr0btain.ticketman.components.database.UserDatabase
import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.data.inventory.Inventory
import org.mr0btain.ticketman.data.invoice.*
import org.mr0btain.ticketman.data.report.CommissionRate
import org.mr0btain.ticketman.data.report.PeriodReport
import org.mr0btain.ticketman.data.shop.ShopProfile
import org.mr0btain.ticketman.data.ticket.*
import org.mr0btain.ticketman.data.user.AccessLevel
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.server.request.*
import org.mr0btain.ticketman.server.response.AuthResponse
import org.mr0btain.ticketman.server.response.CheckInResponse
import java.time.LocalDate
import kotlin.test.*

/**
 * The period report: sales, profit, tax, techs and commission, turnaround and refunds
 */
class ReportTest {
    private val dataDir = createTestDataDir()
    private val json = Json { ignoreUnknownKeys = true }

    private val admin = User(username = "boss", password = "boss_pass", accessLevel = AccessLevel.ADMIN)
    private val tech = User(username = "tech", name = "Tess Tech", password = "tech_pass", accessLevel = AccessLevel.NORMAL)
    private val jane = Customer(name = "Jane Doe")
    private val screen = Inventory(name = "Screen", stock = 10, priceCents = 1_000, costCents = 300)
    private val today = LocalDate.now().toString()

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
        post("/api/data/customer/add", json.encodeToString(jane))
        post("/api/data/inventory/add", json.encodeToString(screen))
        assertEquals(HttpStatusCode.Accepted, post("/api/shop/update", json.encodeToString(ShopProfile(name = "Fix It", taxRate = "10", laborRateCents = 5_000)), adminToken).status)
    }

    private suspend fun ApplicationTestBuilder.login(user: User): String {
        val response = client.post("/api/auth") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(LoginRequest(user.username, user.password)))
        }

        return json.decodeFromString<AuthResponse>(response.bodyAsText()).accessToken!!
    }

    private suspend fun ApplicationTestBuilder.post(path: String, body: String, as_: String? = null) =
        client.post(path) {
            contentType(ContentType.Application.Json)
            bearerAuth(as_ ?: token)
            setBody(body)
        }

    private suspend fun ApplicationTestBuilder.get(path: String, as_: String? = null) =
        client.get(path) { bearerAuth(as_ ?: adminToken) }

    /** A ticket with 1.5 hours of labor ($75, untaxed) and two screens ($20 plus 10%), made ready and picked up */
    private suspend fun ApplicationTestBuilder.repairedAndPickedUp(): Invoice {
        val ticket = json.decodeFromString<CheckInResponse>(
            post("/api/counter/check-in", json.encodeToString(CheckInRequest(Ticket(device = Device("Apple", "iPhone"), problem = "Screen"), jane.identifier))).bodyAsText()
        ).ticket
        val work = listOf(
            WorkRequest(WorkType.LABOR, "Replace screen", 1.5),
            WorkRequest(WorkType.PART, quantity = 2.0, inventoryIdentifier = screen.identifier)
        )
        assertEquals(HttpStatusCode.Created, post("/api/data/ticket/add/work/all/${ticket.identifier}", json.encodeToString(work)).status)
        assertEquals(HttpStatusCode.Accepted, post("/api/data/ticket/update", json.encodeToString(ticket.copy(status = TicketStatus(TicketStatusType.DONE)))).status)

        val pickup = post("/api/counter/pickup", json.encodeToString(PickupRequest(ticket.identifier, listOf(PaymentRequest(PaymentMethod.CASH, 9_700)))))
        assertEquals(HttpStatusCode.OK, pickup.status)
        return json.decodeFromString(pickup.bodyAsText())
    }

    private suspend fun ApplicationTestBuilder.report(from: String = today, to: String = today): PeriodReport {
        val response = get("/api/report/period?from=$from&to=$to")
        assertEquals(HttpStatusCode.OK, response.status)
        return json.decodeFromString(response.bodyAsText())
    }

    @Test
    fun salesProfitAndTaxComeFromPaidInvoices() = testApplication {
        setup()
        val invoice = repairedAndPickedUp()
        assertEquals(9_700, invoice.totalCents)

        // An open invoice isn't a sale yet
        json.decodeFromString<CheckInResponse>(
            post("/api/counter/check-in", json.encodeToString(CheckInRequest(Ticket(device = Device("Apple", "iPad"), problem = "Battery"), jane.identifier))).bodyAsText()
        )

        val report = report()
        with(report.sales) {
            assertEquals(1, invoices)
            assertEquals(9_700, totalCents)
            assertEquals(200, taxCents)
            assertEquals(7_500, laborCents)
            assertEquals(2_000, partsCents)
            assertEquals(600, partsCostCents, "two screens at their cost when paid")
            assertEquals(0, partsWithoutCost)
            assertEquals(9_500, netCents)
            assertEquals(8_900, grossProfitCents)
        }
        assertEquals(listOf(today), report.days.map { it.date })
        assertEquals(200, report.taxes.single().taxCents)
        assertEquals(2_000, report.taxes.single().taxableCents)
        assertEquals(9_700, report.takenCents)

        // Yesterday had nothing
        val yesterday = LocalDate.now().minusDays(1).toString()
        assertEquals(0, report(yesterday, yesterday).sales.invoices)
    }

    @Test
    fun aCostChangedAfterPaymentDoesntChangeTheProfit() = testApplication {
        setup()
        repairedAndPickedUp()
        post("/api/data/inventory/update", json.encodeToString(screen.copy(costCents = 900)), adminToken)

        assertEquals(600, report().sales.partsCostCents)
    }

    @Test
    fun techsGetTheirBilledWorkAndCommission() = testApplication {
        setup()
        val techUser = UserDatabase().getByUsername("tech")!!
        val rates = listOf(CommissionRate(techUser.identifier, 1_000))
        assertEquals(HttpStatusCode.Accepted, post("/api/report/commissions", json.encodeToString(rates), adminToken).status)
        assertEquals(rates, json.decodeFromString<List<CommissionRate>>(get("/api/report/commissions").bodyAsText()))

        repairedAndPickedUp()
        val tess = report().techs.single()
        assertEquals(techUser.identifier, tess.userIdentifier)
        assertEquals("Tess Tech", tess.name)
        assertEquals(1, tess.tickets)
        assertEquals(1.5, tess.laborHours)
        assertEquals(7_500, tess.laborCents)
        assertEquals(2_000, tess.partsCents)
        assertEquals(750, tess.commissionCents)
    }

    @Test
    fun turnaroundAndRefunds() = testApplication {
        setup()
        val invoice = repairedAndPickedUp()
        val refund = post("/api/invoice/${invoice.identifier}/refund", json.encodeToString(RefundRequest(PaymentMethod.CASH, 500, "Scratched")), adminToken)
        assertEquals(HttpStatusCode.OK, refund.status)

        val report = report()
        assertEquals(1, report.turnaround.pickedUp)
        assertEquals(1, report.turnaround.readyCount)
        assertNotNull(report.turnaround.medianHoursToReady)
        assertTrue(report.turnaround.averageHoursToPickup!! < 1.0)

        assertEquals(1, report.refunds.count)
        assertEquals(500, report.refunds.cents)
        assertEquals("Scratched", report.refunds.refunds.single().reason)
        assertEquals(invoice.label, report.refunds.refunds.single().invoiceLabel)
        assertEquals(9_200, report.takenCents)
    }

    @Test
    fun onlyAdminsWithGoodDays() = testApplication {
        setup()
        assertEquals(HttpStatusCode.Forbidden, get("/api/report/period?from=$today&to=$today", token).status)
        assertEquals(HttpStatusCode.Forbidden, get("/api/report/commissions", token).status)
        assertEquals(HttpStatusCode.BadRequest, get("/api/report/period?from=$today").status)
        assertEquals(HttpStatusCode.BadRequest, get("/api/report/period?from=2026-10-02&to=2026-10-01").status)
        assertEquals(HttpStatusCode.BadRequest, get("/api/report/period?from=2020-01-01&to=2026-01-01").status)

        val techUser = UserDatabase().getByUsername("tech")!!
        assertEquals(HttpStatusCode.BadRequest, post("/api/report/commissions", json.encodeToString(listOf(CommissionRate(techUser.identifier, 20_000))), adminToken).status)
        assertEquals(HttpStatusCode.BadRequest, post("/api/report/commissions", json.encodeToString(listOf(CommissionRate(jane.identifier, 100))), adminToken).status)
    }
}
