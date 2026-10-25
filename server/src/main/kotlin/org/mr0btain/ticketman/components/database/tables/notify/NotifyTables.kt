package org.mr0btain.ticketman.components.database.tables.notify

import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.dao.id.UuidTable
import org.jetbrains.exposed.v1.json.json
import org.mr0btain.ticketman.data.notify.NotificationSettings

/**
 * A single row with how the shop sends messages, its mail password and text token included
 */
object NotifySettingsTable : IntIdTable("notify_settings") {
    val settings = json<NotificationSettings>("settings", Json { ignoreUnknownKeys = true })
}

/**
 * Every message to a customer: waiting, sent or given up on. [ticket] isn't a foreign key, so what a customer was
 * told stays in the admin's list after the ticket is deleted, with [ticketLabel] to tell it apart
 */
object OutboxTable : UuidTable("outbox") {
    val ticket = uuid("ticket_id").nullable().index()
    val ticketLabel = text("ticket_label").default("")
    val customer = uuid("customer_id").nullable()
    val channel = text("channel")
    val event = text("event")
    val recipient = text("recipient")
    val subject = text("subject").default("")
    val body = text("body")
    val status = text("status")
    val attempts = integer("attempts").default(0)
    val nextAttemptAt = long("next_attempt_at")
    val lastError = text("last_error").default("")
    val createdAt = long("created_at")
    val sentAt = long("sent_at").nullable()
    val createdBy = text("created_by").default("")

    init {
        index("outbox_due", false, status, nextAttemptAt)
    }
}
