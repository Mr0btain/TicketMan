package org.mr0btain.ticketman.components.database

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.mr0btain.ticketman.components.database.tables.notify.InboundTable
import org.mr0btain.ticketman.data.notify.InboundMessage
import org.mr0btain.ticketman.data.notify.NotificationChannel
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Texts customers sent the shop, see [InboundMessage]
 */
@OptIn(ExperimentalUuidApi::class)
@Suppress("PrivatePropertyName")
class InboxDatabase {
    private val TAG = "InboxDatabase"
    private val ADD_TAG = "$TAG:Add"
    private val LIST_TAG = "$TAG:List"
    private val READ_TAG = "$TAG:Read"

    /**
     * Keeps [message], unless one with the same [providerId] is already kept. The same one delivered twice at once
     * fails the second on the unique index (H2 has no insert or ignore)
     * @return Whether it was new, null when it couldn't be saved
     */
    fun add(message: InboundMessage, providerId: String): Boolean? =
        dbQuery(ADD_TAG) {
            if (!InboundTable.selectAll().where { InboundTable.providerId eq providerId }.empty()) return@dbQuery false
            InboundTable.insert { row ->
                row[InboundTable.id] = message.identifier
                row[InboundTable.providerId] = providerId
                row[customer] = message.customerIdentifier
                row[customerName] = message.customerName
                row[ticket] = message.ticketIdentifier
                row[ticketLabel] = message.ticketLabel
                row[channel] = message.channel.name
                row[sender] = message.sender
                row[body] = message.body
                row[receivedAt] = message.receivedAt
                row[optedOut] = message.optedOut
            }
            true
        }

    fun get(identifier: Uuid): InboundMessage? =
        dbQuery(LIST_TAG) { InboundTable.selectAll().where { InboundTable.id eq identifier }.firstOrNull()?.toMessage() }

    /** The newest texts, newest first. Null when they couldn't be read */
    fun newest(limit: Int): List<InboundMessage>? =
        dbQuery(LIST_TAG) {
            InboundTable.selectAll()
                .orderBy(InboundTable.receivedAt to SortOrder.DESC, InboundTable.id to SortOrder.DESC)
                .limit(limit)
                .map { it.toMessage() }
        }

    /** How many aren't read yet, null when it couldn't be counted */
    fun unreadCount(): Int? =
        dbQuery(LIST_TAG) { InboundTable.selectAll().where { InboundTable.readAt.isNull() }.count().toInt() }

    /** The texts about a ticket, newest first */
    fun forTicket(ticketIdentifier: Uuid): List<InboundMessage>? =
        dbQuery(LIST_TAG) {
            InboundTable.selectAll()
                .where { InboundTable.ticket eq ticketIdentifier }
                .orderBy(InboundTable.receivedAt to SortOrder.DESC, InboundTable.id to SortOrder.DESC)
                .map { it.toMessage() }
        }

    /** Marks the unread texts about a ticket read. False when it couldn't be saved */
    fun markTicketRead(ticketIdentifier: Uuid, at: Long, by: String): Boolean =
        dbWrite(READ_TAG) {
            InboundTable.update(where = { (InboundTable.ticket eq ticketIdentifier) and InboundTable.readAt.isNull() }) { row ->
                row[readAt] = at
                row[readBy] = by
            }
            true
        }

    /** Marks one text read, leaving one already read as it was. False when it isn't there */
    fun markRead(identifier: Uuid, at: Long, by: String): Boolean =
        dbWrite(READ_TAG) {
            if (InboundTable.selectAll().where { InboundTable.id eq identifier }.empty()) return@dbWrite false
            InboundTable.update(where = { (InboundTable.id eq identifier) and InboundTable.readAt.isNull() }) { row ->
                row[readAt] = at
                row[readBy] = by
            }
            true
        }

    private fun ResultRow.toMessage(): InboundMessage =
        InboundMessage(
            identifier = this[InboundTable.id].value,
            customerIdentifier = this[InboundTable.customer],
            customerName = this[InboundTable.customerName],
            ticketIdentifier = this[InboundTable.ticket],
            ticketLabel = this[InboundTable.ticketLabel],
            channel = NotificationChannel.entries.firstOrNull { it.name == this[InboundTable.channel] } ?: NotificationChannel.SMS,
            sender = this[InboundTable.sender],
            body = this[InboundTable.body],
            receivedAt = this[InboundTable.receivedAt],
            readAt = this[InboundTable.readAt],
            readBy = this[InboundTable.readBy],
            optedOut = this[InboundTable.optedOut]
        )
}
