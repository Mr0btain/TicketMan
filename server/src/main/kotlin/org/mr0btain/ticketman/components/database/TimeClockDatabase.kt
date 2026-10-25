package org.mr0btain.ticketman.components.database

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.mr0btain.ticketman.components.database.tables.timeclock.TimeEntryTable
import org.mr0btain.ticketman.components.database.tables.user.UserTable
import org.mr0btain.ticketman.data.timeclock.TimeEntry
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Time on the clock. Rules like one open stretch per person are the time clock service's
 */
@OptIn(ExperimentalUuidApi::class)
@Suppress("PrivatePropertyName")
class TimeClockDatabase {
    private val TAG = "TimeClockDatabase"
    private val GET_TAG = "$TAG:Get"
    private val WRITE_TAG = "$TAG:Write"

    fun get(identifier: Uuid): TimeEntry? =
        dbQuery(GET_TAG) { TimeEntryTable.selectAll().where { TimeEntryTable.id eq identifier }.toList().toEntries().singleOrNull() }

    /** The user's stretch still on the clock, null when they're off it (or the read failed) */
    fun open(user: Uuid): TimeEntry? =
        dbQuery(GET_TAG) {
            TimeEntryTable.selectAll()
                .where { (TimeEntryTable.user eq user) and TimeEntryTable.clockedOutAt.isNull() }
                .orderBy(TimeEntryTable.clockedInAt to SortOrder.DESC)
                .limit(1)
                .toList()
                .toEntries()
                .firstOrNull()
        }

    /** Everyone on the clock now, earliest first. Null when the read failed */
    fun allOpen(): List<TimeEntry>? =
        dbQuery(GET_TAG) {
            TimeEntryTable.selectAll()
                .where { TimeEntryTable.clockedOutAt.isNull() }
                .orderBy(TimeEntryTable.clockedInAt to SortOrder.ASC)
                .toList()
                .toEntries()
        }

    /**
     * Stretches started from [fromMillis] up to but not including [toMillis], only [user]'s when given, oldest first.
     * Null when the read failed
     */
    fun between(fromMillis: Long, toMillis: Long, user: Uuid? = null): List<TimeEntry>? =
        dbQuery(GET_TAG) {
            TimeEntryTable.selectAll()
                .where {
                    val range = (TimeEntryTable.clockedInAt greaterEq fromMillis) and (TimeEntryTable.clockedInAt less toMillis)
                    if (user != null) range and (TimeEntryTable.user eq user) else range
                }
                .orderBy(TimeEntryTable.clockedInAt to SortOrder.ASC)
                .toList()
                .toEntries()
        }

    /** Whether another of the user's stretches overlaps [entry]'s time, leaving [entry] itself out */
    fun overlaps(entry: TimeEntry, now: Long): Boolean =
        dbQuery(GET_TAG) {
            val end = entry.clockedOutAt ?: now
            TimeEntryTable.select(TimeEntryTable.id)
                .where {
                    (TimeEntryTable.user eq entry.userIdentifier) and (TimeEntryTable.id neq entry.identifier) and
                        (TimeEntryTable.clockedInAt less end) and
                        (TimeEntryTable.clockedOutAt.isNull() or (TimeEntryTable.clockedOutAt greater entry.clockedInAt))
                }
                .limit(1)
                .any()
        } ?: true

    fun add(entry: TimeEntry): Boolean =
        dbWrite(WRITE_TAG) {
            TimeEntryTable.insert { row ->
                row[TimeEntryTable.id] = entry.identifier
                row[user] = entry.userIdentifier
                row[userName] = entry.userName
                row[clockedInAt] = entry.clockedInAt
                row[clockedOutAt] = entry.clockedOutAt
                row[station] = entry.station
                row[note] = entry.note.trim()
                row[editedBy] = entry.editedBy
            }
            true
        }

    /** Saves the times and note, and who changed it */
    fun update(entry: TimeEntry): Boolean =
        dbWrite(WRITE_TAG) {
            TimeEntryTable.update(where = { TimeEntryTable.id eq entry.identifier }) { row ->
                row[clockedInAt] = entry.clockedInAt
                row[clockedOutAt] = entry.clockedOutAt
                row[note] = entry.note.trim()
                row[editedBy] = entry.editedBy
            } > 0
        }

    /** Ends an open stretch at [at]. False when it was closed already */
    fun clockOut(identifier: Uuid, at: Long): Boolean =
        dbWrite(WRITE_TAG) {
            TimeEntryTable.update(where = { (TimeEntryTable.id eq identifier) and TimeEntryTable.clockedOutAt.isNull() }) { row ->
                row[clockedOutAt] = at
            } > 0
        }

    fun remove(identifier: Uuid): Boolean =
        dbWrite(WRITE_TAG) { TimeEntryTable.deleteWhere { TimeEntryTable.id eq identifier } > 0 }

    // Inside a transaction. Names come from the user's record while it's there, else what was kept
    private fun List<ResultRow>.toEntries(): List<TimeEntry> {
        val ids = map { it[TimeEntryTable.user] }.distinct()
        val names = if (ids.isEmpty()) emptyMap() else UserTable
            .select(UserTable.id, UserTable.name, UserTable.username)
            .where { UserTable.id inList ids }
            .associate { it[UserTable.id].value to it[UserTable.name].ifBlank { it[UserTable.username] } }

        return map { row ->
            TimeEntry(
                identifier = row[TimeEntryTable.id].value,
                userIdentifier = row[TimeEntryTable.user],
                userName = names[row[TimeEntryTable.user]] ?: row[TimeEntryTable.userName],
                clockedInAt = row[TimeEntryTable.clockedInAt],
                clockedOutAt = row[TimeEntryTable.clockedOutAt],
                station = row[TimeEntryTable.station],
                note = row[TimeEntryTable.note],
                editedBy = row[TimeEntryTable.editedBy]
            )
        }
    }
}
