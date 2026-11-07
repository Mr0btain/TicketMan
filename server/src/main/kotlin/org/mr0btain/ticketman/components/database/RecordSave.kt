package org.mr0btain.ticketman.components.database

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.mr0btain.ticketman.error.Error
import org.mr0btain.applogger.AppLogger
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** What came of saving a record that may have changed since whoever's saving it opened it */
enum class RecordSave {
    DONE,
    NOT_FOUND,

    /** Someone saved it since, so this would have overwritten what they did */
    CHANGED_SINCE
}

/**
 * Counts a save of a record in one statement, so of two at once only one gets through. Only if its [revision] is
 * still at [expected] when given. Inside a transaction
 * @return Whether it counted
 */
@OptIn(ExperimentalUuidApi::class)
internal fun IdTable<Uuid>.nextRevision(identifier: Uuid, revision: Column<Long>, expected: Long? = null): Boolean =
    update(where = {
        val record = id eq identifier
        if (expected != null) record and (revision eq expected) else record
    }) { row -> row[revision] = revision + 1 } > 0

/**
 * Counts a save with [nextRevision] for a `save` to go on with. Inside a transaction
 * @return Null when it counted, otherwise why the save can't go on
 */
@OptIn(ExperimentalUuidApi::class)
internal fun IdTable<Uuid>.saveRefused(identifier: Uuid, revision: Column<Long>, expected: Long?, tag: String, notFound: Error): RecordSave? {
    if (nextRevision(identifier, revision, expected)) return null

    val exists = !selectAll().where { id eq identifier }.empty()
    if (!exists) AppLogger.i(tag, notFound)

    return if (exists) RecordSave.CHANGED_SINCE else RecordSave.NOT_FOUND
}
