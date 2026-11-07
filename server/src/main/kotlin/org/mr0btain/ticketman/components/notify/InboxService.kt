package org.mr0btain.ticketman.components.notify

import org.mr0btain.ticketman.data.ticket.TicketStatusType
import org.mr0btain.ticketman.components.database.CounterDatabase
import org.mr0btain.ticketman.components.database.AuditLogDatabase
import org.mr0btain.ticketman.components.database.CustomerDatabase
import org.mr0btain.ticketman.components.database.InboxDatabase
import org.mr0btain.ticketman.components.database.NotifyDatabase
import org.mr0btain.ticketman.components.database.ShopDatabase
import org.mr0btain.ticketman.data.audit.AuditAction
import org.mr0btain.ticketman.data.notify.InboundMessage
import org.mr0btain.ticketman.data.notify.Inbox
import org.mr0btain.ticketman.data.notify.InboxResponse
import org.mr0btain.ticketman.data.notify.NotificationChannel
import org.mr0btain.ticketman.data.notify.PhoneNumbers
import org.mr0btain.ticketman.logging.AppLogger
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Texts customers send the shop's number: taken from the provider's webhook once its signature checks out, matched
 * to the customer and ticket they're about, and kept for staff to read and answer
 * @param now Epoch millis, swapped in tests
 */
@OptIn(ExperimentalUuidApi::class)
@Suppress("PrivatePropertyName")
class InboxService(
    private val database: InboxDatabase,
    private val notify: NotifyDatabase,
    private val customers: CustomerDatabase,
    private val shop: ShopDatabase,
    private val auditLog: AuditLogDatabase,
    private val counter: CounterDatabase,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() }
) {
    private val TAG = "InboxService"
    private val WEBHOOK_TAG = "$TAG:Webhook"

    /** How a webhook call went */
    enum class Webhook { DONE, NOT_SET_UP, REFUSED }

    /**
     * A text from Twilio. Only taken when its `X-Twilio-Signature` is right for the shop's auth token and the
     * address Twilio was given, [Inbox.webhookUrl]
     * @param params Every form field Twilio sent, which the signature covers
     */
    fun twilio(signature: String?, params: Map<String, String>): Webhook {
        val settings = notify.settings()
        val token = settings.sms.authToken
        if (!settings.sms.isConfigured || token.isNullOrEmpty()) return Webhook.NOT_SET_UP

        val url = Inbox.webhookUrl(shop.get().publicUrl) ?: run {
            AppLogger.i(WEBHOOK_TAG, "A text came in, but the shop has no public address to check its signature against")
            return Webhook.NOT_SET_UP
        }
        if (signature == null || !TwilioSignature.matches(token, url, params, signature)) {
            AppLogger.i(WEBHOOK_TAG, "Refused a text whose signature doesn't match")
            return Webhook.REFUSED
        }

        val id = params["MessageSid"].orEmpty().ifBlank { params["SmsSid"].orEmpty() }
        val from = params["From"].orEmpty()
        if (id.isBlank() || from.isBlank()) return Webhook.REFUSED

        receive(id, from, params["Body"].orEmpty(), settings.sms.countryCode)
        return Webhook.DONE
    }

    /**
     * Keeps a text, matched to who sent it: the customer and ticket of the last text sent to that number, or else
     * a customer with that phone number and their one ticket not yet picked up, if they have just one. A stop request
     * turns off their texts. One already kept is left as it was
     * @return The text as kept, null when it was already kept or couldn't be
     */
    fun receive(providerId: String, from: String, body: String, countryCode: String): InboundMessage? {
        val sender = PhoneNumbers.toInternational(from, countryCode) ?: from.trim()
        val last = notify.lastTextTo(sender)
        val customer = last?.customerIdentifier?.let { customers.getById(it) }
            ?: customers.withPhone(sender, countryCode).singleOrNull()
        val stop = Inbox.isStop(body)
        val ticket = if (last != null && last.ticketIdentifier != null && customer?.identifier == last.customerIdentifier) {
            last.ticketIdentifier to last.ticketLabel
        } else {
            customer?.let { counter.customerTickets(it.identifier).map { summary -> summary.ticket } }
                ?.filter { it.status.status != TicketStatusType.PICKED_UP }
                ?.singleOrNull()?.let { it.identifier to it.label }
        }

        val message = InboundMessage(
            customerIdentifier = customer?.identifier,
            customerName = customer?.name.orEmpty(),
            ticketIdentifier = ticket?.first,
            ticketLabel = ticket?.second.orEmpty(),
            channel = NotificationChannel.SMS,
            sender = sender,
            body = body.take(Inbox.MAX_BODY),
            receivedAt = now(),
            optedOut = stop && customer != null
        )

        if (database.add(message, providerId) != true) return null

        if (message.optedOut) {
            customers.stopTexts(customer!!.identifier)
            auditLog.log(null, AuditAction.UPDATE, "customer", customer.identifier.toString(), "${customer.name} texted ${body.trim()}, so texts to them are off")
        }
        return message
    }

    fun inbox(limit: Int): InboxResponse? {
        val messages = database.newest(limit.coerceIn(1, MAX_LIMIT)) ?: return null
        return InboxResponse(unread = database.unreadCount() ?: return null, messages = messages)
    }

    fun forTicket(ticketIdentifier: Uuid): List<InboundMessage>? = database.forTicket(ticketIdentifier)

    fun markTicketRead(ticketIdentifier: Uuid, by: String): Boolean = database.markTicketRead(ticketIdentifier, now(), by)

    fun markRead(identifier: Uuid, by: String): Boolean = database.markRead(identifier, now(), by)

    companion object {
        const val DEFAULT_LIMIT = 50
        const val MAX_LIMIT = 200
    }
}

/**
 * Twilio's request signature: the address it called with every form field's name and value added in name order,
 * signed with HMAC-SHA1 under the account's auth token, in Base64
 */
object TwilioSignature {
    fun of(authToken: String, url: String, params: Map<String, String>): String {
        val data = url + params.toSortedMap().entries.joinToString("") { (key, value) -> key + value }
        val mac = Mac.getInstance("HmacSHA1").apply { init(SecretKeySpec(authToken.toByteArray(), "HmacSHA1")) }
        return Base64.getEncoder().encodeToString(mac.doFinal(data.toByteArray()))
    }

    fun matches(authToken: String, url: String, params: Map<String, String>, signature: String): Boolean =
        MessageDigest.isEqual(of(authToken, url, params).toByteArray(), signature.trim().toByteArray())
}
