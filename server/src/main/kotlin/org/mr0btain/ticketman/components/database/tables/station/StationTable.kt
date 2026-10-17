package org.mr0btain.ticketman.components.database.tables.station

import org.jetbrains.exposed.v1.core.dao.id.UuidTable

/**
 * Devices staff can PIN in at once an admin approves them
 */
object StationTable : UuidTable("stations") {
    val deviceId = text("device_id").uniqueIndex()
    val name = text("name")
    val status = text("status")
    val secretHash = text("secret_hash")
    val requestedBy = text("requested_by")
    val requestedAt = long("requested_at")
    val approvedBy = text("approved_by").nullable()
    val approvedAt = long("approved_at").nullable()
}
