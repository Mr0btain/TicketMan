package org.mr0btain.ticketman.components.database.debug

import org.mr0btain.ticketman.data.schedule.TimeOffAllowance
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.mr0btain.ticketman.components.database.LocalDatabase
import org.mr0btain.ticketman.components.service.AccountResult
import org.mr0btain.ticketman.components.service.AppointmentService
import org.mr0btain.ticketman.components.service.PurchaseService
import org.mr0btain.ticketman.components.service.ScheduleService
import org.mr0btain.ticketman.components.service.TimeOffService
import org.mr0btain.ticketman.data.appointment.Appointment
import org.mr0btain.ticketman.data.appointment.AppointmentStatus
import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.data.giftcard.GiftCardCode
import org.mr0btain.ticketman.data.inventory.Inventory
import org.mr0btain.ticketman.data.inventory.StockTransfer
import org.mr0btain.ticketman.data.location.Location
import org.mr0btain.ticketman.data.payment.CardReader
import org.mr0btain.ticketman.data.payment.PaymentProvider
import org.mr0btain.ticketman.data.payment.PaymentSettings
import org.mr0btain.ticketman.data.purchase.PurchaseLine
import org.mr0btain.ticketman.data.purchase.Supplier
import org.mr0btain.ticketman.data.report.CommissionRate
import org.mr0btain.ticketman.data.schedule.ScheduleEntry
import org.mr0btain.ticketman.data.schedule.ScheduleKind
import org.mr0btain.ticketman.data.schedule.ScheduleRepeat
import org.mr0btain.ticketman.data.schedule.ScheduleRequest
import org.mr0btain.ticketman.data.schedule.TimeOffRequest
import org.mr0btain.ticketman.data.schedule.TimeOffRules
import org.mr0btain.ticketman.data.shared.PagedRequest
import org.mr0btain.ticketman.data.ticket.Device
import org.mr0btain.ticketman.data.ticket.TicketStatusType
import org.mr0btain.ticketman.data.timeclock.TimeEntry
import org.mr0btain.ticketman.data.user.AccessLevel
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.logging.AppLogger
import org.mr0btain.ticketman.server.request.PurchaseOrderRequest
import org.mr0btain.ticketman.server.request.ReceiveRequest
import org.mr0btain.ticketman.server.request.ReceivedLine
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private const val TAG = "SeedTestData:Operations"

/**
 * What the seeded shop has to run its days with, around the records [seedTestData] made: everyone's weekly shifts
 * from last week to six weeks ahead, time off allowances and requests in each state, the time clock over the past
 * shifts (with a late start and a missed one), appointments, suppliers and purchase orders in each state, stock
 * at the second location and moves to it, store credit, gift cards, commission rates, and with [developmentMode]
 * the simulated card processor with tips
 * @param customerTickets Each seeded customer with the tickets they were given
 */
@OptIn(ExperimentalUuidApi::class)
internal fun seedOperations(
    database: LocalDatabase,
    customerTickets: List<Pair<Customer, List<Uuid>>>,
    parts: List<Inventory>,
    main: Location,
    downtown: Location?,
    developmentMode: Boolean
) {
    val zone = TimeZone.currentSystemDefault()
    val now = Clock.System.now().toEpochMilliseconds()
    val today = Instant.fromEpochMilliseconds(now).toLocalDateTime(zone).date
    val lastMonday = today.minus(DatePeriod(days = today.dayOfWeek.ordinal + 7))
    fun at(day: LocalDate, hour: Int, minute: Int = 0) = LocalDateTime(day, LocalTime(hour, minute)).toInstant(zone).toEpochMilliseconds()
    fun midnight(day: LocalDate) = day.atStartOfDayIn(zone).toEpochMilliseconds()
    fun day(offset: Int) = today.plus(DatePeriod(days = offset))

    val staff = database.userDatabase.getPagedItems(PagedRequest(offset = 0, itemsPerPage = 200)).orEmpty()
        .filter { it.canAccess(AccessLevel.RESTRICTED) }
    val admin = staff.firstOrNull { it.accessLevel == AccessLevel.ADMIN }
    val adminName = admin?.username ?: "admin"
    fun User.at(): Uuid = homeLocation ?: main.identifier
    fun User.label() = name.ifBlank { username }

    // Seeding again from the admin screen adds records, but nobody gets a second set of shifts
    if (database.scheduleDatabase.between(midnight(lastMonday), midnight(day(6 * 7))).isNullOrEmpty()) {
        seedSchedule(database, staff, main, lastMonday, now, zone)
    }

    // Appointments over the week behind and the ten days ahead, inside the shifts. Past ones checked in, no-showed
    // or were cancelled
    val appointments = AppointmentService(database.appointmentDatabase, database.customerDatabase, database.userDatabase, database.auditLogDatabase)
    val technicians = staff.filter { it.accessLevel != AccessLevel.RESTRICTED }
    var booked = 0
    (-7..10).map { day(it) }.filter { it.dayOfWeek != DayOfWeek.SUNDAY }.forEach { date ->
        listOfNotNull(main, downtown).forEach { location ->
            val count = if (location == main) Random.nextInt(2, 5) else Random.nextInt(0, 3)
            (10..16).shuffled().take(count).forEach { hour ->
                val (customer, tickets) = customerTickets.random()
                val technician = technicians.filter { it.at() == location.identifier }.randomOrNull()
                val (device, problem) = APPOINTMENT_REASONS.random()
                val appointment = Appointment(
                    startsAt = at(date, hour, listOf(0, 30).random()),
                    minutes = listOf(30, 30, 45, 60).random(),
                    customerIdentifier = customer.identifier,
                    customerName = customer.name,
                    phone = customer.phone,
                    email = customer.email,
                    device = device,
                    problem = problem,
                    technicianIdentifier = technician?.identifier,
                    technicianName = technician?.label().orEmpty()
                )
                val added = (appointments.add(appointment, adminName, admin?.identifier, location.identifier) as? AccountResult.Done)?.value ?: return@forEach
                booked++
                if (added.endsAt > now) return@forEach

                val ticket = tickets.firstOrNull()
                when {
                    ticket != null && Random.nextInt(3) > 0 -> database.appointmentDatabase.markArrived(added.identifier, ticket, customer.identifier)
                    Random.nextBoolean() -> database.appointmentDatabase.setStatus(added.identifier, AppointmentStatus.NO_SHOW)
                    else -> database.appointmentDatabase.setStatus(added.identifier, AppointmentStatus.CANCELLED)
                }
            }
        }
    }

    seedPurchasing(database, parts, main, downtown, adminName, admin?.identifier, now)

    // Some of the stock moved downtown, the moves kept like the Move stock button's
    if (downtown != null) {
        parts.filter { it.stock != Int.MAX_VALUE }.shuffled().take(parts.size / 2).forEachIndexed { index, part ->
            val quantity = (part.stock / 4).coerceIn(1, 20)
            database.inventoryDatabase.transfer(
                StockTransfer(
                    itemIdentifier = part.identifier,
                    itemName = part.name,
                    fromLocation = main.identifier,
                    toLocation = downtown.identifier,
                    quantity = quantity,
                    note = if (index % 3 == 0) "Opening stock" else "",
                    movedBy = adminName,
                    movedAt = now - Random.nextLong(1, 14) * DAY
                )
            )
        }
    }

    // Store credit and gift cards for a few customers
    customerTickets.shuffled().take(4).forEachIndexed { index, (customer, _) ->
        if (index < 2) database.storedValueDatabase.changeCredit(customer.identifier, listOf(1_500L, 2_500L)[index], "Goodwill for the wait", null, adminName)
        else database.storedValueDatabase.loadGiftCard(GiftCardCode.generate(), listOf(2_500L, 5_000L)[index - 2], null, customer.identifier)
    }

    // Commission for those who do the work
    database.reportDatabase.setCommissions(technicians.filter { it.accessLevel == AccessLevel.NORMAL }.map { CommissionRate(it.identifier, listOf(500, 750, 1_000).random()) })

    // Cards through the simulated processor, only a development server takes it
    if (developmentMode && !database.paymentDatabase.settings().enabled) {
        database.paymentDatabase.saveSettings(
            PaymentSettings(
                enabled = true,
                provider = PaymentProvider.SIMULATED,
                readers = listOfNotNull(
                    CardReader("sim_reader_1", "Front counter reader", main.identifier),
                    downtown?.let { CardReader("sim_reader_2", "Downtown reader", it.identifier) }
                ),
                tips = true
            )
        )
    }

    AppLogger.m(TAG, "Seeded schedules for ${staff.size} staff and $booked appointments")
}

/**
 * Weekly shifts for [staff] in a few patterns from [lastMonday] to six weeks on, training and a staff meeting, time
 * off allowances and requests waiting, approved and declined, and the time clock over the shifts so far
 */
@OptIn(ExperimentalUuidApi::class)
private fun seedSchedule(database: LocalDatabase, staff: List<User>, main: Location, lastMonday: LocalDate, now: Long, zone: TimeZone) {
    val today = Instant.fromEpochMilliseconds(now).toLocalDateTime(zone).date
    fun at(day: LocalDate, hour: Int, minute: Int = 0) = LocalDateTime(day, LocalTime(hour, minute)).toInstant(zone).toEpochMilliseconds()
    fun midnight(day: LocalDate) = day.atStartOfDayIn(zone).toEpochMilliseconds()
    fun day(offset: Int) = today.plus(DatePeriod(days = offset))
    fun weekday(from: LocalDate, weeks: Int, dayOfWeek: DayOfWeek) = from.plus(DatePeriod(days = weeks * 7 + dayOfWeek.ordinal))
    val admin = staff.firstOrNull { it.accessLevel == AccessLevel.ADMIN }
    val adminName = admin?.username ?: "admin"
    val tech = staff.firstOrNull { it.username == DEMO_STAFF.first().username }
    fun User.at(): Uuid = homeLocation ?: main.identifier

    // Weekly shifts, in a few patterns so the board isn't all the same
    val schedule = ScheduleService(database.scheduleDatabase, database.userDatabase, database.auditLogDatabase)
    val until = lastMonday.plus(DatePeriod(days = 7 * 7 - 1)).toString()
    staff.forEachIndexed { index, user ->
        val (weekdays, hours) = SHIFT_PATTERNS[index % SHIFT_PATTERNS.size]
        val first = lastMonday.plus(DatePeriod(days = weekdays.first() - 1))
        val entry = ScheduleEntry(userIdentifier = user.identifier, startsAt = at(first, hours.first), endsAt = at(first, hours.last), locationIdentifier = user.at())
        schedule.add(ScheduleRequest(entry, ScheduleRepeat(weekdays, until)), adminName, admin?.identifier, user.at())
    }

    // Training and a staff meeting on top
    tech?.let { user ->
        val thursday = weekday(lastMonday, 2, DayOfWeek.THURSDAY)
        schedule.add(
            ScheduleRequest(ScheduleEntry(userIdentifier = user.identifier, kind = ScheduleKind.TRAINING, startsAt = at(thursday, 13), endsAt = at(thursday, 15), note = "Microsoldering course")),
            adminName, admin?.identifier, user.at()
        )
    }
    val meeting = weekday(lastMonday, 2, DayOfWeek.MONDAY)
    staff.filter { it.at() == main.identifier }.take(4).forEach { user ->
        schedule.add(
            ScheduleRequest(ScheduleEntry(userIdentifier = user.identifier, kind = ScheduleKind.OTHER, startsAt = at(meeting, 8, 30), endsAt = at(meeting, 9), note = "Staff meeting")),
            adminName, admin?.identifier, main.identifier
        )
    }

    // Allowances, and requests waiting, approved (off the schedule like an admin would) and declined
    val timeOff = TimeOffService(database.timeOffDatabase, database.scheduleDatabase, database.userDatabase, database.auditLogDatabase)
    staff.forEach { user -> timeOff.setAllowance(user.identifier, TimeOffAllowance(if (user.accessLevel == AccessLevel.ADMIN) 120.0 else 80.0), admin?.identifier) }

    fun ask(user: User?, first: LocalDate, last: LocalDate, note: String): Uuid? {
        user ?: return null
        val request = TimeOffRequest(startsAt = midnight(first), endsAt = midnight(last.plus(DatePeriod(days = 1))), hours = TimeOffRules.defaultHours(first, last), note = note)
        return (timeOff.request(user, request) as? AccountResult.Done)?.value?.identifier
    }
    fun approve(identifier: Uuid?) {
        val approved = identifier?.let { (timeOff.approve(it, adminName, admin?.identifier, main.identifier) as? AccountResult.Done)?.value } ?: return
        val shifts = database.scheduleDatabase.between(approved.startsAt, approved.endsAt, user = approved.userIdentifier).orEmpty()
            .filter { it.kind == ScheduleKind.SHIFT }
        database.scheduleDatabase.remove(shifts.map { it.identifier })
    }

    val others = staff.filter { it != tech && it != admin }
    val lastWednesday = weekday(lastMonday, 0, DayOfWeek.WEDNESDAY)
    approve(ask(others.getOrNull(0), lastWednesday, lastWednesday, "Dentist"))
    approve(ask(others.getOrNull(1), weekday(lastMonday, 3, DayOfWeek.WEDNESDAY), weekday(lastMonday, 3, DayOfWeek.FRIDAY), "Family trip"))
    ask(tech, weekday(lastMonday, 2, DayOfWeek.THURSDAY).plus(DatePeriod(days = 7)), weekday(lastMonday, 3, DayOfWeek.FRIDAY), "Long weekend")
    ask(others.getOrNull(2), day(10), day(10), "Moving house")
    ask(others.getOrNull(0), weekday(lastMonday, 4, DayOfWeek.MONDAY), weekday(lastMonday, 4, DayOfWeek.TUESDAY), "")?.let {
        timeOff.decline(it, "We're short that week, can you pick another?", adminName, admin?.identifier)
    }

    // The time clock over the shifts so far: mostly on time, a few late, one missed, and whoever's on now clocked in
    var missed = false
    database.scheduleDatabase.between(midnight(lastMonday), now).orEmpty()
        .filter { it.kind == ScheduleKind.SHIFT && it.startsAt < now }
        .forEach { shift ->
            val user = staff.firstOrNull { it.identifier == shift.userIdentifier } ?: return@forEach
            if (!missed && shift.endsAt < now - 2 * DAY && Random.nextInt(6) == 0) {
                missed = true
                return@forEach
            }
            val late = Random.nextInt(8) == 0
            val clockedIn = shift.startsAt + MINUTE * (if (late) Random.nextInt(12, 30) else Random.nextInt(-10, 4))
            val clockedOut = if (shift.endsAt > now) null else shift.endsAt + MINUTE * Random.nextInt(-5, 15)
            database.timeClockDatabase.add(
                TimeEntry(
                    userIdentifier = user.identifier,
                    userName = user.username,
                    clockedInAt = clockedIn,
                    clockedOutAt = clockedOut,
                    station = if (shift.locationIdentifier == main.identifier) "Front counter" else "Downtown counter",
                    note = if (late) "Traffic" else "",
                    locationIdentifier = shift.locationIdentifier
                )
            )
        }
}

/**
 * Suppliers, and an order in each state: received, partly received, placed with a part for a ticket, and a draft,
 * through [PurchaseService] so stock, costs and the ticket's status follow
 */
@OptIn(ExperimentalUuidApi::class)
private fun seedPurchasing(database: LocalDatabase, parts: List<Inventory>, main: Location, downtown: Location?, adminName: String, admin: Uuid?, now: Long) {
    if (!database.purchaseDatabase.suppliers().isNullOrEmpty()) return
    val service = PurchaseService(database.purchaseDatabase, database.inventoryDatabase, database.ticketDatabase, database.shopDatabase, database.auditLogDatabase)
    val suppliers = SUPPLIERS.mapNotNull { (service.addSupplier(it, admin) as? AccountResult.Done)?.value }
    val tracked = parts.filter { it.stock != Int.MAX_VALUE }
    if (suppliers.isEmpty() || tracked.isEmpty()) return

    fun lines(count: Int, ticket: Uuid? = null) = tracked.shuffled().take(count).map {
        PurchaseLine(inventoryIdentifier = it.identifier, description = it.name, quantity = Random.nextInt(2, 11), unitCostCents = it.costCents, ticketIdentifier = ticket)
    }
    fun order(supplier: Supplier, lines: List<PurchaseLine>, note: String = "", location: Uuid = main.identifier) =
        (service.create(PurchaseOrderRequest(supplier.identifier, lines, note, expectedAt = now + 3 * DAY), adminName, admin, location) as? AccountResult.Done)?.value
    fun place(identifier: Uuid) = (service.place(identifier, adminName, admin) as? AccountResult.Done)?.value

    order(suppliers[0], lines(3), "Monthly restock")?.let { place(it.identifier) }?.let { placed ->
        service.receive(placed.identifier, ReceiveRequest(placed.lines.map { ReceivedLine(it.identifier, it.quantity) }), adminName, admin)
    }
    order(suppliers.getOrElse(1) { suppliers[0] }, lines(3))?.let { place(it.identifier) }?.let { placed ->
        service.receive(placed.identifier, ReceiveRequest(listOf(ReceivedLine(placed.lines.first().identifier, placed.lines.first().quantity))), adminName, admin)
    }

    // A part for a ticket waiting on it
    val waiting = database.ticketDatabase.getPagedItems(PagedRequest(offset = 0, itemsPerPage = 50)).orEmpty()
        .firstOrNull { it.status.status == TicketStatusType.IN_PROGRESS && (it.locationIdentifier ?: main.identifier) == main.identifier }
    order(suppliers.last(), lines(1, waiting?.identifier).map { it.copy(quantity = 1) } + lines(1), "Rush")?.let { place(it.identifier) }

    order(suppliers[0], lines(2), "Next order")
    if (downtown != null) order(suppliers.last(), lines(2), "Downtown restock", downtown.identifier)
}

private const val MINUTE = 60_000L
private const val DAY = 86_400_000L

// ISO weekdays and the hours worked, picked in turn
private val SHIFT_PATTERNS = listOf(
    listOf(1, 2, 3, 4, 5) to (9..17),
    listOf(2, 3, 4, 5, 6) to (10..18),
    listOf(1, 3, 5) to (12..20),
    listOf(4, 5, 6) to (9..15)
)

private val SUPPLIERS = listOf(
    Supplier(name = "Mobile Parts Wholesale", contactName = "Dana Ruiz", phone = "(555) 010-0301", email = "orders@mobileparts.example", accountNumber = "MPW-20417"),
    Supplier(name = "Northside Electronics Supply", contactName = "Lee Park", phone = "(555) 010-0302", email = "sales@northside.example", website = "northside.example"),
    Supplier(name = "Coastal Components", phone = "(555) 010-0303", email = "help@coastal.example", notes = "Ships same day before 2pm")
)

private val APPOINTMENT_REASONS = listOf(
    Device(manufacturer = "Apple", model = "iPhone 13") to "Cracked screen",
    Device(manufacturer = "Samsung", model = "Galaxy S22") to "Battery drains by noon",
    Device(manufacturer = "Dell", model = "XPS 13") to "Won't power on",
    Device(manufacturer = "Apple", model = "MacBook Air") to "Keyboard sticking after a spill",
    Device(manufacturer = "Nintendo", model = "Switch") to "Joy-Con drift",
    Device(manufacturer = "Google", model = "Pixel 7") to "Charging port loose",
    Device(manufacturer = "Lenovo", model = "ThinkPad T14") to "Data recovery quote"
)
