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
import org.mr0btain.ticketman.data.location.Location
import org.mr0btain.ticketman.data.shop.ShopProfile
import org.mr0btain.ticketman.data.ticket.Device
import org.mr0btain.ticketman.data.ticket.Ticket
import org.mr0btain.ticketman.data.user.AccessLevel
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.server.ALL_LOCATIONS
import org.mr0btain.ticketman.server.LOCATION_HEADER
import org.mr0btain.ticketman.server.request.CheckInRequest
import org.mr0btain.ticketman.server.request.InvoiceChangeRequest
import org.mr0btain.ticketman.server.request.LoginRequest
import org.mr0btain.ticketman.server.request.MoveTicketRequest
import org.mr0btain.ticketman.server.request.PaymentRequest
import org.mr0btain.ticketman.server.request.PickupRequest
import org.mr0btain.ticketman.server.request.SaleRequest
import org.mr0btain.ticketman.server.request.StockChangeRequest
import org.mr0btain.ticketman.server.response.AuthResponse
import org.mr0btain.ticketman.server.response.CheckInResponse
import org.mr0btain.ticketman.server.response.CounterQueueResponse
import org.mr0btain.ticketman.server.response.CounterSearchResponse
import java.time.Year
import kotlin.test.*
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.mr0btain.ticketman.components.database.InventoryDatabase
import org.mr0btain.ticketman.components.database.LocationDatabase
import org.mr0btain.ticketman.components.database.TicketDatabase
import org.mr0btain.ticketman.components.database.tables.DataFixTable
import org.mr0btain.ticketman.components.database.tables.inventory.InventoryTable
import org.mr0btain.ticketman.components.database.tables.location.InventoryStockTable
import org.mr0btain.ticketman.components.database.tables.location.LocationTable
import org.mr0btain.ticketman.components.database.tables.ticket.TicketTable

/**
 * More than one location: each numbers, lists and stocks its own, customers and the catalog are shared
 */
class LocationTest {
    private val dataDir = createTestDataDir()
    private val json = Json { ignoreUnknownKeys = true }

    private val admin = User(username = "boss", password = "boss_pass", accessLevel = AccessLevel.ADMIN)
    private val tech = User(username = "tech", password = "tech_pass", accessLevel = AccessLevel.NORMAL)

    private lateinit var token: String
    private lateinit var adminToken: String
    private lateinit var main: Location
    private lateinit var downtown: Location

    private val year = (Year.now().value % 100).toString().padStart(2, '0')

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
        postJson("/api/shop/update", json.encodeToString(ShopProfile(name = "Fix It", phone = "555-0100", taxRate = "5")), adminToken)

        main = locations().single()
        val added = postJson("/api/location/add", json.encodeToString(Location(code = "DT", name = "Downtown", phone = "555-0200", taxRate = "10")), adminToken)
        assertEquals(HttpStatusCode.Created, added.status)
        downtown = json.decodeFromString(added.bodyAsText())
    }

    private suspend fun ApplicationTestBuilder.login(user: User): String {
        val response = client.post("/api/auth") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(LoginRequest(user.username, user.password)))
        }
        return json.decodeFromString<AuthResponse>(response.bodyAsText()).accessToken!!
    }

    private suspend fun ApplicationTestBuilder.postJson(path: String, body: String, as_: String? = null, at: Location? = null) =
        client.post(path) {
            contentType(ContentType.Application.Json)
            bearerAuth(as_ ?: token)
            at?.let { header(LOCATION_HEADER, it.identifier.toString()) }
            setBody(body)
        }

    private suspend fun ApplicationTestBuilder.get(path: String, as_: String? = null, at: String? = null) =
        client.get(path) {
            bearerAuth(as_ ?: token)
            at?.let { header(LOCATION_HEADER, it) }
        }

    private suspend fun ApplicationTestBuilder.locations(): List<Location> =
        json.decodeFromString(get("/api/location").bodyAsText())

    private suspend fun ApplicationTestBuilder.checkIn(at: Location, customer: Customer): CheckInResponse {
        val response = postJson(
            "/api/counter/check-in",
            json.encodeToString(CheckInRequest(Ticket(device = Device("Apple", "iPhone 14"), problem = "Cracked screen"), customer.identifier)),
            at = at
        )
        assertEquals(HttpStatusCode.Created, response.status, response.bodyAsText())
        return json.decodeFromString(response.bodyAsText())
    }

    private suspend fun ApplicationTestBuilder.customer(): Customer {
        val customer = Customer(name = "Ada Lovelace", phone = "5551234567")
        assertEquals(HttpStatusCode.Created, postJson("/api/data/customer/add", json.encodeToString(customer)).status)
        return customer
    }

    private suspend fun ApplicationTestBuilder.item(at: Location, stock: Int): Inventory {
        val item = Inventory(name = "Screen", stock = stock, priceCents = 10_000)
        assertEquals(HttpStatusCode.Created, postJson("/api/data/inventory/add", json.encodeToString(item), at = at).status)
        return item
    }

    private suspend fun ApplicationTestBuilder.stock(item: Inventory, at: Location?): Int =
        json.decodeFromString<Inventory>(get("/api/data/inventory/get/${item.identifier}", at = at?.identifier?.toString() ?: ALL_LOCATIONS, as_ = adminToken).bodyAsText()).stock

    @Test
    fun theMainLocationIsMadeOnStartAndOthersAreChecked() = testApplication {
        setup()

        assertTrue(main.main)
        assertEquals("", main.code)
        assertEquals(listOf(main.identifier, downtown.identifier), locations().map { it.identifier })

        val sameCode = postJson("/api/location/add", json.encodeToString(Location(code = "DT", name = "Uptown")), adminToken)
        assertEquals(HttpStatusCode.BadRequest, sameCode.status)
        assertEquals("Another location already uses DT", sameCode.bodyAsText())
        assertEquals(HttpStatusCode.BadRequest, postJson("/api/location/add", json.encodeToString(Location(name = "Uptown")), adminToken).status)
        assertEquals(HttpStatusCode.BadRequest, postJson("/api/location/add", json.encodeToString(Location(code = "UPTOWN", name = "Uptown")), adminToken).status)
        assertEquals(HttpStatusCode.Forbidden, postJson("/api/location/add", json.encodeToString(Location(code = "UP", name = "Uptown"))).status)

        // Codes come in as capitals; the main one can't be retired
        val up = postJson("/api/location/add", json.encodeToString(Location(code = " up ", name = "Uptown")), adminToken)
        assertEquals("UP", json.decodeFromString<Location>(up.bodyAsText()).code)
        assertEquals(HttpStatusCode.BadRequest, postJson("/api/location/update", json.encodeToString(main.copy(retired = true)), adminToken).status)
    }

    @Test
    fun eachLocationNumbersItsOwnTickets() = testApplication {
        setup()
        val ada = customer()

        val here = checkIn(main, ada)
        val there = checkIn(downtown, ada)
        val again = checkIn(downtown, ada)

        assertEquals("$year-00000001", here.ticket.label)
        assertEquals("DT-$year-00000001", there.ticket.label)
        assertEquals("DT-$year-00000002", again.ticket.label)
        assertEquals(downtown.identifier, there.ticket.locationIdentifier)
        assertEquals(main.identifier, here.ticket.locationIdentifier)

        // Found by number, with or without the code, wherever the search is from
        assertEquals(there.ticket.identifier, json.decodeFromString<Ticket>(get("/api/data/ticket/number/DT-$year-1").bodyAsText()).identifier)
        assertEquals(here.ticket.identifier, json.decodeFromString<Ticket>(get("/api/data/ticket/number/$year-1").bodyAsText()).identifier)
        val found = json.decodeFromString<CounterSearchResponse>(get("/api/counter/search?query=dt-$year-2").bodyAsText())
        assertEquals(listOf(again.ticket.identifier), found.tickets.map { it.ticket.identifier })

        // An invoice from a downtown deposit is numbered there too
        val sale = postJson(
            "/api/counter/sale",
            json.encodeToString(SaleRequest(charges = listOf(Charge(type = ChargeType.OTHER, description = "Case", unitPriceCents = 1_000, taxable = false)), payments = listOf(PaymentRequest(PaymentMethod.CASH, 1_000)))),
            at = downtown
        )
        assertEquals("INV-DT-$year-00000001", json.decodeFromString<Invoice>(sale.bodyAsText()).label)
    }

    @Test
    fun listsKeepToTheLocationTheCallerWorksAt() = testApplication {
        setup()
        val ada = customer()
        val here = checkIn(main, ada).ticket
        val there = checkIn(downtown, ada).ticket

        fun ids(body: String) = json.decodeFromString<List<Ticket>>(body).map { it.identifier }

        assertEquals(listOf(here.identifier), ids(get("/api/data/ticket/fetch").bodyAsText()), "no header is the tech's home, the main one")
        assertEquals(listOf(there.identifier), ids(get("/api/data/ticket/fetch", at = downtown.identifier.toString()).bodyAsText()))
        assertEquals(listOf(there.identifier, here.identifier), ids(get("/api/data/ticket/fetch", adminToken, ALL_LOCATIONS).bodyAsText()))
        assertEquals(listOf(here.identifier), ids(get("/api/data/ticket/fetch", at = ALL_LOCATIONS).bodyAsText()), "only admins see every location")

        val queue = json.decodeFromString<CounterQueueResponse>(get("/api/counter/queue", at = downtown.identifier.toString()).bodyAsText())
        assertEquals(listOf(there.identifier), queue.recentlyCheckedIn.map { it.ticket.identifier })

        // The customer's tickets are everywhere
        val search = json.decodeFromString<CounterSearchResponse>(get("/api/counter/search?query=5551234567", at = downtown.identifier.toString()).bodyAsText())
        assertEquals(setOf(here.identifier, there.identifier), search.tickets.map { it.ticket.identifier }.toSet())

        // A retired location can't be worked at, it falls back to home
        postJson("/api/location/update", json.encodeToString(downtown.copy(retired = true)), adminToken)
        assertEquals(listOf(here.identifier), ids(get("/api/data/ticket/fetch", at = downtown.identifier.toString()).bodyAsText()))
    }

    @Test
    fun stockIsCountedPerLocation() = testApplication {
        setup()
        val screen = item(main, 5)

        assertEquals(5, stock(screen, main))
        assertEquals(0, stock(screen, downtown))
        assertEquals(5, stock(screen, null))

        // Counted at downtown by hand, from none
        val set = postJson("/api/data/inventory/set/stock/${screen.identifier}", json.encodeToString(StockChangeRequest(0, 3, downtown.identifier)))
        assertEquals(HttpStatusCode.Accepted, set.status)
        assertEquals(3, stock(screen, downtown))
        assertEquals(8, stock(screen, null))

        // A downtown sale takes downtown's, and can't take more than it has
        val line = Charge(type = ChargeType.PART, description = "Screen", unitPriceCents = 10_000, inventoryIdentifier = screen.identifier, taxable = false)
        val sale = SaleRequest(charges = listOf(line.copy(quantity = 4.0)), payments = listOf(PaymentRequest(PaymentMethod.CASH, 40_000)))
        val short = postJson("/api/counter/sale", json.encodeToString(sale), at = downtown)
        assertEquals(HttpStatusCode.Conflict, short.status)
        assertContains(short.bodyAsText(), "Only 3 of Screen in stock")
        assertEquals(HttpStatusCode.Created, postJson("/api/counter/sale", json.encodeToString(sale.copy(charges = listOf(line.copy(quantity = 2.0)), payments = listOf(PaymentRequest(PaymentMethod.CASH, 20_000)))), at = downtown).status)
        assertEquals(1, stock(screen, downtown))
        assertEquals(5, stock(screen, main))

        val read = json.decodeFromString<Inventory>(get("/api/data/inventory/get/${screen.identifier}", at = downtown.identifier.toString()).bodyAsText())
        assertEquals(mapOf(main.identifier to 5, downtown.identifier to 1), read.stockAt.associate { it.locationIdentifier to it.stock })
    }

    @Test
    fun aTicketIsTaxedAndPickedUpWhereItIs() = testApplication {
        setup()
        val ada = customer()
        val screen = item(main, 5)
        val ticket = checkIn(downtown, ada).ticket

        // Downtown's own 10%, not the shop's 5%
        val invoice = json.decodeFromString<Invoice>(postJson("/api/invoice/ticket/${ticket.identifier}", "", at = downtown).bodyAsText())
        assertEquals(1_000, invoice.taxRateBasisPoints)
        assertEquals(downtown.identifier, invoice.locationIdentifier)

        // Moved to the main location for the repair: its open invoice goes too, and pickup takes main's stock
        val moved = postJson("/api/data/ticket/move/${ticket.identifier}", json.encodeToString(MoveTicketRequest(main.identifier)))
        assertEquals(HttpStatusCode.OK, moved.status)
        assertEquals(main.identifier, json.decodeFromString<Ticket>(moved.bodyAsText()).locationIdentifier)
        assertEquals("DT-$year-00000001", json.decodeFromString<Ticket>(moved.bodyAsText()).label, "it keeps its number")

        val part = Charge(type = ChargeType.PART, description = "Screen", unitPriceCents = 10_000, inventoryIdentifier = screen.identifier, taxable = false)
        val updated = json.decodeFromString<Invoice>(postJson("/api/invoice/${invoice.identifier}/update", json.encodeToString(InvoiceChangeRequest(added = listOf(part)))).bodyAsText())
        assertEquals(main.identifier, updated.locationIdentifier)
        val picked = postJson("/api/counter/pickup", json.encodeToString(PickupRequest(ticket.identifier, listOf(PaymentRequest(PaymentMethod.CASH, updated.totalCents)))))
        assertEquals(HttpStatusCode.OK, picked.status, picked.bodyAsText())
        assertEquals(4, stock(screen, main))

        assertEquals(HttpStatusCode.BadRequest, postJson("/api/data/ticket/move/${ticket.identifier}", json.encodeToString(MoveTicketRequest(kotlin.uuid.Uuid.random()))).status)
    }

    @Test
    fun theCounterPrintsWithItsLocationsDetails() = testApplication {
        setup()

        val here = json.decodeFromString<ShopProfile>(get("/api/shop/here", at = downtown.identifier.toString()).bodyAsText())
        assertEquals("Fix It, Downtown", here.name)
        assertEquals("555-0200", here.phone)
        assertEquals("10", here.taxRate)

        // The profile itself stays the company's, for admins to edit
        assertEquals("555-0100", json.decodeFromString<ShopProfile>(get("/api/shop", at = downtown.identifier.toString()).bodyAsText()).phone)
    }

    @Test
    fun staffHaveAHomeLocation() = testApplication {
        setup()

        val nina = User(username = "nina", password = "nina_pass_123", name = "Nina", accessLevel = AccessLevel.NORMAL)
        assertEquals(HttpStatusCode.Created, postJson("/api/user/add", json.encodeToString(nina), adminToken, at = downtown).status)
        val ninaToken = login(nina)

        // Made by an admin working downtown, so that's her home: her lists start there without a header
        val customer = customer()
        val ticket = checkIn(downtown, customer).ticket
        assertEquals(listOf(ticket.identifier), json.decodeFromString<List<Ticket>>(get("/api/data/ticket/fetch", ninaToken).bodyAsText()).map { it.identifier })

        val users = json.decodeFromString<List<User>>(get("/api/user/fetch", adminToken, downtown.identifier.toString()).bodyAsText())
        assertEquals(listOf("nina"), users.map { it.username })

        // An older client's update, without a home, keeps hers
        val saved = UserDatabase().getByUsername("nina")!!
        assertEquals(HttpStatusCode.Accepted, postJson("/api/user/update", json.encodeToString(saved.withoutPassword().copy(homeLocation = null, name = "Nina T")), adminToken).status)
        assertEquals(downtown.identifier, UserDatabase().getByUsername("nina")!!.homeLocation)
    }

    /** A database from before locations: its counts move to the main location, its tickets and invoices belong there */
    @Test
    fun olderDatabasesMoveToTheMainLocation() {
        val screen = Inventory(name = "Screen", stock = 5, priceCents = 10_000, reorderPoint = 2)
        val cable = Inventory(name = "Cable")
        val ticket = Ticket(device = Device("Apple", "iPhone 14"), problem = "Cracked")

        // As an older server left it: counts on the items, no locations on the tickets, the fix not yet run
        testApplication {
            configureTestServer(dataDir)
            startApplication()
            InventoryDatabase().apply {
                add(screen)
                add(cable)
            }
            TicketDatabase().add(ticket)
            transaction {
                InventoryStockTable.deleteAll()
                InventoryTable.update(where = { InventoryTable.id eq screen.identifier }) { row ->
                    row[InventoryTable.stock] = 5
                    row[InventoryTable.reorderPoint] = 2
                }
                TicketTable.update { row -> row[TicketTable.location] = null }
                LocationTable.deleteAll()
                DataFixTable.deleteWhere { DataFixTable.name eq "stock-per-location" }
            }
            // Like the server stopping. Committing after it fails, it's closed either way
            runCatching { transaction { exec("SHUTDOWN") } }
        }

        testApplication {
            configureTestServer(dataDir)
            startApplication()
            val main = LocationDatabase().main()

            val moved = InventoryDatabase().getById(screen.identifier, main.identifier)!!
            assertEquals(5, moved.stock)
            assertEquals(2, moved.reorderPoint)
            assertEquals(Int.MAX_VALUE, InventoryDatabase().getById(cable.identifier, main.identifier)!!.stock)
            assertEquals(main.identifier, TicketDatabase().getById(ticket.identifier)!!.locationIdentifier)
        }
    }
}
