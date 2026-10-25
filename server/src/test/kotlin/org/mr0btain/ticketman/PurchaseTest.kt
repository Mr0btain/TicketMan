package org.mr0btain.ticketman

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.mr0btain.ticketman.components.database.InventoryDatabase
import org.mr0btain.ticketman.components.database.TicketDatabase
import org.mr0btain.ticketman.components.database.UserDatabase
import org.mr0btain.ticketman.data.inventory.Inventory
import org.mr0btain.ticketman.data.purchase.PurchaseLine
import org.mr0btain.ticketman.data.purchase.PurchaseOrder
import org.mr0btain.ticketman.data.purchase.PurchaseStatus
import org.mr0btain.ticketman.data.purchase.Supplier
import org.mr0btain.ticketman.data.shop.ShopProfile
import org.mr0btain.ticketman.data.ticket.CustomStatus
import org.mr0btain.ticketman.data.ticket.StatusChoice
import org.mr0btain.ticketman.data.ticket.Ticket
import org.mr0btain.ticketman.data.ticket.TicketStatusType
import org.mr0btain.ticketman.data.user.AccessLevel
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.server.request.*
import org.mr0btain.ticketman.server.response.AuthResponse
import kotlin.test.*

/**
 * Suppliers, purchase orders, receiving into stock, and tickets waiting on parts
 */
class PurchaseTest {
    private val dataDir = createTestDataDir()
    private val json = Json { ignoreUnknownKeys = true }

    private val admin = User(username = "boss", password = "boss_pass", accessLevel = AccessLevel.ADMIN)
    private val tech = User(username = "tech", password = "tech_pass", accessLevel = AccessLevel.NORMAL)
    private val clerk = User(username = "clerk", password = "clerk_pass", accessLevel = AccessLevel.RESTRICTED)

    private val awaiting = CustomStatus(name = "Awaiting parts", actsAs = TicketStatusType.ON_HOLD)
    private val arrived = CustomStatus(name = "Parts arrived", actsAs = TicketStatusType.IN_PROGRESS)
    private val supplier = Supplier(name = "Parts Co", phone = "555-0199", accountNumber = "A-17")

    private lateinit var token: String
    private lateinit var clerkToken: String

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
        clerkToken = login(clerk)

        val shop = ShopProfile(statuses = listOf(awaiting, arrived), partsOrderedStatus = awaiting.choice, partsArrivedStatus = arrived.choice)
        assertEquals(HttpStatusCode.Accepted, postJson("/api/shop/update", json.encodeToString(shop), login(admin)).status)
        assertEquals(HttpStatusCode.Created, postJson("/api/purchase/suppliers/add", json.encodeToString(supplier)).status)
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

    private suspend fun ApplicationTestBuilder.item(name: String, stock: Int = 2, cost: Long = 1_000): Inventory {
        val item = Inventory(name = name, stock = stock, costCents = cost, supplierIdentifier = supplier.identifier)
        postJson("/api/data/inventory/add", json.encodeToString(item))
        return item
    }

    private suspend fun ApplicationTestBuilder.order(response: HttpResponse): PurchaseOrder {
        assertTrue(response.status.isSuccess(), response.bodyAsText())
        return json.decodeFromString(response.bodyAsText())
    }

    private fun stock(item: Inventory) = InventoryDatabase().getById(item.identifier)!!

    private fun status(ticket: Ticket) = TicketDatabase().getById(ticket.identifier)!!.status.choice

    @Test
    fun suppliersAreKeptByName() = testApplication {
        setup()
        postJson("/api/purchase/suppliers/add", json.encodeToString(Supplier(name = "Acme")))

        val list = json.decodeFromString<List<Supplier>>(client.get("/api/purchase/suppliers") { bearerAuth(clerkToken) }.bodyAsText())
        assertEquals(listOf("Acme", "Parts Co"), list.map { it.name })

        assertEquals(HttpStatusCode.BadRequest, postJson("/api/purchase/suppliers/add", json.encodeToString(Supplier(name = " "))).status)
        assertEquals(HttpStatusCode.Forbidden, postJson("/api/purchase/suppliers/add", json.encodeToString(Supplier(name = "Clerk's")), clerkToken).status)

        val renamed = postJson("/api/purchase/suppliers/update", json.encodeToString(supplier.copy(name = "Parts Co Ltd")))
        assertEquals("Parts Co Ltd", json.decodeFromString<Supplier>(renamed.bodyAsText()).name)
        assertEquals(HttpStatusCode.Accepted, client.delete("/api/purchase/suppliers/${list.first().identifier}") { bearerAuth(token) }.status)
    }

    @Test
    fun anOrderIsDraftedPlacedAndReceivedIntoStock() = testApplication {
        setup()
        val screen = item("Screen", stock = 2, cost = 6_000)
        val untracked = Inventory(name = "Adhesive")
        postJson("/api/data/inventory/add", json.encodeToString(untracked))

        val draft = order(postJson("/api/purchase/orders/add", json.encodeToString(PurchaseOrderRequest(
            lines = listOf(PurchaseLine(inventoryIdentifier = screen.identifier, quantity = 3, unitCostCents = 5_500), PurchaseLine(inventoryIdentifier = untracked.identifier, quantity = 1))
        ))))
        assertEquals(PurchaseStatus.DRAFT, draft.status)
        assertEquals("PO-${java.time.Year.now().value % 100}-00000001", draft.label)
        assertEquals("Screen", draft.lines.first().description)

        // Needs a supplier to be placed
        assertEquals(HttpStatusCode.BadRequest, postJson("/api/purchase/orders/${draft.identifier}/place", "").status)
        val withSupplier = order(postJson("/api/purchase/orders/${draft.identifier}/update", json.encodeToString(PurchaseOrderRequest(supplier.identifier, draft.lines, "Rush please"))))
        assertEquals("Parts Co", withSupplier.supplierName)

        val placed = order(postJson("/api/purchase/orders/${draft.identifier}/place", ""))
        assertEquals(PurchaseStatus.ORDERED, placed.status)
        assertNotNull(placed.orderedAt)
        assertEquals(HttpStatusCode.Conflict, postJson("/api/purchase/orders/${draft.identifier}/update", json.encodeToString(PurchaseOrderRequest(supplier.identifier))).status)

        // Two of three screens arrive
        val screenLine = placed.lines.first()
        val partly = order(postJson("/api/purchase/orders/${draft.identifier}/receive", json.encodeToString(ReceiveRequest(listOf(ReceivedLine(screenLine.identifier, 2))))))
        assertEquals(PurchaseStatus.PARTLY_RECEIVED, partly.status)
        assertEquals(4, stock(screen).stock)
        assertEquals(5_500, stock(screen).costCents, "the cost is what this order paid")

        val tooMany = postJson("/api/purchase/orders/${draft.identifier}/receive", json.encodeToString(ReceiveRequest(listOf(ReceivedLine(screenLine.identifier, 2)))))
        assertEquals(HttpStatusCode.BadRequest, tooMany.status)
        assertEquals("Only 1 of Screen are still to come on ${draft.label}", tooMany.bodyAsText())

        val done = order(postJson("/api/purchase/orders/${draft.identifier}/receive", json.encodeToString(ReceiveRequest(placed.lines.map { ReceivedLine(it.identifier, it.outstanding - if (it == screenLine) 2 else 0) }))))
        assertEquals(PurchaseStatus.RECEIVED, done.status)
        assertNotNull(done.receivedAt)
        assertEquals(5, stock(screen).stock)
        assertEquals(Int.MAX_VALUE, stock(untracked).stock, "untracked stays untracked")

        assertEquals(HttpStatusCode.Conflict, postJson("/api/purchase/orders/${draft.identifier}/cancel", "").status)
    }

    @Test
    fun aTicketWaitsForItsPartsAndMovesOnWhenTheyCome() = testApplication {
        setup()
        val battery = item("Battery")
        val ticket = json.decodeFromString<Ticket>(postJson("/api/data/ticket/add", json.encodeToString(Ticket())).bodyAsText())

        // Ordering for the ticket puts it on the supplier's draft, made now since there's none
        val first = order(postJson("/api/purchase/orders/part", json.encodeToString(OrderPartRequest(ticket.identifier, battery.identifier, quantity = 1))))
        val second = order(postJson("/api/purchase/orders/part", json.encodeToString(OrderPartRequest(ticket.identifier, battery.identifier, quantity = 1))))
        assertEquals(first.identifier, second.identifier)
        assertEquals(2, second.lines.size)
        assertEquals(StatusChoice(TicketStatusType.IN_PROGRESS), status(ticket), "nothing moves until it's placed")

        order(postJson("/api/purchase/orders/${first.identifier}/place", ""))
        assertEquals(awaiting.choice, status(ticket))

        val forTicket = json.decodeFromString<List<PurchaseOrder>>(client.get("/api/purchase/orders/ticket/${ticket.identifier}") { bearerAuth(clerkToken) }.bodyAsText())
        assertEquals(listOf(first.identifier), forTicket.map { it.identifier })

        // One of its two parts isn't enough
        postJson("/api/purchase/orders/${first.identifier}/receive", json.encodeToString(ReceiveRequest(listOf(ReceivedLine(second.lines[0].identifier, 1)))))
        assertEquals(awaiting.choice, status(ticket))
        postJson("/api/purchase/orders/${first.identifier}/receive", json.encodeToString(ReceiveRequest(listOf(ReceivedLine(second.lines[1].identifier, 1)))))
        assertEquals(arrived.choice, status(ticket))

        // An item with no supplier needs one picked
        val loose = Inventory(name = "Loose part")
        postJson("/api/data/inventory/add", json.encodeToString(loose))
        assertEquals(HttpStatusCode.BadRequest, postJson("/api/purchase/orders/part", json.encodeToString(OrderPartRequest(ticket.identifier, loose.identifier))).status)
        assertEquals(HttpStatusCode.OK, postJson("/api/purchase/orders/part", json.encodeToString(OrderPartRequest(ticket.identifier, loose.identifier, supplierIdentifier = supplier.identifier))).status)
    }

    @Test
    fun draftsAreDeletedAndPlacedOrdersCancelled() = testApplication {
        setup()
        val cable = item("Cable")
        val lines = listOf(PurchaseLine(inventoryIdentifier = cable.identifier, quantity = 5))

        val draft = order(postJson("/api/purchase/orders/add", json.encodeToString(PurchaseOrderRequest(supplier.identifier, lines))))
        assertEquals(HttpStatusCode.Accepted, client.delete("/api/purchase/orders/${draft.identifier}") { bearerAuth(token) }.status)

        val placed = order(postJson("/api/purchase/orders/add", json.encodeToString(PurchaseOrderRequest(supplier.identifier, lines))))
        order(postJson("/api/purchase/orders/${placed.identifier}/place", ""))
        assertEquals(HttpStatusCode.Conflict, client.delete("/api/purchase/orders/${placed.identifier}") { bearerAuth(token) }.status)
        assertEquals(PurchaseStatus.CANCELLED, order(postJson("/api/purchase/orders/${placed.identifier}/cancel", "")).status)

        val open = json.decodeFromString<List<PurchaseOrder>>(client.get("/api/purchase/orders?status=ORDERED,PARTLY_RECEIVED") { bearerAuth(token) }.bodyAsText())
        assertTrue(open.isEmpty())
        assertEquals(HttpStatusCode.BadRequest, client.get("/api/purchase/orders?status=LOST") { bearerAuth(token) }.status)

        // Bad lines
        assertEquals(HttpStatusCode.BadRequest, postJson("/api/purchase/orders/add", json.encodeToString(PurchaseOrderRequest(lines = listOf(PurchaseLine(inventoryIdentifier = cable.identifier, quantity = 0))))).status)
        assertEquals(HttpStatusCode.BadRequest, postJson("/api/purchase/orders/add", json.encodeToString(PurchaseOrderRequest(lines = listOf(PurchaseLine(inventoryIdentifier = Inventory().identifier))))).status)
        assertEquals(HttpStatusCode.Forbidden, postJson("/api/purchase/orders/add", json.encodeToString(PurchaseOrderRequest()), clerkToken).status)
    }
}
