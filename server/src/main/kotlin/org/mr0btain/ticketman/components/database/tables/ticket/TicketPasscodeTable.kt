package org.mr0btain.ticketman.components.database.tables.ticket

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table

/**
 * Devices' passcodes, encrypted with a key from the server's secret (`PasscodeVault`), one per ticket and going with
 * it. `tickets.passcode_set` says whether there's one, so a ticket read never touches this
 */
object TicketPasscodeTable : Table("ticket_passcodes") {
    val ticket = reference("ticket_id", TicketTable, onDelete = ReferenceOption.CASCADE)
    val sealed = text("sealed")
    val setAt = long("set_at")
    val setBy = text("set_by")

    override val primaryKey = PrimaryKey(ticket)
}
