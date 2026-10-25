package org.mr0btain.ticketman.components.database

import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.mr0btain.ticketman.components.database.tables.schedule.ScheduleTable
import org.mr0btain.ticketman.components.database.tables.user.UserTable
import org.mr0btain.ticketman.data.schedule.ScheduleEntry
import org.mr0btain.ticketman.data.schedule.ScheduleKind
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * The staff schedule's entries. Names come from the users table when read
 */
@OptIn(ExperimentalUuidApi::class)
@Suppress("PrivatePropertyName")
class ScheduleDatabase {
    private val TAG = "ScheduleDatabase"
    private val GET_TAG = "$TAG:Get"
    private val WRITE_TAG = "$TAG:Write"

    fun get(identifier: Uuid): ScheduleEntry? =
        dbQuery(GET_TAG) { ScheduleTable.selectAll().where { ScheduleTable.id eq identifier }.toList().toEntries().singleOrNull() }

    /**
     * Every entry touching [fromMillis] up to [toMillis], earliest first, at [location] (every one for null) and
     * only [user]'s when given. Null when the read failed
     */
    fun between(fromMillis: Long, toMillis: Long, location: Uuid? = null, user: Uuid? = null): List<ScheduleEntry>? =
        dbQuery(GET_TAG) {
            ScheduleTable.selectAll()
                .where {
                    (ScheduleTable.startsAt less toMillis) and (ScheduleTable.endsAt greater fromMillis) and
                        ScheduleTable.location.at(location) and (user?.let { ScheduleTable.user eq it } ?: Op.TRUE)
                }
                .orderBy(ScheduleTable.startsAt to SortOrder.ASC)
                .toList()
                .toEntries()
        }

    /** A series' entries, earliest first */
    fun series(identifier: Uuid): List<ScheduleEntry> =
        dbQuery(GET_TAG) {
            ScheduleTable.selectAll().where { ScheduleTable.series eq identifier }.orderBy(ScheduleTable.startsAt to SortOrder.ASC).toList().toEntries()
        }.orEmpty()

    /** Saves new entries, all or none */
    fun add(entries: List<ScheduleEntry>): Boolean =
        dbWrite(WRITE_TAG) {
            entries.forEach { entry ->
                ScheduleTable.insert { row ->
                    row[ScheduleTable.id] = entry.identifier
                    row[createdBy] = entry.createdBy
                    row[createdAt] = entry.createdAt
                    row.fill(entry)
                }
            }
            true
        }

    /** Saves what can change, all or none: never who wrote it or when */
    fun update(entries: List<ScheduleEntry>): Boolean =
        dbWrite(WRITE_TAG) {
            entries.forEach { entry ->
                if (ScheduleTable.update(where = { ScheduleTable.id eq entry.identifier }) { row -> row.fill(entry) } == 0) {
                    throw IllegalStateException("Schedule entry ${entry.identifier} isn't there")
                }
            }
            true
        }

    fun remove(identifiers: List<Uuid>): Boolean =
        dbWrite(WRITE_TAG) { ScheduleTable.deleteWhere { ScheduleTable.id inList identifiers } > 0 }

    private fun UpdateBuilder<*>.fill(entry: ScheduleEntry) {
        this[ScheduleTable.user] = entry.userIdentifier
        this[ScheduleTable.kind] = entry.kind.name
        this[ScheduleTable.startsAt] = entry.startsAt
        this[ScheduleTable.endsAt] = entry.endsAt
        this[ScheduleTable.allDay] = entry.allDay
        this[ScheduleTable.note] = entry.note.trim()
        this[ScheduleTable.series] = entry.seriesIdentifier
        this[ScheduleTable.location] = entry.locationIdentifier
    }

    // Inside a transaction. Each person's name comes from their record
    private fun List<ResultRow>.toEntries(): List<ScheduleEntry> {
        val userIds = map { it[ScheduleTable.user] }.distinct()
        val names = if (userIds.isEmpty()) emptyMap() else UserTable
            .select(UserTable.id, UserTable.name, UserTable.username)
            .where { UserTable.id inList userIds }
            .associate { it[UserTable.id].value to it[UserTable.name].ifBlank { it[UserTable.username] } }

        return map { row ->
            ScheduleEntry(
                identifier = row[ScheduleTable.id].value,
                userIdentifier = row[ScheduleTable.user],
                userName = names[row[ScheduleTable.user]].orEmpty(),
                kind = ScheduleKind.entries.firstOrNull { it.name == row[ScheduleTable.kind] } ?: ScheduleKind.OTHER,
                startsAt = row[ScheduleTable.startsAt],
                endsAt = row[ScheduleTable.endsAt],
                allDay = row[ScheduleTable.allDay],
                note = row[ScheduleTable.note],
                seriesIdentifier = row[ScheduleTable.series],
                locationIdentifier = row[ScheduleTable.location],
                createdBy = row[ScheduleTable.createdBy],
                createdAt = row[ScheduleTable.createdAt]
            )
        }
    }
}
