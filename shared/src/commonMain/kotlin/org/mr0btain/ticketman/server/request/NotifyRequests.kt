package org.mr0btain.ticketman.server.request

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.notify.NotificationChannel
import org.mr0btain.ticketman.data.notify.NotificationEvent
import org.mr0btain.ticketman.data.notify.NotificationSettings

/**
 * Sends a test message straight away, with the settings given rather than the saved ones, so an admin can try them
 * before saving. A null password or token uses the saved one
 */
@Serializable
data class TestMessageRequest(
    val settings: NotificationSettings,
    val channel: NotificationChannel,
    val recipient: String
)

/**
 * Sends a message about a ticket to its customer now, by hand
 * @param event Which template, [NotificationEvent.STATUS_LINK] unless staff pick another
 * @param channels Empty for every channel the customer has a contact for and the shop has on
 */
@Serializable
data class TicketMessageRequest(
    val event: NotificationEvent = NotificationEvent.STATUS_LINK,
    val channels: Set<NotificationChannel> = emptySet()
)
