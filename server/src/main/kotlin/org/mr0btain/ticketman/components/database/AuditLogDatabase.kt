package org.mr0btain.ticketman.components.database

import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.mr0btain.ticketman.components.database.tables.audit.AuditLogTable
import org.mr0btain.ticketman.data.audit.AuditAction
import org.mr0btain.ticketman.data.audit.AuditEntry
import org.mr0btain.ticketman.data.shared.PagedRequest
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Keeps a log of who changed what
 */
@OptIn(ExperimentalUuidApi::class)
@Suppress("PrivatePropertyName")
class AuditLogDatabase(
    private val userDatabase: UserDatabase
) {
    private val TAG = "AuditLogDatabase"
    private val LOG_TAG = "$TAG:Log"
    private val GET_PAGED_TAG = "$TAG:GetPaged"
    private val COUNT_TAG = "$TAG:Count"
    private val TRIM_TAG = "$TAG:Trim"

    /**
     * Records an action. Inside someone else's transaction the entry is part of it, so it's only kept if the change
     * it describes is, and a failed write undoes that change too. On its own a failed write is only logged
     * @param actorIdentifier User that did it, their username is looked up unless [actorName] is given
     * @param actorName Name to record instead, used when there's no user like a failed login
     */
    fun log(
        actorIdentifier: Uuid?,
        action: AuditAction,
        entityType: String,
        entityIdentifier: String? = null,
        details: String = "",
        actorName: String? = null
    ) {
        val name = actorName
            ?: actorIdentifier?.let { userDatabase.getById(it)?.username }
            ?: "unknown"

        dbWrite(LOG_TAG) {
            AuditLogTable.insert { row ->
                row[AuditLogTable.id] = Uuid.generateV7()
                row[AuditLogTable.actorIdentifier] = actorIdentifier?.toString()
                row[AuditLogTable.actorName] = name
                row[AuditLogTable.action] = action.name
                row[AuditLogTable.entityType] = entityType
                row[AuditLogTable.entityIdentifier] = entityIdentifier
                row[AuditLogTable.details] = details
                row[AuditLogTable.time] = Clock.System.now().toEpochMilliseconds()
            }

            true
        }
    }

    /** Entries newest first */
    fun getPagedItems(request: PagedRequest): List<AuditEntry>? =
        dbQuery(GET_PAGED_TAG) {
            AuditLogTable
                .selectAll()
                .orderBy(AuditLogTable.time to SortOrder.DESC, AuditLogTable.id to SortOrder.DESC)
                .offset(request.offset)
                .limit(request.itemsPerPage)
                .map { row ->
                    AuditEntry(
                        identifier = row[AuditLogTable.id].value,
                        actorIdentifier = row[AuditLogTable.actorIdentifier]?.let { Uuid.parse(it) },
                        actorName = row[AuditLogTable.actorName],
                        action = AuditAction.valueOf(row[AuditLogTable.action]),
                        entityType = row[AuditLogTable.entityType],
                        entityIdentifier = row[AuditLogTable.entityIdentifier],
                        details = row[AuditLogTable.details],
                        time = row[AuditLogTable.time]
                    )
                }
        }

    fun count(): Long = dbQuery(COUNT_TAG) { AuditLogTable.selectAll().count() } ?: 0L

    /**
     * Deletes entries older than [cutoff], for the retention setting
     * @param cutoff Epoch millis
     * @return How many were removed, 0 if it failed
     */
    fun removeOlderThan(cutoff: Long): Int =
        dbQuery(TRIM_TAG) { AuditLogTable.deleteWhere { AuditLogTable.time less cutoff } } ?: 0
}
