package org.mr0btain.ticketman.components.database

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.mr0btain.ticketman.components.database.tables.ticket.TicketWorkTable
import org.mr0btain.ticketman.data.ticket.WorkEntry
import org.mr0btain.ticketman.data.ticket.WorkType
import kotlin.uuid.Uuid

/**
 * Tickets' work logs. Keeping the invoice in step is up to InvoiceService, which writes both in one transaction
 */
@Suppress("PrivatePropertyName")
class WorkDatabase {
    private val TAG = "WorkDatabase"
    private val GET_TAG = "$TAG:Get"
    private val ADD_TAG = "$TAG:Add"
    private val REMOVE_TAG = "$TAG:Remove"

    /** The ticket's work, oldest first. Null when the read failed */
    fun getForTicket(ticketIdentifier: Uuid): List<WorkEntry>? =
        dbQuery(GET_TAG) {
            TicketWorkTable
                .selectAll()
                .where { TicketWorkTable.ticket eq ticketIdentifier }
                .orderBy(TicketWorkTable.workedAt to SortOrder.ASC)
                .map { it.toWorkEntry() }
        }

    /** One entry and the ticket it's on, null when there's no such entry */
    fun get(identifier: Uuid): Pair<Uuid, WorkEntry>? =
        dbQuery(GET_TAG) {
            TicketWorkTable
                .selectAll()
                .where { TicketWorkTable.id eq identifier }
                .firstOrNull()
                ?.let { row -> row[TicketWorkTable.ticket].value to row.toWorkEntry() }
        }

    fun add(ticketIdentifier: Uuid, entry: WorkEntry): Boolean =
        dbWrite(ADD_TAG) {
            TicketWorkTable.insert { row ->
                row[id] = entry.identifier
                row[ticket] = ticketIdentifier
                row[type] = entry.type.name
                row[description] = entry.description
                row[quantity] = entry.quantity
                row[unitPriceCents] = entry.unitPriceCents
                row[inventory] = entry.inventoryIdentifier?.toString()
                row[user] = entry.userIdentifier?.toString()
                row[userName] = entry.userName
                row[workedAt] = entry.workedAt
            }
            true
        }

    fun remove(identifier: Uuid): Boolean =
        dbWrite(REMOVE_TAG) {
            TicketWorkTable.deleteWhere { TicketWorkTable.id eq identifier } > 0
        }

    // Must be called inside a transaction
    private fun ResultRow.toWorkEntry(): WorkEntry =
        WorkEntry(
            identifier = this[TicketWorkTable.id].value,
            type = WorkType.valueOf(this[TicketWorkTable.type]),
            description = this[TicketWorkTable.description],
            quantity = this[TicketWorkTable.quantity],
            unitPriceCents = this[TicketWorkTable.unitPriceCents],
            inventoryIdentifier = this[TicketWorkTable.inventory]?.let { Uuid.parse(it) },
            userIdentifier = this[TicketWorkTable.user]?.let { Uuid.parse(it) },
            userName = this[TicketWorkTable.userName],
            workedAt = this[TicketWorkTable.workedAt]
        )
}
