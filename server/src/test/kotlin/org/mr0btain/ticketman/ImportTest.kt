package org.mr0btain.ticketman

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.mr0btain.ticketman.components.database.CustomerDatabase
import org.mr0btain.ticketman.components.database.InventoryDatabase
import org.mr0btain.ticketman.components.database.UserDatabase
import org.mr0btain.ticketman.data.audit.AuditEntry
import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.data.inventory.Inventory
import org.mr0btain.ticketman.data.shared.PagedRequest
import org.mr0btain.ticketman.data.user.AccessLevel
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.server.request.LoginRequest
import org.mr0btain.ticketman.server.response.AuthResponse
import org.mr0btain.ticketman.server.response.ImportOutcome
import org.mr0btain.ticketman.server.response.ImportResult
import kotlin.test.*

/**
 * Importing customers and inventory: matches skipped, problems reported, the rest added together
 */
class ImportTest {
    private val dataDir = createTestDataDir()
    private val json = Json { ignoreUnknownKeys = true }

    private val admin = User(username = "boss", password = "boss_pass", accessLevel = AccessLevel.ADMIN)
    private val tech = User(username = "tech", password = "tech_pass", accessLevel = AccessLevel.NORMAL)

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
        adminToken = login(admin)
        CustomerDatabase().add(Customer(name = "Jane Doe", phone = "(555) 014-2000", email = "Jane@Example.com"))
        InventoryDatabase().add(Inventory(name = "iPhone 14 Screen", priceCents = 12_000))
    }

    private suspend fun ApplicationTestBuilder.login(user: User): String =
        json.decodeFromString<AuthResponse>(
            client.post("/api/auth") {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(LoginRequest(user.username, user.password)))
            }.bodyAsText()
        ).accessToken!!

    private suspend fun ApplicationTestBuilder.import(path: String, body: String, dryRun: Boolean, token: String = adminToken) =
        client.post("$path${if (dryRun) "?dryRun=true" else ""}") {
            contentType(ContentType.Application.Json)
            bearerAuth(token)
            setBody(body)
        }

    private val customers = listOf(
        Customer(name = "Bob Smith", phone = "555-0199"),
        Customer(name = "Jane D", phone = "5550142000"),
        Customer(name = "Janet", email = " jane@example.com "),
        Customer(name = "", phone = "5550111"),
        Customer(name = "Bobby", phone = "(555) 0199"),
        Customer(name = "Ann", email = "not an email"),
        Customer(name = "Cara Lee", email = "cara@example.com")
    )

    @Test
    fun aDryRunSaysWhatWouldHappenAndAddsNothing() = testApplication {
        setup()

        val response = import("/api/admin/import/customers", json.encodeToString(customers), dryRun = true)
        assertEquals(HttpStatusCode.OK, response.status)
        val result = json.decodeFromString<ImportResult>(response.bodyAsText())

        assertTrue(result.dryRun)
        assertEquals(0, result.added)
        assertEquals(
            listOf(ImportOutcome.ADD, ImportOutcome.MATCH, ImportOutcome.MATCH, ImportOutcome.ERROR, ImportOutcome.MATCH, ImportOutcome.ERROR, ImportOutcome.ADD),
            result.rows.map { it.outcome }
        )
        assertEquals("Jane Doe has that phone number", result.rows[1].message)
        assertEquals("Jane Doe has that email", result.rows[2].message)
        assertEquals("Same phone number as row 1", result.rows[4].message)
        assertEquals(1, CustomerDatabase().getPagedItems(PagedRequest(0, 50))!!.size)
    }

    @Test
    fun theImportAddsTheNewOnesAndIsAudited() = testApplication {
        setup()

        val result = json.decodeFromString<ImportResult>(import("/api/admin/import/customers", json.encodeToString(customers), dryRun = false).bodyAsText())

        assertFalse(result.dryRun)
        assertEquals(2, result.added)
        val names = CustomerDatabase().getPagedItems(PagedRequest(0, 50))!!.map { it.name }.toSet()
        assertEquals(setOf("Jane Doe", "Bob Smith", "Cara Lee"), names)

        val audit = json.decodeFromString<List<AuditEntry>>(client.get("/api/admin/audit/fetch?offset=0&limit=20") { bearerAuth(adminToken) }.bodyAsText())
        assertTrue(audit.any { it.entityType == "customers" && it.details == "Imported 2 customers, skipped 3 already there and 2 with problems" })

        // Running it again adds nothing, every row is there now
        val again = json.decodeFromString<ImportResult>(import("/api/admin/import/customers", json.encodeToString(customers), dryRun = false).bodyAsText())
        assertEquals(0, again.added)
    }

    @Test
    fun inventoryMatchesByNameIgnoringCase() = testApplication {
        setup()
        val items = listOf(
            Inventory(name = "IPHONE 14 screen", priceCents = 11_000),
            Inventory(name = "USB-C Cable", priceCents = 1_200, stock = 10),
            Inventory(name = "usb-c cable", priceCents = 1_300),
            Inventory(name = "Pixel 8 Battery", priceCents = -1),
            Inventory(name = "Pixel 8 Screen", stock = -2)
        )

        val result = json.decodeFromString<ImportResult>(import("/api/admin/import/inventory", json.encodeToString(items), dryRun = false).bodyAsText())

        assertEquals(listOf(ImportOutcome.MATCH, ImportOutcome.ADD, ImportOutcome.MATCH, ImportOutcome.ERROR, ImportOutcome.ERROR), result.rows.map { it.outcome })
        assertEquals(1, result.added)
        val cable = InventoryDatabase().getPagedItems(PagedRequest(0, 50))!!.single { it.name == "USB-C Cable" }
        assertEquals(10, cable.stock)
        assertEquals(1_200, cable.priceCents)
    }

    @Test
    fun onlyAdminsImport() = testApplication {
        setup()

        assertEquals(HttpStatusCode.Forbidden, import("/api/admin/import/customers", "[]", dryRun = true, token = login(tech)).status)
    }
}
