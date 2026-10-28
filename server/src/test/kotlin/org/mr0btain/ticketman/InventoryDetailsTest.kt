package org.mr0btain.ticketman

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.mr0btain.ticketman.components.database.UserDatabase
import org.mr0btain.ticketman.data.inventory.Inventory
import org.mr0btain.ticketman.data.search.TmSearchResult
import org.mr0btain.ticketman.data.user.AccessLevel
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.server.request.LoginRequest
import org.mr0btain.ticketman.server.response.AdminStatsResponse
import org.mr0btain.ticketman.server.response.AuthResponse
import org.mr0btain.ticketman.server.response.ImportOutcome
import org.mr0btain.ticketman.server.response.ImportResult
import kotlin.test.*

/**
 * SKUs, barcodes, categories and reorder points on inventory items
 */
class InventoryDetailsTest {
    private val dataDir = createTestDataDir()
    private val json = Json { ignoreUnknownKeys = true }

    private val admin = User(username = "boss", password = "boss_pass", accessLevel = AccessLevel.ADMIN)
    private val clerk = User(username = "clerk", password = "clerk_pass", accessLevel = AccessLevel.RESTRICTED)

    private lateinit var token: String
    private lateinit var clerkToken: String

    @AfterTest
    fun cleanup() = cleanupTestDataDir(dataDir)

    private suspend fun ApplicationTestBuilder.setup() {
        configureTestServer(dataDir)
        startApplication()
        UserDatabase().apply {
            add(admin)
            add(clerk)
        }
        token = login(admin)
        clerkToken = login(clerk)
    }

    private suspend fun ApplicationTestBuilder.login(user: User): String {
        val response = client.post("/api/auth") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(LoginRequest(user.username, user.password)))
        }

        return json.decodeFromString<AuthResponse>(response.bodyAsText()).accessToken!!
    }

    private suspend fun ApplicationTestBuilder.postJson(path: String, body: String) =
        client.post(path) {
            contentType(ContentType.Application.Json)
            bearerAuth(token)
            setBody(body)
        }

    private suspend fun ApplicationTestBuilder.add(item: Inventory) = postJson("/api/data/inventory/add", json.encodeToString(item))

    private suspend fun ApplicationTestBuilder.get(path: String, as_: String = clerkToken) = client.get(path) { bearerAuth(as_) }

    @Test
    fun codesAreSavedAndUnique() = testApplication {
        setup()
        val screen = Inventory(name = "iPhone 13 screen", sku = " SCR-13 ", barcode = "0194252707", category = "Screens", reorderPoint = 3, stock = 10)
        val added = add(screen)
        assertEquals(HttpStatusCode.Created, added.status)
        val saved = json.decodeFromString<Inventory>(added.bodyAsText())
        assertEquals("SCR-13", saved.sku)
        assertEquals("0194252707", saved.barcode)
        assertEquals("Screens", saved.category)
        assertEquals(3, saved.reorderPoint)

        // Another item can't share them, whatever the case
        val clash = add(Inventory(name = "Other", sku = "scr-13"))
        assertEquals(HttpStatusCode.Conflict, clash.status)
        assertEquals("SKU scr-13 is already on iPhone 13 screen", clash.bodyAsText())
        assertEquals(HttpStatusCode.Conflict, add(Inventory(name = "Other", barcode = "0194252707")).status)

        // The item itself can be saved with its own
        assertEquals(HttpStatusCode.Accepted, postJson("/api/data/inventory/update", json.encodeToString(saved.copy(name = "iPhone 13 screen, OEM"))).status)

        assertEquals(HttpStatusCode.BadRequest, add(Inventory(name = "Spaced", barcode = "019 425")).status)
        assertEquals(HttpStatusCode.BadRequest, add(Inventory(name = "Negative", reorderPoint = -1)).status)
    }

    @Test
    fun aScannerFindsItemsByCode() = testApplication {
        setup()
        add(Inventory(name = "Battery", sku = "BAT-1", barcode = "885909950805", category = "Batteries"))
        add(Inventory(name = "Cable", sku = "CBL-1", category = "cables"))
        add(Inventory(name = "Case"))

        assertEquals("Battery", json.decodeFromString<Inventory>(get("/api/data/inventory/code/885909950805").bodyAsText()).name)
        assertEquals("Cable", json.decodeFromString<Inventory>(get("/api/data/inventory/code/cbl-1").bodyAsText()).name)
        assertEquals(HttpStatusCode.NotFound, get("/api/data/inventory/code/nothing").status)

        // Search takes the start of a code, and any part of a category
        val bySku = json.decodeFromString<List<TmSearchResult>>(get("/api/data/inventory/search?query=bat-&limit=10").bodyAsText())
        assertEquals(listOf("Battery"), bySku.map { it.text })
        val byCategory = json.decodeFromString<List<TmSearchResult>>(get("/api/data/inventory/search?query=able&limit=10").bodyAsText())
        assertEquals(listOf("Cable"), byCategory.map { it.text })

        assertEquals(listOf("Batteries", "cables"), json.decodeFromString<List<String>>(get("/api/data/inventory/categories").bodyAsText()))
    }

    @Test
    fun lowStockUsesEachItemsReorderPoint() = testApplication {
        setup()
        add(Inventory(name = "A over its own point", stock = 8, reorderPoint = 10))
        add(Inventory(name = "B at the threshold", stock = 5))
        add(Inventory(name = "C below the threshold but not its point", stock = 4, reorderPoint = 2))
        add(Inventory(name = "D untracked"))
        add(Inventory(name = "E fine", stock = 50, reorderPoint = 10))

        val low = json.decodeFromString<List<Inventory>>(get("/api/admin/inventory/low-stock?threshold=5", token).bodyAsText())
        assertEquals(setOf("A over its own point", "B at the threshold"), low.map { it.name }.toSet())
        assertTrue(low.all { it.isLow(5) })

        val stats = json.decodeFromString<AdminStatsResponse>(get("/api/admin/stats?lowStock=5", token).bodyAsText())
        assertEquals(2, stats.lowStockCount)
    }

    @Test
    fun anImportBringsTheCodesAndChecksThem() = testApplication {
        setup()
        add(Inventory(name = "Existing", sku = "TAKEN"))

        val items = listOf(
            Inventory(name = "One", sku = "A1", barcode = "111", category = "Parts", reorderPoint = 2),
            Inventory(name = "Two", sku = "a1"),
            Inventory(name = "Three", sku = "taken")
        )
        val response = client.post("/api/admin/import/inventory?dryRun=false") {
            contentType(ContentType.Application.Json)
            bearerAuth(token)
            setBody(json.encodeToString(items))
        }
        val result = json.decodeFromString<ImportResult>(response.bodyAsText())

        assertEquals(listOf(ImportOutcome.ADD, ImportOutcome.ERROR, ImportOutcome.ERROR), result.rows.map { it.outcome })
        assertEquals("Same SKU or barcode as row 1", result.rows[1].message)
        assertEquals("SKU taken is already on Existing", result.rows[2].message)
        assertEquals("A1", json.decodeFromString<Inventory>(get("/api/data/inventory/code/111").bodyAsText()).sku)
    }
}
