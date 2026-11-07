package org.mr0btain.ticketman.data.notify

import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * A text a customer sent the shop's number, kept so staff can read and answer it
 * @param customerIdentifier The customer it's from: the one last texted from that number, or one with that phone
 * number. Null when nobody matched
 * @param ticketIdentifier The ticket it's about: the one the last text to that number was about. Null when it's not
 * a reply to one
 * @param sender The number it came from, in international form
 * @param readAt When someone at the shop marked it read, null while it's unread
 * @param optedOut It asked for no more texts (STOP and the like), so the customer's texts were turned off
 */
@OptIn(ExperimentalUuidApi::class)
@Serializable
data class InboundMessage(
    val identifier: Uuid = Uuid.generateV7(),
    val customerIdentifier: Uuid? = null,
    val customerName: String = "",
    val ticketIdentifier: Uuid? = null,
    val ticketLabel: String = "",
    val channel: NotificationChannel = NotificationChannel.SMS,
    val sender: String = "",
    val body: String = "",
    val receivedAt: Long = 0,
    val readAt: Long? = null,
    val readBy: String = "",
    val optedOut: Boolean = false
) {
    val isRead: Boolean
        get() = readAt != null
}

/**
 * The texts customers sent, for the counter
 * @param unread How many aren't read yet, everywhere
 * @param messages The newest, unread or not
 */
@Serializable
data class InboxResponse(
    val unread: Int = 0,
    val messages: List<InboundMessage> = emptyList()
)

/** Rules for texts in and replies out */
object Inbox {
    /** Longest reply staff can type, about ten text messages */
    const val MAX_REPLY = 1_500

    /** Longest text kept from a customer */
    const val MAX_BODY = 5_000

    /** Words a customer texts to stop getting texts, which the carrier and Twilio act on too */
    val STOP_WORDS = setOf("STOP", "STOPALL", "UNSUBSCRIBE", "CANCEL", "END", "QUIT", "OPTOUT", "REVOKE")

    /**
     * The address to give the text provider for texts coming in, null until the shop has a public address
     * @param provider Like `twilio`
     */
    fun webhookUrl(publicUrl: String, provider: String = "twilio"): String? =
        publicUrl.trim().trimEnd('/').takeIf { it.isNotEmpty() }?.let { "$it/status/sms/$provider/webhook" }

    /** Whether [body] is a request to stop texts */
    fun isStop(body: String): Boolean = body.trim().trimEnd('.', '!').uppercase() in STOP_WORDS
}
