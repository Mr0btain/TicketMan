package org.mr0btain.ticketman.components.service

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import org.mr0btain.ticketman.components.database.AuditLogDatabase
import org.mr0btain.ticketman.components.database.ScheduleDatabase
import org.mr0btain.ticketman.components.database.UserDatabase
import org.mr0btain.ticketman.components.utils.formatTime
import org.mr0btain.ticketman.data.audit.AuditAction
import org.mr0btain.ticketman.data.schedule.ScheduleEntry
import org.mr0btain.ticketman.data.schedule.ScheduleRequest
import org.mr0btain.ticketman.data.schedule.ScheduleRules
import org.mr0btain.ticketman.data.schedule.ScheduleScope
import org.mr0btain.ticketman.data.user.UserStatus
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * The staff schedule: reading a range, and admins writing shifts, time off and the like, a weekly repeat written as
 * a series of entries. Days are in the shop's time zone (the server's `TZ`)
 */
@OptIn(ExperimentalUuidApi::class)
class ScheduleService(
    private val schedule: ScheduleDatabase,
    private val users: UserDatabase,
    private val auditLog: AuditLogDatabase,
    private val zone: () -> TimeZone = { TimeZone.currentSystemDefault() },
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() }
) {
    /** Entries touching [from] to [to], both included, at [location] (every one for null), only [user]'s if given */
    fun between(from: LocalDate, to: LocalDate, location: Uuid? = null, user: Uuid? = null): AccountResult<List<ScheduleEntry>> {
        if (to < from) return AccountResult.Invalid("Give from and to as days like 2026-10-01, from first")
        if (to.toEpochDays() - from.toEpochDays() >= ScheduleRules.MAX_RANGE_DAYS) return AccountResult.Invalid("Read up to ${ScheduleRules.MAX_RANGE_DAYS} days at once")

        val start = from.atStartOfDayIn(zone()).toEpochMilliseconds()
        val end = to.plus(DatePeriod(days = 1)).atStartOfDayIn(zone()).toEpochMilliseconds()
        return schedule.between(start, end, location, user)?.let { AccountResult.Done(it) } ?: AccountResult.Invalid("Couldn't read the schedule")
    }

    /**
     * Writes a new entry, or a weekly series of them
     * @param location Where it is when the entry doesn't say, where the admin works
     */
    fun add(request: ScheduleRequest, by: String, actor: Uuid?, location: Uuid?): AccountResult<List<ScheduleEntry>> {
        val entry = normalized(request.entry).copy(
            seriesIdentifier = null,
            requestIdentifier = null,
            locationIdentifier = request.entry.locationIdentifier ?: location,
            createdBy = by,
            createdAt = now()
        )
        problem(entry)?.let { return AccountResult.Invalid(it) }
        if (schedule.get(entry.identifier) != null) return AccountResult.Conflict("That entry already exists")
        if (request.repeat != null && entry.allDay) return AccountResult.Invalid("Only shifts and other timed entries repeat; give time off its first and last days")

        val entries = ScheduleRules.expand(request.copy(entry = entry), zone())
            ?: return AccountResult.Invalid("Pick at least one weekday and a last day after the first, up to ${ScheduleRules.MAX_REPEATS} times")
        if (!schedule.add(entries)) return AccountResult.Invalid("Couldn't save the schedule")

        val repeats = if (entries.size > 1) ", ${entries.size} times through ${request.repeat?.until}" else ""
        auditLog.log(actor, AuditAction.CREATE, "schedule", entry.identifier.toString(), "Scheduled ${describe(entry)}$repeats")
        return AccountResult.Done(entries.mapNotNull { schedule.get(it.identifier) })
    }

    /**
     * Changes an entry, and with [scope] the rest of its series or the later ones: they take its person, kind, note
     * and location, and move by as much as it moved, keeping their own days, at its new length
     */
    fun update(received: ScheduleEntry, scope: ScheduleScope, actor: Uuid?): AccountResult<List<ScheduleEntry>> {
        val existing = schedule.get(received.identifier) ?: return AccountResult.NotFound
        val entry = normalized(received).copy(
            seriesIdentifier = existing.seriesIdentifier,
            createdBy = existing.createdBy,
            createdAt = existing.createdAt,
            requestIdentifier = existing.requestIdentifier
        )
        problem(entry)?.let { return AccountResult.Invalid(it) }

        val shift = entry.startsAt - existing.startsAt
        val changed = targets(existing, scope).map { target ->
            if (target.identifier == entry.identifier) entry
            else target.copy(
                userIdentifier = entry.userIdentifier,
                kind = entry.kind,
                allDay = entry.allDay,
                note = entry.note,
                locationIdentifier = entry.locationIdentifier,
                startsAt = target.startsAt + shift,
                endsAt = target.startsAt + shift + entry.millis
            )
        }
        if (!schedule.update(changed)) return AccountResult.Invalid("Couldn't save the schedule")

        val many = if (changed.size > 1) " and ${changed.size - 1} more in its series" else ""
        auditLog.log(actor, AuditAction.UPDATE, "schedule", entry.identifier.toString(), "Changed ${describe(entry)}$many")
        return AccountResult.Done(changed.mapNotNull { schedule.get(it.identifier) })
    }

    /** Deletes an entry, and with [scope] the later ones or its whole series */
    fun remove(identifier: Uuid, scope: ScheduleScope, actor: Uuid?): AccountResult<Unit> {
        val existing = schedule.get(identifier) ?: return AccountResult.NotFound
        val gone = targets(existing, scope)
        if (!schedule.remove(gone.map { it.identifier })) return AccountResult.NotFound

        val many = if (gone.size > 1) " and ${gone.size - 1} more in its series" else ""
        auditLog.log(actor, AuditAction.DELETE, "schedule", identifier.toString(), "Took off ${describe(existing)}$many")
        return AccountResult.Done(Unit)
    }

    // The entries a change to [entry] goes to
    private fun targets(entry: ScheduleEntry, scope: ScheduleScope): List<ScheduleEntry> {
        val series = entry.seriesIdentifier?.takeIf { scope != ScheduleScope.ONE } ?: return listOf(entry)
        return schedule.series(series).filter { scope == ScheduleScope.SERIES || it.startsAt >= entry.startsAt }
    }

    // Whole day entries run midnight to midnight, however they were sent
    private fun normalized(entry: ScheduleEntry): ScheduleEntry {
        if (!entry.allDay) return entry.copy(note = entry.note.trim())
        val zone = zone()
        val first = Instant.fromEpochMilliseconds(entry.startsAt).toLocalDateTime(zone).date
        val last = Instant.fromEpochMilliseconds((entry.endsAt - 1).coerceAtLeast(entry.startsAt)).toLocalDateTime(zone).date
        return entry.copy(
            startsAt = first.atStartOfDayIn(zone).toEpochMilliseconds(),
            endsAt = last.plus(DatePeriod(days = 1)).atStartOfDayIn(zone).toEpochMilliseconds(),
            note = entry.note.trim()
        )
    }

    private fun problem(entry: ScheduleEntry): String? {
        entry.problem()?.let { return it }
        val user = users.getById(entry.userIdentifier) ?: return "That person isn't on the staff"
        if (user.status == UserStatus.DEACTIVATED) return "${user.name.ifBlank { user.username }} is deactivated"
        return null
    }

    private fun describe(entry: ScheduleEntry): String {
        val who = users.getById(entry.userIdentifier)?.let { it.name.ifBlank { it.username } } ?: "someone"
        val day = Instant.fromEpochMilliseconds(entry.startsAt).toLocalDateTime(zone()).date
        val days = entry.days(zone())
        return if (entry.allDay) "$who: ${entry.kind.label} $day" + (if (days.size > 1) " to ${days.last()}" else "")
        else "$who: ${entry.kind.label} $day ${formatTime(entry.startsAt)} to ${formatTime(entry.endsAt)}"
    }
}
