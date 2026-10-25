package org.mr0btain.ticketman.components.service

import org.mr0btain.ticketman.components.database.AppointmentDatabase
import org.mr0btain.ticketman.components.database.AuditLogDatabase
import org.mr0btain.ticketman.components.database.CustomerDatabase
import org.mr0btain.ticketman.components.database.UserDatabase
import org.mr0btain.ticketman.components.utils.formatDateTime
import org.mr0btain.ticketman.data.appointment.Appointment
import org.mr0btain.ticketman.data.appointment.AppointmentRules
import org.mr0btain.ticketman.data.appointment.AppointmentStatus
import org.mr0btain.ticketman.data.audit.AuditAction
import org.mr0btain.ticketman.data.user.AccessLevel
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Booking, changing and calling off appointments. Checking one in is the check in's, see [CheckInService]
 */
@OptIn(ExperimentalUuidApi::class)
class AppointmentService(
    private val appointments: AppointmentDatabase,
    private val customers: CustomerDatabase,
    private val users: UserDatabase,
    private val auditLog: AuditLogDatabase
) {
    /** Those starting from [from] to [to], both included, days in [zone], at [location] or every one for null */
    fun between(from: LocalDate, to: LocalDate, zone: ZoneId = ZoneId.systemDefault(), location: Uuid? = null): AccountResult<List<Appointment>> {
        if (to < from) return AccountResult.Invalid("Give from and to as days like 2026-10-01, from first")
        if (ChronoUnit.DAYS.between(from, to) >= AppointmentRules.MAX_RANGE_DAYS) return AccountResult.Invalid("Read up to ${AppointmentRules.MAX_RANGE_DAYS} days at once")

        val start = from.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return appointments.between(start, end, location)?.let { AccountResult.Done(it) } ?: AccountResult.Invalid("Couldn't read the calendar")
    }

    /** Books one at [location], where it was booked from */
    fun add(received: Appointment, createdBy: String, actor: Uuid?, location: Uuid? = null): AccountResult<Appointment> {
        problem(received)?.let { return AccountResult.Invalid(it) }
        if (appointments.get(received.identifier) != null) return AccountResult.Conflict("That appointment already exists")

        val appointment = received.copy(
            status = AppointmentStatus.BOOKED,
            ticketIdentifier = null,
            reminderSentAt = null,
            createdBy = createdBy,
            createdAt = Clock.System.now().toEpochMilliseconds(),
            locationIdentifier = location
        )
        if (!appointments.add(appointment)) return AccountResult.Invalid("Couldn't save the appointment")

        auditLog.log(actor, AuditAction.CREATE, "appointment", appointment.identifier.toString(), "Booked ${describe(appointment)}")
        return AccountResult.Done(appointments.get(appointment.identifier)!!)
    }

    /** Changes a booked one. Moving it to another time sends its reminder again */
    fun update(received: Appointment, actor: Uuid?): AccountResult<Appointment> {
        val existing = appointments.get(received.identifier) ?: return AccountResult.NotFound
        if (!existing.isOpen) return AccountResult.Conflict("Only booked appointments can be changed, this one is ${existing.status.label.lowercase()}")
        problem(received)?.let { return AccountResult.Invalid(it) }

        if (!appointments.update(received)) return AccountResult.NotFound
        if (existing.startsAt != received.startsAt) appointments.clearReminder(received.identifier)

        auditLog.log(actor, AuditAction.UPDATE, "appointment", received.identifier.toString(), "Changed ${describe(received)}")
        return AccountResult.Done(appointments.get(received.identifier)!!)
    }

    /** Cancels it, marks it a no show, or books it again. Checked in ones stay as they are */
    fun setStatus(identifier: Uuid, status: AppointmentStatus, actor: Uuid?): AccountResult<Appointment> {
        if (status == AppointmentStatus.ARRIVED) return AccountResult.Invalid("Check it in at the counter")
        val existing = appointments.get(identifier) ?: return AccountResult.NotFound
        if (existing.status == AppointmentStatus.ARRIVED) return AccountResult.Conflict("It was checked in already")

        if (existing.status != status) {
            if (!appointments.setStatus(identifier, status)) return AccountResult.NotFound
            auditLog.log(actor, AuditAction.UPDATE, "appointment", identifier.toString(), "${describe(existing)}: ${status.label.lowercase()}")
        }

        return AccountResult.Done(appointments.get(identifier)!!)
    }

    fun remove(identifier: Uuid, actor: Uuid?): AccountResult<Unit> {
        val existing = appointments.get(identifier) ?: return AccountResult.NotFound
        if (!appointments.remove(identifier)) return AccountResult.NotFound

        auditLog.log(actor, AuditAction.DELETE, "appointment", identifier.toString(), "Deleted ${describe(existing)}")
        return AccountResult.Done(Unit)
    }

    private fun problem(appointment: Appointment): String? {
        appointment.problem()?.let { return it }
        appointment.customerIdentifier?.let { if (customers.getById(it) == null) return "That customer doesn't exist" }
        appointment.technicianIdentifier?.let { identifier ->
            val technician = users.getById(identifier)
            if (technician == null || !technician.canAccess(AccessLevel.NORMAL)) return "That technician can't take tickets"
        }
        return null
    }

    private fun describe(appointment: Appointment): String =
        "${appointment.customerName.ifBlank { "an appointment" }} at ${formatDateTime(appointment.startsAt)}"
}
