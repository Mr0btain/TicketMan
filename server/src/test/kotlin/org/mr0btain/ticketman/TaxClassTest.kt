package org.mr0btain.ticketman

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.mr0btain.ticketman.components.database.UserDatabase
import org.mr0btain.ticketman.data.estimate.Estimate
import org.mr0btain.ticketman.data.inventory.Inventory
import org.mr0btain.ticketman.data.invoice.*
import org.mr0btain.ticketman.data.shop.ShopProfile
import org.mr0btain.ticketman.data.ticket.Ticket
import org.mr0btain.ticketman.data.ticket.WorkType
import org.mr0btain.ticketman.data.user.AccessLevel
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.server.request.*
import org.mr0btain.ticketman.server.response.AuthResponse
import kotlin.test.*

/**
 * More than one tax rate: the shop's classes, lines and items that pick one, and invoices keeping the rates they
 * were made with
 */
class TaxClassTest {
    private val dataDir = createTestDataDir()
    private val json = Json { ignoreUnknownKeys = true }

    private val admin = User(username = "boss", password = "boss_pass", accessLevel = AccessLevel.ADMIN)
    private val tech = User(username = "tech", password = "tech_pass", accessLevel = AccessLevel.NORMAL)

    private lateinit var token: String
    private lateinit var adminToken: String

    private val food = TaxClass("food", "Food", "2")
    private val luxury = TaxClass("lux", "Luxury", "20")

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
        assertEquals(HttpStatusCode.Accepted, saveShop(ShopProfile(taxRate = "10", taxClasses = listOf(food, luxury))).status)
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

    private suspend fun ApplicationTestBuilder.saveShop(profile: ShopProfile) = postJson("/api/shop/update", json.encodeToString(profile), adminToken)

    private fun line(description: String, cents: Long, taxClass: String? = null) = Charge(description = description, unitPriceCents = cents, taxClass = taxClass)

    @Test
    fun eachRateTaxesItsOwnLinesAndSharesTheDiscount() {
        val rates = listOf(TaxRate("food", "Food", 200), TaxRate("lux", "Luxury", 2000))
        val charges = listOf(line("Repair", 6_000), line("Snack", 2_000, "food"), line("Case", 2_000, "lux"), line("Fee", 1_000).copy(taxable = false))

        // 10000 taxable shared 60/20/20, so a 1000 discount is 600/200/200
        val totals = InvoiceMath.totals(charges, discountCents = 1_000, taxRateBasisPoints = 1_000, taxRates = rates)
        assertEquals(listOf("Tax", "Food", "Luxury"), totals.taxLines.map { it.name })
        assertEquals(listOf(5_400L, 1_800L, 1_800L), totals.taxLines.map { it.taxableCents })
        assertEquals(listOf(540L, 36L, 360L), totals.taxLines.map { it.taxCents })
        assertEquals(936, totals.taxCents)
        assertEquals(11_000 - 1_000 + 936, totals.totalCents)

        // A class that isn't there is the main rate
        assertEquals(listOf("Tax"), InvoiceMath.totals(listOf(line("Odd", 1_000, "gone")), 0, 1_000, rates).taxLines.map { it.name })

        // Rounding leaves the shares adding up to the discount
        val odd = InvoiceMath.totals(listOf(line("A", 333), line("B", 333, "food"), line("C", 334, "lux")), 100, 1_000, rates)
        assertEquals(900, odd.taxLines.sumOf { it.taxableCents })
    }

    @Test
    fun theShopsClassesAreChecked() = testApplication {
        setup()

        assertEquals(HttpStatusCode.BadRequest, saveShop(ShopProfile(taxClasses = listOf(food.copy(name = " ")))).status)
        assertEquals(HttpStatusCode.BadRequest, saveShop(ShopProfile(taxClasses = listOf(food.copy(rate = "lots")))).status)
        assertEquals(HttpStatusCode.BadRequest, saveShop(ShopProfile(taxClasses = listOf(food, food.copy(name = "Food again")))).status)
    }

    @Test
    fun aSaleChargesEachClassAndKeepsTheRates() = testApplication {
        setup()
        val snack = Inventory(name = "Snack", priceCents = 2_000, taxClass = "food")
        postJson("/api/data/inventory/add", json.encodeToString(snack))

        val charges = listOf(line("Repair", 6_000), Charge(type = ChargeType.PART, description = "Snack", unitPriceCents = 2_000, inventoryIdentifier = snack.identifier, taxClass = "food"))
        val response = postJson("/api/counter/sale", json.encodeToString(SaleRequest(charges = charges, payments = listOf(PaymentRequest(PaymentMethod.CASH, 10_000)))))
        assertEquals(HttpStatusCode.Created, response.status, response.bodyAsText())

        val invoice = json.decodeFromString<Invoice>(response.bodyAsText())
        assertEquals(600 + 40, invoice.taxCents)
        assertEquals(listOf("food", "lux"), invoice.taxRates.map { it.identifier })
        assertEquals("food", invoice.charges.last().taxClass)

        // A class the shop doesn't have is refused
        val unknown = postJson("/api/counter/sale", json.encodeToString(SaleRequest(charges = listOf(line("Odd", 1_000, "nope")), payments = listOf(PaymentRequest(PaymentMethod.CASH, 2_000)))))
        assertEquals(HttpStatusCode.BadRequest, unknown.status)
        assertEquals("\"Odd\" has a tax class the shop doesn't have any more, pick another", unknown.bodyAsText())
    }

    @Test
    fun anOpenInvoiceKeepsItsRatesAndPicksUpNewClasses() = testApplication {
        setup()
        val ticket = json.decodeFromString<Ticket>(postJson("/api/data/ticket/add", json.encodeToString(Ticket())).bodyAsText())
        val invoice = json.decodeFromString<Invoice>(postJson("/api/invoice/ticket/${ticket.identifier}", "").bodyAsText())

        // The food rate goes up and a new class comes in after the invoice was made
        val drinks = TaxClass("drink", "Drinks", "5")
        saveShop(ShopProfile(taxRate = "10", taxClasses = listOf(food.copy(rate = "4"), luxury, drinks)))

        val lines = listOf(line("Snack", 1_000, "food"), line("Soda", 1_000, "drink"))
        val response = postJson("/api/invoice/${invoice.identifier}/update", json.encodeToString(InvoiceChanges.between(invoice.charges, 0, lines, 0)))
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())

        val saved = json.decodeFromString<Invoice>(response.bodyAsText())
        assertEquals(200, saved.taxRates.first { it.identifier == "food" }.basisPoints, "kept from when it was made")
        assertEquals(500, saved.taxRates.first { it.identifier == "drink" }.basisPoints, "added when first used")
        assertEquals(20 + 50, saved.taxCents)
    }

    @Test
    fun workAndEstimatesUseTheClasses() = testApplication {
        setup()
        val case = Inventory(name = "Fancy case", priceCents = 5_000, taxClass = "lux")
        postJson("/api/data/inventory/add", json.encodeToString(case))
        val ticket = json.decodeFromString<Ticket>(postJson("/api/data/ticket/add", json.encodeToString(Ticket())).bodyAsText())

        val logged = postJson("/api/data/ticket/add/work/${ticket.identifier}", json.encodeToString(WorkRequest(WorkType.PART, quantity = 1.0, inventoryIdentifier = case.identifier)))
        assertEquals(HttpStatusCode.Created, logged.status, logged.bodyAsText())
        val invoice = json.decodeFromString<Invoice>(client.get("/api/invoice/ticket/${ticket.identifier}") { bearerAuth(token) }.bodyAsText())
        assertEquals("lux", invoice.charges.single().taxClass)
        assertEquals(1_000, invoice.taxCents)

        val sent = postJson("/api/estimate/ticket/${ticket.identifier}", json.encodeToString(EstimateRequest(lines = listOf(line("Snack", 1_000, "food")))))
        assertEquals(HttpStatusCode.Created, sent.status, sent.bodyAsText())
        val estimate = json.decodeFromString<Estimate>(sent.bodyAsText())
        assertEquals(1_020, estimate.totalCents)
        assertEquals(listOf("food", "lux"), estimate.taxRates.map { it.identifier })
    }
}
