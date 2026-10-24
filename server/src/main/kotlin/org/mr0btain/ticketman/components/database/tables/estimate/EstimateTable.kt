package org.mr0btain.ticketman.components.database.tables.estimate

import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.UuidTable
import org.jetbrains.exposed.v1.json.json
import org.mr0btain.ticketman.components.database.tables.ticket.TicketTable
import org.mr0btain.ticketman.data.estimate.EstimateDecision
import org.mr0btain.ticketman.data.invoice.Charge

/**
 * Estimates sent to customers, going with their ticket. Lines are kept as sent, since that's what was agreed to
 */
object EstimateTable : UuidTable("estimates") {
    val ticket = reference("ticket_id", TicketTable, onDelete = ReferenceOption.CASCADE).index()
    val lines = json<List<Charge>>("lines", Json { ignoreUnknownKeys = true })
    val taxRateBasisPoints = integer("tax_rate_bp").default(0)
    val note = text("note").default("")
    val status = text("status")
    val createdAt = long("created_at")
    val createdBy = text("created_by").default("")
    val decision = json<EstimateDecision>("decision", Json { ignoreUnknownKeys = true }).nullable()
}
