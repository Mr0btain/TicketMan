package org.mr0btain.ticketman.components.database.tables.appointment

import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.dao.id.UuidTable
import org.jetbrains.exposed.v1.json.json
import org.mr0btain.ticketman.data.ticket.Device

/**
 * Appointments on the calendar. [customer], [technician] and [ticket] aren't foreign keys, so deleting one of them
 * leaves the appointment with the name typed on it
 */
object AppointmentTable : UuidTable("appointments") {
    val startsAt = long("starts_at").index()
    val minutes = integer("minutes")
    val customer = uuid("customer_id").nullable()
    val customerName = text("customer_name").default("")
    val phone = text("phone").default("")
    val email = text("email").default("")
    val device = json<Device>("device", Json { ignoreUnknownKeys = true }).default(Device())
    val problem = text("problem").default("")
    val notes = text("notes").default("")
    val technician = uuid("technician_id").nullable()
    val status = text("status")
    val ticket = uuid("ticket_id").nullable()
    val reminderSentAt = long("reminder_sent_at").nullable()
    val createdBy = text("created_by").default("")
    val createdAt = long("created_at")
}
