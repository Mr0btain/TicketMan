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

/**
 * Texts customers sent the shop's number. [providerId] is the provider's own id for it, so one delivered twice is
 * kept once. [ticket] and [customer] aren't foreign keys, like the outbox's
 */
object InboundTable : UuidTable("inbound_messages") {
    val providerId = text("provider_id").uniqueIndex()
    val customer = uuid("customer_id").nullable().index()
    val customerName = text("customer_name").default("")
    val ticket = uuid("ticket_id").nullable().index()
    val ticketLabel = text("ticket_label").default("")
    val channel = text("channel")
    val sender = text("sender")
    val body = text("body")
    val receivedAt = long("received_at").index()
    val readAt = long("read_at").nullable()
    val readBy = text("read_by").default("")
    val optedOut = bool("opted_out").default(false)
}
