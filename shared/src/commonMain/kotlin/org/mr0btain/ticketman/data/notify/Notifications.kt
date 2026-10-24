package org.mr0btain.ticketman.data.notify

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.ticket.StatusChoice
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** How a message reaches the customer */
@Serializable
enum class NotificationChannel(val label: String) {
    EMAIL("Email"),
    SMS("Text message")
}

/**
 * What a message is about. Each has its own template, and all but [STATUS_LINK] and [TEST] are only sent when the
 * shop turns them on
 */
@Serializable
enum class NotificationEvent(val label: String) {
    /** The ticket moved into one of [NotificationSettings.statusChangeStatuses] */
    STATUS_CHANGED("Status changed"),

    /** The ticket moved into a status that acts as ready for pickup */
    READY_FOR_PICKUP("Ready for pickup"),

    /** Staff sent an estimate for the customer to approve */
    ESTIMATE_READY("Estimate ready"),

    /** Still waiting to be picked up, sent by the hourly sweep */
    PICKUP_REMINDER("Pickup reminder"),

    /** Staff sent the status page link by hand */
    STATUS_LINK("Status link"),

    /** An admin trying the settings out, not about a ticket */
    TEST("Test");

    companion object {
        /** The ones the shop turns on or off, the others are sent when someone asks */
        val automatic = listOf(STATUS_CHANGED, READY_FOR_PICKUP, ESTIMATE_READY, PICKUP_REMINDER)
    }
}

@Serializable
enum class SmtpSecurity(val label: String, val defaultPort: Int) {
    /** Plain, then upgraded to TLS before logging in. Refused if the server can't */
    STARTTLS("STARTTLS", 587),

    /** TLS from the start */
    TLS("TLS", 465),

    /** No encryption, only for a relay on the shop's own network */
    NONE("None", 25)
}

/**
 * The shop's own mail server
 * @param password Never sent back by the server. When saving, null keeps the saved one and anything else replaces it
 */
@Serializable
data class SmtpSettings(
    val host: String = "",
    val port: Int = SmtpSecurity.STARTTLS.defaultPort,
    val security: SmtpSecurity = SmtpSecurity.STARTTLS,
    val username: String = "",
    val password: String? = null,
    val fromAddress: String = "",
    val fromName: String = ""
) {
    val isConfigured: Boolean
        get() = host.isNotBlank() && fromAddress.isNotBlank()
}

@Serializable
enum class SmsProvider(val label: String) {
    TWILIO("Twilio")
}

/**
 * The shop's text message account
 * @param authToken Never sent back by the server. When saving, null keeps the saved one and anything else replaces it
 * @param fromNumber The number messages come from, in international form like +15551234567
 * @param countryCode Digits, like `1`: the country customers' numbers saved without one are in
 */
@Serializable
data class SmsSettings(
    val provider: SmsProvider = SmsProvider.TWILIO,
    val accountSid: String = "",
    val authToken: String? = null,
    val fromNumber: String = "",
    val countryCode: String = "1"
) {
    val isConfigured: Boolean
        get() = accountSid.isNotBlank() && fromNumber.isNotBlank()
}

/**
 * The words of one kind of message. [subject] is only used for email. `{name}` style fields are filled in when it's
 * sent, see [MessageTemplates.FIELDS]
 */
@Serializable
data class MessageTemplate(
    val subject: String = "",
    val body: String = ""
)

/**
 * How the shop talks to customers. Kept apart from the shop profile because it holds the mail and text passwords,
 * and only admins read it
 * @param smtpPasswordSet Filled in by the server: whether a password is saved
 * @param smsTokenSet Filled in by the server: whether an auth token is saved
 * @param events The automatic messages that are on
 * @param statusChangeStatuses Statuses whose arrival sends [NotificationEvent.STATUS_CHANGED]
 * @param templates The shop's own words, by event. Missing ones use [MessageTemplates.defaults]
 */
@Serializable
data class NotificationSettings(
    val emailEnabled: Boolean = false,
    val smtp: SmtpSettings = SmtpSettings(),
    val smtpPasswordSet: Boolean = false,
    val smsEnabled: Boolean = false,
    val sms: SmsSettings = SmsSettings(),
    val smsTokenSet: Boolean = false,
    val events: Set<NotificationEvent> = setOf(NotificationEvent.READY_FOR_PICKUP, NotificationEvent.ESTIMATE_READY),
    val statusChangeStatuses: List<StatusChoice> = emptyList(),
    /** Days ready before the first pickup reminder */
    val reminderAfterDays: Int = 3,
    /** Days between reminders after that */
    val reminderEveryDays: Int = 7,
    /** Reminders sent at most for one ticket */
    val reminderLimit: Int = 3,
    val templates: Map<NotificationEvent, MessageTemplate> = emptyMap()
) {
    fun template(event: NotificationEvent, channel: NotificationChannel): MessageTemplate {
        val template = templates[event] ?: MessageTemplates.defaults.getValue(event)
        return if (channel == NotificationChannel.SMS) template.copy(subject = "") else template
    }

    fun channelReady(channel: NotificationChannel): Boolean = when (channel) {
        NotificationChannel.EMAIL -> emailEnabled && smtp.isConfigured
        NotificationChannel.SMS -> smsEnabled && sms.isConfigured
    }

    /** What's wrong with these settings, or null. Checked by both the client and the server */
    fun problem(): String? = when {
        emailEnabled && !smtp.isConfigured -> "Email needs a server and a from address"
        smtp.port !in 1..65535 -> "The mail server's port has to be between 1 and 65535"
        smtp.fromAddress.isNotBlank() && !EMAIL.matches(smtp.fromAddress.trim()) -> "The from address isn't an email address"
        smsEnabled && !sms.isConfigured -> "Text messages need an account and a from number"
        sms.fromNumber.isNotBlank() && !PHONE.matches(sms.fromNumber.trim()) -> "The from number has to look like +15551234567"
        !sms.countryCode.matches(Regex("[0-9]{1,3}")) -> "The country code is 1 to 3 digits, like 1 or 44"
        reminderAfterDays !in 1..365 || reminderEveryDays !in 1..365 -> "Reminder days have to be between 1 and 365"
        reminderLimit !in 0..20 -> "Up to 20 reminders"
        templates.values.any { it.body.length > MessageTemplates.MAX_BODY || it.subject.length > MessageTemplates.MAX_SUBJECT } ->
            "Messages can be up to ${MessageTemplates.MAX_BODY} characters, subjects ${MessageTemplates.MAX_SUBJECT}"
        templates.values.any { it.body.isBlank() } -> "Every message needs some words"
        else -> null
    }

    /** Without the password and token, for sending to a client */
    fun withoutSecrets(): NotificationSettings =
        copy(
            smtp = smtp.copy(password = null),
            smtpPasswordSet = !smtp.password.isNullOrEmpty(),
            sms = sms.copy(authToken = null),
            smsTokenSet = !sms.authToken.isNullOrEmpty()
        )

    /** These settings as saved over [saved]: a password or token left null keeps the saved one */
    fun keepingSecretsOf(saved: NotificationSettings): NotificationSettings =
        copy(
            smtp = smtp.copy(password = smtp.password ?: saved.smtp.password),
            sms = sms.copy(authToken = sms.authToken ?: saved.sms.authToken),
            smtpPasswordSet = false,
            smsTokenSet = false
        )

    companion object {
        val EMAIL = Regex("""^[^\s@]+@[^\s@]+\.[^\s@]+$""")
        val PHONE = Regex("""^\+[1-9][0-9]{6,14}$""")
    }
}

/**
 * The shop's words with the ticket's details put in
 */
object MessageTemplates {
    const val MAX_BODY = 2000
    const val MAX_SUBJECT = 200

    /** Every field a template can use, with what it's filled with */
    val FIELDS = listOf(
        "name" to "The customer's first name",
        "ticket" to "Ticket number",
        "device" to "Make and model",
        "status" to "The status, as customers see it",
        "link" to "The status page link",
        "estimate" to "The estimate's total",
        "balance" to "Still to pay",
        "shop" to "The shop's name",
        "shop_phone" to "The shop's phone"
    )

    private val FIELD = Regex("""\{([a-z_]+)\}""")

    val defaults: Map<NotificationEvent, MessageTemplate> = mapOf(
        NotificationEvent.STATUS_CHANGED to MessageTemplate(
            "Your {device}: {status}",
            "Hi {name}, your {device} (ticket {ticket}) is now: {status}. Follow it at {link}\n{shop}"
        ),
        NotificationEvent.READY_FOR_PICKUP to MessageTemplate(
            "Your {device} is ready",
            "Hi {name}, your {device} (ticket {ticket}) is ready to pick up. Balance: {balance}. {link}\n{shop} {shop_phone}"
        ),
        NotificationEvent.ESTIMATE_READY to MessageTemplate(
            "Estimate for your {device}",
            "Hi {name}, the estimate for your {device} is {estimate}. Approve or decline it at {link}\n{shop} {shop_phone}"
        ),
        NotificationEvent.PICKUP_REMINDER to MessageTemplate(
            "Reminder: your {device} is waiting",
            "Hi {name}, a reminder that your {device} (ticket {ticket}) is ready to pick up. {link}\n{shop} {shop_phone}"
        ),
        NotificationEvent.STATUS_LINK to MessageTemplate(
            "Your repair at {shop}",
            "Hi {name}, follow your {device} (ticket {ticket}) at {link}\n{shop}"
        ),
        NotificationEvent.TEST to MessageTemplate(
            "Test from {shop}",
            "This is a test message from {shop}. If you got it, the settings work."
        )
    )

    /** [text] with each `{field}` from [values] filled in, unknown ones left as they are */
    fun fill(text: String, values: Map<String, String>): String =
        FIELD.replace(text) { match -> values[match.groupValues[1]] ?: match.value }.trim()
}

@Serializable
enum class OutboxStatus(val label: String) {
    /** Waiting to be sent, or to be tried again */
    PENDING("Waiting"),
    SENT("Sent"),

    /** Gave up after too many tries, or the provider refused it outright */
    FAILED("Failed"),

    /** Called off, by an admin or because its channel was turned off */
    CANCELLED("Cancelled")
}

/**
 * One message to a customer, kept after it's sent so the ticket shows what they were told
 * @param createdBy Who sent it by hand, blank for automatic ones
 */
@Serializable
@OptIn(ExperimentalUuidApi::class)
data class OutboxMessage(
    val identifier: Uuid = Uuid.generateV7(),
    val ticketIdentifier: Uuid? = null,
    val ticketLabel: String = "",
    val customerIdentifier: Uuid? = null,
    val channel: NotificationChannel,
    val event: NotificationEvent,
    val recipient: String,
    val subject: String = "",
    val body: String,
    val status: OutboxStatus = OutboxStatus.PENDING,
    val attempts: Int = 0,
    val nextAttemptAt: Long = 0,
    val lastError: String = "",
    val createdAt: Long = 0,
    val sentAt: Long? = null,
    val createdBy: String = ""
)
