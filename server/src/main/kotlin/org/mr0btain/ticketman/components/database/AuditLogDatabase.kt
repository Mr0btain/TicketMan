package org.mr0btain.ticketman.components.database

import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.mr0btain.ticketman.components.database.tables.audit.AuditLogTable
import org.mr0btain.ticketman.data.audit.AuditAction
import org.mr0btain.ticketman.data.audit.AuditEntry
import org.mr0btain.ticketman.data.shared.PagedRequest
import org.mr0btain.ticketman.error.appLogTryCatch
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

    /**
     * Records an action
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

        appLogTryCatch(LOG_TAG) {
            transaction {
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
            }

            true
        }
    }

    /** Entries newest first */
    fun getPagedItems(request: PagedRequest): List<AuditEntry>? {
        return appLogTryCatch<List<AuditEntry>?>(GET_PAGED_TAG) {
            transaction {
                val entries = AuditLogTable
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

                Pair(entries, true)
            }
        }
    }

    fun count(): Long {
        var count = 0L

        appLogTryCatch(COUNT_TAG) {
            transaction {
                count = AuditLogTable.selectAll().count()
            }

            true
        }

        return count
    }
}
