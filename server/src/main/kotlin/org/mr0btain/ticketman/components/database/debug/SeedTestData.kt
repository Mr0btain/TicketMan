package org.mr0btain.ticketman.components.database.debug

import kotlin.uuid.ExperimentalUuidApi
import org.mr0btain.ticketman.data.location.Location
import org.mr0btain.ticketman.components.database.LocalDatabase
import org.mr0btain.ticketman.components.service.AccountResult
import org.mr0btain.ticketman.components.service.InvoiceService
import org.mr0btain.ticketman.data.appointment.OnlineBooking
import org.mr0btain.ticketman.data.customer.Customer
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
 * is filled in first if it has no name, so labor has a rate and tax is charged. Last, what the shop runs its days
 * with ([seedOperations]): the staff schedule, time off, the time clock, appointments, purchase orders and the rest
 * @param developmentMode Turns on the simulated card processor, which only a development server takes
 */
fun seedTestData(database: LocalDatabase, request: SeedRequest, developmentMode: Boolean = false) {
    if (database.shopDatabase.get().name.isBlank()) database.shopDatabase.update(DEMO_SHOP)

    DEMO_STAFF.forEach { staff ->
        if (database.userDatabase.getByUsername(staff.username) == null) database.userDatabase.add(staff)
    }

    // A second location, so the demo shows each keeping its own: a quarter of the tickets are there
    val main = database.locationDatabase.main()
    val downtown = database.locationDatabase.all().firstOrNull { it.code == DEMO_LOCATION.code }
        ?: DEMO_LOCATION.takeIf { database.locationDatabase.add(it) }
    val ticketIdentifiers = mutableListOf<Uuid>()

    repeat(request.tickets) { index ->
        // Generate random ticket
        val (generated, notes) = generateRandomTicket()
        val ticket = generated.copy(locationIdentifier = if (downtown != null && index % 4 == 3) downtown.identifier else main.identifier)

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

    val customerTickets = mutableListOf<Pair<Customer, List<Uuid>>>()
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
        val theirs = (1..Random.nextInt(1, 6)).mapNotNull { ticketIdentifiers.removeFirstOrNull() }
        theirs.forEach { database.customerDatabase.addTicket(customer.identifier, it) }
        customerTickets += customer to theirs
    }

    val parts = (1..request.inventory).map {
        val (inventory, notes) = generateRandomInventoryItem()

        database.inventoryDatabase.add(inventory)

        notes.forEach { note ->
            database.inventoryDatabase.addNote(inventory.identifier, note)
        }

        inventory
    }

    repeat(request.users) { index ->
        // Generate random user, every third working downtown
        val (generated, notes) = generateMockUser()
        val user = generated.copy(homeLocation = if (downtown != null && index % 3 == 1) downtown.identifier else null)

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
    seedOperations(database, customerTickets, parts, main, downtown, developmentMode)
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
        customers = database.customerDatabase,
        values = database.storedValueDatabase
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
            val payments = if (owed > 0) listOf(PaymentRequest(PaymentMethod.entries.filter { !it.storedValue }.random(), owed)) else emptyList()

            if (service.pickup(PickupRequest(identifier, payments), techName, tech?.identifier) is AccountResult.Done) pickedUp++
        }
    }

    AppLogger.m(TAG, "Seeded ${tickets.size} tickets, $pickedUp of them picked up")
}

private val LABOR = listOf("Diagnosis", "Screen replacement", "Battery swap", "Board repair", "Data recovery", "Cleaning")

@OptIn(ExperimentalUuidApi::class)
private val DEMO_LOCATION = Location(code = "DT", name = "Downtown", street = "200 Market St", phone = "(555) 010-0200")

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
    repairTerms = "We aren't responsible for data loss. Devices left over 60 days after we call may be recycled.",
    booking = OnlineBooking(enabled = true, leadMinutes = 60, note = "Bring your charger and any passcodes")
)
