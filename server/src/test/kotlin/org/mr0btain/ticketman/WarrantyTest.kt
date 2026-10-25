package org.mr0btain.ticketman

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.mr0btain.ticketman.components.database.TicketDatabase
import org.mr0btain.ticketman.components.database.UserDatabase
import org.mr0btain.ticketman.components.database.tables.ticket.TicketTable
import org.mr0btain.ticketman.data.counter.TicketSummary
import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.data.invoice.*
import org.mr0btain.ticketman.data.shop.ShopProfile
import org.mr0btain.ticketman.data.ticket.*
import org.mr0btain.ticketman.data.user.AccessLevel
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.server.request.*
import org.mr0btain.ticketman.server.response.AuthResponse
import org.mr0btain.ticketman.server.response.CheckInResponse
import kotlin.test.*

/**
 * Warranties from pickup, warranty returns, and the repeat check saying what's covered
 */
class WarrantyTest {
    private val dataDir = createTestDataDir()
    private val json = Json { ignoreUnknownKeys = true }

    private val admin = User(username = "boss", password = "boss_pass", accessLevel = AccessLevel.ADMIN)
    private val tech = User(username = "tech", password = "tech_pass", accessLevel = AccessLevel.NORMAL)
    private val jane = Customer(name = "Jane Doe")
    private val sam = Customer(name = "Sam")
    private val phone = Device("Apple", "iPhone 13", "SN-W")

    private lateinit var token: String

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
        post("/api/data/customer/add", json.encodeToString(jane))
        post("/api/data/customer/add", json.encodeToString(sam))
        assertEquals(HttpStatusCode.Accepted, post("/api/shop/update", json.encodeToString(ShopProfile(warrantyDays = 90, laborRateCents = 5_000)), login(admin)).status)
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

    private suspend fun ApplicationTestBuilder.checkIn(customer: Customer = jane, warrantyOf: kotlin.uuid.Uuid? = null) =
        post("/api/counter/check-in", json.encodeToString(CheckInRequest(Ticket(device = phone, problem = "Screen", warrantyOf = warrantyOf), customer.identifier)))

    /** Checks one in, puts [lines] on its invoice and picks it up */
    private suspend fun ApplicationTestBuilder.repairedWith(lines: List<Charge>): Ticket {
        val ticket = json.decodeFromString<CheckInResponse>(checkIn().bodyAsText()).ticket
        val invoice = json.decodeFromString<Invoice>(post("/api/invoice/ticket/${ticket.identifier}", "").bodyAsText())
        val updated = json.decodeFromString<Invoice>(post("/api/invoice/${invoice.identifier}/update", json.encodeToString(InvoiceChanges.between(emptyList(), 0, lines, 0))).bodyAsText())
        val paid = post("/api/counter/pickup", json.encodeToString(PickupRequest(ticket.identifier, listOf(PaymentRequest(PaymentMethod.CASH, updated.totalCents.coerceAtLeast(1))))))
        assertEquals(HttpStatusCode.OK, paid.status, paid.bodyAsText())
        return TicketDatabase().getById(ticket.identifier)!!
    }

    private fun labor(days: Int? = null) = Charge(type = ChargeType.LABOR, description = "Screen fitting", unitPriceCents = 5_000, taxable = false, warrantyDays = days)

    @Test
    fun theWarrantyRunsFromPickupForTheLongestLine() = testApplication {
        setup()
        val day = Warranty.DAY_MILLIS

        val standard = repairedWith(listOf(labor()))
        assertEquals(90 * day, standard.warrantyUntil!! - standard.pickedUpAt!!)

        val longer = repairedWith(listOf(labor(), labor(365).copy(description = "Board repair")))
        assertEquals(365 * day, longer.warrantyUntil!! - longer.pickedUpAt!!)

        // Lines that aren't work give none
        assertNull(repairedWith(listOf(Charge(description = "Cleaning kit", unitPriceCents = 500))).warrantyUntil)

        assertEquals(HttpStatusCode.BadRequest, post("/api/counter/sale", json.encodeToString(SaleRequest(charges = listOf(labor(-1))))).status)
    }

    @Test
    fun aWarrantyReturnIsFreeAndHasToBeCovered() = testApplication {
        setup()
        val original = repairedWith(listOf(labor()))

        // The repeat check shows it covered
        val repeats = json.decodeFromString<List<TicketSummary>>(post("/api/counter/repeat-check", json.encodeToString(RepeatCheckRequest(jane.identifier, phone))).bodyAsText())
        assertTrue(repeats.single().ticket.underWarranty(System.currentTimeMillis()))

        val back = checkIn(warrantyOf = original.identifier)
        assertEquals(HttpStatusCode.Created, back.status, back.bodyAsText())
        val ret = json.decodeFromString<CheckInResponse>(back.bodyAsText()).ticket
        assertEquals(original.identifier, ret.warrantyOf)
        assertNull(ret.warrantyUntil)

        // Work logged on it is free unless priced
        post("/api/data/ticket/add/work/${ret.identifier}", json.encodeToString(WorkRequest(WorkType.LABOR, "Refit", 1.0)))
        val invoice = json.decodeFromString<Invoice>(client.get("/api/invoice/ticket/${ret.identifier}") { bearerAuth(token) }.bodyAsText())
        assertEquals(0, invoice.totalCents)

        // Not someone else's ticket, and not once it's run out
        assertEquals("Ticket ${original.label} isn't the customer's", checkIn(sam, original.identifier).bodyAsText())
        transaction { TicketTable.update(where = { TicketTable.id eq original.identifier }) { it[warrantyUntil] = System.currentTimeMillis() - 1000 } }
        val expired = checkIn(warrantyOf = original.identifier)
        assertEquals(HttpStatusCode.BadRequest, expired.status)
        assertTrue(expired.bodyAsText().startsWith("Ticket ${original.label}'s warranty ran out on"))

        // A plain ticket add can't claim one
        val claimed = json.decodeFromString<Ticket>(post("/api/data/ticket/add", json.encodeToString(Ticket(warrantyOf = original.identifier, warrantyUntil = 1))).bodyAsText())
        assertNull(claimed.warrantyOf)
        assertNull(claimed.warrantyUntil)
    }

    @Test
    fun theStatusPageSaysHowLongItsCovered() = testApplication {
        setup()
        val repaired = repairedWith(listOf(labor()))
        val page = client.get("/status/${repaired.statusToken}").bodyAsText()
        assertContains(page, "under warranty until")
    }
}
