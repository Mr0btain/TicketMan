package org.mr0btain.ticketman.components.database.tables.ticket

import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.json.json
import org.mr0btain.ticketman.data.ticket.TicketSignature

/** One signature per ticket, from check in */
object TicketSignatureTable : Table("ticket_signatures") {
    val ticket = reference("ticket_id", TicketTable, onDelete = ReferenceOption.CASCADE)
    val signature = json<TicketSignature>("signature", Json { ignoreUnknownKeys = true })

    override val primaryKey = PrimaryKey(ticket)
}
