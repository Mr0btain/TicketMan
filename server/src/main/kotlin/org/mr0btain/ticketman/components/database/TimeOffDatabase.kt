package org.mr0btain.ticketman.components.database

import org.mr0btain.ticketman.data.schedule.TimeOffAllowance
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert
import org.mr0btain.ticketman.components.database.tables.schedule.ScheduleTable
import org.mr0btain.ticketman.components.database.tables.schedule.TimeOffAllowanceTable
import org.mr0btain.ticketman.components.database.tables.schedule.TimeOffRequestTable
import org.mr0btain.ticketman.components.database.tables.user.UserTable
import org.mr0btain.ticketman.data.schedule.ScheduleKind
import org.mr0btain.ticketman.data.schedule.TimeOffRequest
import org.mr0btain.ticketman.data.schedule.TimeOffStatus
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Time off allowances and requests. A request's status only leaves `PENDING` once, in an update that checks it's
 * still waiting, so two admins answering at once can't both
 */
@OptIn(ExperimentalUuidApi::class)
@Suppress("PrivatePropertyName")
class TimeOffDatabase {
    private val TAG = "TimeOffDatabase"
    private val GET_TAG = "$TAG:Get"
    private val WRITE_TAG = "$TAG:Write"
    private val NO_ALLOWANCE = TimeOffAllowance(0.0, 0.0, 0.0)

    /** Each person's yearly allowance, those with one set */
    fun allowances(): Map<Uuid, TimeOffAllowance> =
        dbQuery(GET_TAG) { TimeOffAllowanceTable.selectAll().associate { it[TimeOffAllowanceTable.user] to it.toAllowance() } }.orEmpty()

    fun allowance(user: Uuid): TimeOffAllowance =
        dbQuery(GET_TAG) {
            TimeOffAllowanceTable.selectAll().where { TimeOffAllowanceTable.user eq user }.firstOrNull()?.toAllowance()
        } ?: NO_ALLOWANCE

    private fun ResultRow.toAllowance() =
        TimeOffAllowance(this[TimeOffAllowanceTable.hours], this[TimeOffAllowanceTable.carryOverMax], this[TimeOffAllowanceTable.floating])

    /** Sets someone's allowance. A null carry over or floating holidays leaves what's saved */
    fun setAllowance(user: Uuid, allowance: TimeOffAllowance): Boolean =
        dbWrite(WRITE_TAG) {
            TimeOffAllowanceTable.upsert { row ->
                row[TimeOffAllowanceTable.user] = user
                row[hours] = allowance.hours
                allowance.carryOverMaxHours?.let { row[carryOverMax] = it }
                allowance.floatingHours?.let { row[floating] = it }
            }
            true
        }

    /** Hours of time off on the schedule starting from [fromMillis] up to [toMillis], by person */
    fun takenHours(fromMillis: Long, toMillis: Long, users: List<Uuid>? = null): Map<Uuid, Double> =
        dbQuery(GET_TAG) {
            ScheduleTable.select(ScheduleTable.user, ScheduleTable.timeOffHours)
                .where {
                    (ScheduleTable.kind eq ScheduleKind.TIME_OFF.name) and (ScheduleTable.startsAt greaterEq fromMillis) and
                        (ScheduleTable.startsAt less toMillis) and (users?.let { ScheduleTable.user inList it } ?: Op.TRUE)
                }
                .groupBy({ it[ScheduleTable.user] }, { it[ScheduleTable.timeOffHours] })
                .mapValues { (_, hours) -> hours.sum() }
        }.orEmpty()

    /** Hours asked for and still waiting starting from [fromMillis] up to [toMillis], by person */
    fun pendingHours(fromMillis: Long, toMillis: Long, users: List<Uuid>? = null): Map<Uuid, Double> =
        dbQuery(GET_TAG) {
            TimeOffRequestTable.select(TimeOffRequestTable.user, TimeOffRequestTable.hours)
                .where {
                    (TimeOffRequestTable.status eq TimeOffStatus.PENDING.name) and (TimeOffRequestTable.startsAt greaterEq fromMillis) and
                        (TimeOffRequestTable.startsAt less toMillis) and (users?.let { TimeOffRequestTable.user inList it } ?: Op.TRUE)
                }
                .groupBy({ it[TimeOffRequestTable.user] }, { it[TimeOffRequestTable.hours] })
                .mapValues { (_, hours) -> hours.sum() }
        }.orEmpty()

    fun add(request: TimeOffRequest): Boolean =
        dbWrite(WRITE_TAG) {
            TimeOffRequestTable.insert { row ->
                row[TimeOffRequestTable.id] = request.identifier
                row[user] = request.userIdentifier!!
                row[startsAt] = request.startsAt
                row[endsAt] = request.endsAt
                row[hours] = request.hours
                row[note] = request.note
                row[status] = request.status.name
                row[createdAt] = request.createdAt
            }
            true
        }

    fun get(identifier: Uuid): TimeOffRequest? =
        dbQuery(GET_TAG) { TimeOffRequestTable.selectAll().where { TimeOffRequestTable.id eq identifier }.toList().toRequests().singleOrNull() }

    /**
     * Requests, newest first: only [status]'s, [user]'s, and those starting from [fromMillis] when given
     * @param limit At most this many
     */
    fun list(status: TimeOffStatus? = null, user: Uuid? = null, fromMillis: Long? = null, limit: Int = 200): List<TimeOffRequest>? =
        dbQuery(GET_TAG) {
            TimeOffRequestTable.selectAll()
                .where {
                    (status?.let { TimeOffRequestTable.status eq it.name } ?: Op.TRUE) and
                        (user?.let { TimeOffRequestTable.user eq it } ?: Op.TRUE) and
                        (fromMillis?.let { TimeOffRequestTable.startsAt greaterEq it } ?: Op.TRUE)
                }
                .orderBy(TimeOffRequestTable.startsAt to SortOrder.DESC)
                .limit(limit)
                .toList()
                .toRequests()
        }

    /**
     * Answers a waiting request, only while it's still waiting. Inside the transaction that makes its entry when
     * approving
     * @return Whether this call answered it
     */
    fun decide(identifier: Uuid, status: TimeOffStatus, by: String, at: Long, reason: String = "", entry: Uuid? = null): Boolean =
        dbWrite(WRITE_TAG) {
            TimeOffRequestTable.update(where = { (TimeOffRequestTable.id eq identifier) and (TimeOffRequestTable.status eq TimeOffStatus.PENDING.name) }) { row ->
                row[TimeOffRequestTable.status] = status.name
                row[decidedBy] = by
                row[decidedAt] = at
                row[TimeOffRequestTable.reason] = reason
                row[TimeOffRequestTable.entry] = entry
            } > 0
        }

    // Inside a transaction. Names from the users
    private fun List<ResultRow>.toRequests(): List<TimeOffRequest> {
        val ids = map { it[TimeOffRequestTable.user] }.distinct()
        val names = if (ids.isEmpty()) emptyMap() else UserTable.select(UserTable.id, UserTable.name, UserTable.username)
            .where { UserTable.id inList ids }
            .associate { it[UserTable.id].value to it[UserTable.name].ifBlank { it[UserTable.username] } }

        return map { row ->
            TimeOffRequest(
                identifier = row[TimeOffRequestTable.id].value,
                userIdentifier = row[TimeOffRequestTable.user],
                userName = names[row[TimeOffRequestTable.user]].orEmpty(),
                startsAt = row[TimeOffRequestTable.startsAt],
                endsAt = row[TimeOffRequestTable.endsAt],
                hours = row[TimeOffRequestTable.hours],
                note = row[TimeOffRequestTable.note],
                status = TimeOffStatus.valueOf(row[TimeOffRequestTable.status]),
                createdAt = row[TimeOffRequestTable.createdAt],
                decidedBy = row[TimeOffRequestTable.decidedBy],
                decidedAt = row[TimeOffRequestTable.decidedAt],
                reason = row[TimeOffRequestTable.reason],
                entryIdentifier = row[TimeOffRequestTable.entry]
            )
        }
    }
}
