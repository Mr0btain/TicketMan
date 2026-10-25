package org.mr0btain.ticketman.components.database

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert
import org.mr0btain.ticketman.components.database.tables.ticket.TicketPasscodeTable
import org.mr0btain.ticketman.components.database.tables.ticket.TicketTable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Sealed device passcodes, one per ticket, with the ticket's `passcode_set` kept in step. Sealing and opening are
 * `PasscodeVault`'s
 */
@OptIn(ExperimentalUuidApi::class)
@Suppress("PrivatePropertyName")
class PasscodeDatabase {
    private val TAG = "PasscodeDatabase"
    private val WRITE_TAG = "$TAG:Write"
    private val READ_TAG = "$TAG:Read"

    /** Saves [sealed] as the ticket's passcode, in place of any it had */
    fun set(ticketIdentifier: Uuid, sealed: String, setAt: Long, setBy: String): Boolean =
        dbWrite(WRITE_TAG) {
            if (TicketTable.update(where = { TicketTable.id eq ticketIdentifier }) { row -> row[passcodeSet] = true } == 0) return@dbWrite false
            TicketPasscodeTable.upsert { row ->
                row[ticket] = ticketIdentifier
                row[TicketPasscodeTable.sealed] = sealed
                row[TicketPasscodeTable.setAt] = setAt
                row[TicketPasscodeTable.setBy] = setBy
            }
            true
        }

    /** The sealed passcode, null when there's none */
    fun get(ticketIdentifier: Uuid): String? =
        dbQuery(READ_TAG) {
            TicketPasscodeTable.selectAll().where { TicketPasscodeTable.ticket eq ticketIdentifier }.singleOrNull()?.get(TicketPasscodeTable.sealed)
        }

    /** @return Whether there was one */
    fun clear(ticketIdentifier: Uuid): Boolean =
        dbWrite(WRITE_TAG) {
            TicketTable.update(where = { TicketTable.id eq ticketIdentifier }) { row -> row[passcodeSet] = false }
            TicketPasscodeTable.deleteWhere { TicketPasscodeTable.ticket eq ticketIdentifier } > 0
        }
}
