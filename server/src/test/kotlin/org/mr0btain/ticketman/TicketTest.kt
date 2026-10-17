package org.mr0btain.ticketman

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.mr0btain.ticketman.components.database.LocalDatabase
import org.mr0btain.ticketman.components.database.TicketDatabaseImpl
import org.mr0btain.ticketman.components.database.UserDatabaseImpl
import org.mr0btain.ticketman.components.database.tables.ticket.TicketTable
import org.mr0btain.ticketman.data.inventory.AccessLevel
import org.mr0btain.ticketman.data.search.SearchQuery
import org.mr0btain.ticketman.data.search.SearchResult
import org.mr0btain.ticketman.data.ticket.*
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.server.request.LoginRequest
import org.mr0btain.ticketman.server.response.AuthResponse
import java.time.Year
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.test.*
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Ticket numbers, check in fields and status history
 */
@OptIn(ExperimentalUuidApi::class)
class TicketTest {
    private val dataDir = createTestDataDir()
    private val json = Json { ignoreUnknownKeys = true }
    private val tech = User(username = "tech", password = "tech_pass", accessLevel = AccessLevel.NORMAL)
    private val year = Year.now().value

    @AfterTest
    fun cleanup() = cleanupTestDataDir(dataDir)

    private suspend fun ApplicationTestBuilder.setup(): String {
        configureTestServer(dataDir)
        startApplication()
        UserDatabaseImpl().add(tech)

        val response = client.post("/api/auth") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(LoginRequest(tech.username, tech.password)))
        }

        return json.decodeFromString<AuthResponse>(response.bodyAsText()).accessToken!!
    }

    private suspend fun ApplicationTestBuilder.addTicket(token: String, ticket: Ticket): Ticket {
        val response = client.post("/api/data/ticket/add") {
            contentType(ContentType.Application.Json)
            bearerAuth(token)
            setBody(json.encodeToString(ticket))
        }

        assertEquals(HttpStatusCode.Created, response.status)
        return json.decodeFromString(response.bodyAsText())
    }

    private suspend fun ApplicationTestBuilder.search(token: String, query: String): List<SearchResult> =
        json.decodeFromString(
            client.post("/api/data/ticket/search") {
                contentType(ContentType.Application.Json)
                bearerAuth(token)
                setBody(json.encodeToString(SearchQuery(query, 20)))
            }.bodyAsText()
        )

    @Test
    fun numberFormat() {
        assertEquals("26-00000042", TicketNumber(2026, 42).toString())
        assertEquals(TicketNumber(2026, 42), TicketNumber.parse("26-00000042"))
        assertEquals(TicketNumber(2026, 42), TicketNumber.parse("26-42"))
        assertNull(TicketNumber.parse("42"))
        assertNull(TicketNumber.parse("2026-42"))
    }

    @Test
    fun addedTicketsGetSequentialNumbersAndKeepIntakeFields() = testApplication {
        val token = setup()

        val first = addTicket(token, Ticket(problem = "Cracked screen", accessories = "Charger", estimateCents = 12_500))
        val second = addTicket(token, Ticket())

        assertEquals(TicketNumber(year, 1), first.number)
        assertEquals(TicketNumber(year, 2), second.number)
        assertEquals("Cracked screen", first.problem)
        assertEquals("Charger", first.accessories)
        assertEquals(12_500, first.estimateCents)
    }

    @Test
    fun numbersStartOverInANewYear() = testApplication {
        val token = setup()
        addTicket(token, Ticket())

        val nextYear = TicketDatabaseImpl(currentYear = { year + 1 })
        val ticket = Ticket()
        nextYear.add(ticket)

        assertEquals(TicketNumber(year + 1, 1), nextYear.getById(ticket.identifier)?.number)
    }

    @Test
    fun ticketsAddedAtTheSameTimeGetDifferentNumbers() = testApplication {
        setup()
        val tickets = TicketDatabaseImpl()
        val added = List(30) { Ticket() }

        val pool = Executors.newFixedThreadPool(8)
        added.forEach { ticket -> pool.submit { tickets.add(ticket) } }
        pool.shutdown()
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS))

        val numbers = added.map { tickets.getById(it.identifier)?.number }
        assertTrue(numbers.none { it == null }, "Some tickets weren't added")
        assertEquals((1L..30L).toSet(), numbers.map { it!!.sequence }.toSet())
    }

    @Test
    fun updateCantChangeTheNumber() = testApplication {
        val token = setup()
        val ticket = addTicket(token, Ticket())

        client.post("/api/data/ticket/update") {
            contentType(ContentType.Application.Json)
            bearerAuth(token)
            setBody(json.encodeToString(ticket.copy(number = TicketNumber(1999, 7), problem = "Updated")))
        }

        val saved = TicketDatabaseImpl().getById(ticket.identifier)!!
        assertEquals(ticket.number, saved.number)
        assertEquals("Updated", saved.problem)
    }

    @Test
    fun olderDatabasesGetNewColumnsAndNumbers() = testApplication {
        setup()
        val oldTicket = Uuid.generateV7()

        // Make the tickets table look like it did before numbers and check in fields
        transaction {
            exec("DROP INDEX IF EXISTS ${TicketTable.NUMBER_INDEX}")
            listOf("number_year", "number_sequence", "problem", "device_condition", "accessories", "estimate_cents", "promised_at", "picked_up_at")
                .forEach { exec("ALTER TABLE tickets DROP COLUMN $it") }
            exec("INSERT INTO tickets (id, device, status, priority) VALUES ('$oldTicket', '{}' FORMAT JSON, '{}' FORMAT JSON, '{}' FORMAT JSON)")
        }

        // Starting up again should bring it back to the current shape
        LocalDatabase(dataDir.resolve("server").invariantSeparatorsPathString).initDatabase()

        val migrated = TicketDatabaseImpl().getById(oldTicket)
        assertNotNull(migrated)
        assertEquals("", migrated.problem)
        assertEquals(TicketNumber(year, 1), migrated.number)
    }

    @Test
    fun backfillNumbersOldestFirst() = testApplication {
        setup()
        val older = Uuid.generateV7()
        Thread.sleep(2)
        val newer = Uuid.generateV7()

        // Rows without numbers, newest put in first
        transaction {
            listOf(newer, older).forEach { identifier ->
                TicketTable.insert { row ->
                    row[TicketTable.id] = identifier
                    row[TicketTable.device] = Device()
                    row[TicketTable.status] = TicketStatus()
                    row[TicketTable.priority] = TicketPriority()
                }
            }
        }

        val tickets = TicketDatabaseImpl()
        tickets.backfillNumbers()

        assertEquals(1, tickets.getById(older)?.number?.sequence)
        assertEquals(2, tickets.getById(newer)?.number?.sequence)
    }

    @Test
    fun searchFindsNumbersDevicesAndProblems() = testApplication {
        val token = setup()
        val phone = addTicket(token, Ticket(device = Device("Apple", "iPhone 14", "SN123")))
        val laptop = addTicket(token, Ticket(device = Device("Dell", "XPS 13"), problem = "Won't boot"))

        assertEquals(listOf(phone.identifier), search(token, phone.number.toString()).map { it.identifier })
        assertEquals(listOf(laptop.identifier), search(token, "${year % 100}-2").map { it.identifier })
        assertTrue(search(token, "2").any { it.identifier == laptop.identifier })
        assertEquals(listOf(phone.identifier), search(token, "iphone").map { it.identifier })
        assertEquals(listOf(phone.identifier), search(token, "sn123").map { it.identifier })
        assertEquals(listOf(laptop.identifier), search(token, "boot").map { it.identifier })

        // Results show the number and device
        assertEquals("${laptop.number} Dell XPS 13", search(token, "xps").single().text)
    }

    @Test
    fun lookupByNumber() = testApplication {
        val token = setup()
        val ticket = addTicket(token, Ticket())

        val found = client.get("/api/data/ticket/number/${ticket.number}") { bearerAuth(token) }
        assertEquals(ticket.identifier, json.decodeFromString<Ticket>(found.bodyAsText()).identifier)

        assertEquals(HttpStatusCode.NotFound, client.get("/api/data/ticket/number/99-00009999") { bearerAuth(token) }.status)
    }

    @Test
    fun statusChangesAreRecordedWithWhoMadeThem() = testApplication {
        val token = setup()
        val ticket = addTicket(token, Ticket())

        // Same status again shouldn't add an entry
        listOf(TicketStatusType.ON_HOLD, TicketStatusType.ON_HOLD, TicketStatusType.DONE).forEach { status ->
            client.post("/api/data/ticket/update") {
                contentType(ContentType.Application.Json)
                bearerAuth(token)
                setBody(json.encodeToString(ticket.copy(status = TicketStatus(status))))
            }
        }

        val history = json.decodeFromString<List<TicketStatusChange>>(
            client.get("/api/data/ticket/get/history/${ticket.identifier}") { bearerAuth(token) }.bodyAsText()
        )

        assertEquals(
            listOf(TicketStatusType.IN_PROGRESS, TicketStatusType.ON_HOLD, TicketStatusType.DONE),
            history.map { it.status }
        )
        assertTrue(history.all { it.changedBy == tech.username })
    }

    @Test
    fun pickedUpTicketsArentOpen() {
        assertTrue(TicketStatusType.IN_PROGRESS.isOpen)
        assertTrue(TicketStatusType.ON_HOLD.isOpen)
        assertFalse(TicketStatusType.DONE.isOpen)
        assertFalse(TicketStatusType.PICKED_UP.isOpen)
        assertFalse(TicketStatusType.PICKED_UP in TicketStatusType.selectable)
    }
}
