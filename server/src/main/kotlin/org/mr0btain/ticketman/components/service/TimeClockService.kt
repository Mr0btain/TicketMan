package org.mr0btain.ticketman.components.service

import org.mr0btain.ticketman.components.database.AuditLogDatabase
import org.mr0btain.ticketman.components.database.TimeClockDatabase
import org.mr0btain.ticketman.components.database.UserDatabase
import org.mr0btain.ticketman.components.utils.formatDateTime
import org.mr0btain.ticketman.components.utils.formatHours
import org.mr0btain.ticketman.data.audit.AuditAction
import org.mr0btain.ticketman.data.timeclock.PunchResult
import org.mr0btain.ticketman.data.timeclock.TimeClockRules
import org.mr0btain.ticketman.data.timeclock.TimeEntry
import org.mr0btain.ticketman.data.timeclock.Timesheet
import org.mr0btain.ticketman.data.timeclock.TimesheetPerson
import org.mr0btain.ticketman.data.user.User
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Clocking in and out, timesheets, and admins fixing the times
 * @param now Epoch millis, swapped in tests
 */
@OptIn(ExperimentalUuidApi::class)
class TimeClockService(
    private val entries: TimeClockDatabase,
    private val users: UserDatabase,
    private val auditLog: AuditLogDatabase,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() }
) {
    /**
     * Clocks [user] out when they're on the clock, otherwise in. One at a time across the server, so a double tap
     * can't leave two stretches open
     * @param station Where, blank from their own device
     */
    fun punch(user: User, station: String): AccountResult<PunchResult> = synchronized(PUNCH_LOCK) {
        val time = now()
        val open = entries.open(user.identifier)
        val where = station.ifBlank { "their own device" }

        if (open != null) {
            if (!entries.clockOut(open.identifier, time)) return AccountResult.Invalid("Couldn't clock out")
            val closed = entries.get(open.identifier)!!
            auditLog.log(user.identifier, AuditAction.UPDATE, "time", open.identifier.toString(), "Clocked out at $where after ${formatHours(closed.hours(time))}")
            return AccountResult.Done(PunchResult(closed, clockedIn = false))
        }

        val entry = TimeEntry(userIdentifier = user.identifier, userName = user.name.ifBlank { user.username }, clockedInAt = time, station = station)
        if (!entries.add(entry)) return AccountResult.Invalid("Couldn't clock in")
        auditLog.log(user.identifier, AuditAction.CREATE, "time", entry.identifier.toString(), "Clocked in at $where")
        return AccountResult.Done(PunchResult(entries.get(entry.identifier)!!, clockedIn = true))
    }

    /** Time started from [from] to [to], both included, days in [zone], only [user]'s when given */
    fun timesheet(from: LocalDate, to: LocalDate, user: Uuid? = null, zone: ZoneId = ZoneId.systemDefault()): AccountResult<Timesheet> {
        if (to < from) return AccountResult.Invalid("Give from and to as days like 2026-10-01, from first")
        if (ChronoUnit.DAYS.between(from, to) >= TimeClockRules.MAX_DAYS) return AccountResult.Invalid("A timesheet covers at most ${TimeClockRules.MAX_DAYS} days")

        val start = from.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val list = entries.between(start, end, user) ?: return AccountResult.Invalid("Couldn't read the time clock")
        val time = now()

        val people = list
            .groupBy { it.userIdentifier }
            .map { (identifier, own) -> TimesheetPerson(identifier, own.last().userName, own.sumOf { it.hours(time) }, own) }
            .sortedBy { it.name.lowercase() }

        return AccountResult.Done(Timesheet(from.toString(), to.toString(), people, time))
    }

    /** A stretch an admin adds by hand, like a forgotten clock in */
    fun add(received: TimeEntry, editedBy: String, actor: Uuid?): AccountResult<TimeEntry> = synchronized(PUNCH_LOCK) {
        val user = users.getById(received.userIdentifier) ?: return AccountResult.Invalid("That user doesn't exist")
        val entry = received.copy(userName = user.name.ifBlank { user.username }, editedBy = editedBy)
        problem(entry)?.let { return AccountResult.Invalid(it) }
        if (entries.get(entry.identifier) != null) return AccountResult.Conflict("That entry already exists")

        if (!entries.add(entry)) return AccountResult.Invalid("Couldn't save it")
        auditLog.log(actor, AuditAction.CREATE, "time", entry.identifier.toString(), "Added time for ${entry.userName}: ${describe(entry)}")
        return AccountResult.Done(entries.get(entry.identifier)!!)
    }

    /** An admin fixing the times or note. Whose time it is stays */
    fun update(received: TimeEntry, editedBy: String, actor: Uuid?): AccountResult<TimeEntry> = synchronized(PUNCH_LOCK) {
        val existing = entries.get(received.identifier) ?: return AccountResult.NotFound
        val entry = existing.copy(clockedInAt = received.clockedInAt, clockedOutAt = received.clockedOutAt, note = received.note, editedBy = editedBy)
        problem(entry)?.let { return AccountResult.Invalid(it) }

        if (!entries.update(entry)) return AccountResult.NotFound
        auditLog.log(actor, AuditAction.UPDATE, "time", entry.identifier.toString(), "Changed ${entry.userName}'s time from ${describe(existing)} to ${describe(entry)}")
        return AccountResult.Done(entries.get(entry.identifier)!!)
    }

    fun remove(identifier: Uuid, actor: Uuid?): AccountResult<Unit> {
        val existing = entries.get(identifier) ?: return AccountResult.NotFound
        if (!entries.remove(identifier)) return AccountResult.NotFound

        auditLog.log(actor, AuditAction.DELETE, "time", identifier.toString(), "Deleted ${existing.userName}'s time ${describe(existing)}")
        return AccountResult.Done(Unit)
    }

    private fun problem(entry: TimeEntry): String? {
        entry.problem()?.let { return it }
        val time = now()
        if (entry.clockedInAt > time || (entry.clockedOutAt ?: 0) > time) return "That's in the future"
        if (entry.isOpen && entries.open(entry.userIdentifier)?.let { it.identifier != entry.identifier } == true) return "They're on the clock already"
        if (entries.overlaps(entry, time)) return "That overlaps their other time"
        return null
    }

    private fun describe(entry: TimeEntry): String =
        formatDateTime(entry.clockedInAt) + " to " + (entry.clockedOutAt?.let { formatDateTime(it) } ?: "now")

    private companion object {
        val PUNCH_LOCK = Any()
    }
}
