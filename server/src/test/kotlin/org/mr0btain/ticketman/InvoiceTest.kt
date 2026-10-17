package org.mr0btain.ticketman

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.mr0btain.ticketman.components.database.InventoryDatabaseImpl
import org.mr0btain.ticketman.components.database.LocalDatabase
import org.mr0btain.ticketman.components.database.TicketDatabaseImpl
import org.mr0btain.ticketman.components.database.UserDatabaseImpl
import org.mr0btain.ticketman.components.database.tables.inventory.InventoryTable
import org.mr0btain.ticketman.data.inventory.AccessLevel
import org.mr0btain.ticketman.data.inventory.Inventory
import org.mr0btain.ticketman.data.invoice.*
import org.mr0btain.ticketman.data.money.Money
import org.mr0btain.ticketman.data.shop.ShopProfile
import org.mr0btain.ticketman.data.ticket.Ticket
import org.mr0btain.ticketman.data.ticket.TicketStatusChange
import org.mr0btain.ticketman.data.ticket.TicketStatusType
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.server.request.*
import org.mr0btain.ticketman.server.response.AuthResponse
import java.time.Year
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.test.*

/**
 * Money, invoices, payments and pickup
 */
class InvoiceTest {
    private val dataDir = createTestDataDir()
    private val json = Json { ignoreUnknownKeys = true }

    private val admin = User(username = "boss", password = "boss_pass", accessLevel = AccessLevel.ADMIN)
    private val tech = User(username = "tech", password = "tech_pass", accessLevel = AccessLevel.NORMAL)
    private val clerk = User(username = "clerk", password = "clerk_pass", accessLevel = AccessLevel.RESTRICTED)

    private lateinit var token: String
    private lateinit var adminToken: String

    @AfterTest
    fun cleanup() = cleanupTestDataDir(dataDir)

    private suspend fun ApplicationTestBuilder.setup(taxRate: String = "") {
        configureTestServer(dataDir)
        startApplication()
        UserDatabaseImpl().apply {
            add(admin)
            add(tech)
            add(clerk)
        }
        token = login(tech)
        adminToken = login(admin)

        if (taxRate.isNotEmpty()) {
            postJson("/api/shop/update", json.encodeToString(ShopProfile(taxRate = taxRate)), adminToken)
        }
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
            (as_ ?: token).let { bearerAuth(it) }
            setBody(body)
        }

    private suspend fun ApplicationTestBuilder.newTicket(): Ticket =
        json.decodeFromString(postJson("/api/data/ticket/add", json.encodeToString(Ticket())).bodyAsText())

    private suspend fun ApplicationTestBuilder.invoiceFor(ticket: Ticket): Invoice {
        val response = postJson("/api/invoice/ticket/${ticket.identifier}", "")
        assertEquals(HttpStatusCode.OK, response.status)
        return json.decodeFromString(response.bodyAsText())
    }

    private suspend fun ApplicationTestBuilder.setCharges(invoice: Invoice, charges: List<Charge>, discount: Long = 0): HttpResponse =
        postJson("/api/invoice/${invoice.identifier}/update", json.encodeToString(InvoiceUpdateRequest(charges, discount)))

    private suspend fun ApplicationTestBuilder.pickup(ticket: Ticket, vararg payments: PaymentRequest) =
        postJson("/api/counter/pickup", json.encodeToString(PickupRequest(ticket.identifier, payments.toList())))

    // ------------------------------------------ Money ------------------------------------------

    @Test
    fun moneyFormatAndParse() {
        assertEquals("$12.34", Money.format(1234))
        assertEquals("$0.05", Money.format(5))
        assertEquals("-$1.00", Money.format(-100))

        assertEquals(1250, Money.parse("12.5"))
        assertEquals(1250, Money.parse("$12.50"))
        assertEquals(1235, Money.parse("$12.3456"))
        assertEquals(-500, Money.parse("-5"))
        assertNull(Money.parse("abc"))
        assertNull(Money.parse("1.2.3"))

        assertEquals(750, Money.percentToBasisPoints("7.5"))
        assertEquals(825, Money.percentToBasisPoints("8.25%"))
        assertNull(Money.percentToBasisPoints("101"))
        assertEquals(75, Money.applyRate(1000, 750))
    }

    @Test
    fun totalsTakeTheDiscountOffTaxableLinesFirst() {
        val charges = listOf(
            Charge(description = "Labor", type = ChargeType.LABOR, quantity = 1.5, unitPriceCents = 6000),
            Charge(description = "Service fee", unitPriceCents = 1000, taxable = false)
        )

        // 9000 taxable + 1000 not, 2000 off the taxable part, 10% tax on 7000
        val totals = InvoiceMath.totals(charges, discountCents = 2000, taxRateBasisPoints = 1000)
        assertEquals(10_000, totals.subtotalCents)
        assertEquals(700, totals.taxCents)
        assertEquals(8_700, totals.totalCents)

        // Discounts can't go past the subtotal
        assertEquals(0, InvoiceMath.totals(charges, discountCents = 50_000, taxRateBasisPoints = 1000).totalCents)
    }

    @Test
    fun oldTextPricesMoveIntoCents() = testApplication {
        setup()
        val item = Inventory(name = "Screen")
        InventoryDatabaseImpl().add(item)

        transaction {
            InventoryTable.update(where = { InventoryTable.id eq item.identifier }) { row ->
                row[InventoryTable.legacyCost] = "$12.3456"
                row[InventoryTable.legacyPrice] = "29.99"
            }
        }

        LocalDatabase(dataDir.resolve("server").invariantSeparatorsPathString).initDatabase()

        val migrated = InventoryDatabaseImpl().getById(item.identifier)!!
        assertEquals(1235, migrated.costCents)
        assertEquals(2999, migrated.priceCents)
    }

    // ------------------------------------------ Invoices ------------------------------------------

    @Test
    fun eachTicketGetsOneNumberedInvoice() = testApplication {
        setup()
        val ticket = newTicket()

        val first = invoiceFor(ticket)
        val again = invoiceFor(ticket)
        val other = invoiceFor(newTicket())

        assertEquals(first.identifier, again.identifier)
        assertEquals(InvoiceNumber(Year.now().value, 1), first.number)
        assertEquals(InvoiceNumber(Year.now().value, 2), other.number)
        assertTrue(first.number.toString().startsWith("INV-"))
        assertEquals(InvoiceStatus.OPEN, first.status)
    }

    @Test
    fun serverWorksOutTotalsWithTheShopTaxRate() = testApplication {
        setup(taxRate = "10")
        val invoice = invoiceFor(newTicket())

        val response = setCharges(
            invoice,
            listOf(
                Charge(type = ChargeType.LABOR, description = "Screen install", quantity = 1.5, unitPriceCents = 6000),
                Charge(description = "Recycling fee", unitPriceCents = 500, taxable = false)
            ),
            discount = 1000
        )
        assertEquals(HttpStatusCode.OK, response.status)

        val saved = json.decodeFromString<Invoice>(response.bodyAsText())
        assertEquals(9_500, saved.subtotalCents)
        assertEquals(800, saved.taxCents)
        assertEquals(9_300, saved.totalCents)
        assertEquals(1000, saved.taxRateBasisPoints)
        assertEquals(listOf("Screen install", "Recycling fee"), saved.charges.map { it.description })
    }

    @Test
    fun badChargesAreRefused() = testApplication {
        setup()
        val invoice = invoiceFor(newTicket())

        assertEquals(HttpStatusCode.BadRequest, setCharges(invoice, listOf(Charge(description = "x", quantity = 0.0))).status)
        assertEquals(HttpStatusCode.BadRequest, setCharges(invoice, listOf(Charge(description = "x", quantity = 1.234))).status)
        assertEquals(HttpStatusCode.BadRequest, setCharges(invoice, listOf(Charge(description = "x", unitPriceCents = -1))).status)
        assertEquals(HttpStatusCode.BadRequest, setCharges(invoice, listOf(Charge(description = "", unitPriceCents = 1))).status)
        assertEquals(HttpStatusCode.BadRequest, setCharges(invoice, listOf(Charge(type = ChargeType.PART, description = "Mystery part"))).status)
        assertEquals(HttpStatusCode.BadRequest, setCharges(invoice, listOf(Charge(description = "x")), discount = -5).status)
    }

    @Test
    fun depositsCanBeTakenAndRemovedWhileOpen() = testApplication {
        setup()
        val invoice = invoiceFor(newTicket())

        val deposit = postJson("/api/invoice/${invoice.identifier}/payments", json.encodeToString(PaymentRequest(PaymentMethod.CASH, 2_000, "Deposit")))
        assertEquals(HttpStatusCode.OK, deposit.status)

        val withDeposit = json.decodeFromString<Invoice>(deposit.bodyAsText())
        val payment = withDeposit.payments.single()
        assertEquals(2_000, withDeposit.paidCents)
        assertEquals(tech.username, payment.receivedBy)

        assertEquals(HttpStatusCode.BadRequest, postJson("/api/invoice/${invoice.identifier}/payments", json.encodeToString(PaymentRequest(PaymentMethod.CASH, 0))).status)

        val removed = client.delete("/api/invoice/${invoice.identifier}/payments/${payment.identifier}") { bearerAuth(token) }
        assertEquals(HttpStatusCode.OK, removed.status)
        assertTrue(json.decodeFromString<Invoice>(removed.bodyAsText()).payments.isEmpty())
    }

    // ------------------------------------------ Pickup ------------------------------------------

    @Test
    fun pickupPaysTheInvoiceTakesStockAndClosesTheTicket() = testApplication {
        setup()
        val screen = Inventory(name = "Screen", stock = 10, priceCents = 8_000)
        postJson("/api/data/inventory/add", json.encodeToString(screen))

        val ticket = newTicket()
        val invoice = invoiceFor(ticket)
        setCharges(
            invoice,
            listOf(
                Charge(type = ChargeType.PART, description = "Screen", quantity = 2.0, unitPriceCents = 8_000, inventoryIdentifier = screen.identifier),
                Charge(type = ChargeType.LABOR, description = "Install", unitPriceCents = 4_000)
            )
        )
        postJson("/api/invoice/${invoice.identifier}/payments", json.encodeToString(PaymentRequest(PaymentMethod.CASH, 5_000, "Deposit")))

        // Not enough to cover it, nothing should change
        val short = pickup(ticket, PaymentRequest(PaymentMethod.CARD, 10_000))
        assertEquals(HttpStatusCode.Conflict, short.status)
        assertEquals("$50.00 is still owed on ${invoice.number}", short.bodyAsText())
        assertEquals(10, InventoryDatabaseImpl().getById(screen.identifier)!!.stock)
        assertEquals(1, json.decodeFromString<Invoice>(client.get("/api/invoice/${invoice.identifier}") { bearerAuth(token) }.bodyAsText()).payments.size)

        // Split across card and cash
        val done = pickup(ticket, PaymentRequest(PaymentMethod.CARD, 10_000), PaymentRequest(PaymentMethod.CASH, 5_000))
        assertEquals(HttpStatusCode.OK, done.status)

        val paid = json.decodeFromString<Invoice>(done.bodyAsText())
        assertEquals(InvoiceStatus.PAID, paid.status)
        assertNotNull(paid.paidAt)
        assertEquals(0, paid.balanceCents)
        assertEquals(3, paid.payments.size)

        assertEquals(8, InventoryDatabaseImpl().getById(screen.identifier)!!.stock)

        val closed = TicketDatabaseImpl().getById(ticket.identifier)!!
        assertEquals(TicketStatusType.PICKED_UP, closed.status.status)
        assertNotNull(closed.pickedUpAt)

        val history = json.decodeFromString<List<TicketStatusChange>>(client.get("/api/data/ticket/get/history/${ticket.identifier}") { bearerAuth(token) }.bodyAsText())
        assertEquals(TicketStatusType.PICKED_UP, history.last().status)

        // Can't do it twice or change a paid invoice
        assertEquals(HttpStatusCode.Conflict, pickup(ticket).status)
        assertEquals(HttpStatusCode.Conflict, setCharges(paid, listOf(Charge(description = "More", unitPriceCents = 1))).status)
    }

    @Test
    fun freePickupNeedsNoPayment() = testApplication {
        setup()
        val ticket = newTicket()

        // No invoice yet, one is made and paid at zero
        val response = pickup(ticket)
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(InvoiceStatus.PAID, json.decodeFromString<Invoice>(response.bodyAsText()).status)
    }

    @Test
    fun overpayingLeavesChangeDue() = testApplication {
        setup()
        val ticket = newTicket()
        setCharges(invoiceFor(ticket), listOf(Charge(description = "Diagnostic", unitPriceCents = 2_500)))

        val response = pickup(ticket, PaymentRequest(PaymentMethod.CASH, 3_000))
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(-500, json.decodeFromString<Invoice>(response.bodyAsText()).balanceCents)
    }

    @Test
    fun voidingAPaidInvoicePutsPartsBack() = testApplication {
        setup()
        val part = Inventory(name = "Battery", stock = 5, priceCents = 3_000)
        postJson("/api/data/inventory/add", json.encodeToString(part))
        val ticket = newTicket()
        val invoice = invoiceFor(ticket)
        setCharges(invoice, listOf(Charge(type = ChargeType.PART, description = "Battery", unitPriceCents = 3_000, inventoryIdentifier = part.identifier)))
        pickup(ticket, PaymentRequest(PaymentMethod.CASH, 3_000))
        assertEquals(4, InventoryDatabaseImpl().getById(part.identifier)!!.stock)

        // Only admins
        assertEquals(HttpStatusCode.Unauthorized, postJson("/api/invoice/${invoice.identifier}/void", "").status)

        val voided = postJson("/api/invoice/${invoice.identifier}/void", "", adminToken)
        assertEquals(HttpStatusCode.OK, voided.status)
        assertEquals(InvoiceStatus.VOID, json.decodeFromString<Invoice>(voided.bodyAsText()).status)
        assertEquals(5, InventoryDatabaseImpl().getById(part.identifier)!!.stock)
    }

    @Test
    fun restrictedUsersCanLookButNotCharge() = testApplication {
        setup()
        val ticket = newTicket()
        val invoice = invoiceFor(ticket)
        val clerkToken = login(clerk)

        assertEquals(HttpStatusCode.OK, client.get("/api/invoice/ticket/${ticket.identifier}") { bearerAuth(clerkToken) }.status)
        assertEquals(HttpStatusCode.Unauthorized, postJson("/api/invoice/${invoice.identifier}/payments", json.encodeToString(PaymentRequest(PaymentMethod.CASH, 100)), clerkToken).status)
        assertEquals(HttpStatusCode.Unauthorized, postJson("/api/counter/pickup", json.encodeToString(PickupRequest(ticket.identifier)), clerkToken).status)
    }
}
