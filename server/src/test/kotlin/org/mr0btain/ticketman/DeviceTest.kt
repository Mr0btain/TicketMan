package org.mr0btain.ticketman

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.mr0btain.ticketman.components.database.TicketDatabase
import org.mr0btain.ticketman.components.database.UserDatabase
import org.mr0btain.ticketman.data.counter.TicketSummary
import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.data.invoice.PaymentMethod
import org.mr0btain.ticketman.data.shop.ShopProfile
import org.mr0btain.ticketman.data.ticket.*
import org.mr0btain.ticketman.data.user.AccessLevel
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.server.request.*
import org.mr0btain.ticketman.server.response.AuthResponse
import org.mr0btain.ticketman.server.response.CheckInResponse
import kotlin.test.*

/**
 * Devices customers own: made at check in, picked again next time, several at one visit, and each one's history
 */
class DeviceTest {
    private val dataDir = createTestDataDir()
    private val json = Json { ignoreUnknownKeys = true }

    private val admin = User(username = "boss", password = "boss_pass", accessLevel = AccessLevel.ADMIN)
    private val tech = User(username = "tech", password = "tech_pass", accessLevel = AccessLevel.NORMAL)
    private val jane = Customer(name = "Jane Doe")
    private val sam = Customer(name = "Sam")

    private val signature = SignatureInput(
        strokes = listOf(listOf(SignaturePoint(0.1f, 0.5f), SignaturePoint(0.9f, 0.6f))),
        aspectRatio = 3f,
        signedName = "Jane Doe"
    )

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
        post("/api/shop/update", json.encodeToString(ShopProfile(repairTerms = "Not responsible for data")), login(admin))
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

    private suspend fun ApplicationTestBuilder.get(path: String) = client.get(path) { bearerAuth(token) }

    private fun ticket(device: Device, problem: String = "Broken") = Ticket(device = device, problem = problem)

    private suspend fun ApplicationTestBuilder.checkIn(request: CheckInRequest): CheckInResponse {
        val response = post("/api/counter/check-in", json.encodeToString(request))
        assertEquals(HttpStatusCode.Created, response.status, response.bodyAsText())
        return json.decodeFromString(response.bodyAsText())
    }

    private suspend fun ApplicationTestBuilder.devices(customer: Customer): List<CustomerDevice> =
        json.decodeFromString(get("/api/data/customer/devices/${customer.identifier}").bodyAsText())

    @Test
    fun aCheckInMakesTheDeviceAndTheSameSerialFindsItAgain() = testApplication {
        setup()
        val phone = Device("Apple", "iPhone 13", "SN-1", DeviceKind.PHONE)

        val first = checkIn(CheckInRequest(ticket(phone), jane.identifier, signature = signature)).ticket
        val made = devices(jane).single()
        assertEquals(phone, made.device)
        assertEquals(made.identifier, first.deviceIdentifier)
        assertNull(first.groupIdentifier)

        // Typed again without picking it, the serial matches
        val second = checkIn(CheckInRequest(ticket(phone.copy(serial = "sn-1"), "Battery"), jane.identifier, signature = signature)).ticket
        assertEquals(made.identifier, second.deviceIdentifier)
        assertEquals(1, devices(jane).size)

        // Picked, with the colour fixed on the way, which updates the record
        val third = checkIn(CheckInRequest(ticket(phone.copy(model = "iPhone 13 mini")), jane.identifier, signature = signature, deviceIdentifier = made.identifier)).ticket
        assertEquals(made.identifier, third.deviceIdentifier)
        assertEquals("iPhone 13 mini", devices(jane).single().device.model)

        // Its history, newest first
        val history = json.decodeFromString<List<Ticket>>(get("/api/data/customer/device/${made.identifier}/tickets").bodyAsText())
        assertEquals(listOf(third.identifier, second.identifier, first.identifier), history.map { it.identifier })

        // And the repeat check finds them by the device
        val repeats = post("/api/counter/repeat-check", json.encodeToString(RepeatCheckRequest(jane.identifier, Device(), deviceIdentifier = made.identifier)))
        assertEquals(3, json.decodeFromString<List<TicketSummary>>(repeats.bodyAsText()).size)

        // Someone else's device can't be picked
        val wrong = post("/api/counter/check-in", json.encodeToString(CheckInRequest(ticket(phone), sam.identifier, signature = signature, deviceIdentifier = made.identifier)))
        assertEquals(HttpStatusCode.BadRequest, wrong.status)
        assertEquals("That device isn't the customer's", wrong.bodyAsText())
    }

    @Test
    fun severalDevicesAtOneVisitAreLinked() = testApplication {
        setup()
        val request = CheckInRequest(
            ticket = ticket(Device("Apple", "iPhone 13", kind = DeviceKind.PHONE), "Screen"),
            customerIdentifier = jane.identifier,
            technicianIdentifier = tech.identifier,
            deposit = PaymentRequest(PaymentMethod.CASH, 2_000),
            signature = signature,
            more = listOf(
                CheckInItem(ticket(Device("Apple", "iPad Air", kind = DeviceKind.TABLET), "Battery")),
                CheckInItem(ticket(Device("Sony", "PS5", kind = DeviceKind.CONSOLE), "HDMI port"))
            )
        )

        val response = checkIn(request)
        val all = listOf(response.ticket) + response.linked
        assertEquals(3, all.size)
        assertEquals(1, all.map { it.groupIdentifier }.toSet().size)
        assertEquals(response.ticket.identifier, response.ticket.groupIdentifier)
        assertEquals(3, devices(jane).size)
        assertEquals(3, all.map { it.number }.toSet().size)

        // One deposit, on the first; the signature on each
        assertEquals(2_000, response.invoice!!.paidCents)
        all.forEach { assertNotNull(TicketDatabase().getSignature(it.identifier)) }
        assertEquals(HttpStatusCode.NotFound, get("/api/invoice/ticket/${response.linked.first().identifier}").status)

        // A bad one stops all of them
        val bad = request.copy(ticket = ticket(Device("Apple", "iPhone")), more = listOf(CheckInItem(ticket(Device("Apple", "iPad"), problem = " "))))
        assertEquals(HttpStatusCode.BadRequest, post("/api/counter/check-in", json.encodeToString(bad)).status)
        assertNull(TicketDatabase().getById(bad.ticket.identifier))
    }

    @Test
    fun devicesAreKeptOnFileAndTheirTicketsOutliveThem() = testApplication {
        setup()
        val added = post("/api/data/customer/devices/${jane.identifier}", json.encodeToString(CustomerDevice(customerIdentifier = sam.identifier, device = Device("Google", "Pixel 7"), color = "Black")))
        assertEquals(HttpStatusCode.Created, added.status)
        val device = json.decodeFromString<CustomerDevice>(added.bodyAsText())
        assertEquals(jane.identifier, device.customerIdentifier, "the path says whose it is")

        assertEquals(HttpStatusCode.BadRequest, post("/api/data/customer/devices/${jane.identifier}", json.encodeToString(CustomerDevice(customerIdentifier = jane.identifier))).status)

        val updated = post("/api/data/customer/device/update", json.encodeToString(device.copy(device = device.device.copy(serial = "PX7"), notes = "Cracked back")))
        assertEquals("PX7", json.decodeFromString<CustomerDevice>(updated.bodyAsText()).device.serial)

        val repaired = checkIn(CheckInRequest(ticket(Device("Google", "Pixel 7", "PX7")), jane.identifier, signature = signature)).ticket
        assertEquals(device.identifier, repaired.deviceIdentifier)

        assertEquals(HttpStatusCode.Accepted, client.delete("/api/data/customer/device/${device.identifier}") { bearerAuth(token) }.status)
        assertTrue(devices(jane).isEmpty())
        assertEquals("Pixel 7", TicketDatabase().getById(repaired.identifier)!!.device.model)

        // A ticket added outside check in can't claim one
        val claimed = post("/api/data/ticket/add", json.encodeToString(Ticket(deviceIdentifier = device.identifier, groupIdentifier = device.identifier)))
        val saved = json.decodeFromString<Ticket>(claimed.bodyAsText())
        assertNull(saved.deviceIdentifier)
        assertNull(saved.groupIdentifier)
    }

    @Test
    fun ticketsFromBeforeDevicesGetTheirOwn() = testApplication {
        setup()
        val database = TicketDatabase()
        val old = listOf(
            Ticket(device = Device("Apple", "iPhone 12", "SN-A"), problem = "Screen"),
            Ticket(device = Device("Apple", "iPhone 12", "sn-a"), problem = "Battery"),
            Ticket(device = Device("Sony", "PS4"), problem = "Fan"),
            Ticket(device = Device("Sony", "PS4"), problem = "Fan again")
        )
        old.forEach { ticket ->
            database.add(ticket)
            database.setCustomer(ticket.identifier, jane.identifier)
        }
        val orphan = Ticket(device = Device("Nokia", "3310"), problem = "No customer")
        database.add(orphan)

        val made = org.jetbrains.exposed.v1.jdbc.transactions.transaction { org.mr0btain.ticketman.components.database.DeviceDatabase().backfill() }
        assertEquals(2, made)

        val tickets = old.map { database.getById(it.identifier)!! }
        assertEquals(tickets[0].deviceIdentifier, tickets[1].deviceIdentifier)
        assertEquals(tickets[2].deviceIdentifier, tickets[3].deviceIdentifier)
        assertNotEquals(tickets[0].deviceIdentifier, tickets[2].deviceIdentifier)
        assertNull(database.getById(orphan.identifier)!!.deviceIdentifier)
        assertEquals(setOf("iPhone 12", "PS4"), devices(jane).map { it.device.model }.toSet())
    }
}
