package org.mr0btain.ticketman.components.database.tables.device

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.UuidTable
import org.mr0btain.ticketman.components.database.tables.customer.CustomerTable

/**
 * Devices customers own, going with the customer. Tickets point to one with `tickets.device_id`, which isn't a
 * foreign key, so deleting a device leaves its tickets with their copy of its details
 */
object CustomerDeviceTable : UuidTable("customer_devices") {
    val customer = reference("customer_id", CustomerTable, onDelete = ReferenceOption.CASCADE).index()
    val manufacturer = text("manufacturer").default("")
    val model = text("model").default("")
    val serial = text("serial").default("")
    val kind = text("kind").default("OTHER")
    val color = text("color").default("")
    val notes = text("notes").default("")
    val createdAt = long("created_at")
}
