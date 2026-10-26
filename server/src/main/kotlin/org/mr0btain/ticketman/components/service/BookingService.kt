package org.mr0btain.ticketman.components.service

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import org.mr0btain.ticketman.components.database.AppointmentDatabase
import org.mr0btain.ticketman.components.database.LocationDatabase
import org.mr0btain.ticketman.components.database.ScheduleDatabase
import org.mr0btain.ticketman.components.database.ShopDatabase
import org.mr0btain.ticketman.data.appointment.Appointment
import org.mr0btain.ticketman.data.appointment.AppointmentStatus
import org.mr0btain.ticketman.data.appointment.BookingRules
import org.mr0btain.ticketman.data.location.Location
import org.mr0btain.ticketman.data.schedule.ScheduleKind
import org.mr0btain.ticketman.data.shop.ShopProfile
import org.mr0btain.ticketman.data.ticket.Device
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Customers booking a visit from the status page, when the shop turns it on. The times offered are inside the
 * shifts on the staff schedule at a location, one visit per person working at a time, less what's already booked
 * and anyone off then. Booking checks the time is still free and books it under one lock
 */
@OptIn(ExperimentalUuidApi::class)
class BookingService(
    private val appointments: AppointmentDatabase,
    private val appointmentService: AppointmentService,
    private val schedule: ScheduleDatabase,
    private val shop: ShopDatabase,
    private val locations: LocationDatabase,
    private val zone: () -> TimeZone = { TimeZone.currentSystemDefault() },
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    // Each booking puts someone on the calendar, so a script can't fill it
    private val bookingsByAddress: RequestLimiter = RequestLimiter(BOOKINGS_PER_ADDRESS, 1.hours)
) {
    /** A day with times free on it */
    data class Day(val date: LocalDate, val times: List<Long>)

    /**
     * What the booking page shows
     * @param profile The shop's profile as it is at [location]
     * @param locations The live ones, to pick from when there's more than one
     */
    data class Choices(val profile: ShopProfile, val locations: List<Location>, val location: Location, val days: List<Day>)

    /** What the customer typed */
    data class Form(
        val location: Uuid? = null,
        val startsAt: Long? = null,
        val name: String = "",
        val phone: String = "",
        val email: String = "",
        val device: String = "",
        val problem: String = ""
    )

    sealed interface Booked {
        data class Done(val appointment: Appointment, val choices: Choices) : Booked
        data class Problem(val message: String, val choices: Choices) : Booked
        data object Limited : Booked
        data object Off : Booked
    }

    /** The times free at [locationIdentifier] (the main location when null or not one), null when booking is off */
    fun choices(locationIdentifier: Uuid?): Choices? {
        val settings = shop.get().booking
        if (!settings.enabled) return null

        val live = locations.all().filter { !it.retired }
        val location = live.firstOrNull { it.identifier == locationIdentifier } ?: live.firstOrNull { it.main } ?: locations.main()
        val zone = zone()
        val today = Instant.fromEpochMilliseconds(now()).toLocalDateTime(zone).date
        val end = today.plus(DatePeriod(days = settings.daysAhead)).atStartOfDayIn(zone).toEpochMilliseconds()

        val days = openTimes(location, now() + settings.leadMinutes * 60_000L, end, settings.minutes)
            .groupBy { Instant.fromEpochMilliseconds(it).toLocalDateTime(zone).date }
            .map { (date, times) -> Day(date, times) }

        return Choices(shop.at(location.identifier), live, location, days)
    }

    /** Books [form]'s time for the customer, if it's still free */
    fun book(form: Form, address: String): Booked {
        if (!bookingsByAddress.tryAcquire(address)) return Booked.Limited
        val choices = choices(form.location) ?: return Booked.Off
        fun problem(message: String) = Booked.Problem(message, choices)

        val name = form.name.trim()
        val phone = form.phone.trim()
        val email = form.email.trim()
        when {
            name.isBlank() -> return problem("Type your name")
            phone.count { it.isDigit() } < MIN_PHONE_DIGITS && !EMAIL.matches(email) -> return problem("Give a phone number or an email address, so we can reach you")
            email.isNotEmpty() && !EMAIL.matches(email) -> return problem("That email address doesn't look right")
            listOf(name, phone, email, form.device).any { it.length > BookingRules.MAX_FIELD } || form.problem.length > BookingRules.MAX_PROBLEM -> return problem("That's too long")
        }
        val startsAt = form.startsAt ?: return problem("Pick a time")

        val minutes = shop.get().booking.minutes
        return synchronized(LOCK) {
            // Still free, with the latest bookings
            val free = choices.days.any { startsAt in it.times } &&
                startsAt in openTimes(choices.location, startsAt, startsAt + 1, minutes)
            if (!free) return@synchronized problem("Someone just took that time, pick another")

            val appointment = Appointment(
                startsAt = startsAt,
                minutes = minutes,
                customerName = name,
                phone = phone,
                email = email,
                device = Device(model = form.device.trim()),
                problem = form.problem.trim(),
                notes = "Booked online"
            )
            when (val result = appointmentService.add(appointment, CUSTOMER, actor = null, location = choices.location.identifier)) {
                is AccountResult.Done -> Booked.Done(result.value, choices)
                is AccountResult.Invalid -> problem(result.message)
                is AccountResult.Conflict -> problem(result.message)
                else -> problem("That didn't go through, try again")
            }
        }
    }

    // Times starting from [earliest] up to [latest] at [location] that still take a visit
    private fun openTimes(location: Location, earliest: Long, latest: Long, minutes: Int): List<Long> {
        val step = minutes * 60_000L
        val entries = schedule.between(earliest - DAY, latest + step).orEmpty()
        val off = entries.filter { it.kind == ScheduleKind.TIME_OFF }
        val shifts = entries
            .filter { it.kind == ScheduleKind.SHIFT && it.locationIdentifier.at(location) }
            .filter { shift -> off.none { it.userIdentifier == shift.userIdentifier && it.startsAt < shift.endsAt && it.endsAt > shift.startsAt } }
            .map { it.startsAt to it.endsAt }
        if (shifts.isEmpty()) return emptyList()

        val booked = appointments.between(earliest - DAY, latest + step, location.identifier).orEmpty()
            .filter { it.status == AppointmentStatus.BOOKED || it.status == AppointmentStatus.ARRIVED }
            .map { it.startsAt to it.endsAt }

        val zone = zone()
        return BookingRules.openTimes(shifts, booked, minutes, earliest, latest) { time ->
            Instant.fromEpochMilliseconds(time).toLocalDateTime(zone).date.atStartOfDayIn(zone).toEpochMilliseconds()
        }
    }

    // An entry with no location is the main one's
    private fun Uuid?.at(location: Location): Boolean = this == location.identifier || (this == null && location.main)

    companion object {
        /** Who online bookings are booked by */
        const val CUSTOMER = "customer"

        const val BOOKINGS_PER_ADDRESS = 10
        private const val MIN_PHONE_DIGITS = 7
        private const val DAY = 86_400_000L
        private val EMAIL = Regex("""[^@\s]+@[^@\s]+\.[^@\s]+""")
        private val LOCK = Any()
    }
}
