package org.mr0btain.ticketman.components.database.debug

import org.mr0btain.ticketman.components.database.LocalDatabase
import org.mr0btain.ticketman.components.service.AccountResult
import org.mr0btain.ticketman.components.service.InvoiceService
import org.mr0btain.ticketman.data.inventory.Inventory
import org.mr0btain.ticketman.data.invoice.PaymentMethod
import org.mr0btain.ticketman.data.shop.ShopProfile
import org.mr0btain.ticketman.data.ticket.TicketStatusType
import org.mr0btain.ticketman.data.ticket.WorkType
import org.mr0btain.ticketman.data.user.AccessLevel
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.logging.AppLogger
import org.mr0btain.ticketman.server.request.PaymentRequest
import org.mr0btain.ticketman.server.request.PickupRequest
import org.mr0btain.ticketman.server.request.SeedRequest
import org.mr0btain.ticketman.server.request.WorkRequest
import kotlin.random.Random
import kotlin.uuid.Uuid

private const val TAG = "SeedTestData"

/**
 * Staff with known passwords, so each access level can be tried. Added by every seed that doesn't have them yet
 */
val DEMO_STAFF = listOf(
    User(username = "tech", password = "tech_pass", name = "Tess Tech", accessLevel = AccessLevel.NORMAL),
    User(username = "clerk", password = "clerk_pass", name = "Cal Clerk", accessLevel = AccessLevel.RESTRICTED)
)

/**
 * Fills the database with mock tickets, customers, inventory and users. Tickets get handed out to customers
 * and then users until they run out. Then, the way a shop's day goes: work is logged on some tickets (putting labor
 * and parts on their invoices), some take a deposit, and some that are done get picked up and paid. A shop profile
 * is filled in first if it has no name, so labor has a rate and tax is charged
 */
fun seedTestData(database: LocalDatabase, request: SeedRequest) {
    if (database.shopDatabase.get().name.isBlank()) database.shopDatabase.update(DEMO_SHOP)

    DEMO_STAFF.forEach { staff ->
        if (database.userDatabase.getByUsername(staff.username) == null) database.userDatabase.add(staff)
    }

    val ticketIdentifiers = mutableListOf<Uuid>()

    repeat(request.tickets) {
        // Generate random ticket
        val (ticket, notes) = generateRandomTicket()

        // Add ticket to database
        database.ticketDatabase.add(ticket)

        // Add notes
        notes.forEach { note ->
            database.ticketDatabase.addNote(ticket.identifier, note)
        }

        // Insert id into list
        ticketIdentifiers.add(ticket.identifier)
    }

    // Kept for the invoices below, the list above is used up handing tickets out
    val seededTickets = ticketIdentifiers.toList()

    repeat(request.customers) {
        // Generate random customer
        val (customer, notes) = generateMockCustomer()

        // Add customer
        database.customerDatabase.add(customer)

        // Add notes
        notes.forEach { note ->
            database.customerDatabase.addNote(customer.identifier, note)
        }

        // Add random tickets
        repeat(Random.nextInt(1, 6)) {
            if (ticketIdentifiers.isNotEmpty())
                database.customerDatabase.addTicket(customer.identifier, ticketIdentifiers.removeFirst())
        }
    }

    val parts = (1..request.inventory).map {
        val (inventory, notes) = generateRandomInventoryItem()

        database.inventoryDatabase.add(inventory)

        notes.forEach { note ->
            database.inventoryDatabase.addNote(inventory.identifier, note)
        }

        inventory
    }

    repeat(request.users) {
        // Generate random user
        val (user, notes) = generateMockUser()

        // Mock names repeat, skip ones that would clash on username
        if (database.userDatabase.getByUsername(user.username) != null) return@repeat

        // Add user to database
        database.userDatabase.add(user)

        //Add notes
        notes.forEach { note ->
            database.userDatabase.addNote(user.identifier, note)
        }

        // Add random tickets
        repeat(Random.nextInt(4, 12)) {
            if (ticketIdentifiers.isNotEmpty())
                database.userDatabase.addTicket(user.identifier, ticketIdentifiers.removeFirst())
        }
    }

    seedInvoices(database, seededTickets, parts)
}

/**
 * Work, deposits and pickups on about two thirds of [tickets], through [InvoiceService] like the counter, so totals,
 * stock and the work log agree
 */
private fun seedInvoices(database: LocalDatabase, tickets: List<Uuid>, parts: List<Inventory>) {
    val service = InvoiceService(
        invoices = database.invoiceDatabase,
        tickets = database.ticketDatabase,
        inventory = database.inventoryDatabase,
        shop = database.shopDatabase,
        auditLog = database.auditLogDatabase,
        work = database.workDatabase,
        customers = database.customerDatabase
    )
    val tech = database.userDatabase.getByUsername(DEMO_STAFF.first().username)
    val techName = tech?.username ?: "tech"
    var pickedUp = 0

    tickets.filter { Random.nextInt(3) > 0 }.forEach { identifier ->
        val ticket = database.ticketDatabase.getById(identifier) ?: return@forEach

        // Time at the shop's rate, and a part or two
        service.logWork(identifier, WorkRequest(WorkType.LABOR, LABOR.random(), quantity = Random.nextInt(1, 7) * 0.25), tech?.identifier, techName)
        parts.shuffled().take(Random.nextInt(0, 3)).forEach { part ->
            service.logWork(identifier, WorkRequest(WorkType.PART, quantity = 1.0, inventoryIdentifier = part.identifier), tech?.identifier, techName)
        }

        val invoice = (service.forTicket(identifier, tech?.identifier) as? AccountResult.Done)?.value ?: return@forEach

        if (Random.nextInt(4) == 0) {
            service.addPayment(invoice.identifier, PaymentRequest(PaymentMethod.CARD, (invoice.totalCents / 2).coerceAtLeast(100), "Deposit"), techName, tech?.identifier)
        }

        // Done ones mostly go home, paid off in one go
        if (ticket.status.status == TicketStatusType.DONE && Random.nextInt(3) > 0) {
            val owed = (service.forTicket(identifier, tech?.identifier) as? AccountResult.Done)?.value?.balanceCents ?: return@forEach
            val payments = if (owed > 0) listOf(PaymentRequest(PaymentMethod.entries.random(), owed)) else emptyList()

            if (service.pickup(PickupRequest(identifier, payments), techName, tech?.identifier) is AccountResult.Done) pickedUp++
        }
    }

    AppLogger.m(TAG, "Seeded ${tickets.size} tickets, $pickedUp of them picked up")
}

private val LABOR = listOf("Diagnosis", "Screen replacement", "Battery swap", "Board repair", "Data recovery", "Cleaning")

private val DEMO_SHOP = ShopProfile(
    name = "Bench Repair (test data)",
    street = "120 Main St",
    city = "Springfield",
    state = "PA",
    zip = "16101",
    phone = "555-0142",
    email = "front@benchrepair.example",
    taxRate = "6",
    laborRateCents = 8_000,
    invoiceFooter = "Thanks for choosing Bench Repair. Repairs are guaranteed for 90 days.",
    repairTerms = "We aren't responsible for data loss. Devices left over 60 days after we call may be recycled."
)
