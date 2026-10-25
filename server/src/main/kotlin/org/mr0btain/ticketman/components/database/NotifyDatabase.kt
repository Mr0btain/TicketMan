package org.mr0btain.ticketman.components.database

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.mr0btain.ticketman.components.database.tables.notify.NotifySettingsTable
import org.mr0btain.ticketman.components.database.tables.notify.OutboxTable
import org.mr0btain.ticketman.data.notify.NotificationChannel
import org.mr0btain.ticketman.data.notify.NotificationEvent
import org.mr0btain.ticketman.data.notify.NotificationSettings
import org.mr0btain.ticketman.data.notify.OutboxMessage
import org.mr0btain.ticketman.data.notify.OutboxStatus
import org.mr0btain.ticketman.data.shared.PagedRequest
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * How the shop sends messages, and the outbox of messages to customers
 */
@OptIn(ExperimentalUuidApi::class)
@Suppress("PrivatePropertyName")
class NotifyDatabase {
    private val TAG = "NotifyDatabase"
    private val SETTINGS_TAG = "$TAG:Settings"
    private val ADD_TAG = "$TAG:Add"
    private val GET_TAG = "$TAG:Get"
    private val DUE_TAG = "$TAG:Due"
    private val UPDATE_TAG = "$TAG:Update"
    private val LIST_TAG = "$TAG:List"

    private val ROW_ID = 1

    /** The saved settings with their password and token, or the defaults */
    fun settings(): NotificationSettings =
        dbQuery(SETTINGS_TAG) {
            NotifySettingsTable.selectAll().where { NotifySettingsTable.id eq ROW_ID }.firstOrNull()?.get(NotifySettingsTable.settings)
        } ?: NotificationSettings()

    fun saveSettings(settings: NotificationSettings): Boolean =
        dbWrite(SETTINGS_TAG) {
            val updated = NotifySettingsTable.update(where = { NotifySettingsTable.id eq ROW_ID }) { row ->
                row[NotifySettingsTable.settings] = settings
            }

            if (updated == 0) {
                NotifySettingsTable.insert { row ->
                    row[NotifySettingsTable.id] = ROW_ID
                    row[NotifySettingsTable.settings] = settings
                }
            }

            true
        }

    fun add(message: OutboxMessage): Boolean =
        dbWrite(ADD_TAG) {
            OutboxTable.insert { row ->
                row[OutboxTable.id] = message.identifier
                row[ticket] = message.ticketIdentifier
                row[ticketLabel] = message.ticketLabel
                row[customer] = message.customerIdentifier
                row[channel] = message.channel.name
                row[event] = message.event.name
                row[recipient] = message.recipient
                row[subject] = message.subject
                row[body] = message.body
                row[status] = message.status.name
                row[attempts] = message.attempts
                row[nextAttemptAt] = message.nextAttemptAt
                row[lastError] = message.lastError
                row[createdAt] = message.createdAt
                row[sentAt] = message.sentAt
                row[createdBy] = message.createdBy
            }

            true
        }

    fun get(identifier: Uuid): OutboxMessage? =
        dbQuery(GET_TAG) {
            OutboxTable.selectAll().where { OutboxTable.id eq identifier }.firstOrNull()?.toMessage()
        }

    /** Waiting messages whose time has come, oldest first */
    fun due(now: Long, limit: Int): List<OutboxMessage> =
        dbQuery(DUE_TAG) {
            OutboxTable.selectAll()
                .where { (OutboxTable.status eq OutboxStatus.PENDING.name) and (OutboxTable.nextAttemptAt lessEq now) }
                .orderBy(OutboxTable.nextAttemptAt to SortOrder.ASC)
                .limit(limit)
                .map { it.toMessage() }
        }.orEmpty()

    /**
     * Saves what happened when sending: its status, tries, next try, error and when it went
     * @param onlyIf Only changes it while it's still in this status, so a cancel isn't undone
     */
    fun record(message: OutboxMessage, onlyIf: OutboxStatus? = null): Boolean =
        dbWrite(UPDATE_TAG) {
            OutboxTable.update(where = {
                val match = OutboxTable.id eq message.identifier
                if (onlyIf == null) match else match and (OutboxTable.status eq onlyIf.name)
            }) { row ->
                row[status] = message.status.name
                row[attempts] = message.attempts
                row[nextAttemptAt] = message.nextAttemptAt
                row[lastError] = message.lastError
                row[sentAt] = message.sentAt
            } > 0
        }

    /** A ticket's messages, newest first */
    fun forTicket(ticketIdentifier: Uuid): List<OutboxMessage>? =
        dbQuery(LIST_TAG) {
            OutboxTable.selectAll()
                .where { OutboxTable.ticket eq ticketIdentifier }
                .orderBy(OutboxTable.createdAt to SortOrder.DESC, OutboxTable.id to SortOrder.DESC)
                .map { it.toMessage() }
        }

    /** A page of every message, newest first */
    fun page(request: PagedRequest): List<OutboxMessage>? =
        dbQuery(LIST_TAG) {
            OutboxTable.selectAll()
                .orderBy(OutboxTable.createdAt to SortOrder.DESC, OutboxTable.id to SortOrder.DESC)
                .offset(request.offset)
                .limit(request.itemsPerPage)
                .map { it.toMessage() }
        }

    /**
     * Messages of [event] about a ticket made since [since] that weren't cancelled, newest first, for counting
     * reminders
     */
    fun sentSince(ticketIdentifier: Uuid, event: NotificationEvent, since: Long): List<OutboxMessage> =
        dbQuery(LIST_TAG) {
            OutboxTable.selectAll()
                .where {
                    (OutboxTable.ticket eq ticketIdentifier) and (OutboxTable.event eq event.name) and
                        (OutboxTable.createdAt greaterEq since) and (OutboxTable.status neq OutboxStatus.CANCELLED.name)
                }
                .orderBy(OutboxTable.createdAt to SortOrder.DESC)
                .map { it.toMessage() }
        }.orEmpty()

    private fun ResultRow.toMessage(): OutboxMessage =
        OutboxMessage(
            identifier = this[OutboxTable.id].value,
            ticketIdentifier = this[OutboxTable.ticket],
            ticketLabel = this[OutboxTable.ticketLabel],
            customerIdentifier = this[OutboxTable.customer],
            channel = NotificationChannel.valueOf(this[OutboxTable.channel]),
            event = NotificationEvent.valueOf(this[OutboxTable.event]),
            recipient = this[OutboxTable.recipient],
            subject = this[OutboxTable.subject],
            body = this[OutboxTable.body],
            status = OutboxStatus.valueOf(this[OutboxTable.status]),
            attempts = this[OutboxTable.attempts],
            nextAttemptAt = this[OutboxTable.nextAttemptAt],
            lastError = this[OutboxTable.lastError],
            createdAt = this[OutboxTable.createdAt],
            sentAt = this[OutboxTable.sentAt],
            createdBy = this[OutboxTable.createdBy]
        )
}
