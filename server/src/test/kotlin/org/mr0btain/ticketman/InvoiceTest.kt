package org.mr0btain.ticketman

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.mr0btain.ticketman.components.database.InventoryDatabase
import org.mr0btain.ticketman.components.database.LocalDatabase
import org.mr0btain.ticketman.components.database.TicketDatabase
import org.mr0btain.ticketman.components.database.UserDatabase
import org.mr0btain.ticketman.components.database.tables.inventory.InventoryTable
import org.mr0btain.ticketman.data.user.AccessLevel
import org.mr0btain.ticketman.data.inventory.Inventory
import org.mr0btain.ticketman.data.invoice.*
import org.mr0btain.ticketman.data.money.Money
import org.mr0btain.ticketman.data.shop.ShopProfile
import org.mr0btain.ticketman.data.ticket.Ticket
import org.mr0btain.ticketman.data.ticket.TicketStatus
import org.mr0btain.ticketman.data.ticket.TicketStatusChange
import org.mr0btain.ticketman.data.ticket.TicketStatusType
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.server.request.*
import org.mr0btain.ticketman.server.response.AuthResponse
import org.mr0btain.ticketman.server.response.DailyReport
import java.time.LocalDate
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
        UserDatabase().apply {
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
        postJson("/api/invoice/${invoice.identifier}/update", json.encodeToString(InvoiceChanges.between(invoice.charges, invoice.discountCents, charges, discount)))

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
        InventoryDatabase().add(item)

        transaction {
            InventoryTable.update(where = { InventoryTable.id eq item.identifier }) { row ->
                row[InventoryTable.legacyCost] = "$12.3456"
                row[InventoryTable.legacyPrice] = "29.99"
            }
        }

        LocalDatabase(dataDir.resolve("server").invariantSeparatorsPathString).initDatabase()

        val migrated = InventoryDatabase().getById(item.identifier)!!
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
    fun invoiceKeepsTheTaxRateItWasMadeWith() = testApplication {
        setup(taxRate = "10")
        val invoice = invoiceFor(newTicket())

        postJson("/api/shop/update", json.encodeToString(ShopProfile(taxRate = "20")), adminToken)

        val saved = json.decodeFromString<Invoice>(setCharges(invoice, listOf(Charge(description = "Labor", unitPriceCents = 1000))).bodyAsText())
        assertEquals(1000, saved.taxRateBasisPoints)
        assertEquals(100, saved.taxCents)

        // A new invoice takes the new rate
        assertEquals(2000, invoiceFor(newTicket()).taxRateBasisPoints)
    }

    @Test
    fun invoiceChargeAndPaymentIdsAreTimeBased() {
        assertEquals(7, Invoice(ticketIdentifier = Ticket().identifier).identifier.toByteArray()[6].toInt() ushr 4)
        assertEquals(7, Charge(description = "x").identifier.toByteArray()[6].toInt() ushr 4)
        assertEquals(7, Payment(method = PaymentMethod.CASH, amountCents = 1, receivedAt = 0, receivedBy = "").identifier.toByteArray()[6].toInt() ushr 4)
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

    @Test
    fun theShopsCurrencyIsUsedAndChecked() = testApplication {
        setup()

        assertEquals(HttpStatusCode.Accepted, postJson("/api/shop/update", json.encodeToString(ShopProfile(currencySymbol = " £ ")), adminToken).status)
        assertEquals("£", json.decodeFromString<ShopProfile>(client.get("/api/shop") { bearerAuth(token) }.bodyAsText()).currencySymbol)

        val ticket = newTicket()
        val invoice = invoiceFor(ticket)
        setCharges(invoice, listOf(Charge(type = ChargeType.LABOR, description = "Install", unitPriceCents = 4_000)))
        assertEquals("£40.00 is still owed on ${invoice.number}", pickup(ticket).bodyAsText())

        listOf("", "12", "TOOLONG").forEach { symbol ->
            val refused = postJson("/api/shop/update", json.encodeToString(ShopProfile(currencySymbol = symbol)), adminToken)
            assertEquals(HttpStatusCode.BadRequest, refused.status, "\"$symbol\" should be refused")
        }
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
        assertEquals(10, InventoryDatabase().getById(screen.identifier)!!.stock)
        assertEquals(1, json.decodeFromString<Invoice>(client.get("/api/invoice/${invoice.identifier}") { bearerAuth(token) }.bodyAsText()).payments.size)

        // Split across card and cash
        val done = pickup(ticket, PaymentRequest(PaymentMethod.CARD, 10_000), PaymentRequest(PaymentMethod.CASH, 5_000))
        assertEquals(HttpStatusCode.OK, done.status)

        val paid = json.decodeFromString<Invoice>(done.bodyAsText())
        assertEquals(InvoiceStatus.PAID, paid.status)
        assertNotNull(paid.paidAt)
        assertEquals(0, paid.balanceCents)
        assertEquals(3, paid.payments.size)

        assertEquals(8, InventoryDatabase().getById(screen.identifier)!!.stock)

        val closed = TicketDatabase().getById(ticket.identifier)!!
        assertEquals(TicketStatusType.PICKED_UP, closed.status.status)
        assertNotNull(closed.pickedUpAt)

        val history = json.decodeFromString<List<TicketStatusChange>>(client.get("/api/data/ticket/get/history/${ticket.identifier}") { bearerAuth(token) }.bodyAsText())
        assertEquals(TicketStatusType.PICKED_UP, history.last().status)

        // Can't do it twice or change a paid invoice
        assertEquals(HttpStatusCode.Conflict, pickup(ticket).status)
        assertEquals(HttpStatusCode.Conflict, setCharges(paid, listOf(Charge(description = "More", unitPriceCents = 1))).status)
    }

    @Test
    fun pickupsAtTheSameMomentOnlyGoThroughOnce() = testApplication {
        setup()
        val part = Inventory(name = "Battery", stock = 10, priceCents = 3_000)
        postJson("/api/data/inventory/add", json.encodeToString(part))
        val ticket = newTicket()
        val invoice = invoiceFor(ticket)
        setCharges(invoice, listOf(Charge(type = ChargeType.PART, description = "Battery", unitPriceCents = 3_000, inventoryIdentifier = part.identifier)))

        val statuses = coroutineScope {
            (1..6).map { async(Dispatchers.IO) { pickup(ticket, PaymentRequest(PaymentMethod.CASH, 3_000)).status } }.awaitAll()
        }

        assertEquals(1, statuses.count { it == HttpStatusCode.OK })
        assertEquals(5, statuses.count { it == HttpStatusCode.Conflict })

        // Paid once and one battery taken
        val paid = json.decodeFromString<Invoice>(client.get("/api/invoice/${invoice.identifier}") { bearerAuth(token) }.bodyAsText())
        assertEquals(1, paid.payments.size)
        assertEquals(9, InventoryDatabase().getById(part.identifier)!!.stock)
    }

    @Test
    fun pickupWontTakeMoreThanIsInStock() = testApplication {
        setup()
        val screen = Inventory(name = "Screen", stock = 1, priceCents = 8_000)
        postJson("/api/data/inventory/add", json.encodeToString(screen))
        val ticket = newTicket()
        val invoice = invoiceFor(ticket)
        // Two lines for the same item count together
        setCharges(
            invoice,
            listOf(
                Charge(type = ChargeType.PART, description = "Screen", unitPriceCents = 8_000, inventoryIdentifier = screen.identifier),
                Charge(type = ChargeType.PART, description = "Spare screen", unitPriceCents = 8_000, inventoryIdentifier = screen.identifier)
            )
        )

        val response = pickup(ticket, PaymentRequest(PaymentMethod.CARD, 16_000))
        assertEquals(HttpStatusCode.Conflict, response.status)
        assertEquals("Only 1 of Screen in stock, fix the count in Inventory first", response.bodyAsText())

        // Nothing changed
        assertEquals(1, InventoryDatabase().getById(screen.identifier)!!.stock)
        assertEquals(InvoiceStatus.OPEN, json.decodeFromString<Invoice>(client.get("/api/invoice/${invoice.identifier}") { bearerAuth(token) }.bodyAsText()).status)
        assertNotEquals(TicketStatusType.PICKED_UP, TicketDatabase().getById(ticket.identifier)!!.status.status)

        // Untracked stock is never short
        val cable = Inventory(name = "Cable", priceCents = 1_000)
        postJson("/api/data/inventory/add", json.encodeToString(cable))
        setCharges(invoiceFor(ticket), listOf(Charge(type = ChargeType.PART, description = "Cable", quantity = 3.0, unitPriceCents = 1_000, inventoryIdentifier = cable.identifier)))
        assertEquals(HttpStatusCode.OK, pickup(ticket, PaymentRequest(PaymentMethod.CARD, 3_000)).status)
    }

    @Test
    fun aStockCountSetDuringAPickupIsntLost() = testApplication {
        setup()

        repeat(5) {
            val part = Inventory(name = "Battery", stock = 5, priceCents = 3_000)
            postJson("/api/data/inventory/add", json.encodeToString(part))
            val ticket = newTicket()
            setCharges(invoiceFor(ticket), listOf(Charge(type = ChargeType.PART, description = "Battery", unitPriceCents = 3_000, inventoryIdentifier = part.identifier)))

            // Someone opened the item at 5 and is restocking it to 10 as the battery goes out
            val (picked, restocked) = coroutineScope {
                val picked = async(Dispatchers.IO) { pickup(ticket, PaymentRequest(PaymentMethod.CASH, 3_000)).status }
                val restocked = async(Dispatchers.IO) {
                    postJson("/api/data/inventory/set/stock/${part.identifier}", json.encodeToString(StockChangeRequest(5, 10))).status
                }
                picked.await() to restocked.await()
            }

            assertEquals(HttpStatusCode.OK, picked)
            val stock = InventoryDatabase().getById(part.identifier)!!.stock
            when (restocked) {
                // Restocked first, then the battery came out of the 10
                HttpStatusCode.Accepted -> assertEquals(9, stock)
                // The pickup got there first, so the restock was refused rather than putting the battery back
                HttpStatusCode.Conflict -> assertEquals(4, stock)
                else -> fail("Restock answered $restocked")
            }
        }
    }

    @Test
    fun ticketsAreOnlyPickedUpAtTheCounter() = testApplication {
        setup()
        val ticket = newTicket()
        suspend fun update(changed: Ticket) = postJson("/api/data/ticket/update", json.encodeToString(changed))

        // Not through a plain update, the invoice wouldn't be paid
        assertEquals(HttpStatusCode.Conflict, update(ticket.copy(status = TicketStatus(TicketStatusType.PICKED_UP))).status)
        assertEquals(HttpStatusCode.BadRequest, postJson("/api/data/ticket/add", json.encodeToString(Ticket(status = TicketStatus(TicketStatusType.PICKED_UP)))).status)
        assertNotEquals(TicketStatusType.PICKED_UP, TicketDatabase().getById(ticket.identifier)!!.status.status)

        assertEquals(HttpStatusCode.OK, pickup(ticket).status)
        val closed = TicketDatabase().getById(ticket.identifier)!!

        // Once picked up it can't be reopened
        assertEquals(HttpStatusCode.Conflict, update(closed.copy(status = TicketStatus(TicketStatusType.IN_PROGRESS))).status)
        assertEquals(TicketStatusType.PICKED_UP, TicketDatabase().getById(ticket.identifier)!!.status.status)

        // Other details can still be fixed
        assertEquals(HttpStatusCode.Accepted, update(closed.copy(problem = "Cracked screen, fixed")).status)
        val edited = TicketDatabase().getById(ticket.identifier)!!
        assertEquals("Cracked screen, fixed", edited.problem)
        assertEquals(TicketStatusType.PICKED_UP, edited.status.status)

        // A save that started before the pickup still has the old status, it mustn't reopen the ticket
        assertTrue(TicketDatabase().update(ticket.copy(problem = "Late save")))
        assertEquals(TicketStatusType.PICKED_UP, TicketDatabase().getById(ticket.identifier)!!.status.status)
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
        assertEquals(4, InventoryDatabase().getById(part.identifier)!!.stock)

        // Only admins
        assertEquals(HttpStatusCode.Forbidden, postJson("/api/invoice/${invoice.identifier}/void", "").status)

        val voided = postJson("/api/invoice/${invoice.identifier}/void", "", adminToken)
        assertEquals(HttpStatusCode.OK, voided.status)
        assertEquals(InvoiceStatus.VOID, json.decodeFromString<Invoice>(voided.bodyAsText()).status)
        assertEquals(5, InventoryDatabase().getById(part.identifier)!!.stock)
    }

    @Test
    fun restrictedUsersCanLookButNotCharge() = testApplication {
        setup()
        val ticket = newTicket()
        val invoice = invoiceFor(ticket)
        val clerkToken = login(clerk)

        assertEquals(HttpStatusCode.OK, client.get("/api/invoice/ticket/${ticket.identifier}") { bearerAuth(clerkToken) }.status)
        assertEquals(HttpStatusCode.Forbidden, postJson("/api/invoice/${invoice.identifier}/payments", json.encodeToString(PaymentRequest(PaymentMethod.CASH, 100)), clerkToken).status)
        assertEquals(HttpStatusCode.Forbidden, postJson("/api/counter/pickup", json.encodeToString(PickupRequest(ticket.identifier)), clerkToken).status)
    }

    // ------------------------------------------ End of day ------------------------------------------

    @Test
    fun dailyReportAddsUpTheDay() = testApplication {
        setup()

        // One picked up with a card deposit and cash at pickup, one still in
        val done = newTicket()
        val waiting = newTicket()
        val invoice = invoiceFor(done)
        setCharges(invoice, listOf(Charge(type = ChargeType.LABOR, description = "Repair", unitPriceCents = 5_000)))
        postJson("/api/invoice/${invoice.identifier}/payments", json.encodeToString(PaymentRequest(PaymentMethod.CARD, 2_000)))
        pickup(done, PaymentRequest(PaymentMethod.CASH, 3_000))

        val response = client.get("/api/admin/report/daily") { bearerAuth(adminToken) }
        assertEquals(HttpStatusCode.OK, response.status)

        val report = json.decodeFromString<DailyReport>(response.bodyAsText())
        assertEquals(LocalDate.now().toString(), report.date)
        assertEquals(mapOf(PaymentMethod.CARD to 2_000L, PaymentMethod.CASH to 3_000L), report.takenByMethod)
        assertEquals(5_000, report.takenCents)
        assertEquals(2, report.paymentCount)
        assertEquals(5_000, report.salesCents)
        assertEquals(1, report.invoicesPaid)
        assertEquals(2, report.ticketsCheckedIn)
        assertEquals(1, report.ticketsPickedUp)
        assertNotNull(waiting)

        // Another day is empty
        val yesterday = json.decodeFromString<DailyReport>(
            client.get("/api/admin/report/daily?date=${LocalDate.now().minusDays(1)}") { bearerAuth(adminToken) }.bodyAsText()
        )
        assertEquals(0, yesterday.takenCents)
        assertEquals(0, yesterday.ticketsCheckedIn)

        assertEquals(HttpStatusCode.BadRequest, client.get("/api/admin/report/daily?date=yesterday") { bearerAuth(adminToken) }.status)
        assertEquals(HttpStatusCode.Forbidden, client.get("/api/admin/report/daily") { bearerAuth(token) }.status)
    }
}
