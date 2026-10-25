package org.mr0btain.ticketman

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.mr0btain.ticketman.components.database.InventoryDatabase
import org.mr0btain.ticketman.components.database.UserDatabase
import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.data.inventory.Inventory
import org.mr0btain.ticketman.data.invoice.*
import org.mr0btain.ticketman.data.shop.ShopProfile
import org.mr0btain.ticketman.data.user.AccessLevel
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.server.request.LoginRequest
import org.mr0btain.ticketman.server.request.PaymentRequest
import org.mr0btain.ticketman.server.request.SaleRequest
import org.mr0btain.ticketman.server.response.AuthResponse
import java.time.LocalDate
import kotlin.test.*

/**
 * Selling from stock at the counter, with no ticket
 */
class SaleTest {
    private val dataDir = createTestDataDir()
    private val json = Json { ignoreUnknownKeys = true }

    private val admin = User(username = "boss", password = "boss_pass", accessLevel = AccessLevel.ADMIN)
    private val tech = User(username = "tech", password = "tech_pass", accessLevel = AccessLevel.NORMAL)
    private val clerk = User(username = "clerk", password = "clerk_pass", accessLevel = AccessLevel.RESTRICTED)

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
            add(clerk)
        }
        token = login(tech)
        adminToken = login(admin)
        postJson("/api/shop/update", json.encodeToString(ShopProfile(taxRate = "10")), adminToken)
    }

    private suspend fun ApplicationTestBuilder.login(user: User): String {
        val response = client.post("/api/auth") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(LoginRequest(user.username, user.password)))
        }

        return json.decodeFromString<AuthResponse>(response.bodyAsText()).accessToken!!
    }

    private suspend fun ApplicationTestBuilder.postJson(path: String, body: String, as_: String? = null) =
        client.post(path) {
            contentType(ContentType.Application.Json)
            bearerAuth(as_ ?: token)
            setBody(body)
        }

    private suspend fun ApplicationTestBuilder.item(name: String, stock: Int, priceCents: Long): Inventory {
        val item = Inventory(name = name, stock = stock, priceCents = priceCents)
        assertEquals(HttpStatusCode.Created, postJson("/api/data/inventory/add", json.encodeToString(item)).status)
        return item
    }

    private fun part(item: Inventory, quantity: Double = 1.0) =
        Charge(type = ChargeType.PART, description = item.name, quantity = quantity, unitPriceCents = item.priceCents, inventoryIdentifier = item.identifier)

    private suspend fun ApplicationTestBuilder.sell(request: SaleRequest, as_: String? = null) =
        postJson("/api/counter/sale", json.encodeToString(request), as_)

    @Test
    fun aSaleIsPaidAndTakesItsPartsFromStock() = testApplication {
        setup()
        val cable = item("Cable", stock = 5, priceCents = 1_000)
        val customer = Customer(name = "Sam")
        postJson("/api/data/customer/add", json.encodeToString(customer))

        // Two cables and a fee: 2000 taxed at 10% plus 500 untaxed
        val response = sell(
            SaleRequest(
                customerIdentifier = customer.identifier,
                charges = listOf(part(cable, 2.0), Charge(description = "Fee", unitPriceCents = 500, taxable = false)),
                payments = listOf(PaymentRequest(PaymentMethod.CASH, 3_000))
            )
        )
        assertEquals(HttpStatusCode.Created, response.status)

        val invoice = json.decodeFromString<Invoice>(response.bodyAsText())
        assertTrue(invoice.isSale)
        assertNull(invoice.ticketIdentifier)
        assertEquals(customer.identifier, invoice.customerIdentifier)
        assertEquals(InvoiceStatus.PAID, invoice.status)
        assertNotNull(invoice.number)
        assertNotNull(invoice.paidAt)
        assertEquals(2_700, invoice.totalCents)
        // Change was due
        assertEquals(-300, invoice.balanceCents)
        assertEquals(3, InventoryDatabase().getById(cable.identifier)!!.stock)

        // Listed with the rest, and numbered after them
        val listed = json.decodeFromString<List<Invoice>>(client.get("/api/invoice/fetch") { bearerAuth(token) }.bodyAsText())
        assertEquals(listOf(invoice.identifier), listed.map { it.identifier })
    }

    @Test
    fun aSaleHasToBeCoveredAndInStock() = testApplication {
        setup()
        val screen = item("Screen", stock = 1, priceCents = 8_000)

        // 8800 with tax, only 8000 given
        val short = sell(SaleRequest(charges = listOf(part(screen)), payments = listOf(PaymentRequest(PaymentMethod.CARD, 8_000))))
        assertEquals(HttpStatusCode.Conflict, short.status)
        assertEquals("$8.00 is still owed", short.bodyAsText())

        val tooMany = sell(SaleRequest(charges = listOf(part(screen, 2.0)), payments = listOf(PaymentRequest(PaymentMethod.CARD, 20_000))))
        assertEquals(HttpStatusCode.Conflict, tooMany.status)
        assertEquals("Only 1 of Screen in stock, fix the count in Inventory first", tooMany.bodyAsText())

        assertEquals(HttpStatusCode.BadRequest, sell(SaleRequest(charges = emptyList())).status)
        assertEquals(
            HttpStatusCode.BadRequest,
            sell(SaleRequest(customerIdentifier = Customer().identifier, charges = listOf(part(screen)), payments = listOf(PaymentRequest(PaymentMethod.CARD, 8_800)))).status
        )

        // Nothing was made by the refused ones
        assertEquals(1, InventoryDatabase().getById(screen.identifier)!!.stock)
        assertEquals("[]", client.get("/api/invoice/fetch") { bearerAuth(token) }.bodyAsText())

        // A free sale needs no payment
        assertEquals(HttpStatusCode.Created, sell(SaleRequest(charges = listOf(Charge(description = "Advice", unitPriceCents = 0)))).status)
    }

    @Test
    fun restrictedUsersCantSell() = testApplication {
        setup()
        val clerkToken = login(clerk)

        val response = sell(SaleRequest(charges = listOf(Charge(description = "Fee", unitPriceCents = 100)), payments = listOf(PaymentRequest(PaymentMethod.CASH, 200))), clerkToken)
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun voidingASalePutsItsPartsBackAndItShowsInTheExport() = testApplication {
        setup()
        val cable = item("Cable", stock = 5, priceCents = 1_000)
        val invoice = json.decodeFromString<Invoice>(
            sell(SaleRequest(charges = listOf(part(cable)), payments = listOf(PaymentRequest(PaymentMethod.CASH, 1_100)))).bodyAsText()
        )
        assertEquals(4, InventoryDatabase().getById(cable.identifier)!!.stock)

        val today = LocalDate.now()
        val csv = client.get("/api/invoice/export?from=$today&to=$today") { bearerAuth(adminToken) }.bodyAsText()
        assertContains(csv, "${invoice.label},$today,paid,$today,Sale,,10.00,0.00,1.00,11.00,11.00,0.00,0.00")

        val pdf = client.get("/api/invoice/${invoice.identifier}/pdf") { bearerAuth(token) }
        assertEquals(HttpStatusCode.OK, pdf.status)

        assertEquals(HttpStatusCode.OK, postJson("/api/invoice/${invoice.identifier}/void", "", adminToken).status)
        assertEquals(5, InventoryDatabase().getById(cable.identifier)!!.stock)
    }
}
