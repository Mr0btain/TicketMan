package org.mr0btain.ticketman.components.service

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.mr0btain.ticketman.components.database.AuditLogDatabase
import org.mr0btain.ticketman.components.database.ScheduleDatabase
import org.mr0btain.ticketman.components.database.TimeOffDatabase
import org.mr0btain.ticketman.components.database.UserDatabase
import org.mr0btain.ticketman.data.audit.AuditAction
import org.mr0btain.ticketman.data.schedule.MyTimeOff
import org.mr0btain.ticketman.data.schedule.ScheduleEntry
import org.mr0btain.ticketman.data.schedule.ScheduleKind
import org.mr0btain.ticketman.data.schedule.ScheduleRules
import org.mr0btain.ticketman.data.schedule.TimeOffBalance
import org.mr0btain.ticketman.data.schedule.TimeOffRequest
import org.mr0btain.ticketman.data.schedule.TimeOffRules
import org.mr0btain.ticketman.data.schedule.TimeOffStatus
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.data.user.UserStatus
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Time off requests against each person's yearly allowance: staff ask, admins approve (which puts the time off on
 * the schedule) or decline. A year's allowance starts over on 1 January with nothing carried over; a request counts
 * in the year its first day is in
 */
@OptIn(ExperimentalUuidApi::class)
class TimeOffService(
    private val timeOff: TimeOffDatabase,
    private val schedule: ScheduleDatabase,
    private val users: UserDatabase,
    private val auditLog: AuditLogDatabase,
    private val zone: () -> TimeZone = { TimeZone.currentSystemDefault() },
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() }
) {
    /** The year it is now */
    fun thisYear(): Int = Instant.fromEpochMilliseconds(now()).toLocalDateTime(zone()).year

    fun balance(user: User, year: Int): TimeOffBalance {
        val (from, to) = yearRange(year)
        return TimeOffBalance(
            userIdentifier = user.identifier,
            userName = user.name.ifBlank { user.username },
            year = year,
            allowanceHours = timeOff.allowance(user.identifier),
            takenHours = timeOff.takenHours(from, to, listOf(user.identifier))[user.identifier] ?: 0.0,
            pendingHours = timeOff.pendingHours(from, to, listOf(user.identifier))[user.identifier] ?: 0.0
        )
    }

    /** Every active user's balance for [year], by name */
    fun balances(year: Int): List<TimeOffBalance> {
        val (from, to) = yearRange(year)
        val allowances = timeOff.allowances()
        val taken = timeOff.takenHours(from, to)
        val pending = timeOff.pendingHours(from, to)
        return users.getPagedItems(org.mr0btain.ticketman.data.shared.PagedRequest(0, MAX_USERS)).orEmpty()
            .filter { it.status == UserStatus.ACTIVE }
            .map { user ->
                TimeOffBalance(
                    userIdentifier = user.identifier,
                    userName = user.name.ifBlank { user.username },
                    year = year,
                    allowanceHours = allowances[user.identifier] ?: 0.0,
                    takenHours = taken[user.identifier] ?: 0.0,
                    pendingHours = pending[user.identifier] ?: 0.0
                )
            }
            .sortedBy { it.userName.lowercase() }
    }

    /** The caller's balance and requests, newest first */
    fun mine(user: User, year: Int): MyTimeOff =
        MyTimeOff(balance(user, year), timeOff.list(user = user.identifier, limit = MY_LIMIT).orEmpty())

    /**
     * Asks for time off for [user]: whole days, taking its hours from what's left of the year's allowance
     */
    fun request(user: User, received: TimeOffRequest): AccountResult<TimeOffRequest> {
        if (user.status != UserStatus.ACTIVE) return AccountResult.Invalid("Only active staff ask for time off")
        val request = wholeDays(received).copy(
            userIdentifier = user.identifier,
            status = TimeOffStatus.PENDING,
            note = received.note.trim(),
            createdAt = now(),
            decidedBy = "",
            decidedAt = null,
            reason = "",
            entryIdentifier = null
        )
        request.problem()?.let { return AccountResult.Invalid(it) }
        if (timeOff.get(request.identifier) != null) return AccountResult.Conflict("That request already exists")

        val balance = balance(user, request.year(zone()))
        if (balance.allowanceHours <= 0) return AccountResult.Conflict("You have no time off allowance for ${balance.year} yet, ask an admin to set one")
        if (request.hours > balance.leftHours) {
            return AccountResult.Conflict("Only ${ScheduleRules.hoursText(balance.leftHours)} left for ${balance.year}" +
                if (balance.pendingHours > 0) ", with ${ScheduleRules.hoursText(balance.pendingHours)} waiting" else "")
        }

        if (!timeOff.add(request)) return AccountResult.Invalid("Couldn't save the request")
        auditLog.log(user.identifier, AuditAction.CREATE, "time off", request.identifier.toString(), "Asked for ${describe(request)}")
        return AccountResult.Done(timeOff.get(request.identifier)!!)
    }

    /** Takes back [user]'s own request while it's still waiting */
    fun withdraw(user: User, identifier: Uuid): AccountResult<TimeOffRequest> {
        val request = timeOff.get(identifier)?.takeIf { it.userIdentifier == user.identifier } ?: return AccountResult.NotFound
        if (!timeOff.decide(identifier, TimeOffStatus.WITHDRAWN, user.username, now())) return AccountResult.Conflict("It was already ${request.status.label.lowercase()}")
        auditLog.log(user.identifier, AuditAction.UPDATE, "time off", identifier.toString(), "Withdrew ${describe(request)}")
        return AccountResult.Done(timeOff.get(identifier)!!)
    }

    /**
     * Approves a waiting request: it's marked approved and its time off goes on the schedule, together
     * @param location Where the time off is when the person has no home location, where the admin works
     */
    fun approve(identifier: Uuid, by: String, actor: Uuid?, location: Uuid?): AccountResult<TimeOffRequest> {
        val request = timeOff.get(identifier) ?: return AccountResult.NotFound
        if (request.status != TimeOffStatus.PENDING) return AccountResult.Conflict("It was already ${request.status.label.lowercase()}")
        val user = request.userIdentifier?.let { users.getById(it) } ?: return AccountResult.Invalid("They're not on the staff any more")

        val entry = ScheduleEntry(
            userIdentifier = user.identifier,
            kind = ScheduleKind.TIME_OFF,
            startsAt = request.startsAt,
            endsAt = request.endsAt,
            allDay = true,
            note = request.note.ifBlank { "Time off" },
            locationIdentifier = user.homeLocation ?: location,
            createdBy = by,
            createdAt = now(),
            timeOffHours = request.hours,
            requestIdentifier = request.identifier
        )

        val approved = try {
            transaction {
                if (!timeOff.decide(identifier, TimeOffStatus.APPROVED, by, now(), entry = entry.identifier)) throw AlreadyAnswered()
                if (!schedule.add(listOf(entry))) throw IllegalStateException("Time off entry not saved")
                auditLog.log(actor, AuditAction.UPDATE, "time off", identifier.toString(), "Approved ${describe(request)}")
                true
            }
        } catch (_: AlreadyAnswered) {
            false
        }
        if (!approved) return AccountResult.Conflict("It was answered meanwhile")
        return AccountResult.Done(timeOff.get(identifier)!!)
    }

    fun decline(identifier: Uuid, reason: String, by: String, actor: Uuid?): AccountResult<TimeOffRequest> {
        val request = timeOff.get(identifier) ?: return AccountResult.NotFound
        if (reason.length > ScheduleRules.MAX_NOTE) return AccountResult.Invalid("The reason is too long")
        if (!timeOff.decide(identifier, TimeOffStatus.DECLINED, by, now(), reason.trim())) return AccountResult.Conflict("It was already ${request.status.label.lowercase()}")
        auditLog.log(actor, AuditAction.UPDATE, "time off", identifier.toString(), "Declined ${describe(request)}" + reason.trim().ifBlank { null }?.let { ": $it" }.orEmpty())
        return AccountResult.Done(timeOff.get(identifier)!!)
    }

    /** Sets someone's yearly allowance in hours */
    fun setAllowance(userIdentifier: Uuid, hours: Double, actor: Uuid?): AccountResult<TimeOffBalance> {
        val user = users.getById(userIdentifier) ?: return AccountResult.NotFound
        if (hours < 0 || hours > TimeOffRules.MAX_ALLOWANCE_HOURS || hours.isNaN()) return AccountResult.Invalid("An allowance is 0 to ${TimeOffRules.MAX_ALLOWANCE_HOURS.toInt()} hours a year")
        if (!timeOff.setAllowance(userIdentifier, hours)) return AccountResult.Invalid("Couldn't save the allowance")
        auditLog.log(actor, AuditAction.UPDATE, "time off", userIdentifier.toString(), "${user.name.ifBlank { user.username }}'s allowance is ${ScheduleRules.hoursText(hours)} a year")
        return AccountResult.Done(balance(user, thisYear()))
    }

    private class AlreadyAnswered : Exception()

    // Midnight to midnight of the days it covers, however it was sent
    private fun wholeDays(request: TimeOffRequest): TimeOffRequest {
        val zone = zone()
        val first = Instant.fromEpochMilliseconds(request.startsAt).toLocalDateTime(zone).date
        val last = Instant.fromEpochMilliseconds((request.endsAt - 1).coerceAtLeast(request.startsAt)).toLocalDateTime(zone).date
        return request.copy(
            startsAt = first.atStartOfDayIn(zone).toEpochMilliseconds(),
            endsAt = last.plus(DatePeriod(days = 1)).atStartOfDayIn(zone).toEpochMilliseconds()
        )
    }

    private fun yearRange(year: Int): Pair<Long, Long> = TimeOffRules.yearStart(year, zone()) to TimeOffRules.yearStart(year + 1, zone())

    private fun describe(request: TimeOffRequest): String {
        val who = request.userIdentifier?.let { users.getById(it) }?.let { it.name.ifBlank { it.username } } ?: "someone"
        val first = Instant.fromEpochMilliseconds(request.startsAt).toLocalDateTime(zone()).date
        val last = Instant.fromEpochMilliseconds(request.endsAt - 1).toLocalDateTime(zone()).date
        return "$who: time off $first" + (if (last != first) " to $last" else "") + ", ${ScheduleRules.hoursText(request.hours)}"
    }

    private companion object {
        const val MAX_USERS = 500
        const val MY_LIMIT = 50
    }
}
