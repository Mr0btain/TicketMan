package org.mr0btain.ticketman.components.database.tables.ticket

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.UuidTable

/**
 * A ticket's work log: time spent and parts used. Each row's id is also the id of the invoice line it put on the
 * ticket's invoice
 */
object TicketWorkTable : UuidTable("ticket_work") {
    val ticket = reference("ticket_id", TicketTable, onDelete = ReferenceOption.CASCADE)
    val type = text("type")
    val description = text("description")
    val quantity = double("quantity")
    val unitPriceCents = long("unit_price_cents")
    val inventory = text("inventory_id").nullable()
    val user = text("user_id").nullable()
    val userName = text("user_name")
    val workedAt = long("worked_at")
}
