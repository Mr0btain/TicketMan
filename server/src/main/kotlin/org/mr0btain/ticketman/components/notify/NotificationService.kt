package org.mr0btain.ticketman.components.notify

import org.mr0btain.ticketman.components.database.AppointmentDatabase
import org.mr0btain.ticketman.components.database.AuditLogDatabase
import org.mr0btain.ticketman.components.database.CustomerDatabase
import org.mr0btain.ticketman.components.database.EstimateDatabase
import org.mr0btain.ticketman.components.database.InvoiceDatabase
import org.mr0btain.ticketman.components.database.NotifyDatabase
import org.mr0btain.ticketman.components.database.ShopDatabase
import org.mr0btain.ticketman.components.database.TicketDatabase
import org.mr0btain.ticketman.components.service.AccountResult
import org.mr0btain.ticketman.components.utils.formatDayInSentence
import org.mr0btain.ticketman.components.utils.formatTime
import org.mr0btain.ticketman.data.appointment.AppointmentRules
import org.mr0btain.ticketman.data.audit.AuditAction
import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.data.invoice.InvoiceStatus
import org.mr0btain.ticketman.data.money.Money
import org.mr0btain.ticketman.data.shop.Storage
import org.mr0btain.ticketman.data.notify.*
import org.mr0btain.ticketman.data.shared.PagedRequest
import org.mr0btain.ticketman.data.ticket.StatusChoice
import org.mr0btain.ticketman.data.ticket.StatusLink
import org.mr0btain.ticketman.data.ticket.Ticket
import org.mr0btain.ticketman.data.ticket.TicketStatusType
import org.mr0btain.ticketman.data.ticket.customerChoice
import org.mr0btain.ticketman.data.ticket.customerLabel
import org.mr0btain.ticketman.logging.AppLogger
import org.mr0btain.ticketman.server.request.TestMessageRequest
import org.mr0btain.ticketman.server.request.SendLinkRequest
import org.mr0btain.ticketman.data.payment.CardCharge
import org.mr0btain.ticketman.data.payment.CardChargeKind
import org.mr0btain.ticketman.data.payment.CardChargeStatus
import org.mr0btain.ticketman.server.request.TicketMessageRequest
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Messages to customers: what's sent when, in the shop's words, and the outbox that sends them in the background
 * and tries again when it can't. Automatic messages only go to customers who agreed to them
 * ([Customer.notifyEmail], [Customer.notifySms]) and only for the events the shop turned on. Every message, sent or
 * not, stays in the outbox so the ticket shows what the customer was told
 * @param now Epoch millis, swapped in tests
 */
@OptIn(ExperimentalUuidApi::class)
@Suppress("PrivatePropertyName")
class NotificationService(
    private val database: NotifyDatabase,
    private val tickets: TicketDatabase,
    private val customers: CustomerDatabase,
    private val invoices: InvoiceDatabase,
    private val shop: ShopDatabase,
    private val estimates: EstimateDatabase,
    private val auditLog: AuditLogDatabase,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val appointments: AppointmentDatabase = AppointmentDatabase()
) {
    private val TAG = "NotificationService"
    private val QUEUE_TAG = "$TAG:Queue"
    private val SEND_TAG = "$TAG:Send"
    private val REMINDER_TAG = "$TAG:Reminders"

    // ------------------------------------------ Settings ------------------------------------------

    /** The settings without the password and token, for an admin's screen */
    fun settings(): NotificationSettings = database.settings().withoutSecrets()

    fun saveSettings(settings: NotificationSettings, actor: Uuid?): AccountResult<NotificationSettings> {
        settings.problem()?.let { return AccountResult.Invalid(it) }

        val saved = settings.trimmed().keepingSecretsOf(database.settings())
        if (!database.saveSettings(saved)) return AccountResult.Invalid("Couldn't save the settings")

        // Turning a channel off calls off what's still waiting on it, rather than sending it when it's turned back on
        NotificationChannel.entries.filterNot { saved.channelReady(it) }.forEach { channel -> cancelWaiting(channel, "${channel.label} was turned off") }

        auditLog.log(actor, AuditAction.SETTINGS_UPDATE, "notifications", details = "Email ${onOff(saved.emailEnabled)}, texts ${onOff(saved.smsEnabled)}")
        return AccountResult.Done(saved.withoutSecrets())
    }

    /**
     * Sends a test message now with [request]'s settings, so they can be tried before they're saved. Nothing goes
     * in the outbox
     */
    fun test(request: TestMessageRequest, actor: Uuid?): AccountResult<Unit> {
        val settings = request.settings.trimmed().keepingSecretsOf(database.settings())
        val template = settings.template(NotificationEvent.TEST, request.channel)
        val values = mapOf("shop" to shop.get().name.ifBlank { "TicketMan" })

        val result = when (request.channel) {
            NotificationChannel.EMAIL -> {
                if (!settings.smtp.isConfigured) return AccountResult.Invalid("Fill in the mail server and from address first")
                val to = request.recipient.trim().takeIf { NotificationSettings.EMAIL.matches(it) } ?: return AccountResult.Invalid("That isn't an email address")
                NotificationSenders.email.send(settings.smtp, to, MessageTemplates.fill(template.subject, values), MessageTemplates.fill(template.body, values))
            }

            NotificationChannel.SMS -> {
                if (!settings.sms.isConfigured) return AccountResult.Invalid("Fill in the account and from number first")
                val to = PhoneNumbers.toInternational(request.recipient, settings.sms.countryCode) ?: return AccountResult.Invalid("That isn't a phone number")
                NotificationSenders.sms.send(settings.sms, to, MessageTemplates.fill(template.body, values))
            }
        }

        auditLog.log(actor, AuditAction.UPDATE, "notifications", details = "Test ${request.channel.label.lowercase()}: ${describe(result)}")

        return when (result) {
            SendResult.Sent -> AccountResult.Done(Unit)
            is SendResult.Retry -> AccountResult.Invalid(result.reason)
            is SendResult.Failed -> AccountResult.Invalid(result.reason)
        }
    }

    // ------------------------------------------ What's sent when ------------------------------------------

    /**
     * A ticket's status changed by hand. Ready for pickup sends its own message; otherwise one of the statuses the
     * shop picked sends the status changed one
     */
    fun statusChanged(ticket: Ticket, from: StatusChoice?) {
        val to = ticket.status.choice
        if (to == from) return

        val settings = database.settings()
        val event = when {
            to.status == TicketStatusType.DONE && from?.status != TicketStatusType.DONE -> NotificationEvent.READY_FOR_PICKUP
            settings.statusChangeStatuses.any { it == to } -> NotificationEvent.STATUS_CHANGED
            else -> return
        }

        // Moving into ready for pickup only sends the status changed message when the ready one is off
        val sending = if (event == NotificationEvent.READY_FOR_PICKUP && event !in settings.events && settings.statusChangeStatuses.any { it == to }) {
            NotificationEvent.STATUS_CHANGED
        } else event

        if (sending in settings.events) queue(ticket, sending, settings = settings)
    }

    /** An estimate was sent to the customer */
    fun estimateReady(ticket: Ticket) {
        val settings = database.settings()
        if (NotificationEvent.ESTIMATE_READY in settings.events) queue(ticket, NotificationEvent.ESTIMATE_READY, settings = settings)
    }

    /**
     * Staff sending a message about a ticket now. Customers who haven't agreed to automatic messages still get
     * these, since someone at the shop chose to send it
     */
    fun sendForTicket(ticketIdentifier: Uuid, request: TicketMessageRequest, staffName: String, actor: Uuid?): AccountResult<List<OutboxMessage>> {
        if (request.event == NotificationEvent.TEST) return AccountResult.Invalid("Test messages are sent from the settings")
        if (request.event == NotificationEvent.APPOINTMENT_REMINDER) return AccountResult.Invalid("Appointment reminders are sent before an appointment")
        if (request.event == NotificationEvent.PAYMENT_LINK) return AccountResult.Invalid("Payment links are sent from the link itself")
        val ticket = tickets.getById(ticketIdentifier) ?: return AccountResult.NotFound
        val settings = database.settings()

        val wanted = request.channels.ifEmpty { NotificationChannel.entries.toSet() }
        if (wanted.none { settings.channelReady(it) }) return AccountResult.Invalid("Turn on email or text messages in Admin, Notifications first")

        val queued = queue(ticket, request.event, wanted, settings, staffName, requireAgreement = false)
        if (queued.isEmpty()) return AccountResult.Invalid("The customer has no email or phone number to send to")

        auditLog.log(actor, AuditAction.CREATE, "ticket", ticket.identifier.toString(), "Sent ${request.event.label.lowercase()} to the customer of ${ticket.label}")
        return AccountResult.Done(queued)
    }

    /**
     * Staff sending a waiting payment link to the customer of its invoice, by hand like [sendForTicket]. A sale with
     * no customer has no one to send it to
     */
    fun sendPaymentLink(charge: CardCharge, request: SendLinkRequest, staffName: String, actor: Uuid?): AccountResult<List<OutboxMessage>> {
        if (charge.kind != CardChargeKind.LINK || charge.url.isBlank()) return AccountResult.Invalid("Only a payment link can be sent")
        if (charge.status != CardChargeStatus.PENDING) return AccountResult.Conflict("That link is ${charge.status.label.lowercase()}, make a new one")
        val invoice = charge.invoiceIdentifier?.let { invoices.getById(it) } ?: return AccountResult.NotFound
        val customer = invoice.customerIdentifier?.let { customers.getById(it) } ?: return AccountResult.Invalid("${invoice.label} has no customer to send it to")
        val settings = database.settings()

        val wanted = request.channels.ifEmpty { NotificationChannel.entries.toSet() }
        if (wanted.none { settings.channelReady(it) }) return AccountResult.Invalid("Turn on email or text messages in Admin, Notifications first")

        val ticket = invoice.ticketIdentifier?.let { tickets.getById(it) }
        val profile = shop.at(invoice.locationIdentifier)
        val values = (ticket?.let { fields(it, customer) } ?: mapOf(
            "name" to customer.name.trim().substringBefore(' '),
            "ticket" to invoice.label,
            "shop" to profile.name,
            "shop_phone" to profile.phone
        )) + mapOf("pay_link" to charge.url, "amount" to Money.format(charge.amountCents, profile.currencySymbol))

        val queued = queueTo(customer, NotificationEvent.PAYMENT_LINK, values, ticket?.identifier, ticket?.label ?: invoice.label, wanted, settings, staffName, requireAgreement = false)
        if (queued.isEmpty()) return AccountResult.Invalid("The customer has no email or phone number to send to")

        auditLog.log(actor, AuditAction.CREATE, "card charge", charge.identifier.toString(), "Sent the ${Money.format(charge.amountCents, profile.currencySymbol)} payment link for ${invoice.label} by ${queued.joinToString(" and ") { it.channel.label.lowercase() }}")
        return AccountResult.Done(queued)
    }

    /**
     * Puts a message for each channel in the outbox
     * @param requireAgreement Only to customers who agreed to messages on that channel
     * @return What was queued
     */
    private fun queue(
        ticket: Ticket,
        event: NotificationEvent,
        channels: Set<NotificationChannel> = NotificationChannel.entries.toSet(),
        settings: NotificationSettings = database.settings(),
        createdBy: String = "",
        requireAgreement: Boolean = true
    ): List<OutboxMessage> {
        val customer = tickets.getCustomer(ticket.identifier)?.let { customers.getById(it.identifier) } ?: return emptyList()
        return queueTo(customer, event, fields(ticket, customer), ticket.identifier, ticket.label, channels, settings, createdBy, requireAgreement)
    }

    /**
     * Puts a message to [customer] for each channel in the outbox, [values] filling in the template
     * @param ticketIdentifier The ticket it's about, null for one that isn't (like an appointment)
     */
    private fun queueTo(
        customer: Customer,
        event: NotificationEvent,
        values: Map<String, String>,
        ticketIdentifier: Uuid?,
        ticketLabel: String,
        channels: Set<NotificationChannel> = NotificationChannel.entries.toSet(),
        settings: NotificationSettings = database.settings(),
        createdBy: String = "",
        requireAgreement: Boolean = true
    ): List<OutboxMessage> {
        val time = now()

        return channels.filter { settings.channelReady(it) }.mapNotNull { channel ->
            val agreed = if (channel == NotificationChannel.EMAIL) customer.notifyEmail else customer.notifySms
            if (requireAgreement && !agreed) return@mapNotNull null

            val recipient = recipientFor(customer, channel, settings) ?: return@mapNotNull null
            val template = settings.template(event, channel)

            OutboxMessage(
                ticketIdentifier = ticketIdentifier,
                ticketLabel = ticketLabel,
                customerIdentifier = customer.identifier,
                channel = channel,
                event = event,
                recipient = recipient,
                subject = MessageTemplates.fill(template.subject, values),
                body = MessageTemplates.fill(template.body, values),
                nextAttemptAt = time,
                createdAt = time,
                createdBy = createdBy
            ).takeIf { database.add(it) }
                ?.also { AppLogger.i(QUEUE_TAG, "${event.name} by ${channel.name}" + if (ticketLabel.isNotBlank()) " for $ticketLabel" else "") }
        }
    }

    private fun recipientFor(customer: Customer, channel: NotificationChannel, settings: NotificationSettings): String? =
        when (channel) {
            NotificationChannel.EMAIL -> customer.email.trim().takeIf { NotificationSettings.EMAIL.matches(it) }
            NotificationChannel.SMS -> PhoneNumbers.toInternational(customer.phone, settings.sms.countryCode)
        }

    /** What fills in a template's `{fields}` */
    private fun fields(ticket: Ticket, customer: Customer): Map<String, String> {
        // The ticket's location's name and phone
        val profile = shop.at(ticket.locationIdentifier)
        fun money(cents: Long) = Money.format(cents, profile.currencySymbol)

        val invoice = invoices.getByTicket(ticket.identifier)?.takeIf { it.status != InvoiceStatus.VOID }
        val estimate = estimates.pending(ticket.identifier)?.totalCents ?: ticket.estimateCents

        return mapOf(
            "name" to customer.name.trim().substringBefore(' '),
            "ticket" to ticket.label,
            "device" to ticket.device.toString().trim().ifBlank { "device" },
            "status" to profile.statuses.customerLabel(profile.statuses.customerChoice(ticket.status.choice)),
            "link" to StatusLink.url(profile.publicUrl, ticket).orEmpty(),
            "estimate" to estimate?.let { money(it) }.orEmpty(),
            "balance" to money((invoice?.balanceCents ?: 0).coerceAtLeast(0)),
            "storage" to money(profile.storage.dailyFeeCents),
            "shop" to profile.name,
            "shop_phone" to profile.phone
        )
    }

    // ------------------------------------------ Storage ------------------------------------------

    /**
     * Tells each customer whose device is still waiting that storage fees start tomorrow, once since it was ready.
     * Run by the hourly sweep
     * @return How many were told
     */
    fun queueStorageNotices(): Int {
        val settings = database.settings()
        val terms = shop.get().storage
        if (NotificationEvent.STORAGE_FEE !in settings.events || !terms.chargesFee) return 0

        val time = now()
        return tickets.readyForPickup().orEmpty().count { ticket ->
            val readySince = ticket.status.time
            val due = Storage.daysReady(ticket, time) >= (terms.feeAfterDays - 1).coerceAtLeast(0) &&
                database.sentSince(ticket.identifier, NotificationEvent.STORAGE_FEE, readySince).isEmpty()
            due && queue(ticket, NotificationEvent.STORAGE_FEE, settings = settings).isNotEmpty()
        }
    }

    /** A device was treated as abandoned */
    fun abandoned(ticket: Ticket) {
        val settings = database.settings()
        if (NotificationEvent.ABANDONED in settings.events) queue(ticket, NotificationEvent.ABANDONED, settings = settings)
    }

    // ------------------------------------------ Pickup reminders ------------------------------------------

    /**
     * Queues a reminder for each ticket that's been ready long enough: the first [NotificationSettings.reminderAfterDays]
     * after it was ready, then every [NotificationSettings.reminderEveryDays], up to [NotificationSettings.reminderLimit].
     * Run by the hourly sweep
     * @return How many tickets got one
     */
    fun queueReminders(): Int {
        val settings = database.settings()
        if (NotificationEvent.PICKUP_REMINDER !in settings.events || settings.reminderLimit == 0) return 0
        if (NotificationChannel.entries.none { settings.channelReady(it) }) return 0

        val time = now()
        return tickets.readyForPickup().orEmpty().count { ticket ->
            val readySince = ticket.status.time
            val sent = database.sentSince(ticket.identifier, NotificationEvent.PICKUP_REMINDER, readySince)
            // One reminder goes out by every channel, so they're counted by when they were made
            val rounds = sent.map { it.createdAt }.distinct()

            val due = when {
                rounds.size >= settings.reminderLimit -> false
                rounds.isEmpty() -> time - readySince >= settings.reminderAfterDays.days.inWholeMilliseconds
                else -> time - rounds.max() >= settings.reminderEveryDays.days.inWholeMilliseconds
            }

            due && queue(ticket, NotificationEvent.PICKUP_REMINDER, settings = settings).isNotEmpty()
        }.also { if (it > 0) AppLogger.i(REMINDER_TAG, "Reminded $it customers") }
    }

    // ------------------------------------------ Appointment reminders ------------------------------------------

    /**
     * Reminds each customer with a booked appointment in the next [AppointmentRules.REMINDER_HOURS] hours, once. Only
     * appointments with a customer on file, who agreed to messages. Run by the hourly sweep
     * @return How many were reminded
     */
    fun queueAppointmentReminders(): Int {
        val settings = database.settings()
        if (NotificationEvent.APPOINTMENT_REMINDER !in settings.events) return 0
        if (NotificationChannel.entries.none { settings.channelReady(it) }) return 0

        val time = now()
        val due = appointments.dueReminders(time, time + AppointmentRules.REMINDER_HOURS.hours.inWholeMilliseconds).orEmpty()

        return due.count { appointment ->
            val customer = appointment.customerIdentifier?.let { customers.getById(it) } ?: return@count false
            val profile = shop.at(appointment.locationIdentifier)
            val values = mapOf(
                "name" to customer.name.trim().substringBefore(' '),
                "device" to appointment.device.toString().trim().ifBlank { "device" },
                "appointment" to "${formatDayInSentence(appointment.startsAt)} at ${formatTime(appointment.startsAt)}",
                "shop" to profile.name,
                "shop_phone" to profile.phone
            )

            val queued = queueTo(customer, NotificationEvent.APPOINTMENT_REMINDER, values, null, "", settings = settings)
            // Marked either way, so one they didn't agree to isn't looked at every hour
            appointments.markReminded(appointment.identifier, time)
            queued.isNotEmpty()
        }.also { if (it > 0) AppLogger.i(REMINDER_TAG, "Reminded $it customers of their appointments") }
    }

    // ------------------------------------------ The outbox ------------------------------------------

    /**
     * Sends what's due. One run at a time across the server, so a message is never sent twice
     * @return How many went
     */
    fun sendDue(): Int = synchronized(SEND_LOCK) {
        val settings = database.settings()
        var sent = 0

        database.due(now(), BATCH).forEach { message ->
            if (!settings.channelReady(message.channel)) {
                database.record(message.copy(status = OutboxStatus.CANCELLED, lastError = "${message.channel.label} is turned off"), onlyIf = OutboxStatus.PENDING)
                return@forEach
            }

            val result = try {
                when (message.channel) {
                    NotificationChannel.EMAIL -> NotificationSenders.email.send(settings.smtp, message.recipient, message.subject, message.body)
                    NotificationChannel.SMS -> NotificationSenders.sms.send(settings.sms, message.recipient, message.body)
                }
            } catch (ex: Exception) {
                AppLogger.e(SEND_TAG, "Sending ${message.identifier}", ex)
                SendResult.Retry(ex.message ?: ex.toString())
            }

            val attempts = message.attempts + 1
            val updated = when (result) {
                SendResult.Sent -> message.copy(status = OutboxStatus.SENT, attempts = attempts, sentAt = now(), lastError = "").also { sent++ }
                is SendResult.Failed -> message.copy(status = OutboxStatus.FAILED, attempts = attempts, lastError = result.reason)
                is SendResult.Retry ->
                    if (attempts >= RETRY_DELAYS.size + 1) message.copy(status = OutboxStatus.FAILED, attempts = attempts, lastError = "Gave up: ${result.reason}")
                    else message.copy(attempts = attempts, nextAttemptAt = now() + RETRY_DELAYS[attempts - 1].inWholeMilliseconds, lastError = result.reason)
            }

            if (updated.status != OutboxStatus.SENT) AppLogger.i(SEND_TAG, "${message.identifier} to ${message.channel.name}: ${describe(result)}")
            database.record(updated, onlyIf = OutboxStatus.PENDING)
        }

        sent
    }

    /** Tries a failed or waiting message again now */
    fun retry(identifier: Uuid, actor: Uuid?): AccountResult<OutboxMessage> {
        val message = database.get(identifier) ?: return AccountResult.NotFound
        if (message.status == OutboxStatus.SENT) return AccountResult.Conflict("That message was already sent")

        val again = message.copy(status = OutboxStatus.PENDING, nextAttemptAt = now(), attempts = 0)
        if (!database.record(again)) return AccountResult.Invalid("Couldn't save it")

        auditLog.log(actor, AuditAction.UPDATE, "notifications", identifier.toString(), "Retrying a ${message.channel.label.lowercase()} to ${message.recipient}")
        return AccountResult.Done(again)
    }

    fun cancel(identifier: Uuid, actor: Uuid?): AccountResult<OutboxMessage> {
        val message = database.get(identifier) ?: return AccountResult.NotFound
        if (message.status != OutboxStatus.PENDING) return AccountResult.Conflict("Only a waiting message can be cancelled")

        val cancelled = message.copy(status = OutboxStatus.CANCELLED, lastError = "Cancelled")
        if (!database.record(cancelled, onlyIf = OutboxStatus.PENDING)) return AccountResult.Conflict("It was sent meanwhile")

        auditLog.log(actor, AuditAction.UPDATE, "notifications", identifier.toString(), "Cancelled a ${message.channel.label.lowercase()} to ${message.recipient}")
        return AccountResult.Done(cancelled)
    }

    fun forTicket(ticketIdentifier: Uuid): List<OutboxMessage>? = database.forTicket(ticketIdentifier)

    fun page(request: PagedRequest): List<OutboxMessage>? = database.page(request)

    private fun cancelWaiting(channel: NotificationChannel, reason: String) {
        database.due(Long.MAX_VALUE, Int.MAX_VALUE).filter { it.channel == channel }.forEach { message ->
            database.record(message.copy(status = OutboxStatus.CANCELLED, lastError = reason), onlyIf = OutboxStatus.PENDING)
        }
    }

    private fun NotificationSettings.trimmed(): NotificationSettings =
        copy(
            smtp = smtp.copy(host = smtp.host.trim(), username = smtp.username.trim(), fromAddress = smtp.fromAddress.trim(), fromName = smtp.fromName.trim()),
            sms = sms.copy(accountSid = sms.accountSid.trim(), fromNumber = sms.fromNumber.trim(), countryCode = sms.countryCode.trim()),
            statusChangeStatuses = statusChangeStatuses.distinct()
        )

    private fun onOff(on: Boolean) = if (on) "on" else "off"

    private fun describe(result: SendResult): String = when (result) {
        SendResult.Sent -> "sent"
        is SendResult.Retry -> "will retry, ${result.reason}"
        is SendResult.Failed -> "failed, ${result.reason}"
    }

    companion object {
        /** How long to wait after each failed try. One more try after the last, then it's given up */
        val RETRY_DELAYS: List<Duration> = listOf(1.minutes, 5.minutes, 15.minutes, 1.hours, 3.hours, 6.hours)

        /** How often the background loop looks for messages to send */
        val SEND_INTERVAL = 10.seconds

        private const val BATCH = 20
        private val SEND_LOCK = Any()
    }
}
