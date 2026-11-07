package org.mr0btain.ticketman

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.mr0btain.ticketman.components.database.UserDatabase
import org.mr0btain.ticketman.data.catalog.CatalogModel
import org.mr0btain.ticketman.data.catalog.RepairOffer
import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.data.inventory.Inventory
import org.mr0btain.ticketman.data.invoice.ChargeType
import org.mr0btain.ticketman.data.invoice.PaymentMethod
import org.mr0btain.ticketman.data.ticket.Device
import org.mr0btain.ticketman.data.ticket.DeviceKind
import org.mr0btain.ticketman.data.ticket.Ticket
import org.mr0btain.ticketman.data.user.AccessLevel
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.server.request.CheckInItem
import org.mr0btain.ticketman.server.request.CheckInRequest
import org.mr0btain.ticketman.server.request.LoginRequest
import org.mr0btain.ticketman.server.request.PaymentRequest
import org.mr0btain.ticketman.server.request.PickupRequest
import org.mr0btain.ticketman.server.response.AuthResponse
import org.mr0btain.ticketman.server.response.CheckInResponse
import kotlin.test.*

/**
 * The repair menu, and check-in putting what's picked from it on the invoice
 */
class CatalogTest {
    private val dataDir = createTestDataDir()
    private val json = Json { ignoreUnknownKeys = true }

    private val admin = User(username = "boss", password = "boss_pass", accessLevel = AccessLevel.ADMIN)
    private val tech = User(username = "tech", password = "tech_pass", accessLevel = AccessLevel.NORMAL)
    private val clerk = User(username = "clerk", password = "clerk_pass", accessLevel = AccessLevel.RESTRICTED)
    private val customer = Customer(name = "Jane Doe", phone = "555-0100")
    private val screenPart = Inventory(name = "iPhone 14 Pro screen", stock = 3, costCents = 6_000, priceCents = 12_000)

    private val screen = RepairOffer(name = "Screen replacement", priceCents = 18_900, inventoryIdentifier = screenPart.identifier, warrantyDays = 90)
    private val diagnosis = RepairOffer(name = "Diagnosis", priceCents = 2_500)
    private val iphone = CatalogModel(kind = DeviceKind.PHONE, manufacturer = "Apple", model = "iPhone 14 Pro", repairs = listOf(screen, diagnosis))

    @AfterTest
    fun cleanup() = cleanupTestDataDir(dataDir)

    private suspend fun ApplicationTestBuilder.setup() {
        configureTestServer(dataDir)
        startApplication()
        UserDatabase().apply {
            add(admin)
            add(tech)
            add(clerk)
        }
        assertEquals(HttpStatusCode.Created, post("/api/data/customer/add", json.encodeToString(customer), login(tech)).status)
        assertEquals(HttpStatusCode.Created, post("/api/data/inventory/add", json.encodeToString(screenPart), login(admin)).status)
    }

    private suspend fun ApplicationTestBuilder.login(user: User): String {
        val response = client.post("/api/auth") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(LoginRequest(user.username, user.password)))
        }
        return json.decodeFromString<AuthResponse>(response.bodyAsText()).accessToken!!
    }

    private suspend fun ApplicationTestBuilder.post(path: String, body: String, token: String) =
        client.post(path) {
            contentType(ContentType.Application.Json)
            bearerAuth(token)
            setBody(body)
        }

    private suspend fun ApplicationTestBuilder.menu(token: String): List<CatalogModel> =
        json.decodeFromString(client.get("/api/catalog") { bearerAuth(token) }.bodyAsText())

    @Test
    fun adminsKeepTheMenuAndEveryoneReadsIt() = testApplication {
        setup()
        val adminToken = login(admin)

        assertEquals(HttpStatusCode.Forbidden, post("/api/catalog/add", json.encodeToString(iphone), login(tech)).status)
        assertEquals(HttpStatusCode.Created, post("/api/catalog/add", json.encodeToString(iphone.copy(model = "  iPhone 14 Pro ")), adminToken).status)
        assertEquals(listOf(iphone), menu(login(clerk)))

        // The same model twice, a repair named twice, or a part that isn't there are refused
        val again = iphone.copy(identifier = CatalogModel().identifier, model = "iphone 14 pro")
        assertEquals(HttpStatusCode.BadRequest, post("/api/catalog/add", json.encodeToString(again), adminToken).status)
        assertEquals(HttpStatusCode.BadRequest, post("/api/catalog/update", json.encodeToString(iphone.copy(repairs = listOf(screen, screen.copy(identifier = RepairOffer().identifier)))), adminToken).status)
        assertEquals(HttpStatusCode.BadRequest, post("/api/catalog/update", json.encodeToString(iphone.copy(repairs = listOf(screen.copy(inventoryIdentifier = Inventory().identifier)))), adminToken).status)

        val repriced = iphone.copy(repairs = listOf(screen.copy(priceCents = 19_900)))
        assertEquals(HttpStatusCode.OK, post("/api/catalog/update", json.encodeToString(repriced), adminToken).status)
        assertEquals(listOf(repriced), menu(adminToken))

        assertEquals(HttpStatusCode.Accepted, client.delete("/api/catalog/remove/${iphone.identifier}") { bearerAuth(adminToken) }.status)
        assertEquals(emptyList(), menu(adminToken))
        assertEquals(HttpStatusCode.NotFound, client.delete("/api/catalog/remove/${iphone.identifier}") { bearerAuth(adminToken) }.status)
    }

    @Test
    fun repairsPickedAtCheckInGoOnTheInvoiceAndTakeTheirPartAtPickup() = testApplication {
        setup()
        assertEquals(HttpStatusCode.Created, post("/api/catalog/add", json.encodeToString(iphone), login(admin)).status)
        val token = login(tech)

        // Nothing typed for the problem or estimate: the menu fills both in
        val ticket = Ticket(device = Device("Apple", "iPhone 14 Pro", kind = DeviceKind.PHONE))
        val request = CheckInRequest(ticket, customer.identifier, repairs = listOf(screen.identifier, diagnosis.identifier))
        val response = post("/api/counter/check-in", json.encodeToString(request), token)
        assertEquals(HttpStatusCode.Created, response.status, response.bodyAsText())

        val checkedIn = json.decodeFromString<CheckInResponse>(response.bodyAsText())
        assertEquals("Screen replacement, Diagnosis", checkedIn.ticket.problem)
        assertEquals(21_400, checkedIn.ticket.estimateCents)

        val invoice = assertNotNull(checkedIn.invoice)
        assertEquals(listOf(ChargeType.PART, ChargeType.LABOR), invoice.charges.map { it.type })
        assertEquals(listOf("Screen replacement (Apple iPhone 14 Pro)", "Diagnosis (Apple iPhone 14 Pro)"), invoice.charges.map { it.description })
        assertEquals(screenPart.identifier, invoice.charges.first().inventoryIdentifier)
        assertEquals(90, invoice.charges.first().warrantyDays)

        val pickup = PickupRequest(checkedIn.ticket.identifier, listOf(PaymentRequest(PaymentMethod.CASH, invoice.totalCents)))
        assertEquals(HttpStatusCode.OK.value / 100, post("/api/counter/pickup", json.encodeToString(pickup), token).status.value / 100)
        val part = json.decodeFromString<Inventory>(client.get("/api/data/inventory/get/${screenPart.identifier}") { bearerAuth(token) }.bodyAsText())
        assertEquals(2, part.stock)
    }

    @Test
    fun eachDeviceGetsItsOwnRepairsAndTypedDetailsWin() = testApplication {
        setup()
        assertEquals(HttpStatusCode.Created, post("/api/catalog/add", json.encodeToString(iphone), login(admin)).status)

        val first = Ticket(device = Device("Apple", "iPhone 14 Pro"), problem = "Dropped it", estimateCents = 15_000)
        val second = Ticket(device = Device("Apple", "iPhone 14 Pro", "SN2"))
        val request = CheckInRequest(
            first, customer.identifier,
            more = listOf(CheckInItem(second, repairs = listOf(diagnosis.identifier))),
            repairs = listOf(screen.identifier)
        )
        val response = post("/api/counter/check-in", json.encodeToString(request), login(tech))
        assertEquals(HttpStatusCode.Created, response.status, response.bodyAsText())

        val checkedIn = json.decodeFromString<CheckInResponse>(response.bodyAsText())
        assertEquals("Dropped it", checkedIn.ticket.problem)
        assertEquals(15_000, checkedIn.ticket.estimateCents)
        assertEquals("Diagnosis", checkedIn.linked.single().problem)
        assertEquals(listOf(screen.priceCents), checkedIn.invoice!!.charges.map { it.unitPriceCents })
    }

    @Test
    fun aRepairNoLongerOnTheMenuIsRefused() = testApplication {
        setup()
        val request = CheckInRequest(Ticket(device = Device("Apple", "iPhone 14 Pro")), customer.identifier, repairs = listOf(RepairOffer().identifier))
        assertEquals(HttpStatusCode.BadRequest, post("/api/counter/check-in", json.encodeToString(request), login(tech)).status)
    }
}
