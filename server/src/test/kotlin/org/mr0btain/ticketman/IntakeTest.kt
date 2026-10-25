package org.mr0btain.ticketman

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.mr0btain.ticketman.components.database.AuditLogDatabase
import org.mr0btain.ticketman.components.database.TicketDatabase
import org.mr0btain.ticketman.components.database.UserDatabase
import org.mr0btain.ticketman.components.database.tables.ticket.TicketPasscodeTable
import org.mr0btain.ticketman.components.service.PasscodeVault
import org.mr0btain.ticketman.data.audit.AuditAction
import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.data.invoice.PaymentMethod
import org.mr0btain.ticketman.data.shared.PagedRequest
import org.mr0btain.ticketman.data.ticket.*
import org.mr0btain.ticketman.data.user.AccessLevel
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.server.request.*
import org.mr0btain.ticketman.server.response.AuthResponse
import org.mr0btain.ticketman.server.response.CheckInResponse
import kotlin.test.*

/**
 * The intake checklist and the device's passcode: sealed, shown only to staff who can edit, audited, gone at pickup
 */
class IntakeTest {
    private val dataDir = createTestDataDir()
    private val json = Json { ignoreUnknownKeys = true }

    private val tech = User(username = "tech", password = "tech_pass", accessLevel = AccessLevel.NORMAL)
    private val clerk = User(username = "clerk", password = "clerk_pass", accessLevel = AccessLevel.RESTRICTED)
    private val jane = Customer(name = "Jane Doe")

    private lateinit var token: String
    private lateinit var clerkToken: String

    @AfterTest
    fun cleanup() = cleanupTestDataDir(dataDir)

    private suspend fun ApplicationTestBuilder.setup() {
        configureTestServer(dataDir)
        startApplication()
        UserDatabase().apply {
            add(tech)
            add(clerk)
        }
        token = login(tech)
        clerkToken = login(clerk)
        post("/api/data/customer/add", json.encodeToString(jane))
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

    private val checklist = listOf(ChecklistItem("Powers on", CheckResult.WORKS), ChecklistItem("Front camera", CheckResult.FAULTY), ChecklistItem("Face unlock"))

    private suspend fun ApplicationTestBuilder.checkIn(passcode: String = "1234"): Ticket {
        val ticket = Ticket(device = Device("Apple", "iPhone 13", kind = DeviceKind.PHONE), problem = "Screen", checklist = checklist, passcodeSet = true)
        val response = post("/api/counter/check-in", json.encodeToString(CheckInRequest(ticket, jane.identifier, passcode = passcode)))
        assertEquals(HttpStatusCode.Created, response.status, response.bodyAsText())
        return json.decodeFromString<CheckInResponse>(response.bodyAsText()).ticket
    }

    @Test
    fun theChecklistIsKeptAndThePasscodeSealed() = testApplication {
        setup()
        val ticket = checkIn("1234-ABCD")

        assertEquals(checklist, ticket.checklist)
        assertTrue(ticket.passcodeSet)

        // Never in the database as typed
        val sealed = transaction { TicketPasscodeTable.selectAll().single()[TicketPasscodeTable.sealed] }
        assertFalse("1234" in sealed)

        // Staff who can edit see it, and each look is written down
        val shown = client.get("/api/data/ticket/passcode/${ticket.identifier}") { bearerAuth(token) }
        assertEquals("1234-ABCD", json.decodeFromString<PasscodeBody>(shown.bodyAsText()).passcode)
        assertEquals(HttpStatusCode.Forbidden, client.get("/api/data/ticket/passcode/${ticket.identifier}") { bearerAuth(clerkToken) }.status)
        val looks = AuditLogDatabase(UserDatabase()).getPagedItems(PagedRequest(0, 50))!!.filter { "viewed" in it.details }
        assertEquals(1, looks.size)
        assertEquals("tech", looks.single().actorName)

        // A ticket save can't claim one or drop it
        post("/api/data/ticket/update", json.encodeToString(ticket.copy(passcodeSet = false, problem = "Screen and battery")))
        assertTrue(TicketDatabase().getById(ticket.identifier)!!.passcodeSet)

        // Changed and cleared
        assertEquals(HttpStatusCode.Accepted, post("/api/data/ticket/passcode/${ticket.identifier}", json.encodeToString(PasscodeBody("0000"))).status)
        assertEquals("0000", json.decodeFromString<PasscodeBody>(client.get("/api/data/ticket/passcode/${ticket.identifier}") { bearerAuth(token) }.bodyAsText()).passcode)
        assertEquals(HttpStatusCode.BadRequest, post("/api/data/ticket/passcode/${ticket.identifier}", json.encodeToString(PasscodeBody("x".repeat(65)))).status)
        assertEquals(HttpStatusCode.Accepted, client.delete("/api/data/ticket/passcode/${ticket.identifier}") { bearerAuth(token) }.status)
        assertFalse(TicketDatabase().getById(ticket.identifier)!!.passcodeSet)
        assertEquals(HttpStatusCode.NotFound, client.get("/api/data/ticket/passcode/${ticket.identifier}") { bearerAuth(token) }.status)
    }

    @Test
    fun noPasscodeIsNoneAndAPickupForgetsIt() = testApplication {
        setup()
        assertFalse(checkIn(passcode = " ").passcodeSet)

        val ticket = checkIn()
        val response = post("/api/counter/pickup", json.encodeToString(PickupRequest(ticket.identifier, listOf(PaymentRequest(PaymentMethod.CASH, 100)))))
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())

        assertFalse(TicketDatabase().getById(ticket.identifier)!!.passcodeSet)
        assertEquals(0, transaction { TicketPasscodeTable.selectAll().count() })
    }

    @Test
    fun anotherServersSecretCantOpenIt() {
        val ours = PasscodeVault("one secret")
        val sealed = ours.seal("2580")

        assertEquals("2580", ours.open(sealed))
        assertNotEquals(sealed, ours.seal("2580"), "each seal is different")
        assertNull(PasscodeVault("another secret").open(sealed))
        assertNull(ours.open("not:sealed"))
    }

    @Test
    fun theShopsListsReplaceTheDefaults() {
        val own = listOf(IntakeChecklist(DeviceKind.PHONE, listOf("Water damage sticker")), IntakeChecklist(DeviceKind.CONSOLE, emptyList()))

        assertEquals(listOf("Water damage sticker"), IntakeChecklists.itemsFor(DeviceKind.PHONE, own))
        assertEquals(emptyList(), IntakeChecklists.itemsFor(DeviceKind.CONSOLE, own))
        assertEquals(IntakeChecklists.defaults[DeviceKind.LAPTOP], IntakeChecklists.itemsFor(DeviceKind.LAPTOP, own))
    }
}
