package org.mr0btain.ticketman

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.mr0btain.ticketman.components.database.UserDatabase
import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.data.shop.ShopProfile
import org.mr0btain.ticketman.data.ticket.*
import org.mr0btain.ticketman.data.user.AccessLevel
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.server.request.CheckInRequest
import org.mr0btain.ticketman.server.request.LoginRequest
import org.mr0btain.ticketman.server.response.AuthResponse
import org.mr0btain.ticketman.server.response.CheckInResponse
import kotlin.test.*
import kotlin.uuid.ExperimentalUuidApi

/**
 * The shop's own ticket statuses, its public address, and notes the customer can see
 */
@OptIn(ExperimentalUuidApi::class)
class CustomStatusTest {
    private val dataDir = createTestDataDir()
    private val json = Json { ignoreUnknownKeys = true }

    private val admin = User(username = "boss", password = "boss_pass", accessLevel = AccessLevel.ADMIN)
    private val tech = User(username = "tech", password = "tech_pass", accessLevel = AccessLevel.NORMAL)

    private val awaitingParts = CustomStatus(name = "Awaiting parts", actsAs = TicketStatusType.ON_HOLD, showCustomer = true)
    private val qualityCheck = CustomStatus(name = "Quality check", actsAs = TicketStatusType.IN_PROGRESS, tone = StatusTone.ACCENT, showCustomer = false)

    private lateinit var adminToken: String
    private lateinit var token: String

    @AfterTest
    fun cleanup() = cleanupTestDataDir(dataDir)

    private suspend fun ApplicationTestBuilder.setup(statuses: List<CustomStatus> = listOf(awaitingParts, qualityCheck)) {
        configureTestServer(dataDir)
        startApplication()
        UserDatabase().apply {
            add(admin)
            add(tech)
        }
        adminToken = login(admin)
        token = login(tech)

        assertEquals(HttpStatusCode.Accepted, saveShop(ShopProfile(statuses = statuses)).status)
    }

    private suspend fun ApplicationTestBuilder.login(user: User): String {
        val response = client.post("/api/auth") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(LoginRequest(user.username, user.password)))
        }

        return json.decodeFromString<AuthResponse>(response.bodyAsText()).accessToken!!
    }

    private suspend fun ApplicationTestBuilder.post(path: String, body: String, as_: String = token) =
        client.post(path) {
            contentType(ContentType.Application.Json)
            bearerAuth(as_)
            setBody(body)
        }

    private suspend fun ApplicationTestBuilder.saveShop(profile: ShopProfile) =
        post("/api/shop/update", json.encodeToString(profile), adminToken)

    private suspend fun ApplicationTestBuilder.shop(): ShopProfile =
        json.decodeFromString(client.get("/api/shop") { bearerAuth(token) }.bodyAsText())

    private suspend fun ApplicationTestBuilder.ticket(identifier: Any): Ticket =
        json.decodeFromString(client.get("/api/data/ticket/get/$identifier") { bearerAuth(token) }.bodyAsText())

    private suspend fun ApplicationTestBuilder.history(identifier: Any): List<TicketStatusChange> =
        json.decodeFromString(client.get("/api/data/ticket/get/history/$identifier") { bearerAuth(token) }.bodyAsText())

    private suspend fun ApplicationTestBuilder.update(ticket: Ticket) =
        post("/api/data/ticket/update", json.encodeToString(ticket))

    // ------------------------------------------ The shop's statuses ------------------------------------------

    @Test
    fun statusesAreCheckedWhenTheShopIsSaved() = testApplication {
        setup(statuses = emptyList())

        val bad = listOf(
            listOf(CustomStatus(name = " ")),
            listOf(CustomStatus(name = "ready for pickup")),
            listOf(CustomStatus(name = "Gone", actsAs = TicketStatusType.PICKED_UP)),
            listOf(CustomStatus(name = "Waiting"), CustomStatus(name = "waiting ")),
            listOf(CustomStatus(name = "x".repeat(CustomStatusRules.MAX_NAME_LENGTH + 1)))
        )
        bad.forEach { statuses -> assertEquals(HttpStatusCode.BadRequest, saveShop(ShopProfile(statuses = statuses)).status, statuses.toString()) }

        // Only admins set them, and names are saved trimmed
        assertEquals(HttpStatusCode.Forbidden, post("/api/shop/update", json.encodeToString(ShopProfile(statuses = listOf(awaitingParts)))).status)
        assertEquals(HttpStatusCode.Accepted, saveShop(ShopProfile(statuses = listOf(awaitingParts.copy(name = " Awaiting parts ")))).status)
        assertEquals(listOf(awaitingParts), shop().statuses)
    }

    @Test
    fun aRemovedStatusIsKeptRetiredAndCantChangeWhatItCountsAs() = testApplication {
        setup()

        assertEquals(HttpStatusCode.Accepted, saveShop(ShopProfile(statuses = listOf(qualityCheck))).status)
        assertEquals(listOf(qualityCheck, awaitingParts.copy(retired = true)), shop().statuses)

        // A retired name can be used again, by a new status
        val again = CustomStatus(name = "Awaiting parts", actsAs = TicketStatusType.ON_HOLD)
        assertEquals(HttpStatusCode.Accepted, saveShop(ShopProfile(statuses = shop().statuses + again)).status)

        val moved = qualityCheck.copy(actsAs = TicketStatusType.DONE)
        assertEquals(HttpStatusCode.BadRequest, saveShop(ShopProfile(statuses = listOf(moved))).status)
    }

    @Test
    fun publicAddressIsCheckedAndTidied() = testApplication {
        setup()

        listOf("repairs.example.com", "ftp://example.com", "https://", "https://exa mple.com").forEach { url ->
            assertEquals(HttpStatusCode.BadRequest, saveShop(ShopProfile(publicUrl = url)).status, url)
        }

        assertEquals(HttpStatusCode.Accepted, saveShop(ShopProfile(publicUrl = " https://repairs.example.com/ ")).status)
        assertEquals("https://repairs.example.com", shop().publicUrl)
    }

    // ------------------------------------------ Tickets in them ------------------------------------------

    @Test
    fun aTicketTakesTheBuiltInStatusItsCustomOneActsAs() = testApplication {
        setup()

        // Sent with the wrong built-in status, saved with the right one
        val response = post("/api/data/ticket/add", json.encodeToString(Ticket(status = TicketStatus(TicketStatusType.IN_PROGRESS, custom = awaitingParts.identifier))))
        assertEquals(HttpStatusCode.Created, response.status)
        val added = json.decodeFromString<Ticket>(response.bodyAsText())
        assertEquals(awaitingParts.choice, added.status.choice)

        // Moving between two custom statuses, or back to a plain one, is a change too
        assertEquals(HttpStatusCode.Accepted, update(added.copy(status = qualityCheck.choice.toStatus())).status)
        assertEquals(HttpStatusCode.Accepted, update(added.copy(status = TicketStatus(TicketStatusType.IN_PROGRESS))).status)

        assertEquals(
            listOf(awaitingParts.choice, qualityCheck.choice, StatusChoice(TicketStatusType.IN_PROGRESS)),
            history(added.identifier).map { it.choice }
        )

        val unknown = TicketStatus(TicketStatusType.ON_HOLD, custom = CustomStatus().identifier)
        assertEquals(HttpStatusCode.BadRequest, post("/api/data/ticket/add", json.encodeToString(Ticket(status = unknown))).status)
        assertEquals(HttpStatusCode.BadRequest, update(added.copy(status = unknown)).status)
    }

    @Test
    fun aRetiredStatusCantBePickedButTicketsInItStay() = testApplication {
        setup()
        val inIt = json.decodeFromString<Ticket>(post("/api/data/ticket/add", json.encodeToString(Ticket(status = awaitingParts.choice.toStatus()))).bodyAsText())
        val other = json.decodeFromString<Ticket>(post("/api/data/ticket/add", json.encodeToString(Ticket())).bodyAsText())

        saveShop(ShopProfile(statuses = listOf(qualityCheck)))

        assertEquals(HttpStatusCode.BadRequest, update(other.copy(status = awaitingParts.choice.toStatus())).status)
        assertEquals(HttpStatusCode.Accepted, update(inIt.copy(problem = "Still waiting")).status)
        assertEquals(awaitingParts.choice, ticket(inIt.identifier).status.choice)
    }

    @Test
    fun checkInCanStartInACustomStatus() = testApplication {
        setup()
        val customer = Customer(name = "Jane Doe")
        post("/api/data/customer/add", json.encodeToString(customer))

        val ticket = Ticket(device = Device("Apple", "iPhone 14"), problem = "No power", status = TicketStatus(TicketStatusType.IN_PROGRESS, custom = awaitingParts.identifier))
        val response = post("/api/counter/check-in", json.encodeToString(CheckInRequest(ticket, customer.identifier, null, null, null)))
        assertEquals(HttpStatusCode.Created, response.status, response.bodyAsText())

        val saved = json.decodeFromString<CheckInResponse>(response.bodyAsText()).ticket
        assertEquals(awaitingParts.choice, saved.status.choice)
        assertEquals(awaitingParts.choice, history(saved.identifier).single().choice)
    }

    // ------------------------------------------ Notes ------------------------------------------

    @Test
    fun notesAreInternalUntilMarkedForTheCustomer() = testApplication {
        setup()
        val ticket = json.decodeFromString<Ticket>(post("/api/data/ticket/add", json.encodeToString(Ticket())).bodyAsText())

        val internal = Note(message = "Board has corrosion")
        val shared = Note(message = "Your new screen is on its way", customerVisible = true)
        post("/api/data/ticket/add/note/${ticket.identifier}", json.encodeToString(internal))
        post("/api/data/ticket/add/note/${ticket.identifier}", json.encodeToString(shared))

        suspend fun notes() = json.decodeFromString<List<Note>>(client.get("/api/data/ticket/get/notes/${ticket.identifier}") { bearerAuth(token) }.bodyAsText())
            .associate { it.identifier to it.customerVisible }

        assertEquals(mapOf(internal.identifier to false, shared.identifier to true), notes())

        assertEquals(HttpStatusCode.Accepted, post("/api/data/ticket/update/note/${shared.identifier}", json.encodeToString(shared.copy(customerVisible = false))).status)
        assertEquals(mapOf(internal.identifier to false, shared.identifier to false), notes())
    }

    // ------------------------------------------ Reading them ------------------------------------------

    @Test
    fun namesForStaffAndCustomers() {
        val statuses = listOf(awaitingParts, qualityCheck)

        assertEquals("Awaiting parts", statuses.label(awaitingParts.choice))
        assertEquals("Awaiting parts", statuses.customerLabel(awaitingParts.choice))
        assertEquals("Quality check", statuses.label(qualityCheck.choice))
        assertEquals("In progress", statuses.customerLabel(qualityCheck.choice))
        assertEquals(StatusTone.ACCENT, statuses.tone(qualityCheck.choice))
        assertEquals(StatusTone.DANGER, statuses.tone(awaitingParts.choice))

        // A status that's gone reads as the built-in one
        assertEquals("On hold", emptyList<CustomStatus>().label(awaitingParts.choice))

        val retired = listOf(awaitingParts.copy(retired = true), qualityCheck)
        assertEquals(
            listOf(StatusChoice(TicketStatusType.IN_PROGRESS), qualityCheck.choice, StatusChoice(TicketStatusType.ON_HOLD), StatusChoice(TicketStatusType.DONE)),
            retired.selectable()
        )
        assertTrue(awaitingParts.choice in retired.selectable(current = awaitingParts.choice))
    }
}
